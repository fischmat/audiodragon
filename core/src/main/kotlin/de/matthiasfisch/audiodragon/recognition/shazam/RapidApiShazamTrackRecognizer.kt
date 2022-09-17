package de.matthiasfisch.audiodragon.recognition.shazam

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import de.matthiasfisch.audiodragon.model.PcmData
import de.matthiasfisch.audiodragon.model.TrackData
import de.matthiasfisch.audiodragon.model.duration
import de.matthiasfisch.audiodragon.recognition.TrackRecognizer
import de.matthiasfisch.audiodragon.util.readTextAtPath
import de.matthiasfisch.audiodragon.util.readTextListAtPath
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration

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
    maxRetriesForRecognition: Int
): TrackRecognizer(delayUntilRecognition, sampleDuration, maxRetriesForRecognition) {
    private val httpClient = OkHttpClient()
    private val recognizerId = UUID.randomUUID()

    init {
        require(apiKey.isNotBlank()) { "RapidAPI Shazam API key must not be blank." }
    }

    override fun recognizeTrackInternal(
        sample: PcmData,
        audioFormat: AudioFormat,
        previousSamples: List<PcmData>
    ): TrackData? {
        val alreadySubmittedDuration = previousSamples.map { it.duration(audioFormat) }.reduce { d1, d2 -> d1 + d2 }
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

    protected fun shazamRecognitionEndpoint(totalSentDuration: Duration) = String.format(
        "https://shazam.p.rapidapi.com/songs/v2/detect?timezone=Europe%%2FBerlin&locale=en-US&identifier=%s&samplems=%s",
        recognizerId,
        totalSentDuration.inWholeMilliseconds
    )

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

