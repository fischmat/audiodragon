package de.matthiasfisch.audiodragon.model

import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.recording.Recording
import javax.sound.sampled.AudioFormat

abstract class AudioSource(val name: String) {

    abstract fun getSupportedFormats(): List<AudioFormat>
}

interface AudioPlatform {
    fun createRecording(
        audioSource: AudioSource,
        audioFormat: AudioFormat,
        bufferFactory: (AudioFormat) -> AudioBuffer,
        batchSize: Int
    ): Recording

    fun getAudioSources(): List<AudioSource>
}