package de.matthiasfisch.audiodragon.model

import de.matthiasfisch.audiodragon.util.AudioMetricsUtil
import de.matthiasfisch.audiodragon.util.durationToByteCount
import de.matthiasfisch.audiodragon.util.getEffectiveFrameRate
import de.matthiasfisch.audiodragon.util.getEffectiveFrameSize
import javax.sound.sampled.AudioFormat
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

typealias PcmData = ByteArray

fun PcmData.duration(audioFormat: AudioFormat): Duration {
    val frameRate = audioFormat.getEffectiveFrameRate()
    val frameSize = audioFormat.getEffectiveFrameSize()

    check(this.size % frameSize == 0) { "The PCM sample has size ${this.size} bytes, which is not a multiple of the provided frame size of $frameSize bytes." }
    val framesInSample = this.size / frameSize
    return ((framesInSample / frameRate)*1000).roundToInt().milliseconds
}

fun PcmData.timeSlice(audioFormat: AudioFormat, offset: Duration, length: Duration): PcmData {
    val byteOffset = audioFormat.durationToByteCount(offset).toInt()
    val byteLength = audioFormat.durationToByteCount(length).toInt()
    return this.slice(byteOffset until min(size, byteOffset + byteLength)).toByteArray()
}

fun PcmData.getRMS(audioFormat: AudioFormat): Double = AudioMetricsUtil.getRMS(this, audioFormat)