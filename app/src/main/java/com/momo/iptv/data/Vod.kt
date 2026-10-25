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
    val description: String? = null,
    val genre: String? = null,
    val director: String? = null,
    val cast: String? = null,
    /** Durée lisible (« 1 h 52 »). */
    val duration: String? = null,
    /** Grande image de fond, sinon on utilise l'affiche. */
    val backdrop: String? = null,
)

/** Un film ou un épisode commencé : alimente la rangée « Continuer à regarder ». */
@Serializable
data class HistoryEntry(
    val item: VodItem,
    /** Clé de reprise (film ou épisode). */
    val key: String,
    val episodeId: String? = null,
    val episodeLabel: String? = null,
    val updatedAt: Long,
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
    val episodeId: String? = null,
)

/** Une émission du guide TV (heures en millisecondes). */
data class Programme(val title: String, val start: Long, val end: Long, val description: String? = null)

@Serializable
data class CachedList<T>(val updatedAt: Long, val items: List<T>)

/** 112 → « 1 h 52 ». */
fun readableDuration(minutes: Long): String =
    if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
