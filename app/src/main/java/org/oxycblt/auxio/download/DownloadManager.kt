package org.oxycblt.auxio.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.oxycblt.auxio.download.model.AudioQuality
import org.oxycblt.auxio.download.model.DownloadResult
import org.oxycblt.auxio.download.model.TrackAvailability
import org.oxycblt.auxio.download.model.TrackMetadata
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    private val songLinkClient: SongLinkClient,
    private val tidalClient: TidalClient,
    private val qobuzClient: QobuzClient,
    private val deezerClient: DeezerClient
) {

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadTrack(
        context: Context,
        spotifyTrackId: String,
        metadata: TrackMetadata,
        quality: AudioQuality = AudioQuality.LOSSLESS,
        outputDir: File
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val availability = songLinkClient.checkTrackAvailability(spotifyTrackId)
            val streamUrl = resolveStreamUrl(availability, quality)

            if (streamUrl == null) {
                return@withContext DownloadResult(success = false, error = "No stream source found")
            }

            val fileName = sanitizeFileName("${metadata.artists} - ${metadata.name}.flac")
            val outputFile = File(outputDir, fileName)

            if (outputFile.exists()) {
                return@withContext DownloadResult(success = true, filePath = outputFile.absolutePath)
            }

            downloadFile(streamUrl, outputFile)

            DownloadResult(success = true, filePath = outputFile.absolutePath)
        } catch (e: Exception) {
            Timber.e(e, "Download failed for $spotifyTrackId")
            DownloadResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    private suspend fun resolveStreamUrl(
        availability: TrackAvailability,
        quality: AudioQuality
    ): String? {
        // Priority: Qobuz (best lossless) → Tidal → Deezer
        if (quality == AudioQuality.LOSSLESS) {
            if (availability.qobuz && availability.qobuzId.isNotBlank()) {
                val url = qobuzClient.getStreamUrl(availability.qobuzId)
                if (url != null) return url
            }
            if (availability.tidal && availability.tidalId.isNotBlank()) {
                val url = tidalClient.getStreamUrl(availability.tidalId)
                if (url != null) return url
            }
        }

        // Tidal fallback
        if (availability.tidal && availability.tidalId.isNotBlank()) {
            val url = tidalClient.getStreamUrl(availability.tidalId)
            if (url != null) return url
        }

        return null
    }

    private fun downloadFile(url: String, outputFile: File) {
        val request = Request.Builder().url(url).build()
        val response = downloadClient.newCall(request).execute()

        if (!response.isSuccessful) throw Exception("Download HTTP ${response.code}")

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            response.body?.byteStream()?.use { input ->
                input.copyTo(fos, bufferSize = 8192)
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(200)
    }
}
