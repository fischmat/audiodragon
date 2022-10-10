package de.matthiasfisch.audiodragon.controller

import de.matthiasfisch.audiodragon.model.getRecordableAudioSources
import de.matthiasfisch.audiodragon.types.AudioFormatDTO
import de.matthiasfisch.audiodragon.types.AudioSourceDTO
import de.matthiasfisch.audiodragon.util.AudioSourceId
import de.matthiasfisch.audiodragon.util.getAudioSource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/sources")
class AudioSourceController {
    @GetMapping
    fun getAudioSources() = getRecordableAudioSources().map{ AudioSourceDTO(it) }

    @GetMapping("/{sourceId}/formats")
    fun getAudioFormats(@PathVariable("sourceId") sourceId: AudioSourceId) =
        sourceId.getAudioSource()
        .getSupportedAudioFormats()
        .map { AudioFormatDTO(it) }
}