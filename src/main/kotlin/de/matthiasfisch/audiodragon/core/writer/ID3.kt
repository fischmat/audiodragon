package de.matthiasfisch.audiodragon.core.writer

import de.matthiasfisch.audiodragon.core.model.TrackData
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagField
import org.jaudiotagger.tag.datatype.DataTypes
import org.jaudiotagger.tag.id3.ID3v24Frame
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.ID3v24Tag
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.round

typealias ArtworkUrlLoader = (URL) -> InputStream

private const val PNG_IMAGEIO_ID = "png"
private const val PNG_MIME_TYPE = "image/png"

// IDs for picture types (see org.jaudiotagger.tag.reference.PictureTypes)
private const val COVERART_FRONT_ID = 3
private const val COVERART_BACK_ID = 4

fun addID3TagToMP3File(path: Path, trackData: TrackData, artworkLoader: ArtworkUrlLoader = defaultArtworkLoader(), artworkDimension: Int = 500) {
    val file = path.toFile()
    require(file.isFile) { "File $path does not exist or is not a file." }
    require(file.canRead()) { "User is not allowed to read file $path." }
    require(file.canWrite()) { "User is not allowed to write file $path." }

    val mp3 = getMP3File(file)
    mp3.iD3v2Tag = getID3Tag(trackData, artworkLoader, artworkDimension)
    mp3.save()
}

private fun getMP3File(file: File): MP3File {
    val audioFile = AudioFileIO.read(file)
    require(audioFile is MP3File) { "File ${file.absolutePath} is not a valid MP3 file." }
    return audioFile
}

private fun getID3Tag(trackData: TrackData, artworkLoader: ArtworkUrlLoader, artworkDimensions: Int): ID3v24Tag {
    val id3 = ID3v24Tag()
    trackData.apply {
        title?.apply { id3.setField(FieldKey.TITLE, this) }
        artist?.apply { id3.setField(FieldKey.ARTIST, this) }
        album?.apply { id3.setField(FieldKey.ALBUM, this) }
        labels?.apply { id3.setField(FieldKey.RECORD_LABEL, *this.toTypedArray()) }
        genres?.apply { id3.setField(FieldKey.GENRE, *this.toTypedArray()) }
        releaseYear?.apply { id3.setField(FieldKey.YEAR, this) }
        lyrics?.apply { id3.setField(FieldKey.LYRICS, this.joinToString("\n")) }
        coverartImageUrl?.apply { id3.addField(loadArtwork(this, COVERART_FRONT_ID, artworkDimensions, artworkLoader)) }
        backgroundImageUrl?.apply { id3.addField(loadArtwork(this, COVERART_BACK_ID, artworkDimensions, artworkLoader)) }
    }
    return id3
}

private fun loadArtwork(url: String, typeId: Int, artworkDimensions: Int, artworkLoader: ArtworkUrlLoader): TagField {
    return artworkLoader(URL(url)).use {
        val originalImage = ImageIO.read(it)
        checkNotNull(originalImage) { "No image reader is available for the file type of $url." }
        val scaledImage = scaleImage(originalImage, artworkDimensions)

        val id3v24Key = ID3v24Frames.getInstanceOf().getId3KeyFromGenericKey(FieldKey.COVER_ART)
        val frame = ID3v24Frame(id3v24Key.frameId)
        frame.body.setObjectValue(DataTypes.OBJ_PICTURE_DATA, exportImage(scaledImage))
        frame.body.setObjectValue(DataTypes.OBJ_PICTURE_TYPE, typeId)
        frame.body.setObjectValue(DataTypes.OBJ_MIME_TYPE, PNG_MIME_TYPE)
        frame.body.setObjectValue(DataTypes.OBJ_DESCRIPTION, "")
        frame.body.setObjectValue(DataTypes.OBJ_URL, url)
        frame
    }
}

private fun scaleImage(image: BufferedImage, dim: Int): BufferedImage {
    val newWidth = if (image.width > image.height) dim else round((image.width / image.height.toFloat()) * dim).toInt()
    val newHeight = if (image.height > image.width) dim else round((image.height / image.width.toFloat()) * dim).toInt()
    return BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB).also {
        it.graphics.drawImage(image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
    }
}

private fun exportImage(image: BufferedImage): ByteArray = ByteArrayOutputStream().also {
    ImageIO.write(image, PNG_IMAGEIO_ID, it)
}.toByteArray()

fun defaultArtworkLoader(): ArtworkUrlLoader = { DefaultArtworkLoader.invoke(it) }

private object DefaultArtworkLoader {
    private val httpClient = OkHttpClient()

    fun invoke(url: URL): InputStream {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AudioDragon")
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Loading artwork at $url failed with status ${response.code}.")
        }
        return response.body?.byteStream() ?: throw IOException("Body of request to $url is empty.")
    }
}