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
class QobuzClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getTrackById(qobuzId: String): TrackMetadata? =
        withContext(Dispatchers.IO) {
            val url = "${API_BASE}track/get?track_id=$qobuzId"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            parseTrack(json.parseToJsonElement(body).jsonObject)
        }

    suspend fun getStreamUrl(qobuzId: String): String? =
        withContext(Dispatchers.IO) {
            val downloadApis = listOf(
                "$ZARZ_DOWNLOAD_URL?id=$qobuzId",
                "$SQUID_DOWNLOAD_URL$qobuzId"
            )

            for (apiUrl in downloadApis) {
                try {
                    val request = Request.Builder().url(apiUrl).get().build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) continue

                    val body = response.body?.string() ?: continue
                    val jsonResp = json.parseToJsonElement(body).jsonObject
                    val streamUrl = jsonResp["url"]?.jsonPrimitive?.contentOrNull
                    if (!streamUrl.isNullOrBlank()) return@withContext streamUrl
                } catch (_: Exception) {
                    continue
                }
            }
            null
        }

    private fun parseTrack(obj: JsonObject): TrackMetadata {
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
        val isrc = obj["isrc"]?.jsonPrimitive?.contentOrNull ?: ""
        val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 0
        val trackNumber = obj["track_number"]?.jsonPrimitive?.intOrNull ?: 0
        val discNumber = obj["media_number"]?.jsonPrimitive?.intOrNull ?: 0

        val album = obj["album"]?.jsonObject
        val albumTitle = album?.get("title")?.jsonPrimitive?.contentOrNull ?: ""
        val albumId = album?.get("id")?.jsonPrimitive?.contentOrNull ?: ""
        val releaseDate = album?.get("release_date_original")?.jsonPrimitive?.contentOrNull ?: ""
        val albumImage = album?.get("image")?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull ?: ""

        val albumArtist = album?.get("artist")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
        val performer = obj["performer"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: albumArtist

        return TrackMetadata(
            spotifyId = "qobuz:$id",
            artists = performer,
            name = title,
            albumName = albumTitle,
            albumArtist = albumArtist,
            durationMs = duration * 1000,
            images = albumImage,
            releaseDate = releaseDate,
            trackNumber = trackNumber,
            discNumber = discNumber,
            externalUrl = "https://open.qobuz.com/track/$id",
            isrc = isrc,
            albumId = "qobuz:$albumId",
            artistId = ""
        )
    }

    companion object {
        private const val API_BASE = "https://api.zarz.moe/v1/qbz/"
        private const val ZARZ_DOWNLOAD_URL = "https://api.zarz.moe/dl/qbz"
        private const val SQUID_DOWNLOAD_URL = "https://qobuz.squid.wtf/api/download-music?country=US&track_id="
    }
}
