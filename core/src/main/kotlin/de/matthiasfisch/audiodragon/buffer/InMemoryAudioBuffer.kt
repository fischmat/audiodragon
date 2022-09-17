package de.matthiasfisch.audiodragon.buffer

import de.matthiasfisch.audiodragon.model.PcmData
import de.matthiasfisch.audiodragon.util.byteCountToDuration
import de.matthiasfisch.audiodragon.util.durationToByteCount
import java.io.ByteArrayOutputStream
import java.util.stream.Stream
import javax.sound.sampled.AudioFormat
import kotlin.math.min
import kotlin.streams.asStream
import kotlin.time.Duration

const val DEFAULT_INITIAL_BUFFER_SIZE = 10 * 1024 * 1024 // 10 MB

class InMemoryAudioBuffer(private val audioFormat: AudioFormat, initialSize: Int = DEFAULT_INITIAL_BUFFER_SIZE): AudioBuffer {
    private val buffer = ByteArrayOutputStream(initialSize)

    override fun audioFormat(): AudioFormat = audioFormat

    override fun add(data: PcmData) = buffer.write(data)

    override fun get(): Stream<Byte> = buffer.toByteArray().asSequence().asStream()

    override fun get(offset: Duration, length: Duration?): Stream<Byte> {
        val bytes = buffer.toByteArray()
        val offsetInBytes = audioFormat.durationToByteCount(offset).toInt()
        val lengthInBytes = length?.let { offsetInBytes + audioFormat.durationToByteCount(it).toInt() } ?: bytes.size
        return bytes.slice(offsetInBytes until min(lengthInBytes, bytes.size)).stream()
    }

    override fun duration(): Duration = audioFormat.byteCountToDuration(size())

    override fun size(): Long = buffer.size().toLong()
}