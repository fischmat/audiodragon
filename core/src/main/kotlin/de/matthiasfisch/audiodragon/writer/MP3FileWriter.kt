package de.matthiasfisch.audiodragon.writer

import com.cloudburst.lame.lowlevel.LameEncoder
import com.cloudburst.lame.mp3.MPEGMode
import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.model.TrackData
import de.matthiasfisch.audiodragon.util.byteCountToDuration
import de.matthiasfisch.audiodragon.util.durationToByteCount
import mu.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.outputStream
import kotlin.streams.asSequence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val LOGGER = KotlinLogging.logger {}

class MP3FileWriter(
    private val outputDirectory: Path,
    private val bitRate: Int,
    private val channels: Int,
    private val quality: Int,
    private val variableBitRate: Boolean,
    private val chunkLength: Duration = 1.minutes
) : AudioFileWriter {

    override fun writeToFile(audioBuffer: AudioBuffer, trackData: TrackData?): Path {
        val targetPath = outputDirectory.resolve(filenameFor(trackData, "mp3"))
        LOGGER.info { "Writing track to file $targetPath as MP3." }

        val audioFormat = audioBuffer.audioFormat()
        val lame = LameEncoder(
            audioFormat,
            bitRate,
            when (channels) {
                1 -> MPEGMode.MONO
                2 -> MPEGMode.STEREO
                else -> throw IllegalArgumentException("No MPEG mode is available for $channels channels.")
            },
            quality,
            variableBitRate
        )

        val pcmData = audioBuffer.get()
        targetPath.outputStream().use { out ->
            val chunkByteCount = audioFormat.durationToByteCount(chunkLength).toInt()
            val outBuffer = ByteArray(chunkByteCount)

            pcmData.asSequence()
                .chunked(chunkByteCount)
                .forEach {
                    LOGGER.debug { "Encoding chunk of length ${it.size} bytes (${audioFormat.byteCountToDuration(it.size.toLong()).inWholeMilliseconds / 1000f}s) using ${lame.javaClass.simpleName}." }
                    val bytesEncoded = lame.encodeBuffer(it.toByteArray(), 0, it.size, outBuffer)
                    out.write(outBuffer, 0, bytesEncoded)
                }
        }

        trackData?.apply {
            LOGGER.debug { "Adding ID3 tag to file $targetPath." }
            addID3TagToMP3File(targetPath, this)
        }

        return targetPath
    }
}