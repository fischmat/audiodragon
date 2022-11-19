package de.matthiasfisch.audiodragon.recognition.shazam

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import de.matthiasfisch.audiodragon.model.PcmData
import de.matthiasfisch.audiodragon.model.TrackData
import de.matthiasfisch.audiodragon.model.duration
import de.matthiasfisch.audiodragon.recognition.TrackRecognitionPostprocessor
import de.matthiasfisch.audiodragon.recognition.TrackRecognizer
import de.matthiasfisch.audiodragon.util.ApiConfig
import de.matthiasfisch.audiodragon.util.readTextAtPath
import de.matthiasfisch.audiodragon.util.readTextListAtPath
import mu.KotlinLogging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val LOGGER = KotlinLogging.logger {}

private const val DEFAULT_BASE_URL = "https://shazam.p.rapidapi.com"

private val SHAZAM_FORMAT = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 1, 2, 44100f, false)
private const val SHAZAM_SAMPLE_SIZE_LIMIT = 500 * 1024
private val SONG_TILE_PATH = JsonPath.compile("$.track.title")
private val SONG_ARTIST_PATH = JsonPath.compile("$.track.subtitle")
private val SONG_ALBUM_PATH = JsonPath.compile("$.track.sections[?(@.type=='SONG')].metadata[?(@.title=='Album')].text")
private val SONG_LABEL_PATH = JsonPath.compile("$.track.sections[?(@.type=='SONG')].metadata[?(@.title=='Label')].text")
private val SONG_RELEASED_YEAR_PATH =
    JsonPath.compile("$.track.sections[?(@.type=='SONG')].metadata[?(@.title=='Released')].text")
private val SONG_GENRE_PATH = JsonPath.compile("$.track.genres.primary")
private val SONG_BACKGROUND_IMAGE_PATH = JsonPath.compile("$.track.images.background")
private val SONG_COVERART_IMAGE_PATH = JsonPath.compile("$.track.images.coverart")
private val SONG_COVERART_IMAGE_HQ_PATH = JsonPath.compile("$.track.images.coverarthq")
private val SONG_LYRICS_PATH = JsonPath.compile("$.track.sections[?(@.type=='LYRICS')].text[*]")

open class RapidApiShazamTrackRecognizer(
    private val apiKey: String,
    delayUntilRecognition: Duration,
    sampleDuration: Duration,
    maxRetriesForRecognition: Int,
    postprocessors: List<TrackRecognitionPostprocessor>,
    private val apiConfig: ApiConfig = ApiConfig.unconfigured()
) : TrackRecognizer(delayUntilRecognition, sampleDuration, maxRetriesForRecognition, postprocessors) {
    private val httpClient: OkHttpClient = apiConfig.configure(OkHttpClient.Builder()).build()
    private val recognizerId = UUID.randomUUID()

    init {
        require(apiKey.isNotBlank()) { "RapidAPI Shazam API key must not be blank." }
    }

    override fun recognizeTrackInternal(
        sample: PcmData,
        audioFormat: AudioFormat,
        previousSamples: List<PcmData>
    ): TrackData? {
        val previousDurations = previousSamples.map { it.duration(audioFormat) }
        val alreadySubmittedDuration =
            if (previousDurations.isNotEmpty()) previousDurations.reduce { d1, d2 -> d1 + d2 } else Duration.ZERO
        val response = requestSongRecognition(sample, audioFormat, alreadySubmittedDuration)
        return parseResponse(response)
    }

    private fun requestSongRecognition(
        sample: PcmData,
        audioFormat: AudioFormat,
        alreadySubmittedDuration: Duration
    ): DocumentContext {
        val encodedSample = encodeSampleData(sample, audioFormat)
        val totalSentDuration = alreadySubmittedDuration + sample.duration(audioFormat)

        val requestBody: RequestBody = encodedSample.toRequestBody("text/plain".toMediaTypeOrNull())
        val request: Request = Request.Builder()
            .url(shazamRecognitionEndpoint(totalSentDuration))
            .post(requestBody)
            .addHeader("content-type", "text/plain")
            .addHeader("x-rapidapi-host", "shazam.p.rapidapi.com")
            .addHeader("x-rapidapi-key", apiKey)
            .build()

        httpClient.newCall(request).execute().use { response ->
            response.body.use { responseBody ->
                LOGGER.info {
                    "RapidAPI Shazam: ${
                        response.headers("x-ratelimit-requests-remaining").first()
                    } of ${
                        response.headers("x-ratelimit-requests-limit").first()
                    } requests remaining in period (will be reset in ${
                        response.headers("x-ratelimit-requests-reset").first().toInt().seconds
                    })."
                }
                return if (response.isSuccessful && responseBody != null) {
                    JsonPath.parse(responseBody.byteStream())
                } else {
                    throw IOException(
                        String.format(
                            "Request to RapidAPI Shazam API failed with exist code %s",
                            response.code
                        )
                    )
                }
            }
        }
    }

    private fun parseResponse(response: DocumentContext): TrackData? {
        val title = response.readTextAtPath(SONG_TILE_PATH) ?: return null
        return TrackData(
            title,
            response.readTextAtPath(SONG_ARTIST_PATH),
            response.readTextAtPath(SONG_ALBUM_PATH),
            response.readTextListAtPath(SONG_LABEL_PATH),
            response.readTextListAtPath(SONG_GENRE_PATH),
            response.readTextAtPath(SONG_RELEASED_YEAR_PATH),
            response.readTextAtPath(SONG_COVERART_IMAGE_HQ_PATH) ?: response.readTextAtPath(SONG_COVERART_IMAGE_PATH),
            response.readTextAtPath(SONG_BACKGROUND_IMAGE_PATH),
            response.readTextListAtPath(SONG_LYRICS_PATH)
        )
    }

    private fun shazamRecognitionEndpoint(totalSentDuration: Duration) =
        "${apiConfig.apiBaseUrlOverride ?: DEFAULT_BASE_URL}/songs/v2/detect?timezone=Europe%%2FBerlin&locale=en-US&identifier=$recognizerId&samplems=${totalSentDuration.inWholeMilliseconds}"

    private fun encodeSampleData(sample: PcmData, sourceFormat: AudioFormat): String {
        val pcmDataInShazamFormat = convertPcmDataToShazamFormat(sample, sourceFormat)
        require(pcmDataInShazamFormat.size < SHAZAM_SAMPLE_SIZE_LIMIT) { "Size of sample converted to Shazam format is bigger than the allowed $SHAZAM_SAMPLE_SIZE_LIMIT bytes." }
        return Base64.getEncoder().encodeToString(pcmDataInShazamFormat)
    }

    private fun convertPcmDataToShazamFormat(sample: PcmData, sourceFormat: AudioFormat) = try {
        val audioInputStream = AudioInputStream(sample.inputStream(), sourceFormat, sample.size.toLong())
        val shazamFormatInput: AudioInputStream = AudioSystem.getAudioInputStream(SHAZAM_FORMAT, audioInputStream)
        shazamFormatInput.readAllBytes()
    } catch (e: IOException) {
        throw IOException("Failed to convert PCM data to Shazam format due to an IO exception.", e)
    }
}

