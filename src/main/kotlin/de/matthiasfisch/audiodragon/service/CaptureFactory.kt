package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.core.buffer.DiskSpillingAudioBuffer
import de.matthiasfisch.audiodragon.core.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.core.capture.Capture
import de.matthiasfisch.audiodragon.core.model.AudioBufferFactory
import de.matthiasfisch.audiodragon.core.model.AudioSource
import de.matthiasfisch.audiodragon.core.model.Recording
import de.matthiasfisch.audiodragon.core.recognition.TrackRecognitionPostprocessor
import de.matthiasfisch.audiodragon.core.recognition.musicbrainz.MusicBrainzTrackDataLoader
import de.matthiasfisch.audiodragon.core.recognition.shazam.RapidApiShazamTrackRecognizer
import de.matthiasfisch.audiodragon.core.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.core.writer.MP3FileWriter
import de.matthiasfisch.audiodragon.types.FileOutputOptionsDTO
import de.matthiasfisch.audiodragon.types.MP3OptionsDTO
import de.matthiasfisch.audiodragon.types.Settings
import de.matthiasfisch.audiodragon.types.settings.*
import org.springframework.stereotype.Component
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Component
class CaptureFactory(
    private val settingsService: SettingsService,
    private val audioPlatformService: AudioPlatformService
) {
    fun createCapture(audioSource: AudioSource, audioFormat: AudioFormat, fileOutputOptions: FileOutputOptionsDTO): Capture {
        val settings = settingsService.settings
        return Capture(
            recording = createRecording(settings, audioSource, audioFormat),
            trackBoundsDetector = getTrackBoundsDetector(settings),
            trackRecognizer = getTrackRecognizer(settings),
            fileWriter = getFileWriter(fileOutputOptions, settings)
        )
    }

    private fun createRecording(settings: Settings, audioSource: AudioSource, audioFormat: AudioFormat): Recording<*> {
        val bufferFactory = createBufferFactory(settings)
        val platform = audioPlatformService.getAudioPlatform()
        return platform.createRecording(
            audioSource,
            audioFormat,
            settings.recording.buffer.batchSize,
            bufferFactory
        )
    }

    private fun createBufferFactory(settings: Settings) = with(settings) {
            when (val buffer = recording.buffer) {
                is InMemoryBufferSettings -> AudioBufferFactory { format ->
                    InMemoryAudioBuffer(
                        format,
                        buffer.initialBufferSize
                    )
                }

                is DiskSpillingBufferSettings -> AudioBufferFactory { format ->
                    DiskSpillingAudioBuffer(
                        format,
                        buffer.inMemoryBufferMaxSize
                    )
                }

                else -> throw IllegalArgumentException("Unknown buffer settings.")
            }
        }

    private fun getTrackBoundsDetector(settings: Settings) = with(settings) {
        TrackBoundsDetector(splitting.splitAfterSilenceMillis.milliseconds, splitting.silenceRmsTolerance)
    }

    private fun getTrackRecognizer(settings: Settings) = with(settings) {
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
        when (config) {
            is MusicBrainzPostprocessorConfig -> MusicBrainzTrackDataLoader(
                config.minScore,
                config.preferInput,
                config.apiConfig()
            )

            else -> throw IllegalArgumentException("Unknown postprocessor type.")
        }

    private fun getFileWriter(fileOutputOptions: FileOutputOptionsDTO, settings: Settings) = with(settings) {
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