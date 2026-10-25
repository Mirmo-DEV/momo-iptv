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

    fun normalizeServer(raw: String): String {
        var s = raw.trim().trimEnd('/')
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) s = "http://$s"
        return s
    }

    fun fetch(account: Account): Playlist {
        val base = normalizeServer(account.server)
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
            if (!resp.isSuccessful) throw IOException("le serveur a répondu HTTP ${resp.code}")
            val root = runCatching { Json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject }.getOrNull()
                ?: throw IOException("réponse inattendue, vérifiez l'adresse du serveur")
            val info = root["user_info"] as? JsonObject ?: throw IOException("identifiants refusés")
            if ((info["auth"] as? JsonPrimitive)?.content != "1") throw IOException("identifiants refusés")
            val status = (info["status"] as? JsonPrimitive)?.content
            if (status != null && status != "null" && !status.equals("Active", ignoreCase = true)) {
                throw IOException("abonnement $status")
            }
        }
    }

    /**
     * Lecture en flux d'un tableau JSON d'objets « plats » : peu de mémoire même avec
     * des dizaines de milliers de chaînes, et tolérant aux nombres envoyés en texte (ou l'inverse).
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

    private fun readFlatObject(reader: JsonReader): Map<String, String?> {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return emptyMap()
        }
        val map = HashMap<String, String?>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> map[key] = reader.nextString()
                JsonToken.BOOLEAN -> map[key] = reader.nextBoolean().toString()
                JsonToken.NULL -> {
                    reader.nextNull()
                    map[key] = null
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return map
    }
}
