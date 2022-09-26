package de.matthiasfisch.audiodragon.types

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import de.matthiasfisch.audiodragon.model.TrackData
import java.time.Instant

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = TrackStartedEventDTO::class, name = "trackStarted"),
    JsonSubTypes.Type(value = TrackEndedEventDTO::class, name = "trackEnded"),
    JsonSubTypes.Type(value = TrackRecognitionEventDTO::class, name = "trackRecognized"),
    JsonSubTypes.Type(value = CaptureStartedEventDTO::class, name = "captureStarted"),
    JsonSubTypes.Type(value = CaptureEndedEventDTO::class, name = "captureEnded"),
    JsonSubTypes.Type(value = CaptureEndRequestedEventDTO::class, name = "captureEndRequested"),
    JsonSubTypes.Type(value = AudioMetricsEventDTO::class, name = "metrics"),
    JsonSubTypes.Type(value = ErrorEventDTO::class, name = "error")
)
abstract class EventDTO {
    val timestamp: Instant = Instant.now()
}

abstract class CaptureEventDTO(val capture: CaptureDTO) : EventDTO()

class TrackStartedEventDTO(capture: CaptureDTO) : CaptureEventDTO(capture)

class TrackEndedEventDTO(capture: CaptureDTO) : CaptureEventDTO(capture)

class TrackRecognitionEventDTO(capture: CaptureDTO, val track: TrackData) : CaptureEventDTO(capture)

class CaptureStartedEventDTO(capture: CaptureDTO): CaptureEventDTO(capture)

class CaptureEndedEventDTO(capture: CaptureDTO): CaptureEventDTO(capture)

class CaptureEndRequestedEventDTO(capture: CaptureDTO): CaptureEventDTO(capture)

class ErrorEventDTO(val message: String?, val stacktrace: List<String>): EventDTO() {
    constructor(exception: Throwable) : this(exception.message, exception.stackTrace.map { it.toString() })
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = InMemoryBufferStats::class, name = "inMemory"),
    JsonSubTypes.Type(value = DiskSpillingBufferStats::class, name = "diskSpilling")
)
interface BufferStats

data class InMemoryBufferStats (
    val size: Long,
    val maxMemory: Long
): BufferStats

data class DiskSpillingBufferStats(
    val size: Long,
    val maxMemory: Long,
    val freeDiskSpace: Long
): BufferStats

data class AudioMetricsEventDTO(
    val rms: Double,
    val trackTime: Long,
    val frequencies: List<Float>,
    val buffer: BufferStats
): EventDTO()