package de.matthiasfisch.audiodragon.core.capture

import de.matthiasfisch.audiodragon.core.model.TrackData
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import java.nio.file.Path

class CaptureEvents {
    private val trackStartedPublisher = PublishProcessor.create<Unit>()
    private val trackEndedPublisher = PublishProcessor.create<Unit>()
    private val trackRecognizedPublisher = PublishProcessor.create<TrackData>()
    private val trackWrittenPublisher = PublishProcessor.create<Path>()
    private val captureStartedPublisher = PublishProcessor.create<Unit>()
    private val captureStoppedPublisher = PublishProcessor.create<Unit>()
    private val captureStopRequestedPublisher = PublishProcessor.create<Unit>()

    fun trackStarted() {
        trackStartedPublisher.onNext(Unit)
    }

    fun trackEnded() {
        trackStartedPublisher.onNext(Unit)
    }

    fun trackRecognized(trackData: TrackData) {
        trackRecognizedPublisher.onNext(trackData)
    }

    fun trackWritten(path: Path) {
        trackWrittenPublisher.onNext(path)
    }

    fun captureStarted() {
        captureStartedPublisher.onNext(Unit)
    }

    fun captureStopped() {
        captureStoppedPublisher.onNext(Unit)
    }

    fun captureStopRequested() {
        captureStopRequestedPublisher.onNext(Unit)
    }

    fun trackStartEvents() = Flowable.fromPublisher(trackStartedPublisher)
    fun trackEndedEvents() = Flowable.fromPublisher(trackEndedPublisher)
    fun trackRecognizedEvents() = Flowable.fromPublisher(trackRecognizedPublisher)
    fun trackWrittenEvents() = Flowable.fromPublisher(trackWrittenPublisher)
    fun captureStartedEvents() = Flowable.fromPublisher(captureStartedPublisher)
    fun captureStoppedEvents() = Flowable.fromPublisher(captureStoppedPublisher)
    fun captureStopRequestedEvents() = Flowable.fromPublisher(captureStopRequestedPublisher)
}