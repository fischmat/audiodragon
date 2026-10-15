package de.matthiasfisch.audiodragon.core.platform

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.matthiasfisch.audiodragon.core.model.*
import io.reactivex.processors.PublishProcessor
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import kotlin.concurrent.thread

private val LOGGER = KotlinLogging.logger {}
private const val MAX_SAMPLE_RATE = 384000
private const val STDERR_LIMIT = 8192

/** Captures playback monitors via PipeWire's PulseAudio compatibility service. */
class PipeWireAudioPlatform internal constructor(
    private val launch: (List<String>) -> Process = { ProcessBuilder(it).start() },
    private val osName: String = System.getProperty("os.name"),
    private val discoveryTimeoutMillis: Long = 5000
) : AudioPlatform {
    override val platformId = "pipewire"

    override fun getAudioSources(): List<AudioSource> {
        checkLinux()
        val process = startProcess(launch, listOf("pactl", "--format=json", "list", "sinks"))
        val stdout = StreamCollector(process.inputStream, 4 * 1024 * 1024)
        val stderr = StreamCollector(process.errorStream, STDERR_LIMIT)
        try {
            check(process.waitFor(discoveryTimeoutMillis, TimeUnit.MILLISECONDS)) {
                "PipeWire discovery timed out. Check that pipewire-pulse is running for this desktop user."
            }
            stdout.await()
            stderr.await()
            check(process.exitValue() == 0) {
                "PipeWire discovery failed (exit ${process.exitValue()}): ${stderr.text()}"
            }
            check(!stdout.truncated) { "PipeWire discovery response exceeded the size limit." }
            val sinks = try {
                jacksonObjectMapper().readTree(stdout.text())
            } catch (e: Exception) {
                throw IllegalStateException("Invalid JSON from PipeWire discovery.", e)
            }
            check(sinks != null && sinks.isArray) { "PipeWire discovery must return a JSON array of sinks." }
            return sinks.map { sink ->
                val monitor = sink.path("monitor_source").takeIf { it.isTextual }?.asText()
                check(!monitor.isNullOrBlank()) { "PipeWire sink has no monitor source name." }
                val description = sink.path("description").asText("").ifBlank {
                    sink.path("name").asText(monitor)
                }
                val rate = Regex("(?:^|\\s)(\\d+)Hz(?:$|\\s)")
                    .find(sink.path("sample_specification").asText(""))
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?.takeIf { it in 1..MAX_SAMPLE_RATE } ?: 48000
                PipeWireAudioSource(monitor, "Monitor of $description", rate)
            }.distinctBy { it.monitorName }
        } finally {
            terminate(process)
            stdout.close()
            stderr.close()
        }
    }

    override fun createRecording(
        audioSource: AudioSource,
        audioFormat: AudioFormat,
        blockSize: Int,
        bufferFactory: AudioBufferFactory
    ): Recording<*> {
        checkLinux()
        require(audioSource is PipeWireAudioSource) { "The PipeWire backend requires a playback monitor source." }
        require(audioFormat.encoding == AudioFormat.Encoding.PCM_SIGNED && !audioFormat.isBigEndian) {
            "PipeWire capture requires signed little-endian integer PCM."
        }
        require(audioFormat.sampleSizeInBits in listOf(16, 24, 32)) { "Supported PCM widths are 16, 24 and 32 bits." }
        require(audioFormat.channels in 1..2) { "PipeWire capture supports mono or stereo." }
        val rate = audioFormat.sampleRate
        require(rate.isFinite() && rate >= 1 && rate <= MAX_SAMPLE_RATE && rate == rate.toInt().toFloat()) {
            "The sample rate must be an integer from 1 to $MAX_SAMPLE_RATE Hz."
        }
        require(audioFormat.frameSize == audioFormat.channels * (audioFormat.sampleSizeInBits / 8) &&
                audioFormat.frameRate == rate) { "PCM frame size or frame rate is inconsistent." }
        require(blockSize > 0) { "The recording block size must be positive." }
        return PipeWireRecording(audioSource, audioFormat, blockSize, bufferFactory, launch)
    }

    private fun checkLinux() {
        check(osName.startsWith("Linux", ignoreCase = true)) { "The PipeWire backend is supported on Linux only." }
    }
}

internal class PipeWireAudioSource(
    val monitorName: String,
    name: String,
    private val sampleRate: Int
) : AudioSource(name) {
    override val identity get() = "pipewire:$monitorName"

    override fun getAudioFormats(): List<AudioFormat> = listOf(2, 1).flatMap { channels ->
        listOf(32, 24, 16).map { bits ->
            AudioFormat(sampleRate.toFloat(), bits, channels, true, false)
        }
    }
}

