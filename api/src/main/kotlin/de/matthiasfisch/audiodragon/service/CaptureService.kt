package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.buffer.DiskSpillingAudioBuffer
import de.matthiasfisch.audiodragon.capture.Capture
import de.matthiasfisch.audiodragon.capture.Capture.Companion.capture
import de.matthiasfisch.audiodragon.exception.CaptureOngoingException
import de.matthiasfisch.audiodragon.exception.NoCaptureOngoingException
import de.matthiasfisch.audiodragon.model.AudioSource
import de.matthiasfisch.audiodragon.recognition.shazam.RapidApiShazamTrackRecognizer
import de.matthiasfisch.audiodragon.recording.JavaAudioSystemRecording
import de.matthiasfisch.audiodragon.recording.Recording
import de.matthiasfisch.audiodragon.recording.RecordingFactory
import de.matthiasfisch.audiodragon.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.types.FileOutputOptionsDTO
import de.matthiasfisch.audiodragon.types.MP3OptionsDTO
import de.matthiasfisch.audiodragon.types.ShazamRecognitionSettings
import de.matthiasfisch.audiodragon.util.AudioSourceId
import de.matthiasfisch.audiodragon.util.getId
import de.matthiasfisch.audiodragon.writer.MP3FileWriter
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val LOGGER = KotlinLogging.logger {}

@Service
class CaptureService(val settingsService: SettingsService, val captureEventBroker: CaptureEventBroker, val recordingFactory: RecordingFactory) {
    private val ongoingCaptures = mutableMapOf<AudioSourceId, Capture>()

    fun startCapture(audioSource: AudioSource, audioFormat: AudioFormat, recognizeSongs: Boolean, fileOutputOptions: FileOutputOptionsDTO) = synchronized(ongoingCaptures) {
        if (ongoingCaptures.containsKey(audioSource.getId())) {
            throw CaptureOngoingException(audioSource)
        }

        val capture = audioSource.capture(
            audioFormat = audioFormat,
            recordingFactory = recordingFactory,
            trackBoundsDetector = getTrackBoundsDetector(),
            trackRecognizer = if (recognizeSongs) getTrackRecognizer() else null,
            fileWriter = getFileWriter(fileOutputOptions)
        )
        captureEventBroker.monitor(capture)
        capture.start()
        ongoingCaptures[audioSource.getId()] = capture
        capture
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

    private fun getTrackBoundsDetector() = with(settingsService.settings) {
        TrackBoundsDetector(splitting.splitAfterSilenceMillis.milliseconds, splitting.silenceRmsTolerance)
    }

    private fun getTrackRecognizer() = with(settingsService.settings) {
        when(recognition) {
            is ShazamRecognitionSettings -> RapidApiShazamTrackRecognizer(recognition.rapidApiToken, recognition.secondsUntilRecognition.seconds, recognition.sampleSeconds.seconds, recognition.maxRetries)
            else -> throw IllegalStateException("Unknown recognition type ${recognition.javaClass.simpleName}")
        }
    }

    private fun getFileWriter(fileOutputOptions: FileOutputOptionsDTO) = with(settingsService.settings) {
        when(fileOutputOptions) {
            is MP3OptionsDTO -> MP3FileWriter(output.path, fileOutputOptions.bitRate, fileOutputOptions.channels, fileOutputOptions.quality, fileOutputOptions.vbr)
            else -> throw IllegalStateException("Unknown file output type ${fileOutputOptions.javaClass.simpleName}")
        }
    }
}

@Component
class CaptureRecordingFactory(private val settingsService: SettingsService): RecordingFactory {
    override fun createRecording(audioSource: AudioSource, audioFormat: AudioFormat, bufferSize: Int): Recording {
        val settings = settingsService.settings
        val bufferFactory = { format: AudioFormat -> DiskSpillingAudioBuffer(format) }
        return JavaAudioSystemRecording(audioSource, audioFormat, bufferFactory, settings.recording.bufferSize)
    }
}