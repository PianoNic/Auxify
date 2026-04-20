package org.oxycblt.auxio.recommend

import android.content.Context
import org.oxycblt.auxio.sync.SyncDatabaseProvider
import java.util.concurrent.TimeUnit

class RecommendationEngine(private val context: Context) {

    private val db get() = SyncDatabaseProvider.get(context)

    suspend fun getSuggestedForToday(): List<SuggestedTrack> {
        val history = db.listenHistoryDao()
        val oneWeekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)

        val topArtists = history.getTopArtists(oneWeekAgo, 10)
        val topAlbums = history.getTopAlbums(oneWeekAgo, 10)
        val recent = history.getRecent(50)

        // Build a weighted pool: favor frequently played artists/albums
        // but exclude recently played tracks to keep it fresh
        val recentPaths = recent.take(15).map { it.trackPath }.toSet()

        val candidates = recent
            .filter { it.trackPath !in recentPaths || it.durationListenedMs > 60_000 }
            .distinctBy { it.trackPath }

        // Score by artist popularity + album popularity + recency
        val artistWeights = topArtists.associate { it.artistName to it.playCount }
        val albumWeights = topAlbums.associate { "${it.albumName}|${it.artistName}" to it.playCount }

        val scored = candidates.map { entry ->
            val artistScore = artistWeights[entry.artistName] ?: 0
            val albumScore = albumWeights["${entry.albumName}|${entry.artistName}"] ?: 0
            val skipPenalty = if (entry.skipped) -2 else 0
            val score = artistScore + albumScore + skipPenalty

            SuggestedTrack(
                trackPath = entry.trackPath,
                trackName = entry.trackName,
                artistName = entry.artistName,
                albumName = entry.albumName,
                score = score
            )
        }

        return scored
            .sortedByDescending { it.score }
            .take(30)
            .shuffled()
    }

    suspend fun getTopMixes(): List<Mix> {
        val history = db.listenHistoryDao()
        val twoWeeksAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)

        val topArtists = history.getTopArtists(twoWeeksAgo, 5)

        return topArtists.map { artist ->
            Mix(
                name = "${artist.artistName} Mix",
                basedOn = artist.artistName,
                type = MixType.ARTIST
            )
        }
    }

    suspend fun recordListen(
        trackPath: String,
        trackName: String,
        artistName: String,
        albumName: String,
        durationMs: Long,
        skipped: Boolean
    ) {
        db.listenHistoryDao().insert(
            org.oxycblt.auxio.sync.ListenEntry(
                trackPath = trackPath,
                trackName = trackName,
                artistName = artistName,
                albumName = albumName,
                durationListenedMs = durationMs,
                skipped = skipped
            )
        )
    }
}

data class SuggestedTrack(
    val trackPath: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val score: Int
)

data class Mix(
    val name: String,
    val basedOn: String,
    val type: MixType
)

enum class MixType {
    ARTIST, GENRE, DISCOVERY
}
