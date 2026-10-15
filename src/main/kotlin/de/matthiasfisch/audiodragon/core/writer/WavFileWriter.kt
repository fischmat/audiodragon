package de.matthiasfisch.audiodragon.core.writer

import de.matthiasfisch.audiodragon.core.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.core.model.TrackData
import mu.KotlinLogging
import java.io.InputStream
import java.nio.file.Path
import java.util.stream.Stream
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.io.path.createDirectories

private val LOGGER = KotlinLogging.logger {}

/** Writes captured PCM to an uncompressed, broadly compatible WAV file. */
class WavFileWriter(private val outputDirectory: Path) : AudioFileWriter {
    override fun writeToFile(audioBuffer: AudioBuffer, trackData: TrackData?): Path {
        val targetPath = outputDirectory.resolve(filenameFor(trackData, "wav"))
        targetPath.parent.createDirectories()
        val format = audioBuffer.audioFormat()
        val frameCount = audioBuffer.size() / format.frameSize

        audioBuffer.get().use { bytes ->
            AudioInputStream(ByteStream(bytes), format, frameCount).use { audio ->
                AudioSystem.write(audio, AudioFileFormat.Type.WAVE, targetPath.toFile())
            }
        }
        LOGGER.info { "Track was written to $targetPath as lossless WAV." }
        return targetPath
    }
}

private class ByteStream(private val bytes: Stream<Byte>) : InputStream() {
    private val iterator = bytes.iterator()

    override fun read(): Int = if (iterator.hasNext()) iterator.next().toInt() and 0xff else -1

    override fun close() {
        bytes.close()
    }
}
