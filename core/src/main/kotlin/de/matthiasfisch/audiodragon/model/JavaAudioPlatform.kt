package de.matthiasfisch.audiodragon.model

import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.recording.JavaAudioSystemRecording
import de.matthiasfisch.audiodragon.recording.Recording
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer

class JASAudioSource(val mixer: Mixer): AudioSource(mixer.mixerInfo.name) {
    val vendor: String  =  mixer.mixerInfo.vendor
    val description: String  =  mixer.mixerInfo.description
    val version: String  =  mixer.mixerInfo.version

    override fun getSupportedFormats(): List<AudioFormat> {
        val dataLineInfos = mixer.targetLineInfo.filterIsInstance<DataLine.Info>()
        check(dataLineInfos.isNotEmpty()) { "No target data lines available for mixer ${name}." }
        return dataLineInfos
            .flatMap { it.formats.toList() }
            .distinct()
    }
}

class JavaAudioPlatform : AudioPlatform {
    override fun createRecording(
        audioSource: AudioSource,
        audioFormat: AudioFormat,
        bufferFactory: (AudioFormat) -> AudioBuffer,
        batchSize: Int
    ): Recording {
        require(audioSource is JASAudioSource) { "Incompatible audio source provided." }
        return JavaAudioSystemRecording(audioSource, audioFormat, bufferFactory, batchSize)
    }

    override fun getAudioSources(): List<AudioSource> {
        return AudioSystem.getMixerInfo()
            .map { AudioSystem.getMixer(it) }
            .filter { isRecordable(it) }
            .map { JASAudioSource(it) }
    }

    private fun isRecordable(mixer: Mixer) = mixer.targetLineInfo.filterIsInstance<DataLine.Info>().isNotEmpty()
}