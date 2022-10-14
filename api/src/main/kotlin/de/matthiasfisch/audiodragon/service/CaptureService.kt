package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.buffer.DiskSpillingAudioBuffer
import de.matthiasfisch.audiodragon.capture.Capture
import de.matthiasfisch.audiodragon.capture.Capture.Companion.capture
import de.matthiasfisch.audiodragon.exception.CaptureOngoingException
import de.matthiasfisch.audiodragon.exception.NoCaptureOngoingException
import de.matthiasfisch.audiodragon.model.AudioSource
import de.matthiasfisch.audiodragon.recognition.TrackRecognitionPostprocessor
import de.matthiasfisch.audiodragon.recognition.musicbrainz.MusicBrainzTrackDataLoader
import de.matthiasfisch.audiodragon.recognition.shazam.RapidApiShazamTrackRecognizer
import de.matthiasfisch.audiodragon.recording.JavaAudioSystemRecording
import de.matthiasfisch.audiodragon.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.types.*
import de.matthiasfisch.audiodragon.util.AudioSourceId
import de.matthiasfisch.audiodragon.util.getId
import de.matthiasfisch.audiodragon.writer.MP3FileWriter
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.lang.IllegalArgumentException
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val LOGGER = KotlinLogging.logger {}

@Service
class CaptureService(val settingsService: SettingsService, val captureEventBroker: CaptureEventBroker) {
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

        val capture = audioSource.capture(
            audioFormat = audioFormat,
            recording = createRecording(audioSource, audioFormat),
            trackBoundsDetector = getTrackBoundsDetector(),
            trackRecognizer = if (recognizeSongs) getTrackRecognizer() else null,
            fileWriter = getFileWriter(fileOutputOptions)
        )
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
        capture.stopAfterTrack()
        LOGGER.info { "Capture on audio device ${audioSource.name} will be stopped after current track." }
    }

    private fun createRecording(audioSource: AudioSource, audioFormat: AudioFormat) = with(settingsService.settings) {
        val bufferFactory = { format: AudioFormat -> DiskSpillingAudioBuffer(format) }
        JavaAudioSystemRecording(audioSource, audioFormat, bufferFactory, recording.bufferSize)
    }

    private fun getTrackBoundsDetector() = with(settingsService.settings) {
        TrackBoundsDetector(splitting.splitAfterSilenceMillis.milliseconds, splitting.silenceRmsTolerance)
    }

    private fun getTrackRecognizer() = with(settingsService.settings) {
        val additionalProcessors = recognition.postprocessors.map { getRecognitionPostprocessor(it) }
        when (recognition) {
            is ShazamRecognitionSettings -> RapidApiShazamTrackRecognizer(
                recognition.rapidApiToken,
                recognition.secondsUntilRecognition.seconds,
                recognition.sampleSeconds.seconds,
                recognition.maxRetries,
                additionalProcessors
            )

            else -> throw IllegalStateException("Unknown recognition type ${recognition.javaClass.simpleName}")
        }
    }

    private fun getRecognitionPostprocessor(config: RecognitionPostprocessorConfig): TrackRecognitionPostprocessor =
        when(config) {
            is MusicBrainzPostprocessorConfig -> MusicBrainzTrackDataLoader(
                config.minScore,
                config.preferInput,
                config.userAgent
            )
            else -> throw IllegalArgumentException("Unknown postprocessor type.")
        }

    private fun getFileWriter(fileOutputOptions: FileOutputOptionsDTO) = with(settingsService.settings) {
        when (fileOutputOptions) {
            is MP3OptionsDTO -> MP3FileWriter(
                output.path,
                fileOutputOptions.bitRate,
                fileOutputOptions.channels,
                fileOutputOptions.quality,
                fileOutputOptions.vbr
            )

            else -> throw IllegalStateException("Unknown file output type ${fileOutputOptions.javaClass.simpleName}")
        }
    }
}