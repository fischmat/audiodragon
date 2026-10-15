package de.matthiasfisch.audiodragon.controller

import de.matthiasfisch.audiodragon.service.AudioPlatformService
import de.matthiasfisch.audiodragon.service.AudioPreviewService
import de.matthiasfisch.audiodragon.types.AudioFormatDTO
import de.matthiasfisch.audiodragon.types.AudioSourceDTO
import de.matthiasfisch.audiodragon.util.AudioSourceId
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/sources")
class AudioSourceController(
    val audioPlatformService: AudioPlatformService,
    val audioPreviewService: AudioPreviewService
) {
    @GetMapping
    fun getAudioSources() = audioPlatformService.getAudioPlatform()
        .getAudioSources()
        .map{ AudioSourceDTO(it) }

    @GetMapping("/{sourceId}/formats")
    fun getAudioFormats(@PathVariable("sourceId") sourceId: AudioSourceId) =
        audioPlatformService.getAudioSource(sourceId)
        .getAudioFormats()
        .map { AudioFormatDTO(it) }

    @PostMapping("/{sourceId}/preview")
    fun startPreview(@PathVariable("sourceId") sourceId: AudioSourceId, @RequestBody format: AudioFormatDTO) {
        audioPreviewService.start(audioPlatformService.getAudioSource(sourceId), format.toAudioSystemFormat())
    }

    @DeleteMapping("/preview")
    fun stopPreview() = audioPreviewService.stop()
}
