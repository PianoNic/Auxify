package org.oxycblt.auxio.sync

import android.content.Context
import org.oxycblt.auxio.spotify.SpotifySession
import timber.log.Timber

class SubscriptionManager(private val context: Context) {

    private val db get() = SyncDatabaseProvider.get(context)

    suspend fun subscribe(spotifyId: String, type: String, name: String, imageUrl: String = "") {
        db.subscriptionDao().insert(
            Subscription(
                spotifyId = spotifyId,
                type = type,
                name = name,
                imageUrl = imageUrl
            )
        )
        // Trigger an immediate sync for this new subscription
        SyncWorker.syncNow(context)
    }

    suspend fun unsubscribe(spotifyId: String) {
        val sub = db.subscriptionDao().getById(spotifyId) ?: return
        db.subscriptionDao().delete(sub)
    }

    suspend fun getSubscriptions(): List<Subscription> {
        return db.subscriptionDao().getAll()
    }

    suspend fun isSubscribed(spotifyId: String): Boolean {
        return db.subscriptionDao().getById(spotifyId) != null
    }

    suspend fun getDownloadProgress(spotifyId: String): Pair<Int, Int> {
        val sub = db.subscriptionDao().getById(spotifyId) ?: return 0 to 0
        val downloaded = db.syncedTrackDao().countDownloaded(spotifyId)
        return downloaded to sub.trackCount
    }
}
