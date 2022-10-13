package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.exception.NotFoundException
import de.matthiasfisch.audiodragon.model.TrackData
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import org.springframework.stereotype.Service
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.math.round

private val AUDIO_FILE_EXTENSIONS = listOf("mp3")

@Service
class LibraryService(private val settingsService: SettingsService) {
    private var library = mapOf<UUID, LibraryItem>()

    init {
        refreshLibrary()
    }

    fun getAllItems() = library

    fun writeFrontCoverartAsPng(itemId: UUID, out: OutputStream, maxDimension: Int) {
        if (!library.containsKey(itemId)) {
            throw NotFoundException("Library item with ID $itemId does not exist.")
        }

        val filePath = Paths.get(library[itemId]!!.filePath)
        val artwork = getCoverart(filePath) ?: throw NotFoundException("Item $itemId does not have a front-cover artwork.")
        writeScaledCoverartAsPng(artwork, out, maxDimension)
    }

    final fun refreshLibrary() {
        library = Files.walk(settingsService.settings.output.path)
            .filter { it.extension.lowercase() in AUDIO_FILE_EXTENSIONS }
            .map { getTrackDataForFile(it) }
            .collect(Collectors.toList())
            .filterNotNull()
            .associateBy { it.id }
    }

    private fun getTrackDataForFile(path: Path): LibraryItem? {
        val audioFile = AudioFileIO.read(path.toFile())
        if (audioFile !is MP3File) {
            return null
        }

        val itemId = UUID.randomUUID()
        val trackData = if (audioFile.hasID3v2Tag()) {
            with(audioFile.iD3v2Tag) {
                TrackData(
                    getFirst(FieldKey.TITLE),
                    getFirst(FieldKey.ARTIST),
                    getFirst(FieldKey.ALBUM),
                    getAll(FieldKey.RECORD_LABEL),
                    getAll(FieldKey.GENRE),
                    getFirst(FieldKey.YEAR),
                    "/v1/library/coverart/front/$itemId",
                    null,
                    getFirst(FieldKey.LYRICS).split("\n", "\r\n")
                )
            }
        } else if (audioFile.hasID3v1Tag()) {
            with(audioFile.iD3v1Tag) {
                TrackData(
                    getFirst(FieldKey.TITLE),
                    getFirst(FieldKey.ARTIST),
                    getFirst(FieldKey.ALBUM),
                    getAll(FieldKey.RECORD_LABEL),
                    getAll(FieldKey.GENRE),
                    getFirst(FieldKey.YEAR),
                    "/v1/library/coverart/front/$itemId",
                    null,
                    getFirst(FieldKey.LYRICS).split("\n", "\r\n")
                )
            }
        } else {
            null
        }

        return trackData?.let { LibraryItem(itemId, it, path.absolutePathString()) }
    }

    private fun getCoverart(path: Path): Artwork? {
        val mp3File = getAsMp3File(path)
        val artworkList = if (mp3File.hasID3v2Tag()) {
            mp3File.iD3v2Tag.artworkList
        } else if (mp3File.hasID3v1Tag()) {
            mp3File.iD3v1Tag.artworkList
        } else {
            throw NotFoundException("MP3 file $path does not have an ID3 tag.")
        }
        return artworkList.firstOrNull()
    }

    private fun getAsMp3File(path: Path): MP3File {
        val audioFile = AudioFileIO.read(path.toFile())
        check(audioFile is MP3File) { "Expected $path to be a MP3 file." }
        return audioFile
    }

    private fun writeScaledCoverartAsPng(artwork: Artwork, out: OutputStream, maxDimension: Int) {
        val previewImage = ByteArrayInputStream(artwork.binaryData).use {
            scaleImage(ImageIO.read(it), maxDimension)
        }
        ImageIO.write(previewImage, "png", out)
    }

    private fun scaleImage(image: BufferedImage, maxDimension: Int): BufferedImage {
        if (image.width >= maxDimension && image.height >= maxDimension) {
            return image
        }
        val newWidth = if (image.width > image.height) maxDimension else round((image.width / image.height.toFloat()) * maxDimension).toInt()
        val newHeight = if (image.height > image.width) maxDimension else round((image.height / image.width.toFloat()) * maxDimension).toInt()
        return BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB).also {
            it.graphics.drawImage(image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
        }
    }
}

data class LibraryItem (
    val id: UUID,
    val track: TrackData,
    val filePath: String
)