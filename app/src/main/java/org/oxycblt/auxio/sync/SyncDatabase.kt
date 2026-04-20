package org.oxycblt.auxio.sync

import androidx.room.*

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey val spotifyId: String,
    val type: String, // "playlist", "album", "liked_songs"
    val name: String,
    val imageUrl: String = "",
    val lastSyncedAt: Long = 0,
    val trackCount: Int = 0
)

@Entity(tableName = "synced_tracks")
data class SyncedTrack(
    @PrimaryKey val spotifyTrackId: String,
    val subscriptionId: String,
    val name: String,
    val artists: String,
    val albumName: String,
    val localPath: String = "",
    val downloadedAt: Long = 0,
    val status: String = "pending" // "pending", "downloading", "done", "failed"
)

@Entity(tableName = "listen_history")
data class ListenEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackPath: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val listenedAt: Long = System.currentTimeMillis(),
    val durationListenedMs: Long = 0,
    val skipped: Boolean = false
)

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<Subscription>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: Subscription)

    @Delete
    suspend fun delete(subscription: Subscription)

    @Query("SELECT * FROM subscriptions WHERE spotifyId = :id")
    suspend fun getById(id: String): Subscription?
}

@Dao
interface SyncedTrackDao {
    @Query("SELECT * FROM synced_tracks WHERE subscriptionId = :subId")
    suspend fun getBySubscription(subId: String): List<SyncedTrack>

    @Query("SELECT * FROM synced_tracks WHERE status = 'pending'")
    suspend fun getPending(): List<SyncedTrack>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(track: SyncedTrack)

    @Query("UPDATE synced_tracks SET status = :status, localPath = :path, downloadedAt = :time WHERE spotifyTrackId = :trackId")
    suspend fun updateStatus(trackId: String, status: String, path: String = "", time: Long = 0)

    @Query("SELECT COUNT(*) FROM synced_tracks WHERE subscriptionId = :subId AND status = 'done'")
    suspend fun countDownloaded(subId: String): Int
}

@Dao
interface ListenHistoryDao {
    @Insert
    suspend fun insert(entry: ListenEntry)

    @Query("SELECT * FROM listen_history ORDER BY listenedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<ListenEntry>

    @Query("SELECT artistName, COUNT(*) as playCount FROM listen_history WHERE listenedAt > :since GROUP BY artistName ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopArtists(since: Long, limit: Int = 20): List<ArtistPlayCount>

    @Query("SELECT albumName, artistName, COUNT(*) as playCount FROM listen_history WHERE listenedAt > :since GROUP BY albumName, artistName ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopAlbums(since: Long, limit: Int = 20): List<AlbumPlayCount>
}

data class ArtistPlayCount(val artistName: String, val playCount: Int)
data class AlbumPlayCount(val albumName: String, val artistName: String, val playCount: Int)

@Database(
    entities = [Subscription::class, SyncedTrack::class, ListenEntry::class],
    version = 1
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun syncedTrackDao(): SyncedTrackDao
    abstract fun listenHistoryDao(): ListenHistoryDao
}
