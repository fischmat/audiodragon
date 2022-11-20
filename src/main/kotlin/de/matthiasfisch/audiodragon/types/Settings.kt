package de.matthiasfisch.audiodragon.types

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import de.matthiasfisch.audiodragon.types.settings.OutputSettings
import de.matthiasfisch.audiodragon.types.settings.RecognitionSettings
import de.matthiasfisch.audiodragon.types.settings.RecordingSettings
import de.matthiasfisch.audiodragon.types.settings.SplittingSettings

const val SETTINGS_VERSION = 1

@JsonIgnoreProperties(ignoreUnknown = true)
data class Settings(
    val recognition: RecognitionSettings,
    val splitting: SplittingSettings,
    val output: OutputSettings,
    val recording: RecordingSettings,
    val library: LibrarySettings
)
