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
})
