package org.oxycblt.auxio.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.oxycblt.auxio.download.model.TrackAvailability
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongLinkClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    var region: String = "US"
        set(value) {
            field = normalizeRegion(value)
        }

    suspend fun checkTrackAvailability(spotifyTrackId: String): TrackAvailability =
        withContext(Dispatchers.IO) {
            val spotifyUrl = "https://open.spotify.com/track/$spotifyTrackId"
            val links = resolveTrackPlatforms(spotifyUrl)
            buildAvailability(spotifyTrackId, links)
        }

    suspend fun checkAvailabilityByIsrc(isrc: String): TrackAvailability =
        withContext(Dispatchers.IO) {
            val links = songLinkByPlatform("spotify", "song", isrc)
            buildAvailability("", links)
        }

    private fun resolveTrackPlatforms(inputUrl: String): Map<String, String> {
        if (isSpotifyUrl(inputUrl)) {
            try {
                val links = doResolveRequest(inputUrl)
                if (links.isNotEmpty()) return links
            } catch (_: Exception) { }
        }
        return songLinkByTargetUrl(inputUrl)
    }

    private fun doResolveRequest(spotifyUrl: String): Map<String, String> {
        val payload = buildJsonObject {
            put("url", spotifyUrl)
        }.toString()

        val request = Request.Builder()
            .url(RESOLVE_API_URL)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Resolve API returned ${response.code}")

        val body = response.body?.string() ?: throw Exception("Empty response")
        val jsonResponse = json.parseToJsonElement(body).jsonObject

        val success = jsonResponse["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!success) throw Exception("Resolve API returned success=false")

        val songUrls = jsonResponse["songUrls"]?.jsonObject ?: return emptyMap()

        val keyMap = mapOf(
            "Spotify" to "spotify",
            "Deezer" to "deezer",
            "Tidal" to "tidal",
            "YouTubeMusic" to "youtubeMusic",
            "YouTube" to "youtube",
            "AmazonMusic" to "amazonMusic",
            "Qobuz" to "qobuz"
        )

        val links = mutableMapOf<String, String>()
        for ((resolveKey, platformKey) in keyMap) {
            val rawValue = songUrls[resolveKey] ?: continue
            val url = extractResolveUrlValue(rawValue)
            if (url.isNotBlank()) {
                links[platformKey] = url
            }
        }
        return links
    }

    private fun extractResolveUrlValue(raw: JsonElement): String {
        if (raw is JsonNull) return ""
        if (raw is JsonPrimitive && raw.isString) return raw.content.trim()
        if (raw is JsonArray) {
            for (element in raw) {
                if (element is JsonPrimitive && element.isString) {
                    val cleaned = element.content.trim()
                    if (cleaned.isNotEmpty()) return cleaned
                }
            }
        }
        return ""
    }

    private fun songLinkByTargetUrl(targetUrl: String): Map<String, String> {
        val encodedUrl = URLEncoder.encode(targetUrl, "UTF-8")
        val apiUrl = "$SONGLINK_BASE_URL?url=$encodedUrl&userCountry=$region"
        return doSongLinkRequest(apiUrl)
    }

    private fun songLinkByPlatform(platform: String, entityType: String, entityId: String): Map<String, String> {
        val apiUrl = "$SONGLINK_BASE_URL?platform=$platform&type=$entityType&id=$entityId&userCountry=$region"
        return doSongLinkRequest(apiUrl)
    }

    private fun doSongLinkRequest(apiUrl: String): Map<String, String> {
        val request = Request.Builder().url(apiUrl).get().build()
        val response = client.newCall(request).execute()

        if (response.code == 429) throw Exception("SongLink rate limit exceeded")
        if (!response.isSuccessful) throw Exception("SongLink returned ${response.code}")

        val body = response.body?.string() ?: throw Exception("Empty response")
        val jsonResponse = json.parseToJsonElement(body).jsonObject

        val linksByPlatform = jsonResponse["linksByPlatform"]?.jsonObject ?: return emptyMap()

        val links = mutableMapOf<String, String>()
        for ((key, value) in linksByPlatform) {
            val url = value.jsonObject["url"]?.jsonPrimitive?.contentOrNull
            if (!url.isNullOrBlank()) {
                links[key] = url
            }
        }
        return links
    }

    private fun buildAvailability(spotifyId: String, links: Map<String, String>): TrackAvailability {
        return TrackAvailability(
            spotifyId = spotifyId.ifBlank { extractSpotifyIdFromUrl(links["spotify"] ?: "") },
            tidal = links.containsKey("tidal"),
            tidalUrl = links["tidal"] ?: "",
            tidalId = extractTidalIdFromUrl(links["tidal"] ?: ""),
            qobuz = links.containsKey("qobuz"),
            qobuzUrl = links["qobuz"] ?: "",
            qobuzId = extractQobuzIdFromUrl(links["qobuz"] ?: ""),
            deezer = links.containsKey("deezer"),
            deezerUrl = links["deezer"] ?: "",
            deezerId = extractDeezerIdFromUrl(links["deezer"] ?: ""),
            amazon = links.containsKey("amazonMusic"),
            amazonUrl = links["amazonMusic"] ?: "",
            youtube = links.containsKey("youtubeMusic") || links.containsKey("youtube"),
            youtubeUrl = links["youtubeMusic"] ?: links["youtube"] ?: "",
            youtubeId = extractYoutubeIdFromUrl(links["youtubeMusic"] ?: links["youtube"] ?: "")
        )
    }

    companion object {
        private const val RESOLVE_API_URL = "https://api.zarz.moe/v1/resolve"
        private const val SONGLINK_BASE_URL = "https://api.song.link/v1-alpha.1/links"

        fun normalizeRegion(region: String): String {
            val normalized = region.trim().uppercase()
            if (normalized.length != 2) return "US"
            if (normalized.any { it !in 'A'..'Z' }) return "US"
            return normalized
        }

        fun isSpotifyUrl(url: String): Boolean {
            val lower = url.lowercase()
            return "spotify.com/" in lower || "spotify:" in lower
        }

        fun extractSpotifyIdFromUrl(url: String): String {
            val parts = url.split("/track/")
            if (parts.size > 1) {
                return parts[1].substringBefore("?")
            }
            return ""
        }

        fun extractTidalIdFromUrl(url: String): String {
            if (url.isBlank()) return ""
            if ("/track/" in url) {
                val id = url.substringAfter("/track/").substringBefore("?").substringBefore("/").trim()
                if (id.isNotEmpty() && id.all { it.isDigit() }) return id
            }
            return ""
        }

        fun extractQobuzIdFromUrl(url: String): String {
            if (url.isBlank()) return ""
            if ("/track/" in url) {
                val id = url.substringAfter("/track/").substringBefore("?").substringBefore("/").trim()
                if (id.isNotEmpty() && id.all { it.isDigit() }) return id
            }
            if ("trackId=" in url) {
                val id = url.substringAfter("trackId=").substringBefore("&").trim()
                if (id.isNotEmpty() && id.all { it.isDigit() }) return id
            }
            return ""
        }

        fun extractDeezerIdFromUrl(url: String): String {
            if (url.isBlank()) return ""
            val parts = url.split("/")
            val lastPart = parts.last().substringBefore("?")
            return lastPart
        }

        fun extractYoutubeIdFromUrl(url: String): String {
            if (url.isBlank()) return ""
            if ("youtu.be/" in url) {
                return url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            }
            if ("v=" in url) {
                return url.substringAfter("v=").substringBefore("&")
            }
            return ""
        }
    }
}
