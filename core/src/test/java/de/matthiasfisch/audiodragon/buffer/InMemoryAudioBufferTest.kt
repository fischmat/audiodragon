package de.matthiasfisch.audiodragon.buffer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SAMPLE_RATE = 100
private const val SAMPLE_SIZE_BYTES = 2
private const val CHANNELS = 2
private val AUDIO_FORMAT = AudioFormat(SAMPLE_RATE.toFloat(), SAMPLE_SIZE_BYTES * 8, CHANNELS, true, true)

class InMemoryAudioBufferTest : FunSpec({

    fun generatePcmData(size: Int) = ByteArray(size) { (it % Byte.MAX_VALUE).toByte() }

    context("get") {
        test("Small chunk can be added and retrieved again") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val data = generatePcmData(10)

            // Act
            subject.add(data)
            val result = subject.get().toArray()

            // Assert
            result shouldBe data
        }

        test("Large chunk exceeding initial size can be added and retrieved again") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val data = generatePcmData(123)

            // Act
            subject.add(data)
            val result = subject.get().toArray()

            // Assert
            result shouldBe data
        }

        test("Multiple chunks can be added and retrieved again") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val data1 = generatePcmData(10)
            val data2 = generatePcmData(40)
            val data3 = generatePcmData(90)

            // Act
            subject.add(data1)
            subject.add(data2)
            subject.add(data3)
            val result = subject.get().toArray()

            // Assert
            result shouldBe data1 + data2 + data3
        }

        test("Multiple can be added concurrently and retrieved again") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val chunks = IntRange(1, 50).map { generatePcmData(it) }

            // Act
            chunks.map { CompletableFuture.supplyAsync { subject.add(it) } }
                .forEach { it.join() }
            val result = subject.get().toArray()

            // Assert
            result.size shouldBe chunks.sumOf { it.size }
            chunks.forEach {
                Collections.indexOfSubList(result.toList(), it.toList()) shouldNotBe -1
            }
        }

        test("Reads between writes don't affect result") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val data1 = generatePcmData(30)
            val data2 = generatePcmData(30)
            val data3 = generatePcmData(200)

            // Act
            subject.add(data1)
            val result1 = subject.get().toArray()
            subject.add(data2)
            val result2 = subject.get().toArray()
            subject.add(data3)
            val result3 = subject.get().toArray()

            // Assert
            result1 shouldBe data1
            result2 shouldBe data1 + data2
            result3 shouldBe data1 + data2 + data3
        }
    }

    context("size") {
        test("Size is correct when after adding a small chunk") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val data = generatePcmData(10)

            // Act
            subject.add(data)

            // Act
            subject.size() shouldBe data.size
        }

        test("Size is correct after adding a large chunk exceeding initial buffer size") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val data = generatePcmData(150)

            // Act
            subject.add(data)

            // Act
            subject.size() shouldBe data.size
        }

        test("Size is correct after adding multiple chunks exceeding initial buffer size") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val data1 = generatePcmData(30)
            val data2 = generatePcmData(30)
            val data3 = generatePcmData(60)

            // Act
            subject.add(data1)
            subject.add(data2)
            subject.add(data3)

            // Act
            subject.size() shouldBe 120
        }
    }

    context("get time-slice") {
        test("Slice until end is retrievable") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val bytesPerSecond = SAMPLE_RATE * SAMPLE_SIZE_BYTES * CHANNELS
            val data = generatePcmData(10 * bytesPerSecond) // 10 seconds
            subject.add(data)

            // Act
            val result = subject.get(4.seconds).toArray()

            // Assert
            result shouldBe data.slice(4 * bytesPerSecond until 10 * bytesPerSecond)
        }

        test("Slice from start is retrievable") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val bytesPerSecond = SAMPLE_RATE * SAMPLE_SIZE_BYTES * CHANNELS
            val data = generatePcmData(10 * bytesPerSecond) // 10 seconds
            subject.add(data)

            // Act
            val result = subject.get(0.seconds).toArray()

            // Assert
            result shouldBe data
        }

        test("Slice starting at higher resolution than sample rate is retrievable") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val bytesPerSecond = SAMPLE_RATE * SAMPLE_SIZE_BYTES * CHANNELS
            val data = generatePcmData(10 * bytesPerSecond) // 10 seconds
            subject.add(data)

            // Act
            val result = subject.get(4.seconds + 5.milliseconds).toArray()

            // Assert
            result shouldBe data.slice(4 * bytesPerSecond until 10 * bytesPerSecond)
        }

        test("Slice with length is retrievable") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val bytesPerSecond = SAMPLE_RATE * SAMPLE_SIZE_BYTES * CHANNELS
            val data = generatePcmData(10 * bytesPerSecond) // 10 seconds
            subject.add(data)

            // Act
            val result = subject.get(4.seconds, 1.seconds).toArray()

            // Assert
            result shouldBe data.slice(4 * bytesPerSecond until 5 * bytesPerSecond)
        }

        test("Slice with zero length is empty") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val bytesPerSecond = SAMPLE_RATE * SAMPLE_SIZE_BYTES * CHANNELS
            val data = generatePcmData(10 * bytesPerSecond) // 10 seconds
            subject.add(data)

            // Act
            val result = subject.get(4.seconds, 0.seconds).toArray()

            // Assert
            result.shouldBeEmpty()
        }

        test("Slice with length overlapping end is retrievable") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val bytesPerSecond = SAMPLE_RATE * SAMPLE_SIZE_BYTES * CHANNELS
            val data = generatePcmData(10 * bytesPerSecond) // 10 seconds
            subject.add(data)

            // Act
            val result = subject.get(9.seconds, 3.seconds).toArray()

            // Assert
            result shouldBe data.slice(9 * bytesPerSecond until 10 * bytesPerSecond)
        }
    }

    context("duration") {
        test("Correct duration is returned for non-empty buffer") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)
            val bytesPerSecond = SAMPLE_RATE * SAMPLE_SIZE_BYTES * CHANNELS
            val data = generatePcmData((3.25 * bytesPerSecond).toInt())
            subject.add(data)

            // Act
            val result = subject.duration()

            // Assert
            result shouldBe 3250.milliseconds
        }

        test("Correct duration is returned for empty buffer") {
            // Arrange
            val subject = InMemoryAudioBuffer(AUDIO_FORMAT, 50)

            // Act
            val result = subject.duration()

            // Assert
            result shouldBe 0.milliseconds
        }
    }
})
