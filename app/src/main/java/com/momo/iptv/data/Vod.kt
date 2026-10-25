package com.momo.iptv.data

import kotlinx.serialization.Serializable

@Serializable
enum class VodKind { MOVIE, SERIES }

/** Un film ou une série dans le catalogue. */
@Serializable
data class VodItem(
    val id: String,
    val name: String,
    val kind: VodKind,
    val categoryId: String,
    val poster: String? = null,
    /** Film : lien (Xtream) ou commande (Stalker). Série Stalker : commande commune aux épisodes. */
    val url: String = "",
    val year: String? = null,
    val rating: String? = null,
    /** Stalker : liste d'épisodes intégrée (anciens portails). */
    val episodeNumbers: List<Int> = emptyList(),
    /** Stalker : rubrique d'origine (« vod » ou « series »). */
    val section: String = "",
)

data class Season(val number: Int, val name: String, val episodes: List<Episode>)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val url: String,
    /** Stalker : numéro d'épisode à transmettre au portail pour obtenir le lien. */
    val stalkerSeries: Int? = null,
)

/** Ce que le lecteur de films/séries sait lire. [key] sert à mémoriser la position de reprise. */
data class Playable(
    val key: String,
    val title: String,
    val subtitle: String?,
    val url: String,
    val stalkerSeries: Int? = null,
)

/** Une émission du guide TV (heures en millisecondes). */
data class Programme(val title: String, val start: Long, val end: Long, val description: String? = null)

@Serializable
data class CachedList<T>(val updatedAt: Long, val items: List<T>)
