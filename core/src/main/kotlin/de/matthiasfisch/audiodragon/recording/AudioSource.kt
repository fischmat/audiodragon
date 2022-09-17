package de.matthiasfisch.audiodragon.recording

import javax.sound.sampled.*

class AudioSource(val mixer: Mixer) {
    val name: String by lazy { mixer.mixerInfo.name }
    val vendor: String by lazy { mixer.mixerInfo.vendor }
    val description: String by lazy { mixer.mixerInfo.description }
    val version: String by lazy { mixer.mixerInfo.version }

    fun getSupportedAudioFormats(): List<AudioFormat> {
        val dataLineInfos = mixer.targetLineInfo.filterIsInstance<DataLine.Info>()
        check(dataLineInfos.isNotEmpty()) { "No target data lines available for mixer ${name}." }
        return dataLineInfos
            .flatMap { it.formats.toList() }
            .distinct()
    }
}