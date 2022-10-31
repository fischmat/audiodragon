package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.buffer.DiskSpillingAudioBuffer
import de.matthiasfisch.audiodragon.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.model.AudioSource
import de.matthiasfisch.audiodragon.model.JavaAudioPlatform
import de.matthiasfisch.audiodragon.model.XtAudioPlatform
import de.matthiasfisch.audiodragon.types.*
import de.matthiasfisch.audiodragon.util.AudioSourceId
import de.matthiasfisch.audiodragon.util.getId
import org.springframework.stereotype.Service
import javax.sound.sampled.AudioFormat

@Service
class AudioPlatformService(private val settingsService: SettingsService) {

    fun resolveAudioSource(sourceId: AudioSourceId): AudioSource
        = getAudioPlatform()
        .getAudioSources()
        .singleOrNull { it.getId() == sourceId }
        ?: throw java.lang.IllegalArgumentException("No audio source with id $sourceId exists.")

    fun createRecording(audioSource: AudioSource, audioFormat: AudioFormat) = with(settingsService.settings) {
        val bufferFactory = getBufferFactory()
        getAudioPlatform().createRecording(audioSource, audioFormat, bufferFactory, recording.buffer.batchSize)
    }

    fun getAudioSources(): List<AudioSourceDTO> =
        getAudioPlatform()
            .getAudioSources()
            .map { AudioSourceDTO(it) }

    fun getSupportedAudioFormats(sourceId: AudioSourceId): List<AudioFormatDTO> {
        return resolveAudioSource(sourceId).getSupportedFormats().map { AudioFormatDTO(it) }
    }

    fun getAudioPlatform() = with(settingsService.settings) {
        when(recording.platform) {
            PlatformType.JAVA_AUDIO_SYSTEM -> JavaAudioPlatform()
            PlatformType.XT_AUDIO -> XtAudioPlatform()
        }
    }

    private fun getBufferFactory() = with(settingsService.settings) {
        when (val buffer = recording.buffer) {
            is InMemoryBufferSettings -> { format: AudioFormat ->
                InMemoryAudioBuffer(
                    format,
                    buffer.initialBufferSize
                )
            }

            is DiskSpillingBufferSettings -> { format: AudioFormat ->
                DiskSpillingAudioBuffer(
                    format,
                    buffer.inMemoryBufferMaxSize
                )
            }

            else -> throw IllegalArgumentException("Unknown buffer settings.")
        }
    }
}