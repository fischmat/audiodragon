package de.matthiasfisch.audiodragon.splitting

import de.matthiasfisch.audiodragon.model.PcmData
import de.matthiasfisch.audiodragon.model.getRMS
import de.matthiasfisch.audiodragon.recording.AudioChunk
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TrackBoundsDetector(private val minSilenceBetweenTracks: Duration, private val silenceRmsThreshold: Float) {
    private var lastSoundTime: Instant? = null
    private var isSoundPresent: Boolean = false

    operator fun invoke(chunk: AudioChunk): TrackState {
        if (isSilence(chunk.pcmData, chunk.audioFormat)) {
            if (lastSoundTime != null && isSoundPresent) {
                val timeSinceLastSound = lastSoundTime!!.until(Instant.now(), ChronoUnit.MILLIS).milliseconds
                if (timeSinceLastSound >= minSilenceBetweenTracks) {
                    isSoundPresent = false
                    return TrackState.TRACK_ENDED
                }
            }
        } else {
            val soundPreviouslyPresent = isSoundPresent
            isSoundPresent = true
            lastSoundTime = Instant.now()
            if (!soundPreviouslyPresent) {
                return TrackState.TRACK_STARTED
            }
        }
        return if (isSoundPresent) TrackState.PLAYING else TrackState.SILENCE
    }

    private fun isSilence(pcmData: PcmData, audioFormat: AudioFormat) = pcmData.getRMS(audioFormat) <= silenceRmsThreshold
}

enum class TrackState {
    TRACK_STARTED, TRACK_ENDED, SILENCE, PLAYING
}