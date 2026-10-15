package de.matthiasfisch.audiodragon.types

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
@JsonIgnoreProperties(ignoreUnknown = true)
data class LibrarySettings(
    val scanThreads: Int
)
