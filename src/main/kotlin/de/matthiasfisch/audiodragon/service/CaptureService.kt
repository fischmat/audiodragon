package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.core.capture.Capture
import de.matthiasfisch.audiodragon.core.model.AudioSource
import de.matthiasfisch.audiodragon.exception.CaptureOngoingException
import de.matthiasfisch.audiodragon.exception.NoCaptureOngoingException
import de.matthiasfisch.audiodragon.types.FileOutputOptionsDTO
import de.matthiasfisch.audiodragon.util.AudioSourceId
import de.matthiasfisch.audiodragon.util.getId
import mu.KotlinLogging
import org.springframework.stereotype.Service
import javax.sound.sampled.AudioFormat

private val LOGGER = KotlinLogging.logger {}

@Service
class CaptureService(
    val settingsService: SettingsService,
    val captureEventBroker: CaptureEventBroker,
    val audioPlatformService: AudioPlatformService,
    val captureFactory: CaptureFactory
) {
    private val ongoingCaptures = mutableMapOf<AudioSourceId, Capture>()

    fun startCapture(
        audioSource: AudioSource,
        audioFormat: AudioFormat,
        recognizeSongs: Boolean,
        fileOutputOptions: FileOutputOptionsDTO
    ) = synchronized(ongoingCaptures) {
        if (ongoingCaptures.containsKey(audioSource.getId())) {
            throw CaptureOngoingException(audioSource)
        }

        val capture = captureFactory.createCapture(audioSource, audioFormat, fileOutputOptions)
        captureEventBroker.monitor(capture)
        capture.start()
        ongoingCaptures[audioSource.getId()] = capture
        capture
    }

    fun getOngoingCapture(audioSource: AudioSource) =
        synchronized(ongoingCaptures) { ongoingCaptures[audioSource.getId()] }

    fun stopCapture(audioSource: AudioSource) = synchronized(ongoingCaptures) {
        val capture = ongoingCaptures[audioSource.getId()] ?: throw NoCaptureOngoingException(audioSource)
        capture.stop()
        ongoingCaptures.remove(audioSource.getId())
        LOGGER.info { "Capture on audio device ${audioSource.name} stopped." }
    }

    fun stopCaptureAfterCurrentTrack(audioSource: AudioSource) = synchronized(ongoingCaptures) {
        val capture = ongoingCaptures[audioSource.getId()] ?: throw NoCaptureOngoingException(audioSource)
        capture.stopAfterTrack().thenRun {
            ongoingCaptures.remove(audioSource.getId())
        }
        LOGGER.info { "Capture on audio device ${audioSource.name} will be stopped after current track." }
    }

    fun cancelStopRequest(audioSourceId: AudioSourceId) = synchronized(ongoingCaptures) {
        val capture = ongoingCaptures[audioSourceId] ?: throw NoCaptureOngoingException(audioPlatformService.getAudioSource(audioSourceId))
        capture.cancelStop()
    }
}