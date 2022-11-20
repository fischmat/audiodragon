package de.matthiasfisch.audiodragon.core.buffer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val IN_MEMORY_BUFFER_SIZE = 50
private const val SAMPLE_RATE = 100
private const val SAMPLE_SIZE_BYTES = 2
private const val CHANNELS = 2
private val AUDIO_FORMAT = AudioFormat(SAMPLE_RATE.toFloat(), SAMPLE_SIZE_BYTES * 8, CHANNELS, true, true)

class DiskSpillingAudioBufferTest : FunSpec({

    fun generatePcmData(size: Int) = ByteArray(size) { (it % Byte.MAX_VALUE).toByte() }

    context("get") {
        test("A small chunk can be added and retrieved") {
            // Arrange
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
            val data = generatePcmData(10)

            // Act
            subject.add(data)
            val result = subject.get().toArray()

            // Assert
            result shouldBe data
        }

        test("A chunk with exactly the memory limit can be added and retrieved") {
            // Arrange
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
            val data = generatePcmData(IN_MEMORY_BUFFER_SIZE)

            // Act
            subject.add(data)
            val result = subject.get().toArray()

            // Assert
            result shouldBe data
        }

        test("Multiple small chunks can be added and retrieved") {
            // Arrange
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
            val data1 = generatePcmData(10)
            val data2 = generatePcmData(10)
            val data3 = generatePcmData(10)

            // Act
            subject.add(data1)
            subject.add(data2)
            subject.add(data3)
            val result = subject.get().toArray()

            // Assert
            result shouldBe data1 + data2 + data3
        }

        test("Large data exceeding memory limit can be added and retrieved") {
            // Arrange
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
            val data = generatePcmData(120)

            // Act
            subject.add(data)
            val result = subject.get().toArray()

            // Assert
            result shouldBe data
        }

        test("Multiple chunks exceeding memory limit can be added and retrieved") {
            // Arrange
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
            val data1 = generatePcmData(30)
            val data2 = generatePcmData(30)
            val data3 = generatePcmData(200)

            // Act
            subject.add(data1)
            subject.add(data2)
            subject.add(data3)
            val result = subject.get().toArray()

            // Assert
            result shouldBe data1 + data2 + data3
        }

        test("Reads between writes don't affect result") {
            // Arrange
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
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

        test("Concurrent writes are possible") {
            // Arrange
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
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
    }

    context("size") {
        test("Size is correct when after adding a small chunk") {
            // Arrange
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
            val data = generatePcmData(10)

            // Act
            subject.add(data)

            // Act
            subject.size() shouldBe data.size
        }

        test("Size is correct after adding a large chunk exceeding memory size") {
            // Arrange
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
            val data = generatePcmData(150)

            // Act
            subject.add(data)

            // Act
            subject.size() shouldBe data.size
        }

        test("Size is correct after adding multiple chunks exceeding memory size") {
            // Arrange
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
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
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
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
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
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
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
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
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
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
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
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
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
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
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)
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
            val subject = DiskSpillingAudioBuffer(AUDIO_FORMAT, IN_MEMORY_BUFFER_SIZE)

            // Act
            val result = subject.duration()

            // Assert
            result shouldBe 0.milliseconds
        }
    }
})
