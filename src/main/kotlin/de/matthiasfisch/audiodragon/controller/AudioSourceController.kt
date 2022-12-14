package de.matthiasfisch.audiodragon.controller

import de.matthiasfisch.audiodragon.service.AudioPlatformService
import de.matthiasfisch.audiodragon.types.AudioFormatDTO
import de.matthiasfisch.audiodragon.types.AudioSourceDTO
import de.matthiasfisch.audiodragon.util.AudioSourceId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/sources")
class AudioSourceController(val audioPlatformService: AudioPlatformService) {
    @GetMapping
    fun getAudioSources() = audioPlatformService.getAudioPlatform()
        .getAudioSources()
        .map{ AudioSourceDTO(it) }

    @GetMapping("/{sourceId}/formats")
    fun getAudioFormats(@PathVariable("sourceId") sourceId: AudioSourceId) =
        audioPlatformService.getAudioSource(sourceId)
        .getAudioFormats()
        .map { AudioFormatDTO(it) }
}