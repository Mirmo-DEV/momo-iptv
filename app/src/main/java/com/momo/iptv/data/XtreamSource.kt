package com.momo.iptv.data

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.serialization.json.Json
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

    fun fetch(account: Account): Playlist {
        val base = parseServer(account.server).base
        val api = "$base/player_api.php".toHttpUrlOrNull()
            ?: throw IOException("adresse du serveur invalide")

        fun apiUrl(action: String?): HttpUrl = api.newBuilder()
            .addQueryParameter("username", account.username.trim())
            .addQueryParameter("password", account.password.trim())
            .apply { if (action != null) addQueryParameter("action", action) }
            .build()

        checkAuth(apiUrl(null))

        val builder = PlaylistBuilder()
        readArray(apiUrl("get_live_categories")) { o ->
            val id = o["category_id"] ?: return@readArray
            builder.category(id, o["category_name"]?.takeIf { it.isNotBlank() } ?: "Catégorie $id")
        }

        val user = Uri.encode(account.username.trim())
        val pass = Uri.encode(account.password.trim())
        val ext = if (account.useHls) "m3u8" else "ts"
        readArray(apiUrl("get_live_streams")) { o ->
            val streamId = o["stream_id"] ?: return@readArray
            builder.channel(
                baseId = streamId,
                name = o["name"]?.trim().orEmpty().ifEmpty { "Chaîne $streamId" },
                categoryId = o["category_id"] ?: "0",
                url = "$base/live/$user/$pass/$streamId.$ext",
                logo = o["stream_icon"],
                epgId = o["epg_channel_id"],
            )
        }
        return builder.build()
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

    /**
     * Lecture en flux d'un tableau JSON d'objets : peu de mémoire même avec
     * des dizaines de milliers de chaînes.
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
}
