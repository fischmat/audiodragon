package de.matthiasfisch.audiodragon.recording

import de.matthiasfisch.audiodragon.model.AudioSource
import javax.sound.sampled.AudioFormat

interface RecordingFactory {

    fun createRecording(audioSource: AudioSource, audioFormat: AudioFormat, bufferSize: Int): Recording
}