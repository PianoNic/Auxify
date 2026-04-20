package org.oxycblt.auxio.download.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackAvailability(
    val spotifyId: String = "",
    val tidal: Boolean = false,
    val amazon: Boolean = false,
    val qobuz: Boolean = false,
    val deezer: Boolean = false,
    val youtube: Boolean = false,
    val tidalUrl: String = "",
    val amazonUrl: String = "",
    val qobuzUrl: String = "",
    val deezerUrl: String = "",
    val youtubeUrl: String = "",
    val deezerId: String = "",
    val qobuzId: String = "",
    val tidalId: String = "",
    val youtubeId: String = ""
)
