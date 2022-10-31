package de.matthiasfisch.audiodragon.model

import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.recording.Recording
import xt.audio.Enums.XtEnumFlags
import xt.audio.Enums.XtSample
import xt.audio.Enums.XtSetup
import xt.audio.Structs
import xt.audio.Structs.XtFormat
import xt.audio.XtAudio
import xt.audio.XtService
import java.util.*
import javax.sound.sampled.AudioFormat

private val AUDIO_FORMATS = listOf(
    AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 8, 1, 1, 44000f, false), // 8 bit Mono
    AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 8, 2, 2, 44000f, false), // 8 bit Stereo
    AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44000f, 16, 1, 2, 44000f, false), // 16 bit Mono
    AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 16, 2, 4, 44000f, false), // 16 bit Stereo
    AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 24, 1, 3, 44000f, false), // 24 bit Mono
    AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 24, 2, 6, 44000f, false), // 24 bit Stereo
    AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 32, 1, 4, 44000f, false), // 32 bit Mono
    AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44000f, 32, 2, 8, 44000f, false), // 32 bit Stereo
)

class XtAudioSource(private val xtAudioId: String, name: String) : AudioSource(name) {
    override fun getSupportedFormats(): List<AudioFormat> = consumerAudioService { service ->
        service.openDevice(xtAudioId).use { device ->
            AUDIO_FORMATS.filter { device.supportsFormat(it.toXtAudioFormat()) }
        }
    }
}

class XtAudioPlatform : AudioPlatform {

    override fun createRecording(
        audioSource: AudioSource,
        audioFormat: AudioFormat,
        bufferFactory: (AudioFormat) -> AudioBuffer,
        batchSize: Int
    ): Recording {
        TODO("Not yet implemented")
    }

    override fun getAudioSources(): List<AudioSource> = consumerAudioService {  service ->
        service.openDeviceList(EnumSet.of(XtEnumFlags.INPUT)).use { deviceList ->
            (0 until deviceList.count).map {
                val deviceId = deviceList.getId(it)
                val name = deviceList.getName(deviceId)
                XtAudioSource(deviceId, name)
            }
        }
    }
}

private fun <T> consumerAudioService(action: (XtService) -> T) = XtAudio.init(null, null).use { platform ->
    val system = platform.setupToSystem(XtSetup.CONSUMER_AUDIO)
    action(platform.getService(system))
}

private fun AudioFormat.toXtAudioFormat(): XtFormat {
    val sampleType = when(sampleSizeInBits) {
        8 -> XtSample.UINT8
        16 -> XtSample.INT16
        24 -> XtSample.INT24
        32 -> XtSample.INT32
        else -> throw IllegalStateException("Unsupported sample size of $sampleSizeInBits bits.")
    }
    val mix = Structs.XtMix(sampleRate.toInt(), sampleType)
    val channels = Structs.XtChannels(channels, 0, 0, 0)
    return XtFormat(mix, channels)
}