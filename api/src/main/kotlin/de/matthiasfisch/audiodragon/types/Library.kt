package de.matthiasfisch.audiodragon.types

import java.time.Instant

data class LibraryItemDTO(
    val filePath: String,
    val addedAt: Instant? = null,
    val updatedAt: Instant? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val genres: List<String> = listOf(),
    val labels: List<String> = listOf(),
    val releaseYear: String? = null,
    val lyrics: List<String>? = null,
    val lengthMillis: Long? = null
)