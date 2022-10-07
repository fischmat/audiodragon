package de.matthiasfisch.audiodragon.model

import de.matthiasfisch.audiodragon.fft.JavaFFT
import de.matthiasfisch.audiodragon.util.durationToByteCount
import de.matthiasfisch.audiodragon.util.getEffectiveFrameRate
import de.matthiasfisch.audiodragon.util.getEffectiveFrameSize
import io.reactivex.internal.util.Pow2.isPowerOfTwo
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

typealias PcmData = ByteArray

fun PcmData.duration(audioFormat: AudioFormat): Duration {
    val frameRate = audioFormat.getEffectiveFrameRate()
    val frameSize = audioFormat.getEffectiveFrameSize()

    check(this.size % frameSize == 0) { "The PCM sample has size ${this.size} bytes, which is not a multiple of the provided frame size of $frameSize bytes." }
    val framesInSample = this.size / frameSize
    return ((framesInSample / frameRate) * 1000).roundToInt().milliseconds
}

fun PcmData.timeSlice(audioFormat: AudioFormat, offset: Duration, length: Duration): PcmData {
    val byteOffset = audioFormat.durationToByteCount(offset).toInt()
    val byteLength = audioFormat.durationToByteCount(length).toInt()
    return this.slice(byteOffset until min(size, byteOffset + byteLength)).toByteArray()
}

fun PcmData.getFrequencies(audioFormat: AudioFormat): DoubleArray {
    return JavaFFT.getFrequencies(averagedFloats(audioFormat).toFloatArray())
}

fun PcmData.getFrequenciesPerChannel(audioFormat: AudioFormat) =
    floatsPerChannel(audioFormat).map { JavaFFT.getFrequencies(it.toFloatArray()) }

fun PcmData.getRMS(audioFormat: AudioFormat) = getRMSPerChannel(audioFormat).average()

fun PcmData.getRMSPerChannel(audioFormat: AudioFormat) = floatsPerChannel(audioFormat).map { frame ->
    sqrt(frame.map { it * it }.sum() / frame.size)
}

fun PcmData.averagedFloats(audioFormat: AudioFormat) = combineChannels(floatsPerChannel(audioFormat)) { f1, f2 -> (f1 + f2)/2 }

fun PcmData.floatsPerChannel(audioFormat: AudioFormat): List<List<Float>> {
    val maxPossibleValue = 2f.pow(audioFormat.sampleSizeInBits - 1) - 1
    val frames = toDiscreteFrames(audioFormat).map { frame ->
        frame.map { it / maxPossibleValue }
    }
    return transpose(frames)
}

fun PcmData.toDiscreteFrames(audioFormat: AudioFormat): List<List<Long>> {
    val byteOrder = if (audioFormat.isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    val pcmBuffer = ByteBuffer.wrap(this).order(byteOrder)

    val framesInSample = this.size / audioFormat.frameSize
    return (0 until framesInSample).map { getNextFrame(pcmBuffer, audioFormat) }
}

private fun getNextFrame(pcmBuffer: ByteBuffer, audioFormat: AudioFormat): List<Long> {
    val sampleSizeInBytes = audioFormat.sampleSizeInBits / 8
    val isSigned = when (audioFormat.encoding) {
        AudioFormat.Encoding.PCM_UNSIGNED -> false
        AudioFormat.Encoding.PCM_SIGNED -> true
        else -> throw IllegalArgumentException("Unsupported encoding ${audioFormat.encoding}.")
    }

    return (1..audioFormat.channels).map {
        val sampleBytes = ByteArray(sampleSizeInBytes)
        pcmBuffer.get(sampleBytes, 0, sampleSizeInBytes)
        bytesToLong(sampleBytes, audioFormat.isBigEndian, isSigned)
    }
}

fun bytesToLong(bytes: ByteArray, bigEndian: Boolean, signed: Boolean): Long {
    val bytesBE = if (bigEndian) bytes else bytes.reversedArray()
    // In case the value is unsigned, prepend 0x00 to ensure that we end up with a positive value (msb = 0 indicates positive value in twos complement)
    val padded = if (signed) bytesBE else arrayOf(0x00.toByte()).plus(bytesBE.toTypedArray()).toByteArray()
    return BigInteger(padded).toLong()
}

private fun <E> transpose(xs: List<List<E>>): List<List<E>> {
    fun <E> List<E>.head(): E = this.first()
    fun <E> List<E>.tail(): List<E> = this.takeLast(this.size - 1)
    fun <E> E.append(xs: List<E>): List<E> = listOf(this).plus(xs)

    xs.filter { it.isNotEmpty() }.let { ys ->
        return when (ys.isNotEmpty()) {
            true -> ys.map { it.head() }.append(transpose(ys.map { it.tail() }))
            else -> emptyList()
        }
    }
}

private fun <T> combineChannels(channelValues: List<List<T>>, transform: (T, T) -> T) =
    channelValues.reduce { ch1, ch2 ->
        ch1.zip(ch2, transform)
    }