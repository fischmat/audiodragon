package de.matthiasfisch.audiodragon.buffer

import de.matthiasfisch.audiodragon.model.PcmData
import de.matthiasfisch.audiodragon.util.byteCountToDuration
import de.matthiasfisch.audiodragon.util.durationToByteCount
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.stream.Stream
import javax.sound.sampled.AudioFormat
import kotlin.math.min
import kotlin.streams.asStream
import kotlin.time.Duration

class DiskSpillingAudioBuffer(private val audioFormat: AudioFormat, private val inMemoryBufferMaxSize: Int) : AudioBuffer {
    private val spillFile = Files.createTempFile("audiodragon", "audio_buffer").toFile()
    private val out = FileOutputStream(spillFile)
    private val buffer = ByteArrayOutputStream(inMemoryBufferMaxSize)
    private var bytesSpilled = 0L

    override fun audioFormat(): AudioFormat = audioFormat

    override fun add(data: PcmData) {
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
}