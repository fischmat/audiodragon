package de.matthiasfisch.audiodragon.core.capture

import de.matthiasfisch.audiodragon.core.model.AudioSource
import de.matthiasfisch.audiodragon.core.model.Recording
import de.matthiasfisch.audiodragon.core.model.TrackData
import de.matthiasfisch.audiodragon.core.recognition.TrackRecognizer
import de.matthiasfisch.audiodragon.core.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.core.splitting.TrackState
import de.matthiasfisch.audiodragon.core.writer.AudioFileWriter
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import mu.KotlinLogging
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import kotlin.concurrent.withLock

private val LOGGER = KotlinLogging.logger {}

class Capture private constructor(
    val audioSource: AudioSource,
    val audioFormat: AudioFormat,
    private val recording: Recording<*>,
    private val trackBoundsDetector: TrackBoundsDetector,
    private val trackRecognizer: TrackRecognizer?,
    private val fileWriter: AudioFileWriter
) {
    companion object {
        fun AudioSource.capture(
            audioFormat: AudioFormat,
            recording: Recording<*>,
            trackBoundsDetector: TrackBoundsDetector,
            trackRecognizer: TrackRecognizer?,
            fileWriter: AudioFileWriter
        ) = Capture(
            this,
            audioFormat,
            recording,
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
    private val trackWrittenPublisher = PublishProcessor.create<Path>()
    private val captureStartedPublisher = PublishProcessor.create<Unit>()
    private val captureStoppedPublisher = PublishProcessor.create<Unit>()
    private val captureStopRequestedPublisher = PublishProcessor.create<Unit>()

    init {
        recording.audioChunkFlowable()
            .subscribe {
                when (trackBoundsDetector.process(it)) {
                    TrackState.TRACK_STARTED -> onTrackStarted()
                    TrackState.TRACK_ENDED -> onTrackEnded()
                    TrackState.SILENCE -> {}
                    TrackState.PLAYING -> {}
                }
            }
    }

    fun start() {
        recording.startRecording()
        captureStartedPublisher.onNext(Unit)
    }

    fun stop() {
        recording.stopRecording().thenApply {  audio ->
            audio.close()
        }
        captureStoppedPublisher.onNext(Unit)
        LOGGER.debug { "Capture on audio device ${audioSource.name} stopped." }
    }

    fun stopAfterTrack() {
        stopRequested = true
        captureStopRequestedPublisher.onNext(Unit)
    }

    fun currentTrack() = trackData

    fun audio() = recording.getAudio()

    fun mergeTrackData(trackDataToMerge: TrackData) = trackDataLock.withLock {
        trackData = if (trackData == null) trackDataToMerge else trackData!!.merge(trackDataToMerge)
        trackData!!
    }

    private fun onTrackStarted() {
        trackStartedPublisher.onNext(Unit)

        trackRecognizer?.recognizeTrack { recording.getAudio() }
            ?.thenAccept { trackData ->
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
            .thenAccept { trackWrittenPublisher.onNext(it) }

        if (stopRequested) {
            stop()
        }
    }

    // Event flowables
    fun trackStartEvents() = Flowable.fromPublisher(trackStartedPublisher)
    fun trackEndedEvents() = Flowable.fromPublisher(trackEndedPublisher)
    fun trackRecognizedEvents() = Flowable.fromPublisher(trackRecognizedPublisher)
    fun trackWrittenEvents() = Flowable.fromPublisher(trackWrittenPublisher)
    fun captureStartedEvents() = Flowable.fromPublisher(captureStartedPublisher)
    fun captureStoppedEvents() = Flowable.fromPublisher(captureStoppedPublisher)
    fun captureStopRequestedEvents() = Flowable.fromPublisher(captureStopRequestedPublisher)
    fun audioChunksFlowable() = recording.audioChunkFlowable()
}