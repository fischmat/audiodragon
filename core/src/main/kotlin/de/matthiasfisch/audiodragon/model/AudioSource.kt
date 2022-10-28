package de.matthiasfisch.audiodragon.model

import xt.audio.Enums
import xt.audio.XtAudio
import java.util.*
import javax.sound.sampled.*

abstract class AudioSource(
    val name: String
) {
    abstract fun getSupportedAudioFormats(): List<AudioFormat>
}

// FIXME Make dynamic
fun getAudioPlatform(): AudioPlatform = XtAudioPlatform()

interface AudioPlatform {
    fun getRecordableAudioSources(): List<AudioSource>
}

class JASAudioPlatform: AudioPlatform {
    override fun getRecordableAudioSources() = AudioSystem.getMixerInfo()
            .map { AudioSystem.getMixer(it) }
            .filter { isRecordable(it) }
            .map { JASAudioSource(it) }

    private fun isRecordable(mixer: Mixer) = mixer.targetLineInfo.filterIsInstance<DataLine.Info>().isNotEmpty()
}

class JASAudioSource(val mixer: Mixer): AudioSource(mixer.mixerInfo.name) {
    val vendor: String by lazy { mixer.mixerInfo.vendor }
    val description: String by lazy { mixer.mixerInfo.description }
    val version: String by lazy { mixer.mixerInfo.version }

    override fun getSupportedAudioFormats(): List<AudioFormat> {
        val dataLineInfos = mixer.targetLineInfo.filterIsInstance<DataLine.Info>()
        check(dataLineInfos.isNotEmpty()) { "No target data lines available for mixer ${name}." }
        return dataLineInfos
            .flatMap { it.formats.toList() }
            .distinct()
    }
}

class XtAudioPlatform: AudioPlatform {
    override fun getRecordableAudioSources(): List<AudioSource> {
        XtAudio.init(null, null).use { platform ->
            val system = platform.setupToSystem(Enums.XtSetup.CONSUMER_AUDIO)
            val service = platform.getService(system) ?: throw IllegalStateException("Could not get service")
            val deviceList = service.openDeviceList(EnumSet.of(Enums.XtEnumFlags.ALL))
            return (0 until deviceList.count)
                    .map { deviceList.getId(it) }
                    .map { XtAudioSource(it, deviceList.getName(it)) }
        }
    }
}

class XtAudioSource(id: String, name: String): AudioSource(name) {
    override fun getSupportedAudioFormats(): List<AudioFormat> {
        return listOf()
    }
}