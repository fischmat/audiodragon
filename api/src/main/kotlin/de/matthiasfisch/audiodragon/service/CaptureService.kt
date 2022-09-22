package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.buffer.DiskSpillingAudioBuffer
import de.matthiasfisch.audiodragon.exception.CaptureOngoingException
import de.matthiasfisch.audiodragon.exception.NoCaptureOngoingException
import de.matthiasfisch.audiodragon.recognition.shazam.RapidApiShazamTrackRecognizer
import de.matthiasfisch.audiodragon.recording.AudioSource
import de.matthiasfisch.audiodragon.recording.Capture
import de.matthiasfisch.audiodragon.recording.Capture.Companion.capture
import de.matthiasfisch.audiodragon.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.types.FileOutputOptionsDTO
import de.matthiasfisch.audiodragon.types.MP3OptionsDTO
import de.matthiasfisch.audiodragon.types.ShazamRecognitionSettings
import de.matthiasfisch.audiodragon.util.AudioSourceId
import de.matthiasfisch.audiodragon.util.getId
import de.matthiasfisch.audiodragon.writer.MP3FileWriter
import mu.KotlinLogging
import org.springframework.stereotype.Service
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val LOGGER = KotlinLogging.logger {}

@Service
class CaptureService(val settingsService: SettingsService, val captureEventBroker: CaptureEventBroker) {
    private val ongoingCaptures = mutableMapOf<AudioSourceId, Capture>()
    private val bufferFactory = { format: AudioFormat -> DiskSpillingAudioBuffer(format) }

    fun startCapture(audioSource: AudioSource, audioFormat: AudioFormat, recognizeSongs: Boolean, fileOutputOptions: FileOutputOptionsDTO) = synchronized(ongoingCaptures) {
        if (ongoingCaptures.containsKey(audioSource.getId())) {
            throw CaptureOngoingException(audioSource)
        }

        with(settingsService.settings) {
            val trackBoundsDetector = TrackBoundsDetector(splitting.splitAfterSilenceMillis.milliseconds, splitting.silenceRmsTolerance)
            val trackRecognizer = when(recognition) {
                is ShazamRecognitionSettings -> RapidApiShazamTrackRecognizer(recognition.rapidApiToken, recognition.secondsUntilRecognition.seconds, recognition.sampleSeconds.seconds, recognition.maxRetries)
                else -> throw IllegalStateException("Unknown recognition type ${recognition.javaClass.simpleName}")
            }
            val fileWriter = when(fileOutputOptions) {
                is MP3OptionsDTO -> MP3FileWriter(output.path, fileOutputOptions.bitRate, fileOutputOptions.channels, fileOutputOptions.quality, fileOutputOptions.vbr)
                else -> throw IllegalStateException("Unknown file output type ${fileOutputOptions.javaClass.simpleName}")
            }
            val capture = audioSource.capture(audioFormat, bufferFactory, trackBoundsDetector, trackRecognizer, fileWriter)
            captureEventBroker.monitor(capture)
            capture.start()
            capture
        }.also {
            ongoingCaptures[audioSource.getId()] = it
        }
    }

    fun getOngoingCapture(audioSource: AudioSource) = synchronized(ongoingCaptures) { ongoingCaptures[audioSource.getId()] }

    fun stopCapture(audioSource: AudioSource) = synchronized(ongoingCaptures) {
        val capture = ongoingCaptures[audioSource.getId()] ?: throw NoCaptureOngoingException(audioSource)
        capture.stop()
        ongoingCaptures.remove(audioSource.getId())
        LOGGER.info { "Capture on audio device ${audioSource.name} stopped." }
    }

    fun stopCaptureAfterCurrentTrack(audioSource: AudioSource) = synchronized(ongoingCaptures) {
        val capture = ongoingCaptures[audioSource.getId()] ?: throw NoCaptureOngoingException(audioSource)
        capture.stopAfterTrack()
        LOGGER.info { "Capture on audio device ${audioSource.name} will be stopped after current track." }
    }

    fun isCaptureStoppedAfterCurrentTrack(audioSource: AudioSource) = synchronized(ongoingCaptures) {
        val capture = ongoingCaptures[audioSource.getId()] ?: throw NoCaptureOngoingException(audioSource)
        capture.stopAfterTrackRequested()
    }
}