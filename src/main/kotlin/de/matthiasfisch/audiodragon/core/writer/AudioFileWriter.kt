package de.matthiasfisch.audiodragon.core.writer

import de.matthiasfisch.audiodragon.core.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.core.model.TrackData
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val WRITING_THREAD_POOL: ExecutorService = Executors.newFixedThreadPool(1)

interface AudioFileWriter {
    fun writeToFile(audioBuffer: AudioBuffer, trackData: TrackData?): Path

    fun writeToFileAsync(audioBuffer: AudioBuffer, trackData: TrackData?) =
        CompletableFuture.supplyAsync({ writeToFile(audioBuffer, trackData) }, WRITING_THREAD_POOL)
}

private val ILLEGAL_FILENAME_CHARS = listOf('"', '*', ':', '<', '>', '?', '\\', '|', 0x7F.toChar(), '\u0000')

fun filenameFor(trackData: TrackData?, extension: String) = if (trackData?.title != null && trackData.artist != null) {
    sanitizeFilename("${trackData.artist} - ${trackData.title}.$extension")
} else {
    sanitizeFilename("track-${Instant.now()}.$extension")
}

private fun sanitizeFilename(filename: String): String {
    var sanitized = filename
    for (c in ILLEGAL_FILENAME_CHARS) {
        sanitized = sanitized.replace(c, '_')
    }
    return sanitized
}