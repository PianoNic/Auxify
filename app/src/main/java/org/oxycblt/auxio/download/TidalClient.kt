package org.oxycblt.auxio.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.oxycblt.auxio.download.model.TrackMetadata
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TidalClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private var apiUrl: String = ""

    init {
        val apis = getAvailableApis()
        if (apis.isNotEmpty()) apiUrl = apis.first()
    }

    suspend fun searchTrack(query: String, limit: Int = 10): List<TrackMetadata> =
        withContext(Dispatchers.IO) {
            val searchUrl = "$TIDAL_PUBLIC_API/search/tracks?query=${query}&limit=$limit&countryCode=$COUNTRY_CODE&locale=$LOCALE"
            val request = Request.Builder()
                .url(searchUrl)
                .header("x-tidal-token", TIDAL_PUBLIC_TOKEN)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val items = jsonResponse["items"]?.jsonArray ?: return@withContext emptyList()

            items.map { parseTrack(it.jsonObject) }
        }

    suspend fun getTrackById(tidalId: String): TrackMetadata? =
        withContext(Dispatchers.IO) {
            val url = "$TIDAL_PUBLIC_API/tracks/$tidalId?countryCode=$COUNTRY_CODE&locale=$LOCALE"
            val request = Request.Builder()
                .url(url)
                .header("x-tidal-token", TIDAL_PUBLIC_TOKEN)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val jsonObj = json.parseToJsonElement(body).jsonObject
            parseTrack(jsonObj)
        }

    suspend fun getStreamUrl(tidalId: String): String? =
        withContext(Dispatchers.IO) {
            if (apiUrl.isBlank()) return@withContext null

            val url = "$apiUrl/track/$tidalId"
            val request = Request.Builder().url(url).get().build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val jsonResponse = json.parseToJsonElement(body).jsonObject

            jsonResponse["url"]?.jsonPrimitive?.contentOrNull
                ?: parseManifestUrl(jsonResponse)
        }

    private fun parseManifestUrl(jsonResponse: JsonObject): String? {
        val data = jsonResponse["data"]?.jsonObject ?: return null
        val manifest = data["manifest"]?.jsonPrimitive?.contentOrNull ?: return null
        val mimeType = data["manifestMimeType"]?.jsonPrimitive?.contentOrNull

        if (mimeType == "application/vnd.tidal.bts") {
            val decoded = String(android.util.Base64.decode(manifest, android.util.Base64.DEFAULT))
            val manifestJson = json.parseToJsonElement(decoded).jsonObject
            val urls = manifestJson["urls"]?.jsonArray
            return urls?.firstOrNull()?.jsonPrimitive?.contentOrNull
        }

        return null
    }

    private fun getAvailableApis(): List<String> {
        val apis = listOf(
            "https://api.zarz.moe/dl/td",
            "https://tidal.401658.xyz"
        )
        return apis
    }

    private fun parseTrack(obj: JsonObject): TrackMetadata {
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
        val isrc = obj["isrc"]?.jsonPrimitive?.contentOrNull ?: ""
        val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 0
        val trackNumber = obj["trackNumber"]?.jsonPrimitive?.intOrNull ?: 0
        val volumeNumber = obj["volumeNumber"]?.jsonPrimitive?.intOrNull ?: 0

        val album = obj["album"]?.jsonObject
        val albumTitle = album?.get("title")?.jsonPrimitive?.contentOrNull ?: ""
        val albumId = album?.get("id")?.jsonPrimitive?.longOrNull ?: 0
        val albumCover = album?.get("cover")?.jsonPrimitive?.contentOrNull ?: ""
        val releaseDate = album?.get("releaseDate")?.jsonPrimitive?.contentOrNull ?: ""

        val artists = obj["artists"]?.jsonArray
        val artistNames = artists?.mapNotNull {
            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        }?.joinToString(", ") ?: ""

        val mainArtist = obj["artist"]?.jsonObject
        val mainArtistName = mainArtist?.get("name")?.jsonPrimitive?.contentOrNull ?: ""

        val coverUrl = if (albumCover.isNotBlank()) {
            "$TIDAL_RESOURCE_BASE/images/${albumCover.replace("-", "/")}/1280x1280.jpg"
        } else ""

        return TrackMetadata(
            spotifyId = "tidal:$id",
            artists = artistNames.ifBlank { mainArtistName },
            name = title,
            albumName = albumTitle,
            albumArtist = mainArtistName,
            durationMs = duration * 1000,
            images = coverUrl,
            releaseDate = releaseDate,
            trackNumber = trackNumber,
            discNumber = volumeNumber,
            externalUrl = "https://tidal.com/browse/track/$id",
            isrc = isrc,
            albumId = "tidal:$albumId",
            artistId = ""
        )
    }

    companion object {
        private const val TIDAL_PUBLIC_API = "https://api.tidal.com/v1"
        private const val TIDAL_PUBLIC_TOKEN = "txNoH4kkV41MfH25"
        private const val TIDAL_RESOURCE_BASE = "https://resources.tidal.com"
        private const val COUNTRY_CODE = "US"
        private const val LOCALE = "en_US"
    }
}
