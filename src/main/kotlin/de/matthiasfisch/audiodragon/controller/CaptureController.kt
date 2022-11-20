package de.matthiasfisch.audiodragon.controller

import de.matthiasfisch.audiodragon.core.model.TrackData
import de.matthiasfisch.audiodragon.exception.NoCaptureOngoingException
import de.matthiasfisch.audiodragon.service.AudioPlatformService
import de.matthiasfisch.audiodragon.service.CaptureService
import de.matthiasfisch.audiodragon.types.CaptureDTO
import de.matthiasfisch.audiodragon.types.StartCaptureInstructionDTO
import de.matthiasfisch.audiodragon.util.AudioSourceId
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/capture")
class CaptureController(val captureService: CaptureService, val audioPlatformService: AudioPlatformService) {

    @PostMapping
    fun startCapture(@RequestBody instruction: StartCaptureInstructionDTO) = with(instruction) {
        captureService.startCapture(
            audioPlatformService.getAudioSource(audioSourceId),
            format.toAudioSystemFormat(),
            recognizeSongs,
            outputFormat
        ).let { CaptureDTO(it) }
    }

    @GetMapping("/{audioSourceId}")
    fun getOngoingCapture(@PathVariable("audioSourceId") audioSourceId: AudioSourceId) =
        CaptureDTO(getCaptureForId(audioSourceId))

    @GetMapping("/{audioSourceId}/track")
    fun getCurrentTrack(@PathVariable("audioSourceId") audioSourceId: AudioSourceId) =
        getCaptureForId(audioSourceId).currentTrack()

    @PutMapping("/{audioSourceId}/track")
    fun updateCurrentTrack(@PathVariable("audioSourceId") audioSourceId: AudioSourceId, @RequestBody trackData: TrackData) =
        getCaptureForId(audioSourceId).mergeTrackData(trackData)

    @DeleteMapping("/{audioSourceId}")
    fun stopRecording(@PathVariable("audioSourceId") audioSourceId: AudioSourceId, @RequestParam(required = false) immediately: Boolean?) {
        if (immediately != false) {
            captureService.stopCapture(audioPlatformService.getAudioSource(audioSourceId))
        } else {
            captureService.stopCaptureAfterCurrentTrack(audioPlatformService.getAudioSource(audioSourceId))
        }
    }

    private fun getCaptureForId(audioSourceId: AudioSourceId) = captureService.getOngoingCapture(audioPlatformService.getAudioSource(audioSourceId))
        ?: throw NoCaptureOngoingException(
            audioPlatformService.getAudioSource(audioSourceId)
        )
}