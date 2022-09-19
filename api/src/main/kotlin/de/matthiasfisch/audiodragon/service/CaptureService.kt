package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.buffer.DiskSpillingAudioBuffer
import de.matthiasfisch.audiodragon.exception.CaptureOngoingException
import de.matthiasfisch.audiodragon.exception.NoCaptureOngoingException
import de.matthiasfisch.audiodragon.recognition.TrackRecognizer
import de.matthiasfisch.audiodragon.recognition.shazam.RapidApiShazamTrackRecognizer
import de.matthiasfisch.audiodragon.recording.AudioSource
import de.matthiasfisch.audiodragon.recording.Capture
import de.matthiasfisch.audiodragon.recording.Capture.Companion.capture
import de.matthiasfisch.audiodragon.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.types.FileOutputOptionsDTO
import de.matthiasfisch.audiodragon.types.MP3OptionsDTO
import de.matthiasfisch.audiodragon.types.ShazamRecognitionSettings
import de.matthiasfisch.audiodragon.writer.MP3FileWriter
import org.springframework.stereotype.Service
import java.nio.file.Paths
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Service
class CaptureService(val settingsService: SettingsService) {
    private val ongoingCaptures = mutableMapOf<AudioSource, Capture>()
    private val bufferFactory = { format: AudioFormat -> DiskSpillingAudioBuffer(format) }

    fun startCapture(audioSource: AudioSource, audioFormat: AudioFormat, recognizeSongs: Boolean, fileOutputOptions: FileOutputOptionsDTO) = synchronized(ongoingCaptures) {
        if (ongoingCaptures.containsKey(audioSource)) {
            throw CaptureOngoingException(audioSource)
        }

        with(settingsService.settings) {
            val trackBoundsDetector = TrackBoundsDetector(splitting.splitAfterSilenceMillis.milliseconds, splitting.silenceRmsTolerance)
            val trackRecognizer = when(recognition) {
                is ShazamRecognitionSettings -> RapidApiShazamTrackRecognizer(recognition.rapidApiToken, recognition.secondsUntilRecognition.seconds, recognition.sampleSeconds.seconds, recognition.maxRetries)
                else -> throw IllegalStateException("Unknown recognition type ${recognition.javaClass.simpleName}")
            }
            val fileWriter = when(fileOutputOptions) {
                is MP3OptionsDTO -> MP3FileWriter(Paths.get(output.location), fileOutputOptions.bitRate, fileOutputOptions.channels, fileOutputOptions.quality, fileOutputOptions.vbr)
                else -> throw IllegalStateException("Unknown file output type ${fileOutputOptions.javaClass.simpleName}")
            }
            val capture = audioSource.capture(audioFormat, bufferFactory, trackBoundsDetector, trackRecognizer, fileWriter)
            captureEventBroker.monitor(capture)
            capture.start()
            capture
        }.also {
            ongoingCaptures[audioSource] = it
        }
    }

    fun getOngoingCapture(audioSource: AudioSource) = synchronized(ongoingCaptures) { ongoingCaptures[audioSource] }

    fun stopCapture(audioSource: AudioSource) = synchronized(ongoingCaptures) {
        val capture = ongoingCaptures[audioSource] ?: throw NoCaptureOngoingException(audioSource)
        capture.stop()
    }

    fun stopCaptureAfterCurrentTrack(audioSource: AudioSource) = synchronized(ongoingCaptures) {
        val capture = ongoingCaptures[audioSource] ?: throw NoCaptureOngoingException(audioSource)
        capture.stopAfterTrack()
    }

    fun isCaptureStoppedAfterCurrentTrack(audioSource: AudioSource) = synchronized(ongoingCaptures) {
        val capture = ongoingCaptures[audioSource] ?: throw NoCaptureOngoingException(audioSource)
        capture.stopAfterTrackRequested()
    }
}