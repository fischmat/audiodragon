package de.matthiasfisch.audiodragon.splitting

import de.matthiasfisch.audiodragon.model.PcmData
import de.matthiasfisch.audiodragon.model.duration
import de.matthiasfisch.audiodragon.model.getRMS
import de.matthiasfisch.audiodragon.recording.AudioChunk
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TrackBoundsDetector(private val minSilenceBetweenTracks: Duration, private val silenceRmsThreshold: Float) {
    private var lastSoundTime: Duration? = null
    private var currentTime: Duration = 0.milliseconds
    private var isSoundPresent: Boolean = false

    fun process(chunk: AudioChunk): TrackState {
        currentTime += chunk.pcmData.duration(chunk.audioFormat)
        if (isSilence(chunk.pcmData, chunk.audioFormat)) {
            if (lastSoundTime != null && isSoundPresent) {
                val timeSinceLastSound = currentTime - lastSoundTime!!
                if (timeSinceLastSound >= minSilenceBetweenTracks) {
                    isSoundPresent = false
                    return TrackState.TRACK_ENDED
                }
            }
        } else {
            val soundPreviouslyPresent = isSoundPresent
            isSoundPresent = true
            lastSoundTime = currentTime
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