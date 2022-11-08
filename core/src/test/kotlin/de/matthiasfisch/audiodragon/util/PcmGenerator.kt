package de.matthiasfisch.audiodragon.util

import de.matthiasfisch.audiodragon.model.PcmData
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class PcmGenerator(val pcm: PcmData, private val samplingRate: Float) {
    constructor(samplingRate: Float): this(PcmData(0), samplingRate)

    fun tone(hz: Float, length: Duration, amplitude: Float = 1f): PcmGenerator {
        val newPcm = generateFrequency(hz, amplitude, samplingRate, length)
        return PcmGenerator(pcm + newPcm.first, samplingRate)
    }

    fun silence(length: Duration): PcmGenerator = tone(1f, length, 0f)

    fun audioFormat() = generatedAudioFormat(samplingRate)
}

fun generateFrequency(hz: Float, amplitude: Float, samplingRate: Float, length: Duration): Pair<PcmData, AudioFormat> {
    require(hz > 0) { "Frequency must be positive" }
    require(amplitude in 0f..1f) { "Amplitude must be in [0, 1]" }
    require(samplingRate > 0) { "Sampling rate must be positive" }
    require(length > 0.milliseconds) { "Length must be positive" }

    val audioFormat = generatedAudioFormat(samplingRate)
    val numSamples = (samplingRate * (length.inWholeMilliseconds/1000f)).toInt()
    val buffer = ByteArrayOutputStream(numSamples * Int.SIZE_BYTES)

    for (sampleNum in 0 until numSamples) {
        val time = sampleNum / numSamples.toDouble()
        val sineValue = sin(2 * Math.PI * hz * time)
        val value = (sineValue * Int.MAX_VALUE * amplitude).toInt()
        buffer.write(value.toByteArray())
    }
    return buffer.toByteArray() to audioFormat
}

fun generateSilence(samplingRate: Float, length: Duration): Pair<PcmData, AudioFormat> =
    generateFrequency(1f, 0f, samplingRate, length)

private fun generatedAudioFormat(samplingRate: Float) = AudioFormat(
    AudioFormat.Encoding.PCM_SIGNED,
    samplingRate,
    Int.SIZE_BITS,
    1,
    Int.SIZE_BYTES,
    samplingRate,
    true
)

private fun Int.toByteArray(): ByteArray {
    val buffer = ByteArray(4)
    buffer[3] = (this shr 0).toByte()
    buffer[2] = (this shr 8).toByte()
    buffer[1] = (this shr 16).toByte()
    buffer[0] = (this shr 24).toByte()
    return buffer
}