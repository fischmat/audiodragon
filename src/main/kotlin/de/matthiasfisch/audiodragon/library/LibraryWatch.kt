package de.matthiasfisch.audiodragon.library

import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit

fun watchDirectory(path: Path, executor: Executor = ForkJoinPool.commonPool(), callback: DirectoryWatcher): AutoCloseable {
    require(path.toFile().isDirectory) { "Only directories can be watched, but $path is not a directory." }
    val watchService = path.fileSystem.newWatchService()

    path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE)
    path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
    path.register(watchService, StandardWatchEventKinds.ENTRY_DELETE)

    val scheduledAction = Runnable {
        val watch = watchService.poll() ?: return@Runnable

        val events = watch.pollEvents()
        val pathsCreated = events.filter { it.kind() == StandardWatchEventKinds.ENTRY_CREATE }
            .map { it.context() }
            .filterIsInstance(Path::class.java)
        val pathsModified = events.filter { it.kind() == StandardWatchEventKinds.ENTRY_MODIFY }
            .map { it.context() }
            .filterIsInstance(Path::class.java)
        val pathsDeleted = events.filter { it.kind() == StandardWatchEventKinds.ENTRY_DELETE }
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