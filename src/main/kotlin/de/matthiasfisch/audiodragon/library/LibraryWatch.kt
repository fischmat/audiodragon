package de.matthiasfisch.audiodragon.library

import de.matthiasfisch.audiodragon.library.peristence.LibraryItem
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchEvent
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun watchLibraryDirectory(
    path: Path,
    executor: Executor = ForkJoinPool.commonPool(),
    callback: LibraryWatcher
): AutoCloseable {
    require(path.toFile().isDirectory) { "Only directories can be watched, but $path is not a directory." }
    val watchService = path.fileSystem.newWatchService()

    path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)

    val stop = AtomicBoolean(false)
    val scheduler = Executors.newSingleThreadExecutor()
    scheduler.execute {
        while (!stop.get()) {
            val watch = watchService.take()
            try {
                val events = watch.pollEvents()
                val pathsCreated = getPathEvents(events, ENTRY_CREATE, path)
                val pathsModified = getPathEvents(events, ENTRY_MODIFY, path)
                val pathsDeleted = getPathEvents(events, ENTRY_DELETE, path)

                val createdItems = pathsCreated.mapNotNull { runCatching { LibraryScanner.scanFile(it) }.getOrNull() }
                val modifiedItems = pathsModified.mapNotNull { runCatching { LibraryScanner.scanFile(it) }.getOrNull() }

                if (createdItems.isNotEmpty() || modifiedItems.isNotEmpty() || pathsDeleted.isNotEmpty()) {
                    executor.execute { callback.handleChange(createdItems, modifiedItems, pathsDeleted) }
                }
            } finally {
                watch.reset()
            }
        }
    }

    return AutoCloseable {
        stop.set(true)
        scheduler.awaitTermination(5, TimeUnit.SECONDS)
        watchService.close()
        scheduler.shutdownNow()
    }
}

private fun getPathEvents(events: List<WatchEvent<*>>, kind: WatchEvent.Kind<Path>, rootPath: Path): List<Path> =
    events.filter { it.kind() == kind }
        .map { it.context() }
        .filterIsInstance(Path::class.java)
        .map { rootPath.resolve(it) }

interface LibraryWatcher {
    fun handleChange(createdItems: List<LibraryItem>, modifiedItems: List<LibraryItem>, deletedPaths: List<Path>)
}