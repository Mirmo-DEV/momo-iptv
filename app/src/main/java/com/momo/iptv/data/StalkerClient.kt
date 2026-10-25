package com.momo.iptv.data

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.io.InputStreamReader
import java.security.MessageDigest

/**
 * Portail Stalker / Ministra (abonnements « MAG » liés à une adresse MAC).
 * L'appli se présente comme un boîtier MAG250 ; le lien de chaque chaîne est
 * demandé au portail au moment de la lecture (il contient un jeton temporaire).
 */
class StalkerClient(val account: Account) {

    private val mac = normalizeMac(account.mac)
    private val portal: HttpUrl = run {
        var s = account.server.trim()
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) s = "http://$s"
        s.toHttpUrlOrNull() ?: throw IOException("adresse du portail invalide")
    }
    private val cookie = "mac=$mac; stb_lang=fr; timezone=Europe/Paris"
    private var endpoint: HttpUrl? = null
    private var token: String? = null

    val streamHeaders: Map<String, String> get() = mapOf("Cookie" to cookie)

    @Synchronized
    fun fetchPlaylist(): Playlist {
        handshake()
        val builder = PlaylistBuilder()
        (getJs(api("itv", "get_genres")) as? JsonArray)?.forEach { element ->
            val genre = element as? JsonObject ?: return@forEach
            val id = (genre["id"] as? JsonPrimitive)?.content ?: return@forEach
            if (id == "*") return@forEach
            builder.category(id, (genre["title"] as? JsonPrimitive)?.content?.ifBlank { null } ?: "Catégorie $id")
        }
        val root = portal.newBuilder().encodedPath("/").query(null).build()
        readChannels(api("itv", "get_all_channels")) { o ->
            val id = o["id"] ?: return@readChannels
            val cmd = o["cmd"]?.takeIf { it.isNotBlank() } ?: return@readChannels
            val logo = o["logo"]?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else root.resolve(it)?.toString() }
            builder.channel(
                baseId = id,
                name = o["name"]?.trim().orEmpty().ifEmpty { "Chaîne ${o["number"] ?: id}" },
                categoryId = o["tv_genre_id"] ?: "0",
                url = cmd,
                logo = logo,
                epgId = o["xmltv_id"],
            )
        }
        val playlist = builder.build()
        if (playlist.channels.isEmpty()) {
            throw IOException("aucune chaîne reçue : l'adresse MAC n'est peut-être pas activée sur ce portail")
        }
        return playlist
    }

    /** Transforme la commande d'une chaîne (« ffmpeg http://… ») en lien de lecture valide. */
    @Synchronized
    fun resolve(cmd: String): StreamRequest {
        if (token == null) handshake()
        var link = createLink(cmd)
        if (link == null) {
            // Jeton expiré : nouvelle session puis nouvel essai.
            handshake()
            link = createLink(cmd)
        }
        // Certains portails renvoient un lien sans identifiant de flux : on garde alors la commande d'origine.
        val chosen = link?.takeUnless { "stream=&" in it || it.endsWith("stream=") } ?: cmd
        val url = extractUrl(chosen) ?: throw IOException("lien de lecture introuvable")
        return StreamRequest(url, USER_AGENT, streamHeaders)
    }

    private fun createLink(cmd: String): String? = runCatching {
        val js = getJs(
            api(
                "itv", "create_link",
                "cmd" to cmd, "series" to "", "forced_storage" to "undefined", "disable_ad" to "0", "download" to "0",
            ),
        ) as? JsonObject
        (js?.get("cmd") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun handshake() {
        token = null
        var lastError: Exception? = null
        for (candidate in endpointCandidates()) {
            try {
                val js = getJs(api("stb", "handshake", "token" to "", "prehash" to "0", endpointOverride = candidate)) as? JsonObject
                val newToken = (js?.get("token") as? JsonPrimitive)?.content
                if (!newToken.isNullOrBlank()) {
                    endpoint = candidate
                    token = newToken
                    // Active la session sur la plupart des portails ; sans conséquence si refusé.
                    runCatching { getJs(profileUrl()) }
                    return
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException(
            "portail injoignable ou adresse incorrecte" + (lastError?.message?.let { " ($it)" } ?: ""),
        )
    }

    /** L'adresse fournie est souvent « http://hôte:port/c/ » ; l'API peut être à plusieurs endroits. */
    private fun endpointCandidates(): List<HttpUrl> {
        val root = portal.newBuilder().encodedPath("/").query(null).build()
        val path = portal.encodedPath.let { if (it.endsWith("/")) it else "$it/" }
        val candidates = mutableListOf<HttpUrl?>()
        if (path.trimEnd('/').endsWith(".php")) candidates += portal.newBuilder().query(null).build()
        val prefix = path.substringBefore("/c/", path).trimEnd('/')
        if (prefix.isNotEmpty() && !prefix.endsWith(".php")) candidates += root.resolve("$prefix/server/load.php")
        candidates += root.resolve("/portal.php")
        candidates += root.resolve("/stalker_portal/server/load.php")
        candidates += root.resolve("/server/load.php")
        return candidates.filterNotNull().distinct()
    }

    private fun profileUrl(): HttpUrl {
        val sn = md5(mac).uppercase().take(13)
        val deviceId = sha256(mac).uppercase()
        return api(
            "stb", "get_profile",
            "hd" to "1",
            "ver" to "ImageDescription: 0.2.18-r23-250; PORTAL version: 5.6.2; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c",
            "num_banks" to "2",
            "sn" to sn,
            "stb_type" to "MAG250",
            "client_type" to "STB",
            "image_version" to "218",
            "video_out" to "hdmi",
            "device_id" to deviceId,
            "device_id2" to deviceId,
            "signature" to sha256(sn + mac).uppercase(),
            "auth_second_step" to "1",
            "hw_version" to "1.7-BD-00",
            "not_valid_token" to "0",
            "timestamp" to (System.currentTimeMillis() / 1000).toString(),
            "api_signature" to "262",
            "prehash" to "0",
        )
    }

    private fun api(type: String, action: String, vararg params: Pair<String, String>, endpointOverride: HttpUrl? = null): HttpUrl {
        val base = endpointOverride ?: endpoint ?: throw IOException("session Stalker non ouverte")
        val builder = base.newBuilder().query(null)
            .addQueryParameter("type", type)
            .addQueryParameter("action", action)
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.addQueryParameter("JsHttpRequest", "1-xml").build()
    }

    private fun request(url: HttpUrl): Request = Request.Builder().url(url)
        .header("User-Agent", USER_AGENT)
        .header("X-User-Agent", "Model: MAG250; Link: WiFi")
        .header("Cookie", cookie)
        .header("Referer", portal.toString())
        .apply { token?.let { header("Authorization", "Bearer $it") } }
        .build()

    /** Petite réponse : renvoie le contenu de « js ». */
    private fun getJs(url: HttpUrl): JsonElement? {
        Http.client.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("le portail a répondu HTTP ${resp.code}")
            val root = runCatching { Json.parseToJsonElement(resp.body?.string().orEmpty()) }.getOrNull()
            return (root as? JsonObject)?.get("js")
        }
    }

    /** Grosse liste de chaînes, lue en flux : { "js": { "data": [ … ] } } ou { "js": [ … ] }. */
    private fun readChannels(url: HttpUrl, onItem: (Map<String, String?>) -> Unit) {
        Http.client.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("le portail a répondu HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("réponse vide")
            JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                if (reader.peek() != JsonToken.BEGIN_OBJECT) return
                reader.beginObject()
                if (!reader.seekKey("js")) return
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    reader.beginObject()
                    if (!reader.seekKey("data")) return
                }
                if (reader.peek() != JsonToken.BEGIN_ARRAY) return
                reader.beginArray()
                while (reader.hasNext()) onItem(readFlatObject(reader))
            }
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

        private val macRegex = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

        fun normalizeMac(raw: String): String = raw.trim().uppercase().replace('-', ':')

        fun isValidMac(raw: String): Boolean = macRegex.matches(normalizeMac(raw))

        private fun extractUrl(cmd: String): String? =
            cmd.trim().split(Regex("\\s+")).lastOrNull { it.contains("://") }

        private fun md5(s: String) = hash("MD5", s)
        private fun sha256(s: String) = hash("SHA-256", s)
        private fun hash(algorithm: String, s: String): String =
            MessageDigest.getInstance(algorithm).digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
