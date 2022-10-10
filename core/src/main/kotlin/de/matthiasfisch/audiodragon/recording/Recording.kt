package de.matthiasfisch.audiodragon.recording

import de.matthiasfisch.audiodragon.model.PcmData
import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.buffer.ResettableAudioBuffer
import de.matthiasfisch.audiodragon.model.AudioSource
import io.reactivex.Flowable
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioFormat

interface Recording {
    fun startRecording()

    fun stopRecording(): CompletableFuture<AudioBuffer>

    fun getAudio(): ResettableAudioBuffer

    fun reset(): AudioBuffer

    fun audioChunkFlowable(): Flowable<AudioChunk>
}

data class AudioChunk(
    val pcmData: PcmData,
    val audioSource: AudioSource,
    val audioFormat: AudioFormat
)