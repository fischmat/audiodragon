package de.matthiasfisch.audiodragon.core.recognition.musicbrainz

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import de.matthiasfisch.audiodragon.core.model.TrackData
import de.matthiasfisch.audiodragon.core.recognition.TrackRecognitionPostprocessor
import de.matthiasfisch.audiodragon.core.util.ApiConfig
import de.matthiasfisch.audiodragon.core.util.readNumberAtPath
import de.matthiasfisch.audiodragon.core.util.readTextAtPath
import de.matthiasfisch.audiodragon.core.util.readTextListAtPath
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val LOGGER = KotlinLogging.logger {}

private val SONG_RECOGNITION_SCORE_PATH = JsonPath.compile("$.recordings[0].score")
private val SONG_TILE_PATH = JsonPath.compile("$.recordings[0].title")
private val SONG_ARTIST_PATH = JsonPath.compile("$.recordings[0].artist-credit[0].name")
private val SONG_ALBUM_PATH = JsonPath.compile("$.recordings[0].releases[0].title")
private val SONG_RELEASED_YEAR_PATH = JsonPath.compile("$.recordings[0].first-release-date")
private val SONG_GENRE_PATH = JsonPath.compile("$.recordings[0].tags[*].name")
private val SONG_LENGTH_PATH = JsonPath.compile("$.recordings[0].length")

private const val DEFAULT_API_BASE_URL = "https://musicbrainz.org"
private const val DEFAULT_USER_AGENT = "AudioDragon (https://github.com/fischmat/audiodragon)"

class MusicBrainzTrackDataLoader(
    private val minScore: Int,
    private val preferInput: Boolean,
    private val apiConfig: ApiConfig
) : TrackRecognitionPostprocessor {
    private val httpClient = apiConfig.configure(OkHttpClient.Builder()).build()

    override fun augment(track: TrackData): TrackData {
        if (track.title == null || track.artist == null) {
            LOGGER.debug { "Title and/or artist of track is unknown. Augmenting track information will not be attempted." }
            return track
        }

        return try {
            val loadedTrackData = loadTrackData(track) ?: return track
            LOGGER.info { "Additional track data successfully loaded from MusicBrainz." }
            if (preferInput) loadedTrackData.merge(track) else track.merge(loadedTrackData)
        } catch (e: Throwable) {
            LOGGER.error(e) { "Failed to load track data for track ${track.artist} - ${track.title} from MusicBrainz API." }
            track // Return input track data on error
        }
    }

    private fun loadTrackData(track: TrackData): TrackData? {
        val url = getSearchEndpointUrl(track)
        val request = Request.Builder()
            .get()
            .url(url)
            .addHeader("user-agent", apiConfig.userAgent ?: DEFAULT_USER_AGENT)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            response.body.use { responseBody ->
                if (!response.isSuccessful) {
                    throw IOException("Loading track information from MusicBrainz API failed with status code ${response.code} ($url).")
                } else if (responseBody == null) {
                    throw IOException("MusicBrainz API returned an empty response for request to $url.")
                }

                val document = JsonPath.parse(responseBody.byteStream())
                val score = getBestRecordingScore(document)
                if (score < minScore) {
                    LOGGER.debug {
                        "The best result returned by the MusicBrainz API has a score ($score) " +
                                "less than the minimum required score $minScore. Track information will not be augmented."
                    }
                    return null
                }
                parseResponseBody(document)
            }
        }
    }

    private fun getBestRecordingScore(response: DocumentContext) =
        response.readNumberAtPath(SONG_RECOGNITION_SCORE_PATH)?.toInt() ?: 0

    private fun parseResponseBody(response: DocumentContext) = TrackData(
        title = response.readTextAtPath(SONG_TILE_PATH),
        artist = response.readTextAtPath(SONG_ARTIST_PATH),
        album = response.readTextAtPath(SONG_ALBUM_PATH),
        genres = response.readTextListAtPath(SONG_GENRE_PATH),
        releaseYear = response.readTextAtPath(SONG_RELEASED_YEAR_PATH),
        lengthMillis = response.readNumberAtPath(SONG_LENGTH_PATH)?.toInt()
    )

    private fun getSearchEndpointUrl(track: TrackData): URL {
        val artistEncoded = URLEncoder.encode(track.artist, StandardCharsets.UTF_8)
        val titleEncoded = URLEncoder.encode(track.title, StandardCharsets.UTF_8)
        return URL("${apiConfig.apiBaseUrlOverride ?: DEFAULT_API_BASE_URL}/ws/2/recording/?query=recording:${titleEncoded}%20and%20artist:$artistEncoded&fmt=json&inc=")
    }
}