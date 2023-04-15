package de.matthiasfisch.audiodragon.core.platform.pactl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.buildobjects.process.ProcBuilder
import java.io.InputStream
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat

object Pactl {
    private val objectMapper = jacksonObjectMapper()

    fun getPcmSources(): List<PulseAudioDevice> {
        check(isPactlInstalled()) { "pactl is not available. Try installing it with apt-get install pulseaudio-utils." }

        val json = ProcBuilder("pactl")
            .withVar("LC_ALL", "en_US.UTF-8") // Comma decimal separator is used with e.g. German locale
            .withArg("--format=json")
            .withArg("list")
            .run()
            .outputString

        return objectMapper.readTree(json).get("sources").map {
            PulseAudioDevice(it)
        }
    }

    fun record(deviceName: String, format: AudioFormat, bufferSize: Int): InputStream {
        val pactlFormat = PactlSampleSpecification(format).toFormatSpecificationString()

        val proc = ProcessBuilder(
            listOf(
                "parec",
                "-d",
                deviceName,
                "--raw",
                "--format=$pactlFormat",
                "--rate=${format.sampleRate}",
                "--channels=${format.channels}"
            )
        ).redirectOutput(ProcessBuilder.Redirect.PIPE)
         .start()
        val inStream = proc.inputStream.buffered(bufferSize)

        return object : InputStream() {
            override fun read(): Int = inStream.read()

            override fun close() {
                proc.destroy()
            }
        }
    }

    fun isPactlInstalled(): Boolean {
        return isBinaryInstalled("pactl") && isBinaryInstalled("parec")
    }

    private fun isBinaryInstalled(binaryName: String): Boolean {
        return ProcBuilder.run("whereis", "-b", binaryName)
            .replace("pactl: ", "")
            .split(" ")
            .isNotEmpty()
    }
}

class PulseAudioDevice(
    val name: String,
    val description: String,
    val formats: List<String>,
    val sampleSpecification: PactlSampleSpecification
) {
    constructor(node: JsonNode) : this(
        node.get("name").textValue(),
        node.get("description").textValue(),
        node.get("formats").map { it.textValue() },
        PactlSampleSpecification(node.get("sample_specification").textValue())
    )
}

data class PactlSampleSpecification(
    val encoding: AudioFormat.Encoding,
    val bitsPerSample: Int,
    val channels: Int,
    val sampleRate: Float,
    val isBigEndian: Boolean
) {
    constructor(sampleSpec: String) : this(
        encoding = getEncoding(sampleSpec),
        bitsPerSample = getBitsPerSample(sampleSpec),
        channels = getChannels(sampleSpec),
        sampleRate = getSampleRate(sampleSpec),
        isBigEndian = isBigEndian(sampleSpec)
    )

    constructor(audioFormat: AudioFormat) : this(
        encoding = audioFormat.encoding,
        bitsPerSample = audioFormat.sampleSizeInBits,
        channels = audioFormat.channels,
        sampleRate = audioFormat.sampleRate,
        isBigEndian = audioFormat.isBigEndian
    )

    fun toAudioFormat() = AudioFormat(
        encoding, sampleRate, bitsPerSample, channels, channels * (bitsPerSample / 8), sampleRate, isBigEndian
    )

    fun toFormatSpecificationString(): String {
        if (encoding == AudioFormat.Encoding.PCM_UNSIGNED && bitsPerSample == 8) {
            return "u8"
        }

        val format = when (encoding) {
            AudioFormat.Encoding.ALAW -> "aLaw"
            AudioFormat.Encoding.ULAW -> "uLaw"
            AudioFormat.Encoding.PCM_FLOAT -> "float"
            AudioFormat.Encoding.PCM_SIGNED -> "s"
            AudioFormat.Encoding.PCM_UNSIGNED -> "u"
            else -> throw IllegalStateException("Unknown encoding $encoding.")
        }
        val sampleBits = bitsPerSample.toString()
        val endianess = if (isBigEndian) "be" else "le"
        return format + sampleBits + endianess
    }

    companion object {
        private fun getEncoding(sampleSpec: String): AudioFormat.Encoding =
            with(splitSampleSpec(sampleSpec)[0]) {
                if (equals("uLaw")) return@with AudioFormat.Encoding.ULAW
                if (equals("aLaw")) return@with AudioFormat.Encoding.ALAW
                if (startsWith("float")) return@with AudioFormat.Encoding.PCM_FLOAT
                if (startsWith("u")) return@with AudioFormat.Encoding.PCM_UNSIGNED
                if (startsWith("s")) return@with AudioFormat.Encoding.PCM_SIGNED
                throw IllegalArgumentException("Unknown sample encoding defined by specification '$sampleSpec'.")
            }

        private fun getBitsPerSample(sampleSpec: String): Int =
            with(splitSampleSpec(sampleSpec)[0]) {
                if (equals("uLaw") || equals("aLaw")) return@with 8
                val pattern = "(s|u|float)([0-9]+)".toRegex()
                pattern.find(this)
                    ?.groups
                    ?.get(2)
                    ?.value
                    ?.toInt()
                    ?: throw IllegalArgumentException("Could not extract sample size from specification '$sampleSpec'")
            }

        private fun isBigEndian(sampleSpec: String) =
            with(splitSampleSpec(sampleSpec)[0]) {
                val nativeByteOrder = ByteOrder.nativeOrder()
                if (lowercase() in listOf("u8", "ulaw", "alaw")) return@with false
                if (endsWith("be")) return@with true
                if (endsWith("le")) return@with false
                if (endsWith("re")) return@with nativeByteOrder != ByteOrder.BIG_ENDIAN
                if (endsWith("ne")) return@with nativeByteOrder == ByteOrder.BIG_ENDIAN
                throw IllegalArgumentException("Could not extract byte order from specification '$sampleSpec'.")
            }

        private fun getChannels(sampleSpec: String): Int =
            with(splitSampleSpec(sampleSpec)[1]) {
                trim().lowercase().removeSuffix("ch").toInt()
            }

        private fun getSampleRate(sampleSpec: String): Float =
            with(splitSampleSpec(sampleSpec)[2]) {
                trim().lowercase().removeSuffix("hz").toFloat()
            }

        private fun splitSampleSpec(sampleSpec: String) =
            sampleSpec.trim().split(" ").also {
                require(it.size == 3) { "Sample specification is malformed and can't be parsed." }
            }
    }
}