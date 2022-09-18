package de.matthiasfisch.audiodragon.exception

import de.matthiasfisch.audiodragon.recording.AudioSource
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.NOT_FOUND)
class NoCaptureOngoingException(audioSource: AudioSource): Exception("Audio source ${audioSource.name} is currently not being captured.")

@ResponseStatus(HttpStatus.CONFLICT)
class CaptureOngoingException(audioSource: AudioSource): Exception("Capture on audio source ${audioSource.name} is already ongoing.")