package de.matthiasfisch.audiodragon.types.settings

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SplittingSettings(
    val splitAfterSilenceMillis: Long,
    val silenceRmsTolerance: Float
) {
    init {
        require(splitAfterSilenceMillis >= 0) { "Silence threshold must be non-negative." }
        require(silenceRmsTolerance >= 0) { "Silence RMS tolerance must be non-negative." }
    }
}