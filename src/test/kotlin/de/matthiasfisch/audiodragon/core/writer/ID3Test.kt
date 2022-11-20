package de.matthiasfisch.audiodragon.core.writer

import de.matthiasfisch.audiodragon.core.model.TrackData
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

private const val TITLE = "Some title"
private const val ARTIST = "Some artist"
private const val ALBUM = "Some album"
private val SIMPLE_TRACK_INFO = TrackData(
    title = TITLE,
    artist = ARTIST,
    album = ALBUM
)

class ID3Test : FunSpec({
    fun createMp3(): Path {
        val file = tempfile(suffix = ".mp3")
        Thread.currentThread().contextClassLoader.getResourceAsStream("sample01.mp3")
            ?.use {  input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }?: throw IllegalStateException("Could not load sample file.")
        return file.toPath()
    }

    fun getID3v2(path: Path): AbstractID3v2Tag? {
        val file = AudioFileIO.read(path.toFile())
        check(file is MP3File)
        return if(file.hasID3v2Tag()) {
            file.iD3v2Tag
        } else {
            null
        }
    }

    fun removeID3(path: Path) {
        val file = AudioFileIO.read(path.toFile())
        check(file is MP3File)
        if (file.hasID3v2Tag()) {
            file.delete(file.iD3v2Tag)
        }
        if (file.hasID3v1Tag()) {
            file.delete(file.iD3v1Tag)
        }
    }

    context("addID3TagToMP3File") {
        test("Error if path does not exist") {
            // Arrange
            val notExistingPath = Paths.get("/not/existing")

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                addID3TagToMP3File(notExistingPath, SIMPLE_TRACK_INFO)
            }

            // Assert
            exception.message shouldBe "File $notExistingPath does not exist or is not a file."
        }

        test("Error if path is a directory") {
            // Arrange
            val path = tempdir().toPath()

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                addID3TagToMP3File(path, SIMPLE_TRACK_INFO)
            }

            // Assert
            exception.message shouldBe "File $path does not exist or is not a file."
        }

        test("Error if file is not readable") {
            // Arrange
            val path = mockk<Path>()
            val file = mockk<File>()
            every { path.toFile() } returns file
            every { file.isFile } returns true
            every { file.canRead() } returns false
            every { file.canWrite() } returns true

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                addID3TagToMP3File(path, SIMPLE_TRACK_INFO)
            }

            // Assert
            exception.message shouldBe "User is not allowed to read file $path."
        }

        test("Error if file is not writeable") {
            // Arrange
            val path = mockk<Path>()
            val file = mockk<File>()
            every { path.toFile() } returns file
            every { file.isFile } returns true
            every { file.canRead() } returns true
            every { file.canWrite() } returns false

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                addID3TagToMP3File(path, SIMPLE_TRACK_INFO)
            }

            // Assert
            exception.message shouldBe "User is not allowed to write file $path."
        }

        test("ID3 info is overwritten if it already exists") {
            // Arrange
            val path = createMp3()

            // Act
            addID3TagToMP3File(path, SIMPLE_TRACK_INFO)

            // Assert
            val id3 = getID3v2(path)
            id3.shouldNotBeNull()
            id3.getFirst(FieldKey.TITLE) shouldBe SIMPLE_TRACK_INFO.title
            id3.getFirst(FieldKey.ARTIST) shouldBe SIMPLE_TRACK_INFO.artist
            id3.getFirst(FieldKey.ALBUM) shouldBe SIMPLE_TRACK_INFO.album
            id3.getFirst(FieldKey.ALBUM_ARTIST).shouldBeEmpty()
        }

        test("New ID3 header is added if not existing yet") {
            // Arrange
            val path = createMp3()
            removeID3(path)

            // Act
            addID3TagToMP3File(path, SIMPLE_TRACK_INFO)

            // Assert
            val id3 = getID3v2(path)
            id3.shouldNotBeNull()
            id3.getFirst(FieldKey.TITLE) shouldBe SIMPLE_TRACK_INFO.title
            id3.getFirst(FieldKey.ARTIST) shouldBe SIMPLE_TRACK_INFO.artist
            id3.getFirst(FieldKey.ALBUM) shouldBe SIMPLE_TRACK_INFO.album
            id3.getFirst(FieldKey.ALBUM_ARTIST).shouldBeEmpty()
        }

        test("Artwork is loaded and scaled if specified") {
            // Arrange
            val path = createMp3()
            removeID3(path)

            val trackData = TrackData(
                title = TITLE,
                artist = ARTIST,
                album = ALBUM,
                coverartImageUrl = "https://github.com/fischmat/audiodragon-client/raw/main/src/assets/notrack.png"
            )
            val targetDim = 123

            // Act
            addID3TagToMP3File(path, trackData, artworkDimension = targetDim)

            // Assert
            val id3 = getID3v2(path)
            id3.shouldNotBeNull()
            id3.getFirst(FieldKey.TITLE) shouldBe SIMPLE_TRACK_INFO.title
            id3.getFirst(FieldKey.ARTIST) shouldBe SIMPLE_TRACK_INFO.artist
            id3.getFirst(FieldKey.ALBUM) shouldBe SIMPLE_TRACK_INFO.album
            val artworks = id3.artworkList
            artworks shouldHaveSize 1
            val coverart = artworks.first()
            coverart.mimeType shouldBe "image/png"
            val actualImage = withContext(Dispatchers.IO) {
                ImageIO.read(ByteArrayInputStream(coverart.binaryData))
            }
            actualImage.width shouldBe targetDim
            actualImage.height shouldBe targetDim
        }

        test("Error if artwork URL is not found") {
            // Arrange
            val path = createMp3()
            removeID3(path)

            val notExistingUrl = "http://example.com/not_existing"
            val trackData = TrackData(
                title = TITLE,
                artist = ARTIST,
                album = ALBUM,
                coverartImageUrl = notExistingUrl
            )

            // Act
            val exception = shouldThrow<IOException> {
                addID3TagToMP3File(path, trackData)
            }

            // Assert
            exception.message shouldBe "Loading artwork at $notExistingUrl failed with status 404."
        }
    }
})
