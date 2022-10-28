package de.matthiasfisch.audiodragon.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SAMPLE_RATE = 100
private const val SAMPLE_SIZE_BYTES = 2
private const val CHANNELS = 2
private const val BYTES_PER_SECOND = SAMPLE_RATE * SAMPLE_SIZE_BYTES * CHANNELS
private val AUDIO_FORMAT = AudioFormat(SAMPLE_RATE.toFloat(), SAMPLE_SIZE_BYTES * 8, CHANNELS, true, true)

class PcmDataTest : FunSpec({

    fun generatePcmData(size: Int) = ByteArray(size) { (it % Byte.MAX_VALUE).toByte() }

    fun toBytes(value: Long, targetBits: Int, bigEndian: Boolean): List<Byte> {
        var bytes = value.toBigInteger()
            .toByteArray()
            .toList()
        while (bytes.size < targetBits / 8) {
            bytes = if (bytes.size + 1 == targetBits / 8) {
                listOf(if (value >= 0) 0x00.toByte() else 0xff.toByte()) + bytes
            } else {
                listOf(0x00.toByte()) + bytes
            }
        }
        return bytes.let { if (bigEndian) it else it.reversed() }
    }

    context("duration") {
        test("Duration for empty data is zero") {
            // Arrange
            val subject = generatePcmData(0)

            // Act + Assert
            subject.duration(AUDIO_FORMAT) shouldBe 0.milliseconds
        }

        test("Duration for non-empty buffer is correct") {
            // Arrange
            val subject = generatePcmData((3.25 * SAMPLE_RATE * SAMPLE_SIZE_BYTES * CHANNELS).toInt())

            // Act + Assert
            subject.duration(AUDIO_FORMAT) shouldBe 3250.milliseconds
        }
    }

    context("timeSlice") {
        val subject = generatePcmData(10 * BYTES_PER_SECOND) // 10 seconds

        test("Correct slice returned for zero offset") {
            // Arrange

            // Act
            val result = subject.timeSlice(AUDIO_FORMAT, 0.milliseconds, 1250.milliseconds)

            // Assert
            result shouldBe subject.sliceArray(0 until (1.25 * BYTES_PER_SECOND).toInt())
        }

        test("Correct slice returned for inner slice") {
            // Arrange

            // Act
            val result = subject.timeSlice(AUDIO_FORMAT, 1500.milliseconds, 1250.milliseconds)

            // Assert
            result shouldBe subject.sliceArray((1.5 * BYTES_PER_SECOND).toInt() until (2.75 * BYTES_PER_SECOND).toInt())
        }

        test("Correct slice returned for slice overlapping end") {
            // Arrange

            // Act
            val result = subject.timeSlice(AUDIO_FORMAT, 9000.milliseconds, 1250.milliseconds)

            // Assert
            result shouldBe subject.sliceArray(9 * BYTES_PER_SECOND until 10 * BYTES_PER_SECOND)
        }

        test("Empty slice returned for offset at end") {
            // Arrange

            // Act
            val result = subject.timeSlice(AUDIO_FORMAT, 10.seconds, 1250.milliseconds)

            // Assert
            result shouldBe ByteArray(0)
        }

        test("Correct slice returned for zero length") {
            // Arrange

            // Act
            val result = subject.timeSlice(AUDIO_FORMAT, 5.milliseconds, 0.milliseconds)

            // Assert
            result shouldBe ByteArray(0)
        }
    }

    context("toDiscreteFrames") {
        test("Correct result for big endian data, standard word size, unsigned encoding") {
            // Arrange
            val values = listOf(
                listOf(1331, 256),
                listOf(0, 138)
            )
            val pcmData = values.flatMap { frame ->
                frame.flatMap { toBytes(it.toLong(), targetBits = 16, bigEndian = true) }
            }.toByteArray()
            val audioFormat = AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 16, 2, 4, 44000f, true)

            // Act
            val result = pcmData.toDiscreteFrames(audioFormat)

            // Assert
            result shouldBe values
        }

        test("Correct result for little endian data, standard word size, unsigned encoding") {
            // Arrange
            val values = listOf(
                listOf(1331, 256),
                listOf(0, 138)
            )
            val pcmData = values.flatMap { frame ->
                frame.flatMap { toBytes(it.toLong(), targetBits = 16, bigEndian = false) }
            }.toByteArray()
            val audioFormat = AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 16, 2, 4, 44000f, false)

            // Act
            val result = pcmData.toDiscreteFrames(audioFormat)

            // Assert
            result shouldBe values
        }

        test("Correct result for big endian data, non-standard word size, unsigned encoding") {
            // Arrange
            val values = listOf(
                listOf(1331, 256),
                listOf(0, 138)
            )
            val pcmData = values.flatMap { frame ->
                frame.flatMap { toBytes(it.toLong(), targetBits = 24, bigEndian = true) }
            }.toByteArray()
            val audioFormat = AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 24, 2, 6, 44000f, true)

            // Act
            val result = pcmData.toDiscreteFrames(audioFormat)

            // Assert
            result shouldBe values
        }

        test("Correct result for little endian data, non-standard word size, unsigned encoding") {
            // Arrange
            val values = listOf(
                listOf(1331, 256),
                listOf(0, 138)
            )
            val pcmData = values.flatMap { frame ->
                frame.flatMap { toBytes(it.toLong(), targetBits = 24, bigEndian = false) }
            }.toByteArray()
            val audioFormat = AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 24, 2, 6, 44000f, false)

            // Act
            val result = pcmData.toDiscreteFrames(audioFormat)

            // Assert
            result shouldBe values
        }

        test("Correct result for big endian data, standard word size, signed encoding") {
            // Arrange
            val values = listOf(
                listOf(-1, -125),
                listOf(1331, 256),
                listOf(0, 138)
            )
            val pcmData = values.flatMap { frame ->
                frame.flatMap { toBytes(it.toLong(), targetBits = 16, bigEndian = true) }
            }.toByteArray()
            val audioFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44000f, 16, 2, 4, 44000f, true)

            // Act
            val result = pcmData.toDiscreteFrames(audioFormat)

            // Assert
            result shouldBe values
        }

        test("Correct result for little endian data, standard word size, signed encoding") {
            // Arrange
            val values = listOf(
                listOf(-1, -125),
                listOf(1331, 256),
                listOf(0, 138)
            )
            val pcmData = values.flatMap { frame ->
                frame.flatMap { toBytes(it.toLong(), targetBits = 16, bigEndian = false) }
            }.toByteArray()
            val audioFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44000f, 16, 2, 4, 44000f, false)

            // Act
            val result = pcmData.toDiscreteFrames(audioFormat)

            // Assert
            result shouldBe values
        }

        test("Correct result for big endian data, non-standard word size, signed encoding") {
            // Arrange
            val values = listOf(
                listOf(-1, -125),
                listOf(1331, 256),
                listOf(0, 138)
            )
            val pcmData = values.flatMap { frame ->
                frame.flatMap { toBytes(it.toLong(), targetBits = 16, bigEndian = true) }
            }.toByteArray()
            val audioFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44000f, 16, 2, 4, 44000f, true)

            // Act
            val result = pcmData.toDiscreteFrames(audioFormat)

            // Assert
            result shouldBe values
        }
    }
})
