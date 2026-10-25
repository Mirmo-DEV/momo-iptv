package com.momo.iptv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.momo.iptv.data.Account
import com.momo.iptv.data.AccountStore
import com.momo.iptv.data.Category
import com.momo.iptv.data.Channel
import com.momo.iptv.data.FavoritesStore
import com.momo.iptv.data.Playlist
import com.momo.iptv.data.PlaylistRepository
import com.momo.iptv.data.Episode
import com.momo.iptv.data.HistoryEntry
import com.momo.iptv.data.LibraryStore
import com.momo.iptv.data.Playable
import com.momo.iptv.data.Programme
import com.momo.iptv.data.ResumeStore
import com.momo.iptv.data.Season
import com.momo.iptv.data.SourceType
import com.momo.iptv.data.StreamRequest
import com.momo.iptv.data.VodItem
import com.momo.iptv.data.VodKind
import com.momo.iptv.data.normalize
import com.momo.iptv.update.Updater
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface Screen {
    data object Accounts : Screen
    data class EditAccount(val account: Account?) : Screen
    data object Home : Screen
    data class Player(val channels: List<Channel>, val startIndex: Int) : Screen
    data class Series(val series: VodItem) : Screen
    data class MovieDetail(val item: VodItem) : Screen
    data class VodPlayer(
        val items: List<Playable>,
        val startIndex: Int,
        val back: Screen,
        val source: VodItem,
        /** Reprendre sans demander (depuis « Continuer à regarder »). */
        val autoResume: Boolean = false,
    ) : Screen
}

enum class Section { SEARCH, HOME, LIVE, MOVIES, SERIES, WATCHLIST }

sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val release: Updater.Release, val percent: Int) : UpdateState
    data class Ready(val release: Updater.Release, val file: File) : UpdateState
    data class Failed(val release: Updater.Release, val reason: String) : UpdateState
}

/** Liste de chaînes indexée pour un affichage rapide par catégorie. */
class Library(val playlist: Playlist) {
    val byCategory: Map<String, List<Channel>> = playlist.channels.groupBy { it.categoryId }
    val byId: Map<String, Channel> = playlist.channels.associateBy { it.id }
    val categories: List<Category> = playlist.categories.filter { byCategory.containsKey(it.id) }
}

const val CAT_FAVORITES = "__favoris__"
const val CAT_ALL = "__toutes__"

