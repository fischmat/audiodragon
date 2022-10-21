package de.matthiasfisch.audiodragon.library

import de.matthiasfisch.audiodragon.library.peristence.LibraryItem
import de.matthiasfisch.audiodragon.library.peristence.LibraryItemSortField
import de.matthiasfisch.audiodragon.library.peristence.LibraryRepository
import de.matthiasfisch.audiodragon.library.peristence.SortOrder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.date.shouldBeBefore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LibraryRepositoryTest : FunSpec({
    val sqliteFile = Files.createTempFile("LibraryRepositoryTest", ".sqlite")
    val subject = LibraryRepository(sqliteFile)

    fun createImage(): BufferedImage {
        val image = BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB)
        image.graphics.color = Color.RED
        image.graphics.fillRect(0, 0, 300, 300)
        image.graphics.drawString("Test image", 0, 0)
        return image
    }

    fun assertItemsEqual(expected: LibraryItem, actual: LibraryItem) {
        actual.filePath.absolutePathString() shouldBe expected.filePath.absolutePathString()
        actual.title shouldBe expected.title
        actual.artist shouldBe expected.artist
        actual.album shouldBe expected.album
        actual.genres shouldContainExactlyInAnyOrder expected.genres
        actual.labels shouldContainExactlyInAnyOrder expected.labels
        actual.releaseYear shouldBe expected.releaseYear
        actual.lyrics shouldBe expected.lyrics
        actual.length shouldBe expected.length
    }

    fun addMockItems(): Triple<LibraryItem, LibraryItem, LibraryItem> {
        val asura = LibraryItem(
            filePath = Paths.get("/asura.mp3"),
            title = "Asura",
            artist = "Charlotte de Witte",
            album = "Asura EP",
            genres = listOf("Techno"),
            labels = listOf("KNTXT"),
            length = 7.minutes + 49.seconds
        )
        val soma = LibraryItem(
            filePath = Paths.get("/soma.mp3"),
            title = "Soma",
            artist = "Charlotte de Witte",
            album = "Asura EP",
            genres = listOf("Techno")
        )
        val attss = LibraryItem(
            filePath = Paths.get("/attss.mp3"),
            title = "All The Things She Said 2020",
            artist = "DJ Gollum",
            album = "All The Things She Said 2020",
            genres = listOf("Hands Up", "EDM"),
        )
        subject.upsertItem(asura)
        subject.upsertItem(soma)
        subject.upsertItem(attss)
        return Triple(asura, soma, attss)
    }

    beforeEach {
        val flyway = Flyway.configure().dataSource("jdbc:sqlite:${sqliteFile.absolutePathString()}", "", "")
            .cleanDisabled(false)
            .load()
        flyway.clean()
        flyway.migrate()
    }

    context("getItem") {
        test("An item can be added and retrieved again") {
            // Arrange
            val path = Paths.get("/asura.mp3")
            val item = LibraryItem(
                filePath = path,
                title = "Asura",
                artist = "Charlotte de Witte",
                album = "Asura EP",
                genres = listOf("Techno"),
                labels = listOf("KNTXT"),
                releaseYear = "2021",
                frontCoverart = lazy { createImage() },
                length = 7.minutes + 49.seconds
            )

            // Act
            subject.upsertItem(item)
            val foundItem = subject.getItem(path)

            // Assert
            foundItem.shouldNotBeNull()
            foundItem.filePath shouldBe path
            foundItem.addedAt.shouldNotBeNull()
            foundItem.updatedAt.shouldNotBeNull()
            foundItem.addedAt!! shouldBeBefore Instant.now()
            foundItem.updatedAt!! shouldBeBefore Instant.now()
            foundItem.title shouldBe "Asura"
            foundItem.artist shouldBe "Charlotte de Witte"
            foundItem.album shouldBe "Asura EP"
            foundItem.genres shouldBe listOf("Techno")
            foundItem.labels shouldBe listOf("KNTXT")
            foundItem.releaseYear shouldBe "2021"
            foundItem.frontCoverart.value.shouldNotBeNull()
            foundItem.backCoverart.value.shouldBeNull()
            foundItem.lyrics.shouldBeNull()
            foundItem.length shouldBe 7.minutes + 49.seconds
        }

        test("A minimal item can be added and retrieved again") {
            // Arrange
            val path = Paths.get("/asura.mp3")
            val item = LibraryItem(
                filePath = path,
                title = "Asura",
                artist = "Charlotte de Witte"
            )

            // Act
            subject.upsertItem(item)
            val foundItem = subject.getItem(path)

            // Assert
            foundItem.shouldNotBeNull()
            foundItem.filePath shouldBe path
            foundItem.addedAt.shouldNotBeNull()
            foundItem.updatedAt.shouldNotBeNull()
            foundItem.addedAt!! shouldBeBefore Instant.now()
            foundItem.updatedAt!! shouldBeBefore Instant.now()
            foundItem.title shouldBe "Asura"
            foundItem.artist shouldBe "Charlotte de Witte"
            foundItem.album.shouldBeNull()
            foundItem.genres.shouldBeEmpty()
            foundItem.labels.shouldBeEmpty()
            foundItem.releaseYear.shouldBeNull()
            foundItem.frontCoverart.value.shouldBeNull()
            foundItem.backCoverart.value.shouldBeNull()
            foundItem.lyrics.shouldBeNull()
            foundItem.length.shouldBeNull()
        }

        test("Existing items can be updated") {
            // Arrange
            val path = Paths.get("/asura.mp3")
            val item = LibraryItem(
                filePath = path,
                title = "Asura",
                artist = "Charlotte de Witte",
                genres = listOf("EDM"),
                labels = listOf("Wrong label")
            )
            subject.upsertItem(item)

            val newItem = LibraryItem(
                filePath = path,
                title = "Soma",
                artist = "Charlotte de Witte",
                album = "Asura EP",
                genres = listOf("Techno")
            )

            // Act
            subject.upsertItem(newItem)

            // Assert
            val foundItem = subject.getItem(path)
            foundItem.shouldNotBeNull()
            foundItem.filePath shouldBe path
            foundItem.addedAt.shouldNotBeNull()
            foundItem.updatedAt.shouldNotBeNull()
            foundItem.updatedAt!! shouldBeAfter foundItem.addedAt!!
            foundItem.title shouldBe "Soma"
            foundItem.artist shouldBe "Charlotte de Witte"
            foundItem.album shouldBe "Asura EP"
            foundItem.genres shouldBe listOf("Techno")
            foundItem.labels shouldBe listOf()
        }
    }

    test("Null returned when retrieving a non-existing item") {
        // Arrange
        val path = Paths.get("/not_existing.mp3")

        // Act
        val item = subject.getItem(path)

        // Assert
        item.shouldBeNull()
    }

    context("getItems") {

        test("All items returned on empty criteria") {
            // Arrange
            val (asura, soma, attss) = addMockItems()

            // Act
            val result = subject.getItems()

            // Assert
            result.shouldHaveSize(3)
            assertItemsEqual(attss, result[0])
            assertItemsEqual(soma, result[1])
            assertItemsEqual(asura, result[2])
        }

        test("Generic search by title returns correct item") {
            // Arrange
            val (_, _, attss) = addMockItems()

            // Act
            val result = subject.getItems(search = "the things")

            // Assert
            result.shouldHaveSize(1)
            assertItemsEqual(attss, result[0])
        }

        test("Generic search by title prefix returns correct item") {
            // Arrange
            val (_, _, attss) = addMockItems()

            // Act
            val result = subject.getItems(search = "all the things")

            // Assert
            result.shouldHaveSize(1)
            assertItemsEqual(attss, result[0])
        }

        test("Generic search by artist returns correct item") {
            // Arrange
            val (asura, soma, _) = addMockItems()

            // Act
            val result = subject.getItems(search = "CHARLOTTE")

            // Assert
            result.shouldHaveSize(2)
            assertItemsEqual(soma, result[0])
            assertItemsEqual(asura, result[1])
        }

        test("Generic search by album returns correct item") {
            // Arrange
            val (asura, soma, _) = addMockItems()

            // Act
            val result = subject.getItems(search = "ra ep")

            // Assert
            result.shouldHaveSize(2)
            assertItemsEqual(soma, result[0])
            assertItemsEqual(asura, result[1])
        }

        test("Search by title returns correct item") {
            // Arrange
            val (asura, _, _) = addMockItems()

            // Act
            val result = subject.getItems(titleSearch = "ura")

            // Assert
            result.shouldHaveSize(1)
            assertItemsEqual(asura, result[0])
        }

        test("Search by non-existing title for returns correct item") {
            // Arrange
            val (_, _, _) = addMockItems()

            // Act
            val result = subject.getItems(titleSearch = "ura EP")

            // Assert
            result.shouldHaveSize(0)
        }

        test("Search by artist returns correct item") {
            // Arrange
            val (_, _, attss) = addMockItems()

            // Act
            val result = subject.getItems(artistSearch = "gollum")

            // Assert
            result.shouldHaveSize(1)
            assertItemsEqual(attss, result[0])
        }

        test("Search by non-existing artist returns correct empty") {
            // Arrange
            addMockItems()

            // Act
            val result = subject.getItems(artistSearch = "the things")

            // Assert
            result.shouldBeEmpty()
        }

        test("Search by album returns correct item") {
            // Arrange
            val (asura, soma, _) = addMockItems()

            // Act
            val result = subject.getItems(albumSearch = "asura")

            // Assert
            result.shouldHaveSize(2)
            assertItemsEqual(soma, result[0])
            assertItemsEqual(asura, result[1])
        }

        test("Search by non-existing album returns correct empty") {
            // Arrange
            addMockItems()

            // Act
            val result = subject.getItems(albumSearch = "gollum")

            // Assert
            result.shouldBeEmpty()
        }

        test("Search by genre returns correct items") {
            // Arrange
            val (asura, soma, _) = addMockItems()

            // Act
            val result = subject.getItems(genres = listOf("TECHNO"))

            // Assert
            result.shouldHaveSize(2)
            assertItemsEqual(soma, result[0])
            assertItemsEqual(asura, result[1])
        }

        test("Search by multiple genres returns correct items") {
            // Arrange
            val (_, _, attss) = addMockItems()

            // Act
            val result = subject.getItems(genres = listOf("HANDS UP", "EDM"))

            // Assert
            result.shouldHaveSize(1)
            assertItemsEqual(attss, result[0])
        }

        test("Search by some-matching genres returns correct items") {
            // Arrange
            val (_, _, attss) = addMockItems()

            // Act
            val result = subject.getItems(genres = listOf("HIP HOP", "EDM"))

            // Assert
            result.shouldHaveSize(1)
            assertItemsEqual(attss, result[0])
        }

        test("Search by non-existing genre returns empty") {
            // Arrange
            addMockItems()

            // Act
            val result = subject.getItems(genres = listOf("HIP HOP"))

            // Assert
            result.shouldBeEmpty()
        }

        test("Sorting by title asc returns correct order") {
            // Arrange
            val (asura, soma, attss) = addMockItems()

            // Act
            val result = subject.getItems(sortBy = LibraryItemSortField.TITLE, sortOrder = SortOrder.ASC)

            // Assert
            result.shouldHaveSize(3)
            assertItemsEqual(attss, result[0])
            assertItemsEqual(asura, result[1])
            assertItemsEqual(soma, result[2])
        }

        test("Sorting by title desc returns correct order") {
            // Arrange
            val (asura, soma, attss) = addMockItems()

            // Act
            val result = subject.getItems(sortBy = LibraryItemSortField.TITLE, sortOrder = SortOrder.DESC)

            // Assert
            result.shouldHaveSize(3)
            assertItemsEqual(soma, result[0])
            assertItemsEqual(asura, result[1])
            assertItemsEqual(attss, result[2])
        }

        test("Sorting by artist asc returns correct order") {
            // Arrange
            val (asura, soma, attss) = addMockItems()

            // Act
            val result = subject.getItems(sortBy = LibraryItemSortField.ARTIST, sortOrder = SortOrder.ASC)

            // Assert
            result.shouldHaveSize(3)
            assertItemsEqual(soma, result[0])
            assertItemsEqual(asura, result[1])
            assertItemsEqual(attss, result[2])
        }

        test("Sorting by artist desc returns correct order") {
            // Arrange
            val (asura, soma, attss) = addMockItems()

            // Act
            val result = subject.getItems(sortBy = LibraryItemSortField.ARTIST, sortOrder = SortOrder.DESC)

            // Assert
            result.shouldHaveSize(3)
            assertItemsEqual(attss, result[0])
            assertItemsEqual(soma, result[1])
            assertItemsEqual(asura, result[2])
        }

        test("Sorting by album asc returns correct order") {
            // Arrange
            val (asura, soma, attss) = addMockItems()

            // Act
            val result = subject.getItems(sortBy = LibraryItemSortField.ALBUM, sortOrder = SortOrder.ASC)

            // Assert
            result.shouldHaveSize(3)
            assertItemsEqual(attss, result[0])
            assertItemsEqual(soma, result[1])
            assertItemsEqual(asura, result[2])
        }

        test("Sorting by album desc returns correct order") {
            // Arrange
            val (asura, soma, attss) = addMockItems()

            // Act
            val result = subject.getItems(sortBy = LibraryItemSortField.ALBUM, sortOrder = SortOrder.DESC)

            // Assert
            result.shouldHaveSize(3)
            assertItemsEqual(soma, result[0])
            assertItemsEqual(asura, result[1])
            assertItemsEqual(attss, result[2])
        }

        test("Sorting by length asc returns correct order") {
            // Arrange
            val (asura, soma, attss) = addMockItems()

            // Act
            val result = subject.getItems(sortBy = LibraryItemSortField.LENGTH, sortOrder = SortOrder.ASC)

            // Assert
            result.shouldHaveSize(3)
            assertItemsEqual(attss, result[0])
            assertItemsEqual(soma, result[1])
            assertItemsEqual(asura, result[2])
        }

        test("Sorting by length desc returns correct order") {
            // Arrange
            val (asura, soma, attss) = addMockItems()

            // Act
            val result = subject.getItems(sortBy = LibraryItemSortField.LENGTH, sortOrder = SortOrder.DESC)

            // Assert
            result.shouldHaveSize(3)
            assertItemsEqual(asura, result[0])
            assertItemsEqual(attss, result[1])
            assertItemsEqual(soma, result[2])
        }
    }
})
