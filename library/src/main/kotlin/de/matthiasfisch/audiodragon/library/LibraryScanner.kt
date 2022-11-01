package de.matthiasfisch.audiodragon.library

import de.matthiasfisch.audiodragon.library.peristence.LibraryItem
import mu.KotlinLogging
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.stream.Collectors
import javax.imageio.ImageIO
import kotlin.io.path.extension
import kotlin.time.Duration.Companion.seconds

private val LOGGER = KotlinLogging.logger {}

object LibraryScanner {

    fun scanForTracks(path: Path,
                      extensions: List<String> = listOf("mp3"),
                      executor: Executor = Executors.newSingleThreadExecutor()): List<LibraryItem> {
        val futures = Files.walk(path)
            .filter { it.extension in extensions }
            .collect(Collectors.toMap({ it }, { CompletableFuture.supplyAsync({ scanFile(it) }, executor) } ))
        return futures.mapValues {
            try {
                it.value.join()
            } catch (e: CompletionException) {
                LOGGER.warn { "Could not process file ${it.key}: ${e.message}" }
                null
            }
        }.values.filterNotNull()
    }

    fun scanFile(path: Path): LibraryItem {
        val file = AudioFileIO.read(path.toFile())
        require(file is MP3File) { "Expected file to be a MP3 file." }
        return if (file.hasID3v2Tag()) {
            LibraryItem(
                filePath = path,
                addedAt = null,
                updatedAt = null,
                title = file.iD3v2Tag.getFirst(FieldKey.TITLE),
                artist = file.iD3v2Tag.getFirst(FieldKey.ARTIST),
                album = file.iD3v2Tag.getFirst(FieldKey.ALBUM),
                genres = file.iD3v2Tag.getAll(FieldKey.GENRE),
                labels = file.iD3v2Tag.getAll(FieldKey.RECORD_LABEL),
                releaseYear = file.iD3v2Tag.getFirst(FieldKey.YEAR),
                frontCoverart = lazyOf(loadArtwork(file, 0)),
                backCoverart = lazyOf(loadArtwork(file, 1)),
                lyrics = file.iD3v2Tag.getFirst(FieldKey.LYRICS)?.split("\n"),
                length = file.mP3AudioHeader.preciseTrackLength.seconds
            )
        } else if(file.hasID3v1Tag()) {
            LibraryItem(
                filePath = path,
                addedAt = null,
                updatedAt = null,
                title = file.iD3v1Tag.getFirst(FieldKey.TITLE),
                artist = file.iD3v1Tag.getFirst(FieldKey.ARTIST),
                album = file.iD3v1Tag.getFirst(FieldKey.ALBUM),
                genres = file.iD3v1Tag.getAll(FieldKey.GENRE),
                labels = file.iD3v1Tag.getAll(FieldKey.RECORD_LABEL),
                releaseYear = file.iD3v1Tag.getFirst(FieldKey.YEAR),
                frontCoverart = lazyOf(loadArtwork(file, 0)),
                backCoverart = lazyOf(loadArtwork(file, 1)),
                lyrics = file.iD3v1Tag.getFirst(FieldKey.LYRICS)?.split("\n"),
                length = file.mP3AudioHeader.preciseTrackLength.seconds
            )
        } else {
            throw IllegalArgumentException("MP3 file $path does not have an ID3 header.")
        }
    }

    private fun loadArtwork(file: MP3File, index: Int): BufferedImage? {
        val artworks = if (file.hasID3v2Tag()) {
            file.iD3v2Tag.artworkList
        } else if (file.hasID3v1Tag()) {
            file.iD3v1Tag.artworkList
        } else {
            throw IllegalArgumentException("MP3 file ${file.file.path} does not have an ID3 header.")
        }
        if (index >= artworks.size) {
            return null
        }
        val artwork = artworks[index]
        return ByteArrayInputStream(artwork.binaryData).use {
            ImageIO.read(it)
        }
    }
}