package com.momo.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momo.iptv.AppViewModel
import com.momo.iptv.CAT_ALL
import com.momo.iptv.CAT_FAVORITES
import com.momo.iptv.Library
import com.momo.iptv.Section
import com.momo.iptv.data.Channel
import com.momo.iptv.data.SourceType
import com.momo.iptv.data.VodKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RAIL_COLLAPSED = 84.dp
private val RAIL_EXPANDED = 240.dp

private class NavEntry(val section: Section, val label: String, val icon: ImageVector)

/**
 * Écran principal façon Netflix : menu d'icônes à gauche (il s'ouvre quand on y va),
 * contenu de la rubrique à droite.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val account = vm.current ?: return
    val entries = buildList {
        add(NavEntry(Section.SEARCH, "Rechercher", Icons.Rounded.Search))
        add(NavEntry(Section.HOME, "Accueil", Icons.Rounded.Home))
        add(NavEntry(Section.LIVE, "Direct", Icons.Rounded.LiveTv))
        if (account.type != SourceType.M3U) {
            add(NavEntry(Section.MOVIES, "Films", Icons.Rounded.Movie))
            add(NavEntry(Section.SERIES, "Séries", Icons.Rounded.Tv))
            add(NavEntry(Section.WATCHLIST, "Ma liste", Icons.Rounded.Bookmark))
        }
    }
    if (entries.none { it.section == vm.section }) vm.section = Section.HOME

    var railFocused by remember { mutableStateOf(false) }
    val selectedFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val railWidth by animateDpAsState(if (railFocused) RAIL_EXPANDED else RAIL_COLLAPSED, tween(200), label = "menu")

    // Retour : depuis le contenu on revient au menu, depuis le menu on va aux comptes.
    BackHandler {
        if (railFocused) vm.goToAccounts() else runCatching { selectedFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = RAIL_COLLAPSED)
                .focusRestorer(),
        ) {
            Crossfade(targetState = vm.section, animationSpec = tween(220), label = "rubrique") { section ->
                when (section) {
                    Section.SEARCH -> SearchScreen(vm)
                    Section.HOME -> BrowseHome(vm)
                    Section.LIVE -> LiveSection(vm)
                    Section.MOVIES -> BrowseCatalog(vm, VodKind.MOVIE)
                    Section.SERIES -> BrowseCatalog(vm, VodKind.SERIES)
                    Section.WATCHLIST -> WatchlistGrid(vm)
                }
            }
        }

        // Voile sombre quand le menu est ouvert.
        AnimatedVisibility(railFocused, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.85f), Color.Transparent))))
        }

        Column(
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)))
                .onFocusChanged { railFocused = it.hasFocus }
                .focusProperties { enter = { selectedFocus } }
                .focusGroup()
                .padding(horizontal = 14.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (railFocused) "MOMO" else "M",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Palette.Accent,
                modifier = Modifier.padding(start = 14.dp, bottom = 28.dp),
            )
            entries.forEach { entry ->
                RailItem(
                    label = entry.label,
                    icon = entry.icon,
                    selected = vm.section == entry.section,
                    expanded = railFocused,
                    modifier = if (vm.section == entry.section) Modifier.focusRequester(selectedFocus) else Modifier,
                    onClick = {
                        vm.section = entry.section
                        scope.launch {
                            delay(250)
                            focusManager.moveFocus(FocusDirection.Right)
                        }
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            RailItem(
                label = account.name,
                icon = Icons.Rounded.AccountCircle,
                selected = false,
                expanded = railFocused,
                onClick = vm::goToAccounts,
            )
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (focused) Color.White else Color.Transparent, tween(150), label = "menu")
    val tint = when {
        focused -> Color.Black
        selected -> Color.White
        else -> Palette.TextDim
    }
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .size(18.dp, 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected && !focused) Palette.Accent else Color.Transparent),
            )
        }
        if (expanded) {
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                fontSize = 18.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveSection(vm: AppViewModel) {
    val library = vm.library
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 48.dp, top = 28.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Direct", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    when {
                        vm.refreshing -> "Mise à jour de la liste…"
                        library != null -> "${library.playlist.channels.size} chaînes · liste du ${formatDate(library.playlist.updatedAt)}"
                        else -> ""
                    },
                    fontSize = 14.sp,
                    color = Palette.TextDim,
                )
            }
            TvButton("Actualiser", vm::refresh, icon = Icons.Rounded.Refresh)
        }
        Spacer(Modifier.height(18.dp))
        when {
            library != null -> Lists(vm, library)
            vm.loading -> Loading("Chargement de la liste… (seulement la première fois)")
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
        if (!vm.consumeHomeFocus()) return@LaunchedEffect
        if (vm.lastChannelId != null && channels.any { it.id == vm.lastChannelId }) focusChannels() else focusCategories()
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = categoryState,
            modifier = Modifier
                .width(300.dp)
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
                    Text(entry.label, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(entry.count.toString(), fontSize = 14.sp, color = if (focused) Color.Black.copy(alpha = 0.6f) else Palette.TextDim)
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (channels.isEmpty()) {
                Text(
                    if (selected == CAT_FAVORITES) "Aucun favori pour l'instant.\nSur une chaîne, appuie sur la touche Menu ≡ pour l'ajouter."
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
            channels.getOrNull(channelIndex)?.let { EpgStrip(vm, it) }
            Text(
                "OK : regarder   ·   Menu ≡ : favori",
                fontSize = 14.sp,
                color = Palette.TextDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Programme en cours et suivant de la chaîne sélectionnée. */
@Composable
private fun EpgStrip(vm: AppViewModel, channel: Channel) {
    LaunchedEffect(channel.id) {
        delay(400)
        vm.requestEpg(channel)
    }
    val now = System.currentTimeMillis()
    val programmes = vm.epg[channel.id].orEmpty().filter { it.end > now }
    val current = programmes.firstOrNull { it.start <= now }
    val next = programmes.firstOrNull { it.start > now }
    if (current == null && next == null) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Palette.Panel, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        current?.let { p ->
            Text("En ce moment · ${timeRange(p)}  ${p.title}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgressLine(((now - p.start).toFloat() / (p.end - p.start).coerceAtLeast(1)).coerceIn(0f, 1f))
        }
        next?.let { p ->
            Text("Ensuite · ${timeRange(p)}  ${p.title}", fontSize = 14.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
