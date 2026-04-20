package org.oxycblt.auxio.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.oxycblt.auxio.download.model.TrackMetadata
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeezerClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchTrack(query: String, limit: Int = 10): List<TrackMetadata> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_URL/track?q=$encoded&limit=$limit"
            val tracks = getJson(url)?.jsonObject?.get("data")?.jsonArray ?: return@withContext emptyList()
            tracks.map { parseTrack(it.jsonObject) }
        }

    suspend fun getTrackById(deezerId: String): TrackMetadata? =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/track/$deezerId"
            val obj = getJson(url)?.jsonObject ?: return@withContext null
            if (obj["id"]?.jsonPrimitive?.longOrNull == null) return@withContext null
            parseTrack(obj)
        }

    suspend fun searchByIsrc(isrc: String): TrackMetadata? =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/track/isrc:$isrc"
            val obj = getJson(url)?.jsonObject ?: return@withContext null
            if (obj["id"]?.jsonPrimitive?.longOrNull == null) return@withContext null
            parseTrack(obj)
        }

    private fun getJson(url: String): JsonElement? {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        return json.parseToJsonElement(body)
    }

    private fun parseTrack(obj: JsonObject): TrackMetadata {
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
        val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 0
        val trackPosition = obj["track_position"]?.jsonPrimitive?.intOrNull ?: 0
        val diskNumber = obj["disk_number"]?.jsonPrimitive?.intOrNull ?: 0
        val isrc = obj["isrc"]?.jsonPrimitive?.contentOrNull ?: ""
        val link = obj["link"]?.jsonPrimitive?.contentOrNull ?: ""

        val artist = obj["artist"]?.jsonObject
        val artistName = artist?.get("name")?.jsonPrimitive?.contentOrNull ?: ""

        val album = obj["album"]?.jsonObject
        val albumTitle = album?.get("title")?.jsonPrimitive?.contentOrNull ?: ""
        val albumId = album?.get("id")?.jsonPrimitive?.longOrNull ?: 0
        val coverXl = album?.get("cover_xl")?.jsonPrimitive?.contentOrNull
            ?: album?.get("cover_big")?.jsonPrimitive?.contentOrNull
            ?: album?.get("cover_medium")?.jsonPrimitive?.contentOrNull ?: ""

        val contributors = obj["contributors"]?.jsonArray
        val allArtists = contributors?.mapNotNull {
            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        }?.joinToString(", ") ?: artistName

        return TrackMetadata(
            spotifyId = "deezer:$id",
            artists = allArtists,
            name = title,
            albumName = albumTitle,
            albumArtist = artistName,
            durationMs = duration * 1000,
            images = coverXl,
            releaseDate = "",
            trackNumber = trackPosition,
            discNumber = diskNumber,
            externalUrl = link,
            isrc = isrc,
            albumId = "deezer:$albumId",
            artistId = ""
        )
    }

    companion object {
        private const val BASE_URL = "https://api.deezer.com/2.0"
        private const val SEARCH_URL = "$BASE_URL/search"
    }
}
