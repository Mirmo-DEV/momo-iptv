package com.momo.iptv.data

import kotlinx.serialization.Serializable

@Serializable
enum class SourceType { XTREAM, M3U }

/** Un compte IPTV. Saisi dans l'appli et stocké uniquement sur l'appareil. */
@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: SourceType,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    /** Xtream : flux en HLS (.m3u8) au lieu de MPEG-TS (.ts). */
    val useHls: Boolean = false,
)

@Serializable
data class Category(val id: String, val name: String)

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val categoryId: String,
    val url: String,
    val logo: String? = null,
    val epgId: String? = null,
)

@Serializable
data class Playlist(
    val categories: List<Category>,
    val channels: List<Channel>,
    val updatedAt: Long,
)

/** Construit une Playlist en garantissant des identifiants uniques et des catégories cohérentes. */
internal class PlaylistBuilder {
    private val categories = LinkedHashMap<String, Category>()
    private val channels = ArrayList<Channel>()
    private val usedIds = HashSet<String>()

    fun category(id: String, name: String) {
        categories.getOrPut(id) { Category(id, name) }
    }

    fun channel(baseId: String, name: String, categoryId: String, url: String, logo: String?, epgId: String?) {
        var id = baseId
        var n = 2
        while (!usedIds.add(id)) id = "$baseId#${n++}"
        channels += Channel(id, name, categoryId, url, logo?.takeIf { it.isNotBlank() }, epgId?.takeIf { it.isNotBlank() })
    }

    fun build(): Playlist {
        channels.forEach { if (it.categoryId !in categories) category(it.categoryId, "Autres") }
        return Playlist(categories.values.toList(), channels, System.currentTimeMillis())
    }
}
