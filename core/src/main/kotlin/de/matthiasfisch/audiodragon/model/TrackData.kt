package de.matthiasfisch.audiodragon.model

data class TrackData(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val labels: List<String?>? = null,
    val genres: List<String?>? = null,
    val releaseYear: String? = null,
    val coverartImageUrl: String? = null,
    val backgroundImageUrl: String? = null,
    val lyrics: List<String?>? = null,
    val lengthMillis: Int? = null
) {
    fun merge(other: TrackData) = TrackData(
        other.title ?: title,
        other.artist ?: artist,
        other.album ?: album,
        other.labels ?: labels,
        other.genres ?: genres,
        other.releaseYear ?: releaseYear,
        other.coverartImageUrl ?: coverartImageUrl,
        other.backgroundImageUrl ?: backgroundImageUrl,
        other.lyrics ?: lyrics,
        other.lengthMillis ?: lengthMillis
    )
}