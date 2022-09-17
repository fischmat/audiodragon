package de.matthiasfisch.audiodragon.recording

import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.model.TrackData
import de.matthiasfisch.audiodragon.recognition.TrackRecognizer
import de.matthiasfisch.audiodragon.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.splitting.TrackState
import de.matthiasfisch.audiodragon.writer.AudioFileWriter
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import kotlin.concurrent.withLock

class Capture private constructor(
    private val recording: Recording,
    private val trackBoundsDetector: TrackBoundsDetector,
    private val trackRecognizer: TrackRecognizer,
    private val fileWriter: AudioFileWriter
) {
    companion object {
        fun AudioSource.capture(
            audioFormat: AudioFormat,
            bufferFactory: () -> AudioBuffer,
            trackBoundsDetector: TrackBoundsDetector,
            trackRecognizer: TrackRecognizer,
            fileWriter: AudioFileWriter,
            blockSize: Int = 2048
        ) = Capture(
            record(audioFormat, bufferFactory, blockSize),
            trackBoundsDetector,
            trackRecognizer,
            fileWriter
        ).also {
            it.start()
        }
    }

    private var trackData: TrackData? = null
    private val trackDataLock = ReentrantLock()
    private var stopRequested = false

    init {
        recording.toFlowable()
            .subscribe {
                when (trackBoundsDetector(it)) {
                    TrackState.TRACK_STARTED -> onTrackStarted()
                    TrackState.TRACK_ENDED -> onTrackEnded()
                    TrackState.SILENCE -> {}
                    TrackState.PLAYING -> {}
                }
            }
    }

    fun start() {
        recording.start()
    }

    fun stop() {
        recording.interrupt()
        recording.join()
    }

    fun stopAfterTrack() {
        stopRequested = true
    }

    fun mergeTrackData(trackDataToMerge: TrackData) = trackDataLock.withLock {
        trackData = if (trackData == null) trackDataToMerge else trackData!!.merge(trackDataToMerge)
    }

    private fun onTrackStarted() {
        trackRecognizer.recognizeTrack { recording.getAudio() }
            .thenAccept { trackData ->
                trackData?.let { mergeTrackData(it) }
            }

        // Reset buffer to be sure that there is no initial silence
        recording.reset()
    }

    private fun onTrackEnded() {
        val audio = recording.reset()
        val trackData = this.trackData
        trackDataLock.withLock {
            this.trackData = null
        }
        fileWriter.writeToFileAsync(audio, trackData)

        if (stopRequested) {
            stop()
        }
    }
}