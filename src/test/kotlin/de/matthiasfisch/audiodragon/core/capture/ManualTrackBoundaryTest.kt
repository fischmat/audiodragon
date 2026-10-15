package de.matthiasfisch.audiodragon.core.capture

import de.matthiasfisch.audiodragon.core.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.core.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.core.model.AudioBufferFactory
import de.matthiasfisch.audiodragon.core.model.AudioSource
import de.matthiasfisch.audiodragon.core.model.Recording
import de.matthiasfisch.audiodragon.core.model.TrackData
import de.matthiasfisch.audiodragon.core.splitting.TrackBoundsDetector
import de.matthiasfisch.audiodragon.core.writer.AudioFileWriter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.reactivex.processors.PublishProcessor
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds

class ManualTrackBoundaryTest : FunSpec({
    test("writes the pending audio and clears its track metadata") {
        val format = AudioFormat(44_100f, 16, 1, true, false)
        val recording = IdleRecording(format)
        val writer = RecordingWriter()
        val capture = Capture(recording, TrackBoundsDetector(100.milliseconds, 0.01f), null, writer)
        recording.getAudio().add(ByteArray(format.frameSize * 4))
        capture.mergeTrackData(TrackData(title = "Manual marker"))

        capture.markTrackBoundary() shouldBe true

        writer.writtenTracks.single()?.title shouldBe "Manual marker"
        capture.currentTrack() shouldBe null
        capture.markTrackBoundary() shouldBe false
    }
})

private class IdleRecording(format: AudioFormat) : Recording<AudioSource>(
    object : AudioSource("test source") { override fun getAudioFormats() = emptyList<AudioFormat>() },
    format,
    1024,
    AudioBufferFactory { InMemoryAudioBuffer(it, 1024) }
) {
    override fun record(chunkPublisher: PublishProcessor<de.matthiasfisch.audiodragon.core.model.AudioChunk>) = Unit
}

private class RecordingWriter : AudioFileWriter {
    val writtenTracks = mutableListOf<TrackData?>()

    override fun writeToFile(audioBuffer: AudioBuffer, trackData: TrackData?): Path {
        writtenTracks += trackData
        return Path.of("recorded.mp3")
    }

    override fun writeToFileAsync(audioBuffer: AudioBuffer, trackData: TrackData?) =
        CompletableFuture.completedFuture(writeToFile(audioBuffer, trackData))
}
