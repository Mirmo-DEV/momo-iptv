package com.momo.iptv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momo.iptv.AppViewModel
import com.momo.iptv.data.VodItem
import com.momo.iptv.data.VodKind
import kotlinx.coroutines.delay

/** Ce qu'affiche le grand bandeau du haut pour l'élément sélectionné. */
private data class Hero(val title: String, val meta: String, val description: String?)

private fun VodItem.meta(extra: String? = null): String =
    listOfNotNull(
        if (kind == VodKind.SERIES) "Série" else "Film",
        year, genre, duration, rating?.let { "★ $it" }, extra,
    ).joinToString("   ·   ")

/** Onglet « Accueil » : bandeau de l'élément sélectionné + rangées personnelles. */
@Composable
fun HomeFeed(vm: AppViewModel) {
    val continueList = vm.continueList()
    val watchlist = vm.watchlist
    val library = vm.library
    val favoriteChannels = remember(library, vm.favorites) { vm.channelsFor(com.momo.iptv.CAT_FAVORITES) }
    val firstFocus = remember { FocusRequester() }
    var hero by remember { mutableStateOf<Hero?>(null) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (vm.consumeHomeFocus()) runCatching { firstFocus.requestFocus() }
    }

    if (continueList.isEmpty() && watchlist.isEmpty() && favoriteChannels.isEmpty()) {
        Welcome(vm)
        return
    }

    Column(Modifier.fillMaxSize()) {
        // Bandeau : titre, infos et résumé de l'élément sélectionné, en fondu.
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            AnimatedContent(
                targetState = hero,
                transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 }) togetherWith fadeOut(tween(150)) },
                label = "bandeau",
            ) { h ->
                if (h != null) {
                    Column(Modifier.fillMaxWidth(0.6f)) {
                        Text(h.title, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(h.meta, fontSize = 14.sp, color = Palette.TextDim, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                        h.description?.let {
                            Text(it, fontSize = 15.sp, color = Palette.Text.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            var firstAssigned = false
            fun takeFirst(): Boolean = if (!firstAssigned) {
                firstAssigned = true
                true
            } else {
                false
            }

            if (continueList.isNotEmpty()) {
                val first = takeFirst()
                item(key = "continue") {
                    FeedRow("Continuer à regarder") {
                        itemsIndexed(continueList, key = { _, e -> e.key }) { index, entry ->
                            val position = vm.resumeOf(entry.key)
                            PosterCard(
                                item = entry.item,
                                progress = position?.fraction,
                                inWatchlist = false,
                                // Série : « S1 · É3 » ; film : temps restant.
                                subtitle = entry.episodeLabel?.split(" · ")?.take(2)?.joinToString(" · ")
                                    ?: position?.let { "Reste ${formatDuration(it.durationMs - it.positionMs)}" },
                                onClick = { vm.continueWatching(entry) },
                                onFocus = {
                                    hero = Hero(entry.item.name, entry.item.meta(entry.episodeLabel), entry.item.description)
                                    vm.ambient = entry.item.backdrop ?: entry.item.poster
                                },
                                onMenu = { vm.removeFromContinue(entry) },
                                modifier = Modifier.width(118.dp).then(if (first && index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                            )
                        }
                    }
                }
            }

            if (watchlist.isNotEmpty()) {
                val first = takeFirst()
                item(key = "watchlist") {
                    FeedRow("Ma liste") {
                        itemsIndexed(watchlist, key = { _, i -> "${i.kind}:${i.id}" }) { index, item ->
                            PosterCard(
                                item = item,
                                progress = null,
                                inWatchlist = false,
                                onClick = { vm.openVod(item) },
                                onFocus = {
                                    hero = Hero(item.name, item.meta(), item.description)
                                    vm.ambient = item.backdrop ?: item.poster
                                },
                                onMenu = { vm.toggleWatchlist(item) },
                                modifier = Modifier.width(118.dp).then(if (first && index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                            )
                        }
                    }
                }
            }

            if (favoriteChannels.isNotEmpty()) {
                val first = takeFirst()
                item(key = "channels") {
                    FeedRow("Chaînes favorites") {
                        itemsIndexed(favoriteChannels, key = { _, c -> c.id }) { index, channel ->
                            val now = System.currentTimeMillis()
                            val current = vm.epg[channel.id]?.firstOrNull { it.start <= now && it.end > now }
                            ChannelCard(
                                channel = channel,
                                now = current,
                                onClick = { vm.play(favoriteChannels, index) },
                                onFocus = {
                                    vm.requestEpg(channel)
                                    hero = Hero(channel.name, current?.let { "En ce moment · ${timeRange(it)}" } ?: "Chaîne en direct", current?.title)
                                    vm.ambient = null
                                },
                                onMenu = { vm.toggleFavorite(channel) },
                                modifier = Modifier.width(190.dp).then(if (first && index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        SectionTitle(title, Modifier.padding(bottom = 12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            // Marge pour que les cartes qui grossissent au focus ne soient pas coupées.
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            content = content,
        )
    }
}

/** Accueil vide : on explique comment le remplir. */
@Composable
private fun Welcome(vm: AppViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        if (vm.consumeHomeFocus()) runCatching { focus.requestFocus() }
    }
    vm.ambient = null
    Column(Modifier.fillMaxSize().padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Bienvenue 👋", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Ton accueil se remplit tout seul : les films et séries commencés apparaissent dans « Continuer à regarder ».\n" +
                "Sur une affiche, appuie sur la touche Menu ≡ pour l'ajouter à « Ma liste ».\n" +
                "Sur une chaîne, la touche Menu ≡ l'ajoute à tes chaînes favorites.",
            fontSize = 17.sp,
            color = Palette.TextDim,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Voir les chaînes", { vm.section = com.momo.iptv.Section.LIVE }, Modifier.focusRequester(focus), primary = true)
            if (vm.current?.type != com.momo.iptv.data.SourceType.M3U) {
                TvButton("Parcourir les films", { vm.section = com.momo.iptv.Section.MOVIES })
            }
        }
    }
}

/** Onglet « Ma liste » : tous les films et séries mis de côté. */
@Composable
fun WatchlistGrid(vm: AppViewModel) {
    val items = vm.watchlist
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (vm.consumeHomeFocus()) runCatching { focus.requestFocus() }
    }
    if (items.isEmpty()) {
        vm.ambient = null
        Column(Modifier.fillMaxSize().padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ta liste est vide", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Dans Films ou Séries, appuie sur la touche Menu ≡ d'une affiche pour la garder ici.",
                fontSize = 17.sp,
                color = Palette.TextDim,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(0.dp)),
    ) {
        itemsIndexed(items, key = { _, i -> "${i.kind}:${i.id}" }) { index, item ->
            val resume = if (item.kind == VodKind.MOVIE) vm.resumeOf(vm.movieKey(item)) else null
            PosterCard(
                item = item,
                progress = resume?.takeIf { it.positionMs > 0 && !it.finished }?.fraction,
                inWatchlist = false,
                onClick = { vm.openVod(item) },
                onFocus = { vm.ambient = item.backdrop ?: item.poster },
                onMenu = { vm.toggleWatchlist(item) },
                modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
}
