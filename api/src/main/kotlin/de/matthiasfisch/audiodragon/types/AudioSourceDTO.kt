package de.matthiasfisch.audiodragon.types

import de.matthiasfisch.audiodragon.recording.AudioSource
import de.matthiasfisch.audiodragon.util.getId
import javax.sound.sampled.AudioFormat

data class AudioSourceDTO(
    val id: String,
    val name: String,
    val vendor: String,
    val description: String,
    val version: String
) {
    constructor(audioSource: AudioSource) : this(
        audioSource.getId(),
        audioSource.name,
        audioSource.vendor,
        audioSource.description,
        audioSource.version
    )
}

data class AudioFormatDTO(
    val encoding: AudioEncodingDTO,
    val sampleRate: Float = -1f,
    val sampleSizeInBits: Int,
    val channels: Int,
    val bigEndian: Boolean
) {
    constructor(audioFormat: AudioFormat): this(
        AudioEncodingDTO.values().single { it.audioSystemEncoding == audioFormat.encoding },
        audioFormat.sampleRate,
        audioFormat.sampleSizeInBits,
        audioFormat.channels,
        audioFormat.isBigEndian
    )

    fun toAudioSystemFormat() = AudioFormat(
        encoding.audioSystemEncoding,
        sampleRate,
        sampleSizeInBits,
        channels,
        channels * (sampleSizeInBits / 8),
        sampleRate,
        bigEndian
    )
}

enum class AudioEncodingDTO(val audioSystemEncoding: AudioFormat.Encoding) {
    PCM_SIGNED(AudioFormat.Encoding.PCM_SIGNED),
    PCM_UNSIGNED(AudioFormat.Encoding.PCM_UNSIGNED),
    PCM_FLOAT(AudioFormat.Encoding.PCM_FLOAT),
    ULAW(AudioFormat.Encoding.ULAW),
    ALAW(AudioFormat.Encoding.ALAW)
}