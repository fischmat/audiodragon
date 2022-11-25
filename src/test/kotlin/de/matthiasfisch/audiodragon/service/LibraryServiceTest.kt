package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.types.LibraryItemDTO
import io.kotest.assertions.retry
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.context.ContextConfiguration
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

@ContextConfiguration(classes = [SettingsService::class])
class LibraryServiceTest(
    private val settingsService: SettingsService
) : FunSpec({
    lateinit var tempDir: File
    lateinit var originalOutputDirectory: String

    fun setOutputDirectory(path: Path) {
        settingsService.settings = settingsService.settings.copy(
            output = settingsService.settings.output.copy(
                location = path.toString()
            )
        )
    }

    fun copySampleFile(target: File) {
        Thread.currentThread().contextClassLoader.getResourceAsStream("sample01.mp3").use { input ->
            requireNotNull(input)
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    suspend fun waitForLibraryInitialization(libraryEventBrokerMock: LibraryEventBroker) {
        retry(10, 10.seconds) {
            verify { libraryEventBrokerMock.sendLibraryInitialized() }
        }
    }

    fun assertSampleItem(item: LibraryItemDTO, path: Path) {
        item.filePath shouldBe path.toString()
        item.title shouldBe "Goldberg Variations, BWV. 988"
        item.artist shouldBe "Sherry Katz"
        item.album shouldBe "Goldberg Variations, BWV. 988 (Album)"
        item.releaseYear shouldBe "2020"
        item.genres shouldBe listOf("Classical")
        item.labels.shouldBeEmpty()
    }

    beforeSpec {
        originalOutputDirectory = settingsService.settings.output.location
    }

    beforeEach {
        tempDir = tempdir()
        setOutputDirectory(tempDir.toPath())
    }

    afterSpec {
        setOutputDirectory(Paths.get(originalOutputDirectory))
    }

    context("CTOR") {
        test("Output directories created if not existing at creation") {
            // Arrange
            val outputDirectory = tempDir.toPath().resolve("foo/bar")
            setOutputDirectory(outputDirectory)
            val libraryEventBroker = mockk<LibraryEventBroker>(relaxed = true)

            try {
                // Act
                LibraryService(libraryEventBroker, settingsService)

                // Assert
                retry(10, 10.seconds) {
                    outputDirectory.exists() shouldBe true
                }
            } finally {
                setOutputDirectory(tempDir.toPath())
            }
        }

        test("Initialization on empty directory is successful") {
            // Arrange
            val libraryEventBroker = mockk<LibraryEventBroker>(relaxed = true)

            // Act
            val subject = LibraryService(libraryEventBroker, settingsService)

            // Assert
            // Wait for library initialization finished
            waitForLibraryInitialization(libraryEventBroker)
            subject.getAllItems().shouldBeEmpty()
        }

        test("Initialization is successful if files are present") {
            // Arrange
            val file = tempDir.resolve("sample01.mp3")
            copySampleFile(file)
            val libraryEventBroker = mockk<LibraryEventBroker>(relaxed = true)

            // Act
            val subject = LibraryService(libraryEventBroker, settingsService)

            // Assert
            // Wait for library initialization finished
            waitForLibraryInitialization(libraryEventBroker)

            val items = subject.getAllItems()
            items.shouldHaveSize(1)
            assertSampleItem(items.first(), file.toPath())
        }
    }

    context("getAllItems") {
        val file = tempDir.resolve("sample01.mp3")
        copySampleFile(file)
        val libraryEventBroker = mockk<LibraryEventBroker>(relaxed = true)
        val subject = LibraryService(libraryEventBroker, settingsService)
        waitForLibraryInitialization(libraryEventBroker)

        test("Search by title returns correct result") {
            // Arrange

            // Act + Assert
            assertSampleItem(subject.getAllItems(search = "goldberg").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "Goldberg").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "bErG").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "GOLDBERG").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "berg var").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "988").first(), file.toPath())
            subject.getAllItems(search = "Classical").shouldBeEmpty()
            subject.getAllItems(search = "foo").shouldBeEmpty()
        }

        test("Search by artist returns correct results") {
            // Arrange

            // Act + Assert
            assertSampleItem(subject.getAllItems(search = "sherry").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "Sherry").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "ShErRy").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "SHERRY").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "y katz").first(), file.toPath())
            subject.getAllItems(search = "Classical").shouldBeEmpty()
            subject.getAllItems(search = "foo").shouldBeEmpty()
        }

        test("Search by album returns correct results") {
            // Arrange

            // Act + Assert
            assertSampleItem(subject.getAllItems(search = "988 (album)").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "988 (Album)").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "988 (ALBUM)").first(), file.toPath())
            assertSampleItem(subject.getAllItems(search = "AlBuM").first(), file.toPath())
            subject.getAllItems(search = "Classical").shouldBeEmpty()
            subject.getAllItems(search = "foo").shouldBeEmpty()
        }

        test("Title filter returns current results") {
            // Arrange

            // Act + Assert
            assertSampleItem(subject.getAllItems(titleSearch = "goldberg").first(), file.toPath())
            assertSampleItem(subject.getAllItems(titleSearch = "Goldberg").first(), file.toPath())
            assertSampleItem(subject.getAllItems(titleSearch = "bErG").first(), file.toPath())
            assertSampleItem(subject.getAllItems(titleSearch = "GOLDBERG").first(), file.toPath())
            assertSampleItem(subject.getAllItems(titleSearch = "berg var").first(), file.toPath())
            assertSampleItem(subject.getAllItems(titleSearch = "988").first(), file.toPath())
            subject.getAllItems(titleSearch = "Sherry Katz").shouldBeEmpty()
            subject.getAllItems(titleSearch = "Classical").shouldBeEmpty()
            subject.getAllItems(titleSearch = "foo").shouldBeEmpty()
        }

        test("Artist filter returns current results") {
            // Arrange

            // Act + Assert
            assertSampleItem(subject.getAllItems(artistSearch = "sherry").first(), file.toPath())
            assertSampleItem(subject.getAllItems(artistSearch = "Sherry").first(), file.toPath())
            assertSampleItem(subject.getAllItems(artistSearch = "ShErRy").first(), file.toPath())
            assertSampleItem(subject.getAllItems(artistSearch = "SHERRY").first(), file.toPath())
            assertSampleItem(subject.getAllItems(artistSearch = "y katz").first(), file.toPath())
            subject.getAllItems(artistSearch = "Goldberg Variations").shouldBeEmpty()
            subject.getAllItems(artistSearch = "Classical").shouldBeEmpty()
            subject.getAllItems(artistSearch = "foo").shouldBeEmpty()
        }

        test("Album search returns correct results") {
            // Arrange

            // Act + Assert
            assertSampleItem(subject.getAllItems(albumSearch = "988 (album)").first(), file.toPath())
            assertSampleItem(subject.getAllItems(albumSearch = "988 (Album)").first(), file.toPath())
            assertSampleItem(subject.getAllItems(albumSearch = "988 (ALBUM)").first(), file.toPath())
            assertSampleItem(subject.getAllItems(albumSearch = "AlBuM").first(), file.toPath())
            subject.getAllItems(albumSearch = "Sherry Katz").shouldBeEmpty()
            subject.getAllItems(albumSearch = "Classical").shouldBeEmpty()
            subject.getAllItems(albumSearch = "foo").shouldBeEmpty()
        }

        test("Search by genre returns correct results") {
            // Arrange

            // Act + Assert
            assertSampleItem(subject.getAllItems(genres = listOf("classical")).first(), file.toPath())
            assertSampleItem(subject.getAllItems(genres = listOf("Classical")).first(), file.toPath())
            assertSampleItem(subject.getAllItems(genres = listOf("ClAsSiCaL")).first(), file.toPath())
            assertSampleItem(subject.getAllItems(genres = listOf("CLASSICAL")).first(), file.toPath())
            assertSampleItem(subject.getAllItems(genres = listOf("Classical", "Techno")).first(), file.toPath())
            subject.getAllItems(genres = listOf("Techno")).shouldBeEmpty()
        }
    }
})
