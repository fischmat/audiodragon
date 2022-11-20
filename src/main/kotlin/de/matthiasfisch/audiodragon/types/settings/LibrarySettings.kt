package de.matthiasfisch.audiodragon.types

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.nio.file.Path
import java.nio.file.Paths

@JsonIgnoreProperties(ignoreUnknown = true)
data class LibrarySettings(
    val dbPath: String,
    val scanThreads: Int
) {
    fun databasePath(): Path = Paths.get(dbPath.replace("~", System.getProperty("user.home")))
}