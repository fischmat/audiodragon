package de.matthiasfisch.audiodragon.recording

import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.model.TrackData
import de.matthiasfisch.audiodragon.recognition.TrackRecognizer
import de.matthiasfisch.audiodragon.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.splitting.TrackState
import de.matthiasfisch.audiodragon.writer.AudioFileWriter
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import mu.KotlinLogging
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import kotlin.concurrent.withLock

private val LOGGER = KotlinLogging.logger {}

class Capture private constructor(
    private val recording: Recording,
    private val trackBoundsDetector: TrackBoundsDetector,
    private val trackRecognizer: TrackRecognizer,
    private val fileWriter: AudioFileWriter
) {
    companion object {
        fun AudioSource.capture(
            audioFormat: AudioFormat,
            bufferFactory: (AudioFormat) -> AudioBuffer,
            trackBoundsDetector: TrackBoundsDetector,
            trackRecognizer: TrackRecognizer,
            fileWriter: AudioFileWriter,
            blockSize: Int = 2048
        ) = Capture(
            record(audioFormat, bufferFactory, blockSize),
            trackBoundsDetector,
            trackRecognizer,
            fileWriter
        )
    }

    private var trackData: TrackData? = null
    private val trackDataLock = ReentrantLock()
    private var stopRequested = false

    // Publishers
    private val trackStartedPublisher = PublishProcessor.create<Unit>()
    private val trackEndedPublisher = PublishProcessor.create<Unit>()
    private val trackRecognizedPublisher = PublishProcessor.create<TrackData>()
    private val captureStartedPublisher = PublishProcessor.create<Unit>()
    private val captureStoppedPublisher = PublishProcessor.create<Unit>()
    private val captureStopRequestedPublisher = PublishProcessor.create<Unit>()

    val audioSource = recording.audioSource
    val audioFormat = recording.audioFormat

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
        captureStartedPublisher.onNext(Unit)
    }

    fun stop() {
        recording.interrupt()
        captureStoppedPublisher.onNext(Unit)
        LOGGER.debug { "Capture on audio device ${audioSource.name} stopped." }
    }

    fun stopAfterTrack() {
        stopRequested = true
        captureStopRequestedPublisher.onNext(Unit)
    }

    fun stopAfterTrackRequested() = stopRequested

    fun currentTrack() = trackData

    fun audio() = recording.getAudio()

    fun mergeTrackData(trackDataToMerge: TrackData) = trackDataLock.withLock {
        trackData = if (trackData == null) trackDataToMerge else trackData!!.merge(trackDataToMerge)
        trackData!!
    }

    private fun onTrackStarted() {
        trackStartedPublisher.onNext(Unit)

        trackRecognizer.recognizeTrack { recording.getAudio() }
            .thenAccept { trackData ->
                trackData?.let {
                    trackRecognizedPublisher.onNext(it)
                    mergeTrackData(it)
                }
            }

        // Reset buffer to be sure that there is no initial silence
        recording.reset()
    }

    private fun onTrackEnded() {
        trackEndedPublisher.onNext(Unit)

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

    // Event flowables
    fun trackStartEvents() = Flowable.fromPublisher(trackStartedPublisher)
    fun trackEndedEvents() = Flowable.fromPublisher(trackEndedPublisher)
    fun trackRecognizedEvents() = Flowable.fromPublisher(trackRecognizedPublisher)
    fun captureStartedEvents() = Flowable.fromPublisher(captureStartedPublisher)
    fun captureStoppedEvents() = Flowable.fromPublisher(captureStoppedPublisher)
    fun captureStopRequestedEvents() = Flowable.fromPublisher(captureStopRequestedPublisher)
    fun audioChunksFlowable() = recording.toFlowable()
}