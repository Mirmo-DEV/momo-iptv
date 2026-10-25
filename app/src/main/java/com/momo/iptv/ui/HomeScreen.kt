package com.momo.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momo.iptv.AppViewModel
import com.momo.iptv.CAT_ALL
import com.momo.iptv.CAT_FAVORITES
import com.momo.iptv.Library
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel) {
    val account = vm.current ?: return
    val library = vm.library
    BackHandler { vm.goToAccounts() }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(account.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        vm.refreshing -> "Mise à jour de la liste en arrière-plan…"
                        library != null -> "${library.playlist.channels.size} chaînes · liste du ${formatDate(library.playlist.updatedAt)}"
                        else -> ""
                    },
                    fontSize = 14.sp,
                    color = Palette.TextDim,
                )
            }
            TvButton("Actualiser", vm::refresh)
            Spacer(Modifier.width(12.dp))
            TvButton("Comptes", vm::goToAccounts)
        }
        Spacer(Modifier.height(16.dp))

        when {
            library != null -> Lists(vm, library)
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Palette.Accent)
                    Spacer(Modifier.height(16.dp))
                    Text("Chargement de la liste… (seulement la première fois)", color = Palette.TextDim)
                }
            }
            else -> LoadFailed(vm)
        }
    }
}

@Composable
private fun LoadFailed(vm: AppViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(vm.loadError ?: "Liste indisponible", color = Palette.Error, fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Réessayer", vm::refresh, Modifier.focusRequester(focus))
            TvButton("Modifier le compte", { vm.current?.let(vm::editAccount) })
        }
    }
}

private class CategoryEntry(val id: String, val label: String, val count: Int)

@Composable
private fun Lists(vm: AppViewModel, library: Library) {
    val favorites = vm.favorites
    val favoriteSet = remember(favorites) { favorites.toSet() }
    val entries = remember(library, favorites) {
        buildList {
            add(CategoryEntry(CAT_FAVORITES, "★ Favoris", favorites.count { it in library.byId }))
            add(CategoryEntry(CAT_ALL, "Toutes les chaînes", library.playlist.channels.size))
            library.categories.forEach { add(CategoryEntry(it.id, it.name, library.byCategory[it.id]?.size ?: 0)) }
        }
    }
    val selected = vm.selectedCategory
    val channels = remember(library, favorites, selected) { vm.channelsFor(selected) }

    val categoryIndex = entries.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val categoryState = rememberLazyListState((categoryIndex - 3).coerceAtLeast(0))
    var channelIndex by remember(selected) {
        mutableIntStateOf(channels.indexOfFirst { it.id == vm.lastChannelId }.coerceAtLeast(0))
    }
    val channelState = remember(selected) { LazyListState((channelIndex - 3).coerceAtLeast(0)) }
    val categoryFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Gauche/droite passe d'une colonne à l'autre en retombant toujours sur l'élément actif,
    // pour ne pas changer de catégorie par accident.
    fun focusCategories() = scope.launch {
        scrollIntoView(categoryState, categoryIndex)
        runCatching { categoryFocus.requestFocus() }
    }

    fun focusChannels() = scope.launch {
        if (channels.isEmpty()) return@launch
        val index = channelIndex.coerceIn(0, channels.lastIndex)
        channelIndex = index
        scrollIntoView(channelState, index)
        runCatching { channelFocus.requestFocus() }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (vm.lastChannelId != null && channels.any { it.id == vm.lastChannelId }) focusChannels() else focusCategories()
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = categoryState,
            modifier = Modifier
                .width(330.dp)
                .fillMaxHeight()
                .background(Palette.Panel, RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.id }) { _, entry ->
                TvItem(
                    onClick = { focusChannels() },
                    onFocus = { vm.selectedCategory = entry.id },
                    selected = entry.id == selected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (entry.id == selected) Modifier.focusRequester(categoryFocus) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionRight) {
                                if (event.type == KeyEventType.KeyDown) focusChannels()
                                true
                            } else {
                                false
                            }
                        },
                ) { focused ->
                    Text(entry.label, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(entry.count.toString(), fontSize = 14.sp, color = if (focused) Palette.Text else Palette.TextDim)
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (channels.isEmpty()) {
                Text(
                    if (selected == CAT_FAVORITES) "Aucun favori pour l'instant.\nSur une chaîne, appuyez sur la touche Menu ≡ pour l'ajouter."
                    else "Aucune chaîne dans cette catégorie.",
                    color = Palette.TextDim,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(state = channelState, modifier = Modifier.weight(1f)) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                        TvItem(
                            onClick = { vm.play(channels, index) },
                            onFocus = { channelIndex = index },
                            onMenu = { vm.toggleFavorite(channel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == channelIndex) Modifier.focusRequester(channelFocus) else Modifier)
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.DirectionLeft) {
                                        if (event.type == KeyEventType.KeyDown) focusCategories()
                                        true
                                    } else {
                                        false
                                    }
                                },
                        ) { focused ->
                            ChannelRowContent(index + 1, channel, channel.id in favoriteSet, focused)
                        }
                    }
                }
            }
            Text(
                "OK : regarder   ·   Menu ≡ : ajouter/retirer des favoris   ·   Retour : comptes",
                fontSize = 14.sp,
                color = Palette.TextDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Fait défiler la liste si besoin pour que l'élément soit affiché (donc focalisable). */
internal suspend fun scrollIntoView(state: LazyListState, index: Int) {
    if (state.layoutInfo.visibleItemsInfo.none { it.index == index }) {
        state.scrollToItem((index - 3).coerceAtLeast(0))
    }
    withFrameNanos { }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM 'à' HH:mm", Locale.FRANCE).format(Date(millis))
