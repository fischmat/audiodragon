package de.matthiasfisch.audiodragon.controller

import de.matthiasfisch.audiodragon.service.AudioPlatformService
import de.matthiasfisch.audiodragon.util.AudioSourceId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/sources")
class AudioSourceController(private val audioPlatformService: AudioPlatformService) {
    @GetMapping
    fun getAudioSources() = audioPlatformService.getAudioSources()

    @GetMapping("/{sourceId}/formats")
    fun getAudioFormats(@PathVariable("sourceId") sourceId: AudioSourceId) =
        audioPlatformService.getSupportedAudioFormats(sourceId)
}