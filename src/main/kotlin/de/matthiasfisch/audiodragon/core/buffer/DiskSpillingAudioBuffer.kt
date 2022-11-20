package de.matthiasfisch.audiodragon.core.buffer

import de.matthiasfisch.audiodragon.core.model.PcmData
import de.matthiasfisch.audiodragon.core.util.byteCountToDuration
import de.matthiasfisch.audiodragon.core.util.durationToByteCount
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.stream.Stream
import javax.sound.sampled.AudioFormat
import kotlin.math.min
import kotlin.streams.asStream
import kotlin.time.Duration

private val LOGGER = KotlinLogging.logger {}
const val DEFAULT_MAX_IN_MEMORY_BUFFER_SIZE = 128 * 1024 * 1024 // 128MB

class DiskSpillingAudioBuffer(private val audioFormat: AudioFormat, private val inMemoryBufferMaxSize: Int = DEFAULT_MAX_IN_MEMORY_BUFFER_SIZE) :
    AudioBuffer {
    private val spillFile = Files.createTempFile("audiodragon", "audio_buffer").toFile()
    private val out = FileOutputStream(spillFile)
    private val buffer = ByteArrayOutputStream(inMemoryBufferMaxSize)
    private var bytesSpilled = 0L
    private var isClosed = false

    val initiallyUsableDiskSpace = Files.getFileStore(spillFile.toPath()).usableSpace

    override fun audioFormat(): AudioFormat = audioFormat

    override fun add(data: PcmData) {
        require(!isClosed) { "Buffer is already closed and can't accept any data." }
        // Write buffer to disk if size would exceed limit
        synchronized(buffer) {
            if (buffer.size() + data.size > inMemoryBufferMaxSize) {
                synchronized(out) {
                    val spillData = buffer.toByteArray()
                    out.write(spillData)
                    bytesSpilled += spillData.size
                    buffer.reset()
                }
            }
            buffer.write(data)
        }
    }

    override fun get(): Stream<Byte> {
        require(!isClosed) { "Buffer is already closed." }

        var bytesRead = 0
        val bytesToReadFromSpill = synchronized(out) {
            bytesSpilled
        }
        val diskStream = spillFile.inputStream()
        // Utility function loading next chunk from spill file and returning an iterator on it
        val readChunkFromSpill = {
            val data =
                diskStream.readNBytes(min(inMemoryBufferMaxSize.toLong(), bytesToReadFromSpill - bytesRead).toInt())
            bytesRead += data.size
            data.iterator()
        }
        var diskDataIterator = readChunkFromSpill()
        val inMemoryDataIterator = synchronized(buffer) {
            buffer.toByteArray().iterator()
        }

        return object : Iterator<Byte> {
            override fun hasNext(): Boolean = bytesRead < bytesToReadFromSpill || inMemoryDataIterator.hasNext()

            override fun next(): Byte {
                return if (diskDataIterator.hasNext()) {
                    diskDataIterator.next()
                } else if (bytesRead < bytesToReadFromSpill) {
                    diskDataIterator = readChunkFromSpill()
                    diskDataIterator.next()
                } else {
                    inMemoryDataIterator.next()
                }
            }
        }.asSequence().asStream()
    }

    override fun get(offset: Duration, length: Duration?): Stream<Byte> {
        val offsetInBytes = audioFormat.durationToByteCount(offset)
        val lengthInBytes = length?.let { audioFormat.durationToByteCount(it) }
        val streamWithOffset = get().skip(offsetInBytes)
        return lengthInBytes?.let { streamWithOffset.limit(it) } ?: streamWithOffset
    }

    override fun duration(): Duration = audioFormat.byteCountToDuration(size())

    override fun size(): Long = synchronized(out) { bytesSpilled } + synchronized(buffer) { buffer.size() }

    override fun close() {
        synchronized(buffer) {
            val inMemorySize = buffer.size()
            buffer.close()
            out.close()
            spillFile.delete()
            LOGGER.debug { "Disk-spilling buffer closed. Freed $bytesSpilled on disk and $inMemorySize bytes in memory." }
            isClosed = true
        }
    }
}