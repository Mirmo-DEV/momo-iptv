package com.momo.iptv.data

import android.net.Uri
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.io.InputStreamReader

object XtreamSource {

    /** Ce que l'on peut tirer de l'adresse saisie (elle peut être une URL M3U complète « get.php?username=… »). */
    data class ServerInput(val base: String, val username: String?, val password: String?)

    fun parseServer(raw: String): ServerInput {
        var s = raw.trim()
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) s = "http://$s"
        val url = s.toHttpUrlOrNull() ?: return ServerInput(s.trimEnd('/'), null, null)
        // On ne garde que http(s)://hôte:port : l'API Xtream est toujours à la racine.
        val port = if (url.port != HttpUrl.defaultPort(url.scheme)) ":${url.port}" else ""
        return ServerInput(
            base = "${url.scheme}://${url.host}$port",
            username = url.queryParameter("username")?.takeIf { it.isNotBlank() },
            password = url.queryParameter("password")?.takeIf { it.isNotBlank() },
        )
    }

    /** Adresses de l'API et des flux pour un compte. */
    private class Server(account: Account) {
        val base = parseServer(account.server).base
        private val api = "$base/player_api.php".toHttpUrlOrNull() ?: throw IOException("adresse du serveur invalide")
        private val username = account.username.trim()
        private val password = account.password.trim()
        val user: String = Uri.encode(username)
        val pass: String = Uri.encode(password)

        fun url(action: String?, vararg params: Pair<String, String>): HttpUrl = api.newBuilder()
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .apply {
                if (action != null) addQueryParameter("action", action)
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }
            .build()

        fun stream(kind: String, id: String, ext: String) = "$base/$kind/$user/$pass/$id.$ext"
    }

    fun fetch(account: Account): Playlist {
        val server = Server(account)
        checkAuth(server.url(null))

        val builder = PlaylistBuilder()
        readArray(server.url("get_live_categories")) { o ->
            val id = o["category_id"] ?: return@readArray
            builder.category(id, o["category_name"]?.takeIf { it.isNotBlank() } ?: "Catégorie $id")
        }
        val ext = if (account.useHls) "m3u8" else "ts"
        readArray(server.url("get_live_streams")) { o ->
            val streamId = o["stream_id"] ?: return@readArray
            builder.channel(
                baseId = streamId,
                name = o["name"]?.trim().orEmpty().ifEmpty { "Chaîne $streamId" },
                categoryId = o["category_id"] ?: "0",
                url = server.stream("live", streamId, ext),
                logo = o["stream_icon"],
                epgId = o["epg_channel_id"],
            )
        }
        return builder.build()
    }

    fun vodCategories(account: Account, kind: VodKind): List<Category> {
        val server = Server(account)
        val out = ArrayList<Category>()
        readArray(server.url(if (kind == VodKind.MOVIE) "get_vod_categories" else "get_series_categories")) { o ->
            val id = o["category_id"] ?: return@readArray
            out += Category(id, o["category_name"]?.takeIf { it.isNotBlank() } ?: "Catégorie $id")
        }
        return out
    }

    /** [categoryId] null = tout le catalogue (pour la recherche). */
    fun vodItems(account: Account, kind: VodKind, categoryId: String?): List<VodItem> {
        val server = Server(account)
        val out = ArrayList<VodItem>()
        val filter = if (categoryId != null) arrayOf("category_id" to categoryId) else emptyArray()
        if (kind == VodKind.MOVIE) {
            readArray(server.url("get_vod_streams", *filter)) { o ->
                val id = o["stream_id"] ?: return@readArray
                out += VodItem(
                    id = id,
                    name = o["name"]?.trim().orEmpty().ifEmpty { "Film $id" },
                    kind = VodKind.MOVIE,
                    categoryId = categoryId ?: o["category_id"].orEmpty(),
                    poster = o["stream_icon"]?.takeIf { it.isNotBlank() },
                    url = server.stream("movie", id, o["container_extension"]?.ifBlank { null } ?: "mp4"),
                    rating = o["rating"]?.takeIf { it.isNotBlank() && it != "0" },
                    year = o["year"]?.takeIf { it.isNotBlank() },
                )
            }
        } else {
            readArray(server.url("get_series", *filter)) { o ->
                val id = o["series_id"] ?: return@readArray
                out += VodItem(
                    id = id,
                    name = o["name"]?.trim().orEmpty().ifEmpty { "Série $id" },
                    kind = VodKind.SERIES,
                    categoryId = categoryId ?: o["category_id"].orEmpty(),
                    poster = o["cover"]?.takeIf { it.isNotBlank() },
                    rating = o["rating"]?.takeIf { it.isNotBlank() && it != "0" },
                    year = (o["releaseDate"] ?: o["release_date"])?.take(4)?.takeIf { it.isNotBlank() },
                    description = o["plot"]?.takeIf { it.isNotBlank() },
                    genre = o["genre"]?.takeIf { it.isNotBlank() },
                    director = o["director"]?.takeIf { it.isNotBlank() },
                    cast = o["cast"]?.takeIf { it.isNotBlank() },
                )
            }
        }
        return out
    }

    /** Fiche complète d'un film (résumé, casting, durée, image de fond). */
    fun movieDetails(account: Account, movie: VodItem): VodItem {
        val server = Server(account)
        val root = getJson(server.url("get_vod_info", "vod_id" to movie.id)) as? JsonObject ?: return movie
        val info = root["info"] as? JsonObject ?: return movie
        val backdrop = when (val b = info["backdrop_path"]) {
            is JsonArray -> (b.firstOrNull() as? JsonPrimitive)?.content
            is JsonPrimitive -> b.content
            else -> null
        }?.takeIf { it.startsWith("http") }
        val seconds = info.text("duration_secs")?.toLongOrNull()
        return movie.copy(
            description = info.text("plot")?.takeIf { it.isNotBlank() } ?: info.text("description")?.takeIf { it.isNotBlank() },
            genre = info.text("genre")?.takeIf { it.isNotBlank() },
            director = info.text("director")?.takeIf { it.isNotBlank() },
            cast = (info.text("cast") ?: info.text("actors"))?.takeIf { it.isNotBlank() },
            duration = seconds?.takeIf { it > 0 }?.let { readableDuration(it / 60) },
            backdrop = backdrop,
            poster = movie.poster ?: info.text("movie_image")?.takeIf { it.startsWith("http") },
            year = movie.year ?: info.text("releasedate")?.take(4)?.takeIf { it.isNotBlank() },
            rating = movie.rating ?: info.text("rating")?.takeIf { it.isNotBlank() && it != "0" },
        )
    }

    fun seasons(account: Account, series: VodItem): List<Season> {
        val server = Server(account)
        val root = getJson(server.url("get_series_info", "series_id" to series.id)) as? JsonObject
            ?: throw IOException("fiche de la série illisible")
        val names = HashMap<Int, String>()
        (root["seasons"] as? JsonArray)?.forEach { s ->
            val o = s as? JsonObject ?: return@forEach
            val n = o.text("season_number")?.toIntOrNull() ?: return@forEach
            o.text("name")?.takeIf { it.isNotBlank() }?.let { names[n] = it }
        }
        // « episodes » : { "1": [ … ], "2": [ … ] } ou [ [ … ], [ … ] ]
        val groups: List<JsonElement> = when (val e = root["episodes"]) {
            is JsonObject -> e.values.toList()
            is JsonArray -> e.toList()
            else -> emptyList()
        }
        val episodes = groups.flatMap { g -> (g as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList() }
            .mapNotNull { o ->
                val id = o.text("id") ?: return@mapNotNull null
                val season = o.text("season")?.toIntOrNull() ?: 1
                val number = o.text("episode_num")?.toIntOrNull() ?: 0
                Episode(
                    id = id,
                    season = season,
                    number = number,
                    title = o.text("title")?.takeIf { it.isNotBlank() } ?: "Épisode $number",
                    url = server.stream("series", id, o.text("container_extension")?.ifBlank { null } ?: "mp4"),
                )
            }
        return episodes.groupBy { it.season }.toSortedMap().map { (n, list) ->
            Season(n, names[n] ?: "Saison $n", list.sortedBy { it.number })
        }
    }

    fun shortEpg(account: Account, channel: Channel): List<Programme> {
        val server = Server(account)
        val streamId = channel.id.substringBefore('#')
        val root = getJson(server.url("get_short_epg", "stream_id" to streamId, "limit" to "4")) as? JsonObject ?: return emptyList()
        return (root["epg_listings"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val start = o.text("start_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            val end = o.text("stop_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            Programme(
                title = decode(o.text("title")).ifBlank { "Programme" },
                start = start * 1000,
                end = end * 1000,
                description = decode(o.text("description")).ifBlank { null },
            )
        }
    }

    /** Les titres du guide Xtream sont encodés en base64. */
    private fun decode(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching { String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(value).trim()
    }

    private fun checkAuth(url: HttpUrl) {
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.code == 404) {
                throw IOException(
                    "ce serveur ne répond pas comme un serveur Xtream (HTTP 404). Vérifiez l'adresse, " +
                        "ou choisissez « Portail Stalker » si votre abonnement fonctionne avec une adresse MAC",
                )
            }
            if (!resp.isSuccessful) throw IOException("le serveur a répondu HTTP ${resp.code}")
            val root = runCatching { Json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject }.getOrNull()
                ?: throw IOException("réponse inattendue : ce n'est pas un serveur Xtream, vérifiez l'adresse")
            val info = root["user_info"] as? JsonObject ?: throw IOException("identifiants refusés")
            if ((info["auth"] as? JsonPrimitive)?.content != "1") throw IOException("identifiants refusés")
            val status = (info["status"] as? JsonPrimitive)?.content
            if (status != null && status != "null" && !status.equals("Active", ignoreCase = true)) {
                throw IOException("abonnement $status")
            }
        }
    }

    private fun getJson(url: HttpUrl): JsonElement? {
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("le serveur a répondu HTTP ${resp.code}")
            return runCatching { Json.parseToJsonElement(resp.body?.string().orEmpty()) }.getOrNull()
        }
    }

    /**
     * Lecture en flux d'un tableau JSON d'objets : peu de mémoire même avec
     * des dizaines de milliers d'éléments.
     */
    private fun readArray(url: HttpUrl, onItem: (Map<String, String?>) -> Unit) {
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("le serveur a répondu HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("réponse vide")
            JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                if (reader.peek() != JsonToken.BEGIN_ARRAY) return
                reader.beginArray()
                while (reader.hasNext()) onItem(readFlatObject(reader))
                reader.endArray()
            }
        }
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
}
