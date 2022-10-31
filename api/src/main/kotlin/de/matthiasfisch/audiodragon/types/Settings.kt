package de.matthiasfisch.audiodragon.types

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.nio.file.Paths

const val SETTINGS_VERSION = 1

@JsonIgnoreProperties(ignoreUnknown = true)
data class Settings(
    val recognition: RecognitionSettings,
    val splitting: SplittingSettings,
    val output: OutputSettings,
    val recording: RecordingSettings,
    val library: LibrarySettings
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "platform")
@JsonSubTypes(
    JsonSubTypes.Type(value = JASRecordingSettings::class, name = "java")
)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class RecordingSettings(
    val buffer: BufferSettings
)

@JsonIgnoreProperties(ignoreUnknown = true)
class JASRecordingSettings(buffer: BufferSettings): RecordingSettings(buffer)

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

@JsonIgnoreProperties(ignoreUnknown = true)
data class SplittingSettings(
    val splitAfterSilenceMillis: Long,
    val silenceRmsTolerance: Float
) {
    init {
        require(splitAfterSilenceMillis >= 0) { "Silence threshold must be non-negative." }
        require(silenceRmsTolerance >= 0) { "Silence RMS tolerance must be non-negative." }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "recognizer")
@JsonSubTypes(JsonSubTypes.Type(value = ShazamRecognitionSettings::class, name = "shazam"))
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class RecognitionSettings(
    val secondsUntilRecognition: Int,
    val sampleSeconds: Int,
    val maxRetries: Int,
    val postprocessors: List<RecognitionPostprocessorConfig>
) {
    init {
        require(secondsUntilRecognition >= 0) { "Seconds until recognition must be positive." }
        require(sampleSeconds > 0) { "Time of recognition samples must be positive." }
        require(maxRetries > 0) { "Maximum number of retries must be positive." }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ShazamRecognitionSettings(
    val rapidApiToken: String,
    secondsUntilRecognition: Int,
    sampleSeconds: Int,
    maxRetries: Int,
    postprocessors: List<RecognitionPostprocessorConfig>
): RecognitionSettings(secondsUntilRecognition, sampleSeconds, maxRetries, postprocessors)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(JsonSubTypes.Type(value = MusicBrainzPostprocessorConfig::class, name = "musicbrainz"))
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class RecognitionPostprocessorConfig(
    val preferInput: Boolean
)

@JsonIgnoreProperties(ignoreUnknown = true)
class MusicBrainzPostprocessorConfig(
    val minScore: Int,
    val userAgent: String,
    preferInput: Boolean
): RecognitionPostprocessorConfig(preferInput)

@JsonIgnoreProperties(ignoreUnknown = true)
class OutputSettings(
    val location: String,
    val encodingChunkLengthMs: Int,
    val coverartMaxDimension: Int
) {
    @JsonIgnore val path = Paths.get(location.replace("~", System.getProperty("user.home")))
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LibrarySettings(
    val dbPath: String,
    val scanThreads: Int
) {
    fun databasePath() = Paths.get(dbPath.replace("~", System.getProperty("user.home")))
}