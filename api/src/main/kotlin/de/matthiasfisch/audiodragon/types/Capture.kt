package de.matthiasfisch.audiodragon.types

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import de.matthiasfisch.audiodragon.recording.Capture
import de.matthiasfisch.audiodragon.util.AudioSourceId

data class CaptureDTO(
    val audioSource: AudioSourceDTO,
    val audioFormat: AudioFormatDTO
) {
    constructor(capture: Capture): this(
        AudioSourceDTO(capture.audioSource),
        AudioFormatDTO(capture.audioFormat)
    )
}

data class StartCaptureInstructionDTO(
    val audioSourceId: AudioSourceId,
    val format: AudioFormatDTO,
    val recognizeSongs: Boolean,
    val outputFormat: FileOutputOptionsDTO
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = MP3OptionsDTO::class)
@JsonSubTypes(JsonSubTypes.Type(value = MP3OptionsDTO::class, name = "mp3"))
interface FileOutputOptionsDTO

data class MP3OptionsDTO(
    val bitRate: Int,
    val channels: Int,
    val quality: Int,
    val vbr: Boolean
): FileOutputOptionsDTO