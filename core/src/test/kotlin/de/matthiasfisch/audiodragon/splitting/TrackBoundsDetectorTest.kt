package de.matthiasfisch.audiodragon.splitting

import de.matthiasfisch.audiodragon.model.AudioChunk
import de.matthiasfisch.audiodragon.model.AudioSource
import de.matthiasfisch.audiodragon.util.PcmGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val MIN_SILENCE_BETWEEN_TRACKS = 100.milliseconds
private const val SILENCE_RMS_THRESHOLD = 0.1f

class TrackBoundsDetectorTest : FunSpec({
    lateinit var subject: TrackBoundsDetector
    val audioSource = mockk<AudioSource>()

    fun getAudioChunks(generator: PcmGenerator) = generator
        .pcm
        .asSequence()
        .chunked(4000)
        .map { it.toByteArray() }
        .map { AudioChunk(it, audioSource, generator.audioFormat()) }
        .toList()

    beforeEach {
        subject = TrackBoundsDetector(MIN_SILENCE_BETWEEN_TRACKS, SILENCE_RMS_THRESHOLD)
    }

    test("Continuous silence recognized") {
        // Arrange
        val generator = PcmGenerator(10000f)
            .silence(1.seconds)
        val audioChunks = getAudioChunks(generator)

        // Act
        val results = audioChunks.map { subject.process(it) }

        // Assert
        results.forEach { it shouldBe TrackState.SILENCE }
    }

    test("Continuous sound recognized") {
        // Arrange
        val generator = PcmGenerator(3000f)
            .tone(20f, 1.seconds)
        val audioChunks = getAudioChunks(generator)

        // Act
        val results = audioChunks.map { subject.process(it) }

        // Assert
        results shouldBe listOf(
            TrackState.TRACK_STARTED,
            TrackState.PLAYING,
            TrackState.PLAYING
        )
    }

    test("Initial silence and track start recognized") {
        // Arrange
        val generator = PcmGenerator(3000f)
            .silence(750.milliseconds)
            .tone(20f, 1.seconds)
        val audioChunks = getAudioChunks(generator)

        // Act
        val results = audioChunks.map { subject.process(it) }

        // Assert
        results shouldBe listOf(
            TrackState.SILENCE,
            TrackState.SILENCE,
            TrackState.TRACK_STARTED,
            TrackState.PLAYING,
            TrackState.PLAYING,
            TrackState.PLAYING
        )
    }

    test("Initial silence, track start and track end recognized") {
        // Arrange
        val generator = PcmGenerator(3000f)
            .silence(750.milliseconds)
            .tone(20f, 1.seconds)
            .silence(1.seconds)
        val audioChunks = getAudioChunks(generator)

        // Act
        val results = audioChunks.map { subject.process(it) }

        // Assert
        results shouldBe listOf(
            TrackState.SILENCE,
            TrackState.SILENCE,
            TrackState.TRACK_STARTED,
            TrackState.PLAYING,
            TrackState.PLAYING,
            TrackState.PLAYING,
            TrackState.TRACK_ENDED,
            TrackState.SILENCE,
            TrackState.SILENCE
        )
    }
})
