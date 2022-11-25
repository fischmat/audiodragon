package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.core.buffer.DiskSpillingAudioBuffer
import de.matthiasfisch.audiodragon.core.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.core.capture.Capture
import de.matthiasfisch.audiodragon.core.model.AudioSource
import de.matthiasfisch.audiodragon.core.recognition.TrackRecognitionPostprocessor
import de.matthiasfisch.audiodragon.core.recognition.musicbrainz.MusicBrainzTrackDataLoader
import de.matthiasfisch.audiodragon.core.recognition.shazam.RapidApiShazamTrackRecognizer
import de.matthiasfisch.audiodragon.core.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.core.writer.MP3FileWriter
import de.matthiasfisch.audiodragon.exception.CaptureOngoingException
import de.matthiasfisch.audiodragon.exception.NoCaptureOngoingException
import de.matthiasfisch.audiodragon.types.FileOutputOptionsDTO
import de.matthiasfisch.audiodragon.types.MP3OptionsDTO
import de.matthiasfisch.audiodragon.types.settings.*
import de.matthiasfisch.audiodragon.util.AudioSourceId
import de.matthiasfisch.audiodragon.util.getId
import mu.KotlinLogging
import org.springframework.stereotype.Service
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val LOGGER = KotlinLogging.logger {}

@Service
class CaptureService(val settingsService: SettingsService, val captureEventBroker: CaptureEventBroker, val audioPlatformService: AudioPlatformService) {
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

        val capture = Capture(
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
        capture.stopAfterTrack().thenRun {
            ongoingCaptures.remove(audioSource.getId())
        }
        LOGGER.info { "Capture on audio device ${audioSource.name} will be stopped after current track." }
    }

    private fun createRecording(audioSource: AudioSource, audioFormat: AudioFormat) = with(settingsService.settings) {
        val bufferFactory = getBufferFactory()
        val platform = audioPlatformService.getAudioPlatform()
        platform.createRecording(audioSource, audioFormat, recording.buffer.batchSize, bufferFactory)
    }

    private fun getBufferFactory() = with(settingsService.settings) {
        when(val buffer = recording.buffer) {
            is InMemoryBufferSettings -> { format: AudioFormat -> InMemoryAudioBuffer(format, buffer.initialBufferSize) }
            is DiskSpillingBufferSettings -> { format: AudioFormat -> DiskSpillingAudioBuffer(format, buffer.inMemoryBufferMaxSize) }
            else -> throw IllegalArgumentException("Unknown buffer settings.")
        }
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
                additionalProcessors,
                recognition.apiConfig()
            )

            else -> throw IllegalStateException("Unknown recognition type ${recognition.javaClass.simpleName}")
        }
    }

    private fun getRecognitionPostprocessor(config: RecognitionPostprocessorConfig): TrackRecognitionPostprocessor =
        when(config) {
            is MusicBrainzPostprocessorConfig -> MusicBrainzTrackDataLoader(
                config.minScore,
                config.preferInput,
                config.apiConfig()
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
                fileOutputOptions.vbr,
                output.encodingChunkLengthMs.milliseconds,
                output.coverartMaxDimension
            )

            else -> throw IllegalStateException("Unknown file output type ${fileOutputOptions.javaClass.simpleName}")
        }
    }
}