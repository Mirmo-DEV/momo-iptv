package com.momo.iptv.data

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
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors

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
    private val root: HttpUrl = portal.newBuilder().encodedPath("/").query(null).build()
    private val cookie = "mac=${URLEncoder.encode(mac, "UTF-8")}; stb_lang=en; timezone=${URLEncoder.encode("Europe/Paris", "UTF-8")}"
    private var endpoint: HttpUrl? = null
    private var token: String? = null

    val streamHeaders: Map<String, String> get() = mapOf("Cookie" to cookie)

    /**
     * Essaie chaque emplacement possible de l'API jusqu'à obtenir des chaînes.
     * En cas d'échec, le message contient un détail technique par emplacement essayé.
     */
    @Synchronized
    fun fetchPlaylist(): Playlist {
        val report = mutableListOf<String>()
        for (candidate in endpointCandidates()) {
            val label = candidate.encodedPath
            try {
                if (!openSession(candidate)) {
                    report += "$label : pas de jeton"
                    continue
                }
                val profile = runCatching { getJs(profileUrl()) as? JsonObject }.getOrNull()
                val playlist = loadChannels(report, label)
                if (playlist.channels.isNotEmpty()) return playlist
                report += "$label : ${describeProfile(profile)}"
            } catch (e: Exception) {
                report += "$label : ${e.message ?: e.javaClass.simpleName}"
            }
        }
        throw IOException(
            "aucune chaîne reçue. Vérifiez que cette adresse MAC est bien activée chez votre fournisseur pour ce portail.\n" +
                "Détail : " + report.joinToString("  ·  "),
        )
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

    private fun loadChannels(report: MutableList<String>, label: String): Playlist {
        val builder = PlaylistBuilder()
        (getJs(api("itv", "get_genres")) as? JsonArray)?.forEach { element ->
            val genre = element as? JsonObject ?: return@forEach
            val id = genre.text("id") ?: return@forEach
            if (id == "*") return@forEach
            builder.category(id, genre.text("title")?.ifBlank { null } ?: "Catégorie $id")
        }

        var count = 0
        val preview = readChannels(api("itv", "get_all_channels")) { o ->
            if (addChannel(builder, o::get)) count++
        }
        if (count == 0) {
            report += "$label : get_all_channels vide ($preview)"
            // Certains portails n'acceptent que la liste paginée.
            count = readOrderedList(builder)
            if (count == 0) report += "$label : get_ordered_list vide"
        }
        return builder.build()
    }

    private fun addChannel(builder: PlaylistBuilder, field: (String) -> String?): Boolean {
        val id = field("id") ?: return false
        val cmd = field("cmd")?.takeIf { it.isNotBlank() } ?: return false
        val logo = field("logo")?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else root.resolve(it)?.toString() }
        builder.channel(
            baseId = id,
            name = field("name")?.trim().orEmpty().ifEmpty { "Chaîne ${field("number") ?: id}" },
            categoryId = field("tv_genre_id") ?: "0",
            url = cmd,
            logo = logo,
            epgId = field("xmltv_id"),
        )
        return true
    }

    private fun readOrderedList(builder: PlaylistBuilder): Int {
        var count = 0
        fetchPages("itv", "genre" to "*", "force_ch_link_check" to "", "fav" to "0", "sortby" to "number", "hd" to "0")
            .forEach { o -> if (addChannel(builder) { key -> o.text(key) }) count++ }
        return count
    }

    /**
     * Liste paginée (≈ 14 éléments par page) : on lit la première page,
     * puis les suivantes en parallèle.
     */
    private fun fetchPages(type: String, vararg params: Pair<String, String>, maxPages: Int = MAX_PAGES): List<JsonObject> {
        fun page(p: Int): JsonObject? =
            getJs(api(type, "get_ordered_list", *params, "p" to p.toString())) as? JsonObject

        val first = page(1) ?: return emptyList()
        val firstData = first["data"] as? JsonArray ?: return emptyList()
        if (firstData.isEmpty()) return emptyList()
        val total = first.text("total_items")?.toIntOrNull() ?: firstData.size
        val perPage = first.text("max_page_items")?.toIntOrNull()?.takeIf { it > 0 } ?: firstData.size
        val pages = ((total + perPage - 1) / perPage).coerceIn(1, maxPages)

        val all = ArrayList<JsonArray>(pages)
        all += firstData
        if (pages > 1) {
            val pool = Executors.newFixedThreadPool(8)
            try {
                val futures = (2..pages).map { p -> pool.submit(Callable { page(p)?.get("data") as? JsonArray }) }
                futures.forEach { f -> runCatching { f.get() }.getOrNull()?.let(all::add) }
            } finally {
                pool.shutdown()
            }
        }
        return all.flatMap { data -> data.mapNotNull { it as? JsonObject } }
    }

    // ---- Films et séries ----

    private fun sectionOf(kind: VodKind) = if (kind == VodKind.MOVIE) "vod" else "series"

    @Synchronized
    fun vodCategories(kind: VodKind): List<Category> {
        if (token == null) handshake()
        val js = runCatching { getJs(api(sectionOf(kind), "get_categories")) }.getOrNull()
        return (js as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.text("id")?.takeIf { it != "*" } ?: return@mapNotNull null
            Category(id, o.text("title")?.ifBlank { null } ?: "Catégorie $id")
        }
    }

    @Synchronized
    fun vodItems(kind: VodKind, categoryId: String): List<VodItem> {
        if (token == null) handshake()
        val section = sectionOf(kind)
        return fetchPages(section, "category" to categoryId, "genre" to categoryId, "sortby" to "added", "fav" to "0", "hd" to "0")
            .mapNotNull { o -> toVodItem(o, kind, categoryId, section) }
    }

    /** Recherche côté portail (quelques pages de résultats suffisent). */
    @Synchronized
    fun search(kind: VodKind, query: String): List<VodItem> {
        if (token == null) handshake()
        val section = sectionOf(kind)
        return runCatching {
            fetchPages(section, "category" to "*", "genre" to "*", "search" to query, "sortby" to "added", "fav" to "0", "hd" to "0", maxPages = 4)
        }.getOrDefault(emptyList()).mapNotNull { o -> toVodItem(o, kind, o.text("category_id").orEmpty(), section) }
    }

    private fun toVodItem(o: JsonObject, kind: VodKind, categoryId: String, section: String): VodItem? {
        run {
                val id = o.text("id") ?: return null
                val episodes = (o["series"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                val isSeries = kind == VodKind.SERIES || episodes.isNotEmpty() || o.text("is_series") == "1"
                val item = VodItem(
                    id = id,
                    name = o.text("name")?.trim()?.ifEmpty { null } ?: "Titre $id",
                    kind = if (isSeries) VodKind.SERIES else VodKind.MOVIE,
                    categoryId = categoryId,
                    poster = (o.text("screenshot_uri") ?: o.text("cover_big") ?: o.text("pic"))
                        ?.takeIf { it.isNotBlank() }
                        ?.let { if (it.startsWith("http")) it else root.resolve(it)?.toString() },
                    url = o.text("cmd").orEmpty(),
                    year = o.text("year")?.takeIf { it.isNotBlank() && it != "0" }?.take(4),
                    rating = (o.text("rating_imdb") ?: o.text("rating_kinopoisk"))?.takeIf { it.isNotBlank() && it != "0" && it != "N/A" },
                    episodeNumbers = episodes,
                    section = section,
                    description = o.text("description")?.takeIf { it.isNotBlank() },
                    genre = o.text("genres_str")?.takeIf { it.isNotBlank() },
                    director = o.text("director")?.takeIf { it.isNotBlank() },
                    cast = o.text("actors")?.takeIf { it.isNotBlank() },
                    duration = o.text("time")?.toLongOrNull()?.takeIf { it > 0 }?.let { readableDuration(it) },
                )
                return item
            }
    }

    @Synchronized
    fun seasons(series: VodItem): List<Season> {
        if (token == null) handshake()
        // Anciens portails : les épisodes sont listés directement dans la fiche.
        if (series.episodeNumbers.isNotEmpty()) {
            return listOf(
                Season(1, "Saison 1", series.episodeNumbers.sorted().map { n ->
                    Episode("${series.id}-$n", 1, n, "Épisode $n", series.url, stalkerSeries = n)
                }),
            )
        }
        // Portails récents : saisons puis épisodes.
        val seasons = fetchPages("series", "movie_id" to series.id, "season_id" to "0", "episode_id" to "0")
        return seasons.mapIndexedNotNull { index, s ->
            val seasonId = s.text("id") ?: return@mapIndexedNotNull null
            val number = s.text("season_number")?.toIntOrNull() ?: (index + 1)
            // On demande d'abord les épisodes de la saison : chacun a sa propre commande de lecture.
            // (La commande de la saison désigne parfois toute la série : le portail lançait alors la dernière saison.)
            val fetched = runCatching {
                fetchPages("series", "movie_id" to series.id, "season_id" to seasonId, "episode_id" to "0").mapNotNull { e ->
                    val epId = e.text("id") ?: return@mapNotNull null
                    val cmd = e.text("cmd")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val n = e.text("series_number")?.toIntOrNull() ?: e.text("episode_number")?.toIntOrNull() ?: 0
                    Episode(
                        id = epId,
                        season = number,
                        number = n,
                        title = e.text("name")?.takeIf { it.isNotBlank() } ?: "Épisode $n",
                        url = cmd,
                    )
                }.sortedBy { it.number }
            }.getOrDefault(emptyList())
            val inline = (s["series"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
            val seasonCmd = s.text("cmd").orEmpty()
            val episodes = fetched.ifEmpty {
                if (inline.isNotEmpty() && seasonCmd.isNotBlank()) {
                    inline.sorted().map { n -> Episode("$seasonId-$n", number, n, "Épisode $n", seasonCmd, stalkerSeries = n) }
                } else {
                    emptyList()
                }
            }
            if (episodes.isEmpty()) null else Season(number, s.text("name")?.takeIf { it.isNotBlank() } ?: "Saison $number", episodes)
        }.sortedBy { it.number }
    }

    @Synchronized
    fun resolveVod(cmd: String, series: Int?): StreamRequest {
        if (token == null) handshake()
        fun link(): String? = runCatching {
            val js = getJs(
                api(
                    "vod", "create_link",
                    "cmd" to cmd, "series" to (series?.toString() ?: ""), "forced_storage" to "", "disable_ad" to "0", "download" to "0",
                ),
            ) as? JsonObject
            js?.text("cmd")?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val result = link() ?: run {
            handshake()
            link()
        }
        val url = extractUrl(result ?: cmd) ?: throw IOException("lien de lecture introuvable")
        return StreamRequest(url, USER_AGENT, streamHeaders)
    }

    /** Programme en cours et suivants d'une chaîne. */
    @Synchronized
    fun shortEpg(channel: Channel): List<Programme> {
        if (token == null) handshake()
        val js = runCatching { getJs(api("itv", "get_short_epg", "ch_id" to channel.id.substringBefore('#'), "size" to "4")) }.getOrNull()
        val list = when (js) {
            is JsonArray -> js
            is JsonObject -> js["data"] as? JsonArray
            else -> null
        }
        return list.orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val start = o.text("start_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            val end = o.text("stop_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            Programme(
                title = o.text("name")?.takeIf { it.isNotBlank() } ?: "Programme",
                start = start * 1000,
                end = end * 1000,
                description = o.text("descr")?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun describeProfile(profile: JsonObject?): String {
        if (profile == null) return "profil illisible"
        val parts = mutableListOf("profil status=${profile.text("status") ?: "?"}")
        listOf("blocked", "block_msg", "msg", "error").forEach { key ->
            profile.text(key)?.takeIf { it.isNotBlank() && it != "0" }?.let { parts += "$key=$it" }
        }
        return parts.joinToString(" ")
    }

    private fun createLink(cmd: String): String? = runCatching {
        val js = getJs(
            api(
                "itv", "create_link",
                "cmd" to cmd, "series" to "", "forced_storage" to "undefined", "disable_ad" to "0", "download" to "0",
            ),
        ) as? JsonObject
        js?.text("cmd")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Rouvre une session sur l'emplacement déjà trouvé, sinon les essaie tous. */
    private fun handshake() {
        endpoint?.let { if (openSession(it)) return }
        for (candidate in endpointCandidates()) {
            if (runCatching { openSession(candidate) }.getOrDefault(false)) return
        }
        throw IOException("portail injoignable ou adresse incorrecte")
    }

    private fun openSession(candidate: HttpUrl): Boolean {
        token = null
        val js = getJs(api("stb", "handshake", "token" to "", "prehash" to "0", endpointOverride = candidate)) as? JsonObject
        val newToken = js?.text("token")
        if (newToken.isNullOrBlank()) return false
        endpoint = candidate
        token = newToken
        return true
    }

    /** L'adresse fournie est souvent « http://hôte:port/c/ » ; l'API peut être à plusieurs endroits. */
    private fun endpointCandidates(): List<HttpUrl> {
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
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val root = runCatching { Json.parseToJsonElement(resp.body?.string().orEmpty()) }.getOrNull()
            return (root as? JsonObject)?.get("js")
        }
    }

    /**
     * Grosse liste de chaînes, lue en flux : { "js": { "data": [ … ] } } ou { "js": [ … ] }.
     * Renvoie le début de la réponse (pour le diagnostic).
     */
    private fun readChannels(url: HttpUrl, onItem: (Map<String, String?>) -> Unit): String {
        Http.client.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) return "HTTP ${resp.code}"
            val preview = resp.peekBody(120).string().replace(Regex("\\s+"), " ").trim()
            val body = resp.body ?: return "réponse vide"
            runCatching {
                JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                    reader.isLenient = true
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) return preview
                    reader.beginObject()
                    if (!reader.seekKey("js")) return preview
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        if (!reader.seekKey("data")) return preview
                    }
                    if (reader.peek() != JsonToken.BEGIN_ARRAY) return preview
                    reader.beginArray()
                    while (reader.hasNext()) onItem(readFlatObject(reader))
                }
            }
            return preview.ifEmpty { "réponse vide" }
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

        private const val MAX_PAGES = 4_000
        private val macRegex = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

        fun normalizeMac(raw: String): String = raw.trim().uppercase().replace('-', ':')

        fun isValidMac(raw: String): Boolean = macRegex.matches(normalizeMac(raw))

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

        private fun extractUrl(cmd: String): String? =
            cmd.trim().split(Regex("\\s+")).lastOrNull { it.contains("://") }

        private fun md5(s: String) = hash("MD5", s)
        private fun sha256(s: String) = hash("SHA-256", s)
        private fun hash(algorithm: String, s: String): String =
            MessageDigest.getInstance(algorithm).digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
