package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.exception.NotFoundException
import de.matthiasfisch.audiodragon.library.LibraryScanner
import de.matthiasfisch.audiodragon.library.LibraryWatcher
import de.matthiasfisch.audiodragon.library.peristence.LibraryItem
import de.matthiasfisch.audiodragon.library.peristence.LibraryItemSortField
import de.matthiasfisch.audiodragon.library.peristence.LibraryRepository
import de.matthiasfisch.audiodragon.library.peristence.SortOrder
import de.matthiasfisch.audiodragon.library.watchLibraryDirectory
import de.matthiasfisch.audiodragon.types.LibraryItemDTO
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

private val LOGGER = KotlinLogging.logger {}

@Service
class LibraryService(private val libraryEventBroker: LibraryEventBroker, private val settingsService: SettingsService) {
    private val libraryPersistence = with(settingsService.settings) {
        LibraryRepository(library.databasePath())
    }
    private val libraryScanExecutor = with(settingsService.settings) {
        val numThreads = if (library.scanThreads > 0) library.scanThreads else Runtime.getRuntime().availableProcessors()
        Executors.newFixedThreadPool(numThreads)
    }

    init {
        CompletableFuture.supplyAsync {
            reinitializeLibrary()
        }.thenRun {
            watchLibrary()
        }
    }

    fun getAllItems(search: String? = null,
                    titleSearch: String? = null,
                    artistSearch: String? = null,
                    albumSearch: String? = null,
                    genres: List<String>? = null,
                    page: Int? = null,
                    pageSize: Int = 10,
                    sortBy: LibraryItemSortField = LibraryItemSortField.UPDATED_AT,
                    sortOrder: SortOrder = SortOrder.DESC
    ): List<LibraryItemDTO> {
        return libraryPersistence.getItems(search, titleSearch, artistSearch, albumSearch, genres, page, pageSize, sortBy, sortOrder)
            .map {
                LibraryItemDTO(
                    filePath = it.filePath.absolutePathString(),
                    addedAt = it.addedAt,
                    updatedAt = it.updatedAt,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    genres = it.genres,
                    labels = it.labels,
                    releaseYear = it.releaseYear,
                    lyrics = it.lyrics,
                    lengthMillis = it.length?.inWholeMilliseconds
                )
            }
    }

    fun getItemFrontCover(filePath: Path, out: OutputStream, formatName: String) {
        val item = libraryPersistence.getItem(filePath) ?: throw NotFoundException("File with path $filePath is not in library.")
        ImageIO.write(item.frontCoverart.value, formatName, out)
    }

    fun getItemBackCover(filePath: Path, out: OutputStream, formatName: String) {
        val item = libraryPersistence.getItem(filePath) ?: throw NotFoundException("File with path $filePath is not in library.")
        ImageIO.write(item.backCoverart.value, formatName, out)
    }

    private fun reinitializeLibrary() {
        val libraryPath = settingsService.settings.output.path
        libraryPath.createDirectories() // Create directory with parent if not exists

        LOGGER.info { "Updating library from files in $libraryPath." }
        try {
            val libraryItems = LibraryScanner.scanForTracks(libraryPath, executor = libraryScanExecutor)
            libraryPersistence.replaceAllItems(libraryItems)
            LOGGER.info { "Directory $libraryPath successfully scanned." }
            libraryEventBroker.sendLibraryInitialized()
        } catch (e: Throwable) {
            LOGGER.error(e) { "Failed to scan library from directory $libraryPath" }
        }
    }

    private fun watchLibrary() {
        val libraryWatcher = object : LibraryWatcher {
            override fun handleChange(createdItems: List<LibraryItem>, modifiedItems: List<LibraryItem>, deletedPaths: List<Path>) {
                LOGGER.info { "Recognized change in library. Adding ${createdItems.size}, updating ${modifiedItems.size} and deleting ${deletedPaths.size} items." }
                createdItems.forEach { libraryPersistence.upsertItem(it) }
                modifiedItems.forEach { libraryPersistence.upsertItem(it) }
                deletedPaths.forEach { libraryPersistence.deleteItem(it) }
                libraryEventBroker.sendLibraryRefreshed()
            }
        }
        watchLibraryDirectory(settingsService.settings.output.path, executor = libraryScanExecutor, callback = libraryWatcher)
    }
}