/** Au-delà de cet âge, la liste en cache est rafraîchie en arrière-plan à l'ouverture du compte. */
private const val AUTO_REFRESH_AFTER_MS = 3 * 60 * 60 * 1000L
private const val VOD_REFRESH_AFTER_MS = 12 * 60 * 60 * 1000L
private const val EPG_REFRESH_AFTER_MS = 15 * 60 * 1000L

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val accountStore = AccountStore(app)
    private val favoritesStore = FavoritesStore(app)
    private val repository = PlaylistRepository(app)
    private val resumeStore = ResumeStore(app)
    private val libraryStore = LibraryStore(app)
    private var loadJob: Job? = null

    var accounts by mutableStateOf(accountStore.load())
        private set
    var screen by mutableStateOf<Screen>(Screen.Accounts)
        private set
    var current by mutableStateOf<Account?>(null)
        private set
    var library by mutableStateOf<Library?>(null)
        private set
    var favorites by mutableStateOf<List<String>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var selectedCategory by mutableStateOf(CAT_ALL)

    /** Dernière chaîne regardée : le focus y revient en sortant du lecteur. */
    var lastChannelId by mutableStateOf<String?>(null)
        private set

    // ---- Films et séries ----
    var section by mutableStateOf(Section.HOME)

    // ---- Recherche ----
    var searchQuery by mutableStateOf("")
        private set
    var searchChannels by mutableStateOf<List<Channel>>(emptyList())
        private set
    var searchMovies by mutableStateOf<List<VodItem>>(emptyList())
        private set
    var searchSeries by mutableStateOf<List<VodItem>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    private var searchJob: Job? = null
    var movieDetail by mutableStateOf<VodItem?>(null)
        private set
    var watchlist by mutableStateOf<List<VodItem>>(emptyList())
        private set
    var history by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set
    private var pendingEpisodeId: String? = null
    val vodCategories = mutableStateMapOf<VodKind, List<Category>>()
    val vodSelected = mutableStateMapOf<VodKind, String>()
    val vodItems = mutableStateMapOf<String, List<VodItem>>()
    val vodLoading = mutableStateMapOf<String, Boolean>()
    val vodErrors = mutableStateMapOf<String, String>()
    var seasons by mutableStateOf<List<Season>?>(null)
        private set
    var seasonsError by mutableStateOf<String?>(null)
        private set
    private var seasonsOf: String? = null
    var seriesSeason by mutableIntStateOf(0)
    var seriesEpisode by mutableIntStateOf(-1)

    /** Dernière affiche sélectionnée par catégorie : le focus y revient au retour du lecteur. */
    val vodGridIndex = HashMap<String, Int>()
    var resumeVersion by mutableIntStateOf(0)
        private set

    /** L'accueil place le focus tout seul seulement en y arrivant (pas en changeant d'onglet). */
    private var pendingHomeFocus = true

    fun consumeHomeFocus(): Boolean {
        val pending = pendingHomeFocus
        pendingHomeFocus = false
        return pending
    }

    // ---- Guide TV ----
    val epg = mutableStateMapOf<String, List<Programme>>()
    private val epgFetched = HashMap<String, Long>()

    val installedVersionName = Updater.installedVersionName(app)
    private val installedVersionCode = Updater.installedVersionCode(app)

    var update by mutableStateOf<UpdateState>(UpdateState.None)
        private set

    init {
        // On rouvre directement le dernier compte utilisé.
        accountStore.lastAccountId?.let { id -> accounts.find { it.id == id } }?.let(::open)
        checkForUpdate(manual = false)
    }

    fun checkForUpdate(manual: Boolean) {
        if (update is UpdateState.Downloading) return
        viewModelScope.launch {
            try {
                val release = Updater.latest()
                if (release != null && release.versionCode > installedVersionCode) {
                    update = UpdateState.Available(release)
                } else if (manual) {
                    message = "Vous avez déjà la dernière version ($installedVersionName)"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) message = "Vérification impossible : ${friendly(e)}"
            }
        }
    }

    fun downloadUpdate() {
        val release = when (val u = update) {
            is UpdateState.Available -> u.release
            is UpdateState.Failed -> u.release
            else -> return
        }
        update = UpdateState.Downloading(release, 0)
        viewModelScope.launch {
            update = try {
                val file = Updater.download(getApplication(), release.apkUrl) { percent ->
                    viewModelScope.launch { if (update is UpdateState.Downloading) update = UpdateState.Downloading(release, percent) }
                }
                UpdateState.Ready(release, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(release, friendly(e))
            }
        }
    }

    fun dismissUpdate() {
        if (update !is UpdateState.Downloading) update = UpdateState.None
    }

    fun open(account: Account) {
        if (account.id == current?.id && library != null) {
            pendingHomeFocus = true
            screen = Screen.Home
            return
        }
        current = account
        accountStore.lastAccountId = account.id
        favorites = favoritesStore.load(account.id)
        selectedCategory = if (favorites.isNotEmpty()) CAT_FAVORITES else CAT_ALL
        lastChannelId = null
        library = null
        watchlist = libraryStore.watchlist(account.id)
        history = libraryStore.history(account.id)
        section = Section.HOME
        vodCategories.clear()
        vodSelected.clear()
        vodItems.clear()
        vodLoading.clear()
        vodErrors.clear()
        seasons = null
        seasonsOf = null
        epg.clear()
        epgFetched.clear()
        pendingHomeFocus = true
        screen = Screen.Home
        load(account, force = false)
    }

    fun refresh() {
        current?.let { load(it, force = true) }
    }

    private fun load(account: Account, force: Boolean) {
        loadJob?.cancel()
        loadError = null
        loadJob = viewModelScope.launch {
            val cached = if (library == null) repository.cached(account) else library?.playlist
            if (cached != null && library == null) library = withContext(Dispatchers.Default) { Library(cached) }
            if (!force && cached != null && System.currentTimeMillis() - cached.updatedAt < AUTO_REFRESH_AFTER_MS) return@launch

            loading = cached == null
            refreshing = true
            try {
                val fresh = repository.refresh(account)
                library = withContext(Dispatchers.Default) { Library(fresh) }
                if (force) message = "Liste mise à jour : ${fresh.channels.size} chaînes"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = friendly(e)
                if (cached != null) message = "Mise à jour impossible ($reason). Liste en cache affichée."
                else loadError = "Chargement impossible : $reason"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun channelsFor(categoryId: String): List<Channel> {
        val lib = library ?: return emptyList()
        return when (categoryId) {
            CAT_FAVORITES -> favorites.mapNotNull { lib.byId[it] }
            CAT_ALL -> lib.playlist.channels
            else -> lib.byCategory[categoryId].orEmpty()
        }
    }

    fun toggleFavorite(channel: Channel) {
        val account = current ?: return
        val adding = channel.id !in favorites
        favorites = if (adding) favorites + channel.id else favorites - channel.id
        favoritesStore.save(account.id, favorites)
        message = if (adding) "★ ${channel.name} ajoutée aux favoris" else "${channel.name} retirée des favoris"
    }

    fun play(channels: List<Channel>, index: Int) {
        if (channels.isEmpty()) return
        lastChannelId = channels[index].id
        screen = Screen.Player(channels, index)
    }

    suspend fun resolveStream(channel: Channel): StreamRequest {
        val account = current ?: throw IOException("aucun compte ouvert")
        return repository.resolve(account, channel)
    }

    // ---- Recherche ----

    fun search(query: String) {
        searchQuery = query.trim()
        searchJob?.cancel()
        searchMovies = emptyList()
        searchSeries = emptyList()
        val account = current
        if (searchQuery.length < 2 || account == null) {
            searchChannels = emptyList()
            searching = false
            return
        }
        val q = normalize(searchQuery)
        searchChannels = library?.playlist?.channels?.filter { normalize(it.name).contains(q) }?.take(60).orEmpty()
        if (account.type == SourceType.M3U) return
        searching = true
        searchJob = viewModelScope.launch {
            try {
                val movies = async { runCatching { repository.searchVod(account, VodKind.MOVIE, searchQuery) }.getOrDefault(emptyList()) }
                val series = async { runCatching { repository.searchVod(account, VodKind.SERIES, searchQuery) }.getOrDefault(emptyList()) }
                searchMovies = movies.await()
                searchSeries = series.await()
            } finally {
                searching = false
            }
        }
    }

    // ---- Guide TV ----

    /** Programme en cours/suivant d'une chaîne, gardé 15 min en mémoire. */
    fun requestEpg(channel: Channel) {
        val account = current ?: return
        if (account.type == SourceType.M3U) return
        val now = System.currentTimeMillis()
        val last = epgFetched[channel.id]
        val list = epg[channel.id]
        val outdated = list != null && list.none { it.end > now }
        if (last != null && now - last < EPG_REFRESH_AFTER_MS && !(outdated && now - last > 60_000)) return
        epgFetched[channel.id] = now
        viewModelScope.launch {
            runCatching { repository.shortEpg(account, channel) }
                .onSuccess { if (current?.id == account.id) epg[channel.id] = it }
        }
    }

    // ---- Films et séries ----

    fun vodKey(kind: VodKind, categoryId: String) = "$kind|$categoryId"

    fun loadVodCategories(kind: VodKind, force: Boolean = false) {
        val account = current ?: return
        val key = "$kind|categories"
        if (vodLoading[key] == true || (!force && vodCategories.containsKey(kind))) return
        vodLoading[key] = true
        viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { repository.cachedVodCategories(account, kind) }
                if (cached != null) vodCategories[kind] = cached.items
                if (cached != null && !force && System.currentTimeMillis() - cached.updatedAt < VOD_REFRESH_AFTER_MS) return@launch
                val fresh = repository.vodCategories(account, kind)
                if (current?.id == account.id) {
                    vodCategories[kind] = fresh
                    vodErrors.remove(key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!vodCategories.containsKey(kind)) vodErrors[key] = "Chargement impossible : ${friendly(e)}"
            } finally {
                vodLoading[key] = false
            }
        }
    }

    fun loadVodItems(kind: VodKind, categoryId: String, force: Boolean = false) {
        val account = current ?: return
        val key = vodKey(kind, categoryId)
        if (vodLoading[key] == true || (!force && vodItems.containsKey(key))) return
        vodLoading[key] = true
        viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { repository.cachedVodItems(account, kind, categoryId) }
                if (cached != null) vodItems[key] = cached.items
                if (cached != null && !force && System.currentTimeMillis() - cached.updatedAt < VOD_REFRESH_AFTER_MS) return@launch
                val fresh = repository.vodItems(account, kind, categoryId)
                if (current?.id == account.id) {
                    vodItems[key] = fresh
                    vodErrors.remove(key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!vodItems.containsKey(key)) vodErrors[key] = "Chargement impossible : ${friendly(e)}"
            } finally {
                vodLoading[key] = false
            }
        }
    }

    /** Film : fiche détaillée. Série : liste des épisodes. */
    fun openVod(item: VodItem) {
        if (item.kind == VodKind.SERIES) openSeries(item) else openMovie(item)
    }

    private fun openMovie(item: VodItem) {
        val account = current ?: return
        movieDetail = item
        screen = Screen.MovieDetail(item)
        if (item.description != null && item.backdrop != null) return
        viewModelScope.launch {
            val full = runCatching { repository.movieDetails(account, item) }.getOrNull() ?: return@launch
            if (movieDetail?.id == item.id) movieDetail = full
        }
    }

    fun playMovie(item: VodItem, back: Screen, autoResume: Boolean = false) {
        val playable = Playable(movieKey(item), item.name, listOfNotNull(item.year, item.duration).joinToString("  ·  ").ifEmpty { null }, item.url)
        screen = Screen.VodPlayer(listOf(playable), 0, back, item, autoResume)
    }

    fun closeMovie() {
        pendingHomeFocus = true
        screen = Screen.Home
    }

    /** Depuis « Continuer à regarder » : le film reprend directement, la série s'ouvre sur l'épisode en cours. */
    fun continueWatching(entry: HistoryEntry) {
        if (entry.item.kind == VodKind.MOVIE) {
            playMovie(entry.item, Screen.Home, autoResume = true)
        } else {
            pendingEpisodeId = entry.episodeId
            openSeries(entry.item, forceReload = seasonsOf != entry.item.id)
        }
    }

    private fun openSeries(item: VodItem, forceReload: Boolean = false) {
        val account = current ?: return
        screen = Screen.Series(item)
        if (!forceReload && seasonsOf == item.id && seasons != null) {
            applyPendingEpisode()
            return
        }
        seasonsOf = item.id
        seasons = null
        seasonsError = null
        seriesSeason = 0
        seriesEpisode = -1
        viewModelScope.launch {
            try {
                val result = repository.seasons(account, item)
                if (seasonsOf == item.id) {
                    seasons = result
                    applyPendingEpisode()
                    if (result.isEmpty()) seasonsError = "Aucun épisode trouvé pour cette série."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (seasonsOf == item.id) seasonsError = "Chargement impossible : ${friendly(e)}"
            }
        }
    }

    /** Place la sélection sur l'épisode demandé par « Continuer à regarder ». */
    private fun applyPendingEpisode() {
        val target = pendingEpisodeId ?: return
        val list = seasons ?: return
        pendingEpisodeId = null
        list.forEachIndexed { s, season ->
            val e = season.episodes.indexOfFirst { it.id == target }
            if (e >= 0) {
                seriesSeason = s
                seriesEpisode = e
                return
            }
        }
    }

    fun playEpisodes(series: VodItem, episodes: List<Episode>, index: Int) {
        val playables = episodes.map {
            Playable(episodeKey(series, it), series.name, "S${it.season} · É${it.number} · ${it.title}", it.url, it.stalkerSeries, it.id)
        }
        screen = Screen.VodPlayer(playables, index, Screen.Series(series), series)
    }

    fun exitVodPlayer(back: Screen) {
        resumeVersion++
        if (back == Screen.Home) pendingHomeFocus = true
        if (back is Screen.MovieDetail) movieDetail = movieDetail?.takeIf { it.id == back.item.id } ?: back.item
        screen = back
    }

    // ---- Ma liste et historique ----

    private fun VodItem.listKey() = "$kind:$id"

    fun inWatchlist(item: VodItem): Boolean = watchlist.any { it.listKey() == item.listKey() }

    fun toggleWatchlist(item: VodItem) {
        val account = current ?: return
        val adding = !inWatchlist(item)
        watchlist = if (adding) listOf(item) + watchlist else watchlist.filterNot { it.listKey() == item.listKey() }
        libraryStore.saveWatchlist(account.id, watchlist)
        message = if (adding) "✓ « ${item.name} » ajouté à Ma liste" else "« ${item.name} » retiré de Ma liste"
    }

    /** Appelé au lancement d'un film/épisode : le remonte en tête de « Continuer à regarder ». */
    fun onPlaybackStarted(source: VodItem, playable: Playable) {
        val account = current ?: return
        val entry = HistoryEntry(
            item = source,
            key = playable.key,
            episodeId = playable.episodeId,
            episodeLabel = playable.subtitle.takeIf { source.kind == VodKind.SERIES },
            updatedAt = System.currentTimeMillis(),
        )
        history = (listOf(entry) + history.filterNot { it.item.listKey() == source.listKey() }).take(40)
        libraryStore.saveHistory(account.id, history)
    }

    /** Films et épisodes commencés mais pas finis, du plus récent au plus ancien. */
    fun continueList(): List<HistoryEntry> {
        resumeVersion
        return history.filter { entry ->
            val position = resumeOf(entry.key)
            position != null && position.positionMs > 0 && !position.finished
        }
    }

    fun removeFromContinue(entry: HistoryEntry) {
        val account = current ?: return
        history = history.filterNot { it.key == entry.key }
        libraryStore.saveHistory(account.id, history)
        message = "« ${entry.item.name} » retiré de Continuer à regarder"
    }

    fun closeSeries() {
        pendingHomeFocus = true
        screen = Screen.Home
    }

    fun movieKey(item: VodItem) = "m:${item.id}"

    fun episodeKey(series: VodItem, episode: Episode) = "e:${series.id}:${episode.id}"

    /** Lire [resumeVersion] fait se mettre à jour l'affichage quand une position change. */
    fun resumeOf(key: String): ResumeStore.Position? {
        resumeVersion
        val account = current ?: return null
        return resumeStore.get(account.id, key)
    }

    fun saveResume(key: String, positionMs: Long, durationMs: Long) {
        val account = current ?: return
        resumeStore.set(account.id, key, positionMs, durationMs)
    }

    suspend fun resolveVod(playable: Playable): StreamRequest {
        val account = current ?: throw IOException("aucun compte ouvert")
        return repository.resolveVod(account, playable)
    }

    fun onChannelShown(channel: Channel) {
        lastChannelId = channel.id
    }

    fun exitPlayer() {
        pendingHomeFocus = true
        screen = Screen.Home
    }

    fun goToAccounts() {
        screen = Screen.Accounts
    }

    fun editAccount(account: Account?) {
        screen = Screen.EditAccount(account)
    }

    fun closeEditor() {
        screen = Screen.Accounts
    }

    fun saveAccount(account: Account) {
        val old = accounts.find { it.id == account.id }
        accounts = if (old == null) accounts + account else accounts.map { if (it.id == account.id) account else it }
        accountStore.save(accounts)
        // Serveur ou identifiants modifiés : l'ancienne liste n'est plus valable.
        if (old != null && old.copy(name = account.name) != account) {
            repository.cache.delete(account.id)
            if (current?.id == account.id) {
                current = null
                library = null
            }
        }
        if (current?.id == account.id) current = account
        open(account)
    }

    fun deleteAccount(account: Account) {
        accounts = accounts - account
        accountStore.save(accounts)
        repository.cache.delete(account.id)
        repository.vodCache.deleteAccount(account.id)
        libraryStore.remove(account.id)
        favoritesStore.remove(account.id)
        if (current?.id == account.id) {
            loadJob?.cancel()
            current = null
            library = null
            accountStore.lastAccountId = null
        }
        screen = Screen.Accounts
    }

    fun clearMessage() {
        message = null
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> "serveur introuvable, vérifiez l'adresse et la connexion"
        is SocketTimeoutException -> "le serveur ne répond pas"
        is ConnectException -> "connexion refusée par le serveur"
        else -> e.message ?: e.javaClass.simpleName
    }
}
