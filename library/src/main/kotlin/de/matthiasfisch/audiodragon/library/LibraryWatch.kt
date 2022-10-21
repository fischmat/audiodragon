package de.matthiasfisch.audiodragon.library

import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.*

fun watchDirectory(path: Path, executor: Executor = ForkJoinPool.commonPool(), callback: DirectoryWatcher): AutoCloseable {
    require(path.toFile().isDirectory) { "Only directories can be watched, but $path is not a directory." }
    val watchService = path.fileSystem.newWatchService()

    val createWatch = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE)
    val modifyWatch = path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
    val deleteWatch = path.register(watchService, StandardWatchEventKinds.ENTRY_DELETE)

    val scheduledAction = Runnable {
        val pathsCreated = createWatch.pollEvents()
            .map { it.context() }
            .filterIsInstance(Path::class.java)
        val pathsModified = modifyWatch.pollEvents()
            .map { it.context() }
            .filterIsInstance(Path::class.java)
        val pathsDeleted = deleteWatch.pollEvents()
            .map { it.context() }
            .filterIsInstance(Path::class.java)

        if (pathsCreated.isNotEmpty() || pathsModified.isNotEmpty() || pathsDeleted.isNotEmpty()) {
            executor.execute { callback.handleChange(pathsCreated, pathsModified, pathsDeleted) }
        }
    }

    val scheduler = Executors.newSingleThreadScheduledExecutor()
    scheduler.scheduleAtFixedRate(scheduledAction, 0, 1, TimeUnit.SECONDS)

    return AutoCloseable {
        watchService.close()
        scheduler.awaitTermination(5, TimeUnit.SECONDS)
        scheduler.shutdownNow()
    }
}

interface DirectoryWatcher {
    fun handleChange(createdPaths: List<Path>, modifiedPaths: List<Path>, deletedPaths: List<Path>)
}