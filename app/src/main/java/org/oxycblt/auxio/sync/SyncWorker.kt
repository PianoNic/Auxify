package org.oxycblt.auxio.sync

import android.content.Context
import android.os.Environment
import androidx.work.*
import org.oxycblt.auxio.download.DownloadManager
import org.oxycblt.auxio.download.model.AudioQuality
import org.oxycblt.auxio.download.model.TrackMetadata
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker starting")

        val db = SyncDatabaseProvider.get(applicationContext)
        val subscriptions = db.subscriptionDao().getAll()

        if (subscriptions.isEmpty()) return Result.success()

        val pendingTracks = db.syncedTrackDao().getPending()
        if (pendingTracks.isEmpty()) return Result.success()

        val downloadManager = DownloadManager(
            songLinkClient = org.oxycblt.auxio.download.SongLinkClient(),
            tidalClient = org.oxycblt.auxio.download.TidalClient(),
            qobuzClient = org.oxycblt.auxio.download.QobuzClient(),
            deezerClient = org.oxycblt.auxio.download.DeezerClient()
        )

        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Auxify"
        )
        musicDir.mkdirs()

        for (track in pendingTracks) {
            if (isStopped) break

            db.syncedTrackDao().updateStatus(track.spotifyTrackId, "downloading")

            val metadata = TrackMetadata(
                spotifyId = track.spotifyTrackId,
                name = track.name,
                artists = track.artists,
                albumName = track.albumName
            )

            val result = downloadManager.downloadTrack(
                context = applicationContext,
                spotifyTrackId = track.spotifyTrackId,
                metadata = metadata,
                quality = AudioQuality.LOSSLESS,
                outputDir = musicDir
            )

            if (result.success) {
                db.syncedTrackDao().updateStatus(
                    track.spotifyTrackId, "done",
                    result.filePath, System.currentTimeMillis()
                )
            } else {
                db.syncedTrackDao().updateStatus(track.spotifyTrackId, "failed")
                Timber.w("Failed to download ${track.name}: ${result.error}")
            }
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "auxify_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