private class PipeWireRecording(
    audioSource: PipeWireAudioSource,
    audioFormat: AudioFormat,
    blockSize: Int,
    bufferFactory: AudioBufferFactory,
    private val launch: (List<String>) -> Process
) : Recording<PipeWireAudioSource>(audioSource, audioFormat, blockSize, bufferFactory) {
    @Volatile private var process: Process? = null

    override fun onStopRequested() {
        // Cancellation must also force an unresponsive child to exit while stdout is blocked.
        process?.let { terminate(it) }
    }

    override fun record(chunkPublisher: PublishProcessor<AudioChunk>) {
        if (isStopRequested()) return
        val child = startProcess(launch, listOf(
            "parec", "--raw", "--device=${audioSource.monitorName}",
            "--format=s${audioFormat.sampleSizeInBits}le", "--rate=${audioFormat.sampleRate.toInt()}",
            "--channels=${audioFormat.channels}", "--client-name=AudioDragon",
            "--stream-name=System-audio", "--latency-msec=50"
        ))
        process = child
        val stderr = StreamCollector(child.errorStream, STDERR_LIMIT)
        try {
            if (isStopRequested()) {
                child.destroy()
                return
            }
            val frameSize = audioFormat.frameSize
            val framesPerBlock = (blockSize / frameSize).coerceAtLeast(1)
            val buffer = ByteArray(framesPerBlock * frameSize)
            var pending = 0
            while (!isStopRequested()) {
                val count = try {
                    child.inputStream.read(buffer, pending, buffer.size - pending)
                } catch (e: IOException) {
                    if (!isStopRequested()) throw e
                    break
                }
                if (count == -1) {
                    if (!isStopRequested()) {
                        publishRemainingFrames(chunkPublisher, buffer, pending)
                        child.waitFor(500, TimeUnit.MILLISECONDS)
                        stderr.await()
                        val exit = if (child.isAlive) "still running" else child.exitValue().toString()
                        throw IOException("PipeWire capture ended unexpectedly (exit $exit, " +
                                "${pending % frameSize} incomplete frame bytes): ${stderr.text()}")
                    }
                    break
                }
                pending += count
                if (pending == buffer.size) {
                    publish(chunkPublisher, buffer.copyOf())
                    pending = 0
                }
            }
            publishRemainingFrames(chunkPublisher, buffer, pending)
        } catch (e: Exception) {
            LOGGER.error(e) { "Recording of ${audioSource.name} failed. ${stderr.text()}" }
            throw e
        } finally {
            terminate(child)
            process = null
            stderr.close()
        }
    }

    private fun publish(publisher: PublishProcessor<AudioChunk>, bytes: ByteArray) {
        publisher.onNext(AudioChunk(bytes, audioSource, audioFormat))
    }

    private fun publishRemainingFrames(publisher: PublishProcessor<AudioChunk>, buffer: ByteArray, count: Int) {
        val completeFrameBytes = count - (count % audioFormat.frameSize)
        if (completeFrameBytes > 0) publish(publisher, buffer.copyOf(completeFrameBytes))
    }
}

private fun startProcess(launch: (List<String>) -> Process, command: List<String>): Process = try {
    launch(command)
} catch (e: IOException) {
    LOGGER.error(e) { "Could not start ${command.first()}; install pulseaudio-utils and check desktop audio-server access." }
    throw IllegalStateException("Could not start ${command.first()}. Install pulseaudio-utils and ensure " +
            "pipewire-pulse is accessible to the desktop user.", e)
}

private fun terminate(process: Process) {
    try {
        process.destroy()
        if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            check(process.waitFor(500, TimeUnit.MILLISECONDS)) { "PipeWire child process did not terminate." }
        }
    } finally {
        process.inputStream.close()
        process.errorStream.close()
        process.outputStream.close()
    }
}

/** Always drains the pipe, even after the retained diagnostic/output limit is reached. */
private class StreamCollector(private val input: InputStream, private val limit: Int) {
    private val output = ByteArrayOutputStream()
    @Volatile var truncated = false
        private set
    @Volatile private var failure: IOException? = null
    private val reader = thread(isDaemon = true, name = "pipewire-output") {
        try {
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                synchronized(output) {
                    val retained = count.coerceAtMost(limit - output.size())
                    output.write(buffer, 0, retained)
                    if (retained < count) truncated = true
                }
            }
        } catch (e: IOException) {
            failure = e
        }
    }

    fun await() {
        reader.join(500)
        check(!reader.isAlive) { "Timed out draining PipeWire process output." }
        failure?.let { throw IOException("Could not read PipeWire process output.", it) }
    }

    fun text(): String = synchronized(output) { output.toString(Charsets.UTF_8.name()).trim() }

    fun close() {
        input.close()
        reader.join(500)
    }
}
