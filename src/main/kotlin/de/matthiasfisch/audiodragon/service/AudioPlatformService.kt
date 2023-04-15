package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.core.model.AudioPlatform
import de.matthiasfisch.audiodragon.core.platform.JavaAudioPlatform
import de.matthiasfisch.audiodragon.core.platform.pactl.PulseAudioPlatform
import de.matthiasfisch.audiodragon.util.AudioSourceId
import de.matthiasfisch.audiodragon.util.getId
import org.springframework.stereotype.Service

private val BUILTIN_PLATFORMS = listOf(
    JavaAudioPlatform(),
    PulseAudioPlatform()
)

@Service
class AudioPlatformService(val settingsService: SettingsService) {
    private val platforms: Map<String, AudioPlatform> = BUILTIN_PLATFORMS
        .filter { it.isSupported() }
        .associateBy { it.platformId }

    fun getAudioPlatform() = with(settingsService.settings) {
        platforms[recording.platform] ?: throw IllegalStateException("Unknown audio platform ${recording.platform}.")
    }

    fun getAudioSource(audioSourceId: AudioSourceId) = getAudioPlatform().getAudioSources()
        .singleOrNull { it.getId() == audioSourceId }
        ?: throw IllegalArgumentException("No audio source with id $this exists.")
}