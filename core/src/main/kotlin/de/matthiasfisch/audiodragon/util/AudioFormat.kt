package de.matthiasfisch.audiodragon.util

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun AudioFormat.getEffectiveFrameRate(): Float {
    require(
        sampleRate != AudioSystem.NOT_SPECIFIED.toFloat()
                || frameRate != AudioSystem.NOT_SPECIFIED.toFloat()
    ) { "Sample rate not specified. Can't calculate sample duration." }
    return if (frameRate != AudioSystem.NOT_SPECIFIED.toFloat()) frameRate else sampleRate
}

fun AudioFormat.getEffectiveFrameSize(): Int {
    require(frameSize != AudioSystem.NOT_SPECIFIED || channels != AudioSystem.NOT_SPECIFIED) { "Channel not specified. Can't calculate sample duration." }
    require(frameSize != AudioSystem.NOT_SPECIFIED || sampleSizeInBits != AudioSystem.NOT_SPECIFIED) { "Sample size not specified. Can't calculate sample duration." }
    return if (frameSize != AudioSystem.NOT_SPECIFIED) frameSize else (channels * sampleSizeInBits)/8
}

fun AudioFormat.durationToFrameCount(duration: Duration): Long = floor(duration.inWholeMilliseconds * (getEffectiveFrameRate() / 1000f)).toLong()

fun AudioFormat.durationToByteCount(duration: Duration): Long = durationToFrameCount(duration) * getEffectiveFrameSize()

fun AudioFormat.frameCountToDuration(frameCount: Long) = floor((frameCount / getEffectiveFrameRate()) * 1000f).toInt().milliseconds

fun AudioFormat.byteCountToDuration(byteCount: Long) = frameCountToDuration(byteCount / getEffectiveFrameSize())