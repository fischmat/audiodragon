package de.matthiasfisch.audiodragon.core.platform

import de.matthiasfisch.audiodragon.core.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.core.model.*
import de.matthiasfisch.audiodragon.core.recognition.shazam.convertPcmDataToShazamFormat
import de.matthiasfisch.audiodragon.core.writer.MP3FileWriter
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import javax.sound.sampled.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun pcm(samples: LongArray, bits: Int): ByteArray = samples.flatMap { sample ->
    (0 until bits / 8).map { byte -> (sample shr (byte * 8)).toByte() }
}.toByteArray()

private fun tone(bits: Int, frames: Int, rate: Int): ByteArray {
    // Exact power-of-two scaling makes the waveform identical across sample widths.
    val samples = LongArray(frames * 2) { index ->
        val base = (sin(2 * PI * 440 * (index / 2) / rate) * 16000).toLong()
        base shl (bits - 16)
    }
    return pcm(samples, bits)
}

class HighPrecisionPcmTest : FunSpec({
    test("Integer extremes retain their sign and normalize consistently at every width") {
        for (bits in listOf(16, 24, 32)) {
            val min = -(1L shl (bits - 1))
            val max = (1L shl (bits - 1)) - 1
            val bytes = pcm(longArrayOf(min, min, 0, 0, max, max), bits)
            val format = AudioFormat(48000f, bits, 2, true, false)
            bytes.toDiscreteFrames(format) shouldBe listOf(listOf(min, min), listOf(0L, 0L), listOf(max, max))
            val floats = bytes.averagedFloats(format)
            (abs(floats[0] + 1) < 0.0001) shouldBe true
            floats[1] shouldBe 0f
            (abs(floats[2] - 1) < 0.0001) shouldBe true
            (abs(bytes.getRMS(format) - kotlin.math.sqrt(2.0 / 3)) < 0.0001) shouldBe true
        }
    }

    test("High precision frequency processing matches the 16-bit waveform") {
        val baseline = tone(16, 1024, 48000).getFrequencies(AudioFormat(48000f, 16, 2, true, false))
        for (bits in listOf(24, 32)) {
            val frequencies = tone(bits, 1024, 48000).getFrequencies(AudioFormat(48000f, bits, 2, true, false))
            frequencies.size shouldBe baseline.size
            frequencies.indices.forEach { index ->
                (abs(frequencies[index] - baseline[index]) <= abs(baseline[index]) * 0.0001 + 0.001) shouldBe true
            }
        }
    }

    test("Recognition conversion handles high precision stereo and resampling without changing source PCM") {
        for (rate in listOf(44100, 48000)) {
            val baseline = convertPcmDataToShazamFormat(tone(16, rate / 10, rate), AudioFormat(rate.toFloat(), 16, 2, true, false))
            (baseline.size in 8700..8900) shouldBe true
            for (bits in listOf(24, 32)) {
                val input = tone(bits, rate / 10, rate)
                val original = input.copyOf()
                val converted = convertPcmDataToShazamFormat(input, AudioFormat(rate.toFloat(), bits, 2, true, false))
                converted.size shouldBe baseline.size
                val expectedSamples = ByteBuffer.wrap(baseline).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val actualSamples = ByteBuffer.wrap(converted).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                // Java Sound's normalization rounds differently at different source widths.
                while (actualSamples.hasRemaining()) {
                    (abs(actualSamples.get().toInt() - expectedSamples.get().toInt()) <= 2) shouldBe true
                }
                input shouldBe original
            }
        }
    }

    test("MP3 writer encodes equivalent 16-, 24- and 32-bit waveforms identically") {
        val outputs = listOf(16, 24, 32).map { bits ->
            val input = tone(bits, 48000, 48000)
            val original = input.copyOf()
            val audio = InMemoryAudioBuffer(AudioFormat(48000f, bits, 2, true, false), input.size)
            audio.add(input)
            val writer = MP3FileWriter(tempdir().toPath(), 320, 2, 2, false, coverartMaxDimension = 500)
            val file = writer.writeToFile(audio, null)
            audio.get().toArray().map { it as Byte }.toByteArray() shouldBe original
            file.toFile().readBytes().also { (it.size > 1000) shouldBe true }
        }
        outputs[1] shouldBe outputs[0]
        outputs[2] shouldBe outputs[0]
    }
})
