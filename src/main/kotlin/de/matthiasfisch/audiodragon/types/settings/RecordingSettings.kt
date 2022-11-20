package de.matthiasfisch.audiodragon.types.settings

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonIgnoreProperties(ignoreUnknown = true)
data class RecordingSettings(
    val platform: String,
    val buffer: BufferSettings
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = InMemoryBufferSettings::class, name = "memory"),
    JsonSubTypes.Type(value = DiskSpillingBufferSettings::class, name = "hybrid")
)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class BufferSettings(
    val batchSize: Int
)

class InMemoryBufferSettings(
    batchSize: Int,
    val initialBufferSize: Int
): BufferSettings(batchSize)

class DiskSpillingBufferSettings(
    batchSize: Int,
    val inMemoryBufferMaxSize: Int
): BufferSettings(batchSize)