package de.matthiasfisch.audiodragon.library.peristence

import java.awt.image.BufferedImage
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration

data class LibraryItem(
    val filePath: Path,
    val addedAt: Instant? = null,
    val updatedAt: Instant? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val genres: List<String> = listOf(),
    val labels: List<String> = listOf(),
    val releaseYear: String? = null,
    val frontCoverart: Lazy<BufferedImage?> = lazy { null },
    val backCoverart: Lazy<BufferedImage?> = lazy { null },
    val lyrics: List<String>? = null,
    val length: Duration? = null
)