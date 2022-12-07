package de.matthiasfisch.audiodragon.core.capture

import de.matthiasfisch.audiodragon.core.model.Recording
import de.matthiasfisch.audiodragon.core.model.TrackData
import de.matthiasfisch.audiodragon.core.recognition.TrackRecognizer
import de.matthiasfisch.audiodragon.core.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.core.splitting.TrackState
import de.matthiasfisch.audiodragon.core.writer.AudioFileWriter
import mu.KotlinLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOGGER = KotlinLogging.logger {}

/**
 * The capture is the central controlling unit for splitting, recognizing and writing tracks.
 * It wraps a [Recording] and controls its buffer making sure that its content is written and cleared after every track.
 */
class Capture constructor(
    val recording: Recording<*>,
    private val trackBoundsDetector: TrackBoundsDetector,
    private val trackRecognizer: TrackRecognizer?,
    private val fileWriter: AudioFileWriter
) {

    private var trackData: TrackData? = null
    private val trackDataLock = ReentrantLock()

    private var stopRequested = false
    private val stopFuture = CompletableFuture<Unit>()

    /**
     * Wrapper for event flowables that can be subscribed for capture events.
     */
    val events = CaptureEvents()

    init {
        // Pass every audio chunk to the track detector and call respective actions if bounds are detected
        recording.audioChunkFlowable()
            .subscribe {
                when (trackBoundsDetector.process(it)) {
                    TrackState.TRACK_STARTED -> onTrackStarted()
                    TrackState.TRACK_ENDED -> onTrackEnded()
                    else -> {}
                }
            }
    }

    /**
     * @return Returns the metadata on the current track if it was already set or null otherwise.
     */
    fun currentTrack() = trackData

    /**
     * Starts the capture and the underlying [recording].
     */
    fun start() {
        recording.startRecording()
        events.captureStarted()
    }

    /**
     * Stops the capture immediately. This also stops the underlying [recording].
     */
    fun stop() {
        recording.stopRecording().thenApply {  audio ->
            audio.close()
        }
        stopFuture.complete(Unit)
        events.captureStopped()
        LOGGER.debug { "Capture on audio device ${recording.audioSource.name} stopped." }
    }

    /**
     * Requests the capture to stop on the next observed track boundary.
     * @return Returns a future that will complete once the capture is stopped.
     */
    fun stopAfterTrack(): CompletableFuture<Unit> {
        stopRequested = true
        events.captureStopRequested()
        return CompletableFuture<Unit>().also {
            stopFuture.thenRun { it.complete(Unit) }
        }
    }

    /**
     * Cancels a previously scheduled stop of the capture.
     */
    fun cancelStop() {
        stopFuture.cancel(false)
        stopRequested = false
    }

    /**
     * Merges the existing track data with the provided track data overwriting any fields if they are set in [trackDataToMerge].
     * @return Returns the new track data.
     */
    fun mergeTrackData(trackDataToMerge: TrackData) = trackDataLock.withLock {
        trackData = if (trackData == null) trackDataToMerge else trackData!!.merge(trackDataToMerge)
        trackData!!
    }

    private fun onTrackStarted() {
        events.trackStarted()

        trackRecognizer?.recognizeTrack { recording.getAudio() }
            ?.thenAccept { trackData ->
                trackData?.let {
                    events.trackRecognized(trackData)
                    mergeTrackData(it)
                }
            }

        // Reset buffer to be sure that there is no initial silence
        recording.reset()
    }

    private fun onTrackEnded() {
        events.trackEnded()

        val audio = recording.reset()
        val trackData = this.trackData
        trackDataLock.withLock {
            this.trackData = null
        }
        fileWriter.writeToFileAsync(audio, trackData)
            .thenAccept { events.trackWritten(it) }

        if (stopRequested) {
            stop()
        }
    }
}