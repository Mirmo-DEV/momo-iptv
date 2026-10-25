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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface Screen {
    data object Accounts : Screen
    data class EditAccount(val account: Account?) : Screen
    data object Home : Screen
    data class Player(val channels: List<Channel>, val startIndex: Int) : Screen
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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val accountStore = AccountStore(app)
    private val favoritesStore = FavoritesStore(app)
    private val repository = PlaylistRepository(app)
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

    init {
        // On rouvre directement le dernier compte utilisé.
        accountStore.lastAccountId?.let { id -> accounts.find { it.id == id } }?.let(::open)
    }

    fun open(account: Account) {
        if (account.id == current?.id && library != null) {
            screen = Screen.Home
            return
        }
        current = account
        accountStore.lastAccountId = account.id
        favorites = favoritesStore.load(account.id)
        selectedCategory = if (favorites.isNotEmpty()) CAT_FAVORITES else CAT_ALL
        lastChannelId = null
        library = null
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

    fun onChannelShown(channel: Channel) {
        lastChannelId = channel.id
    }

    fun exitPlayer() {
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
