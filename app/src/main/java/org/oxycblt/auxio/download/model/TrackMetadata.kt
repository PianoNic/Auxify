package org.oxycblt.auxio.download.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackMetadata(
    val spotifyId: String = "",
    val artists: String = "",
    val name: String = "",
    val albumName: String = "",
    val albumArtist: String = "",
    val durationMs: Int = 0,
    val images: String = "",
    val releaseDate: String = "",
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val externalUrl: String = "",
    val isrc: String = "",
    val albumId: String = "",
    val artistId: String = ""
)

@Serializable
data class DownloadResult(
    val success: Boolean,
    val filePath: String = "",
    val error: String = "",
    val bitDepth: Int = 0,
    val sampleRate: Int = 0
)

enum class AudioQuality {
    LOSSLESS,
    HIGH,
    NORMAL
}
