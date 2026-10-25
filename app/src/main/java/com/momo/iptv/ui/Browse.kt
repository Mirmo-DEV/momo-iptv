package com.momo.iptv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.momo.iptv.AppViewModel
import com.momo.iptv.CAT_FAVORITES
import com.momo.iptv.data.Category
import com.momo.iptv.data.HistoryEntry
import com.momo.iptv.data.SourceType
import com.momo.iptv.data.VodItem
import com.momo.iptv.data.VodKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ce qu'affiche le haut de l'écran pour l'élément sélectionné. */
data class HeroData(
    val title: String,
    val meta: List<String>,
    val rating: String? = null,
    val description: String? = null,
    val image: String? = null,
)

fun VodItem.toHero(extra: String? = null) = HeroData(
    title = name,
    meta = listOfNotNull(if (kind == VodKind.SERIES) "Série" else "Film", year, duration, genre, extra),
    rating = rating,
    description = description,
    image = backdrop ?: poster,
)

private val POSTER_WIDTH = 128.dp
private val LANDSCAPE_WIDTH = 236.dp
private val CHANNEL_WIDTH = 200.dp
private val START = 24.dp

/** Image de fond en haut à droite, fondue vers la gauche et vers le bas. Elle change en fondu. */
@Composable
fun HeroBackdrop(image: String?, modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current
    Box(modifier) {
        Crossfade(targetState = image, animationSpec = tween(450), label = "fond") { url ->
            Box(Modifier.fillMaxSize()) {
                if (url != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(url).size(1280, 720).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.75f,
                        modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(0.78f),
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.2f to Palette.Bg, 0.5f to Palette.Bg.copy(alpha = 0.6f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 0.78f to Palette.Bg)))
    }
}

@Composable
private fun HeroInfo(hero: HeroData?, fallbackTitle: String, modifier: Modifier) {
    AnimatedContent(
        targetState = hero,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
        label = "infos",
        modifier = modifier,
    ) { h ->
        Column(Modifier.fillMaxWidth(0.55f)) {
            Text(
                h?.title ?: fallbackTitle,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 46.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (h != null) {
                MetaChips(h.meta, h.rating, Modifier.padding(top = 10.dp))
                h.description?.let {
                    Text(
                        it,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        color = Palette.Text.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/** Structure commune : grande image + infos en haut, rangées en dessous. */
@Composable
private fun BrowseLayout(
    hero: HeroData?,
    fallbackTitle: String,
    listState: LazyListState,
    rows: LazyListScope.() -> Unit,
) {
    // L'image ne change qu'une fois la sélection posée : défilement fluide même en maintenant la touche.
    var shownImage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hero?.image) {
        delay(300)
        shownImage = hero?.image
    }
    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(shownImage)
        Column(Modifier.fillMaxSize()) {
            HeroInfo(hero, fallbackTitle, Modifier.padding(start = START, end = 48.dp, top = 40.dp).height(200.dp))
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 300.dp),
                modifier = Modifier.fillMaxSize(),
                content = rows,
            )
        }
    }
}

/**
 * Une rangée horizontale. Quand elle reçoit le focus, elle remonte juste sous le bandeau.
 * [requestFirstFocus] : place le focus sur sa première carte dès qu'elle est prête.
 */
@Composable
private fun <T> CardRow(
    title: String,
    items: List<T>?,
    rowIndex: Int,
    listState: LazyListState,
    cardWidth: Dp,
    skeletonRatio: Float,
    requestFirstFocus: Boolean,
    key: (Int, T) -> Any,
    card: @Composable (index: Int, item: T, modifier: Modifier) -> Unit,
) {
    if (items != null && items.isEmpty()) return
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    if (requestFirstFocus && !items.isNullOrEmpty()) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }
    Column(
        Modifier.onFocusChanged {
            if (it.hasFocus) scope.launch { listState.animateScrollToItem(rowIndex) }
        },
    ) {
        SectionTitle(title, Modifier.padding(start = START))
        if (items == null) {
            Row(Modifier.padding(start = START, top = 14.dp, bottom = 34.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(7) { Skeleton(Modifier.width(cardWidth).aspectRatio(skeletonRatio)) }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                // Marges pour que les cartes agrandies au focus ne soient pas coupées.
                contentPadding = PaddingValues(start = START, end = 48.dp, top = 14.dp, bottom = 12.dp),
            ) {
                itemsIndexed(items, key = { i, item -> key(i, item) }) { index, item ->
                    card(index, item, Modifier.width(cardWidth).then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier))
                }
            }
        }
    }
}

private sealed interface RowSpec {
    data object Continue : RowSpec
    data object Watchlist : RowSpec
    data object Channels : RowSpec
    data class Catalog(val kind: VodKind, val category: Category) : RowSpec
}

/** Onglet « Accueil » : reprises, Ma liste, chaînes favorites, puis un aperçu du catalogue. */
@Composable
fun BrowseHome(vm: AppViewModel) {
    val account = vm.current ?: return
    val withVod = account.type != SourceType.M3U
    LaunchedEffect(withVod) {
        if (withVod) {
            vm.loadVodCategories(VodKind.MOVIE)
            vm.loadVodCategories(VodKind.SERIES)
        }
    }
    val continueList = vm.continueList()
    val favoriteChannels = remember(vm.library, vm.favorites) { vm.channelsFor(CAT_FAVORITES) }
    val rows = buildList {
        if (continueList.isNotEmpty()) add(RowSpec.Continue)
        if (vm.watchlist.isNotEmpty()) add(RowSpec.Watchlist)
        if (favoriteChannels.isNotEmpty()) add(RowSpec.Channels)
        vm.vodCategories[VodKind.MOVIE]?.take(6)?.forEach { add(RowSpec.Catalog(VodKind.MOVIE, it)) }
        vm.vodCategories[VodKind.SERIES]?.take(6)?.forEach { add(RowSpec.Catalog(VodKind.SERIES, it)) }
    }
    var hero by remember { mutableStateOf<HeroData?>(null) }
    val listState = rememberLazyListState()
    val wantFocus = remember { vm.consumeHomeFocus() }

    if (rows.isEmpty()) {
        Loading(if (withVod) "Préparation de l'accueil…" else "Ajoute des chaînes favorites (touche Menu ≡) pour remplir l'accueil")
        return
    }

    BrowseLayout(hero, "Accueil", listState) {
        rows.forEachIndexed { rowIndex, spec ->
            val first = wantFocus && rowIndex == 0
            when (spec) {
                RowSpec.Continue -> item(key = "continue") {
                    ContinueRow(vm, continueList, rowIndex, listState, first) { hero = it }
                }
                RowSpec.Watchlist -> item(key = "watchlist") {
                    CardRow(
                        "Ma liste", vm.watchlist, rowIndex, listState, POSTER_WIDTH, 2f / 3f, first,
                        key = { _, item -> "${item.kind}:${item.id}" },
                    ) { _, item, modifier ->
                        VodPoster(vm, item, modifier) { hero = it }
                    }
                }
                RowSpec.Channels -> item(key = "channels") {
                    CardRow(
                        "Chaînes favorites", favoriteChannels, rowIndex, listState, CHANNEL_WIDTH, 16f / 9f, first,
                        key = { _, c -> c.id },
                    ) { index, channel, modifier ->
                        val now = System.currentTimeMillis()
                        val current = vm.epg[channel.id]?.firstOrNull { it.start <= now && it.end > now }
                        LandscapeCard(
                            title = channel.name,
                            subtitle = current?.title,
                            image = channel.logo,
                            progress = null,
                            fit = true,
                            onClick = { vm.play(favoriteChannels, index) },
                            onFocus = {
                                vm.requestEpg(channel)
                                hero = HeroData(channel.name, listOfNotNull("En direct", current?.let { timeRange(it) }), description = current?.title)
                            },
                            onMenu = { vm.toggleFavorite(channel) },
                            modifier = modifier,
                        )
                    }
                }
                is RowSpec.Catalog -> item(key = "${spec.kind}|${spec.category.id}") {
                    CatalogRow(
                        vm, spec.kind, spec.category,
                        title = (if (spec.kind == VodKind.SERIES) "Séries · " else "Films · ") + spec.category.name,
                        rowIndex = rowIndex, listState = listState, requestFirstFocus = first,
                    ) { hero = it }
                }
            }
        }
    }
}

/** Onglet Films ou Séries : une rangée par catégorie, chargée quand elle apparaît. */
@Composable
fun BrowseCatalog(vm: AppViewModel, kind: VodKind) {
    LaunchedEffect(kind) { vm.loadVodCategories(kind) }
    val categories = vm.vodCategories[kind]
    val title = if (kind == VodKind.MOVIE) "Films" else "Séries"
    var hero by remember { mutableStateOf<HeroData?>(null) }
    val listState = rememberLazyListState()
    val wantFocus = remember { vm.consumeHomeFocus() }

    when {
        categories == null && vm.vodErrors["$kind|categories"] != null ->
            Retry(vm.vodErrors["$kind|categories"].orEmpty()) { vm.loadVodCategories(kind, force = true) }
        categories == null -> Loading("Chargement du catalogue…")
        categories.isEmpty() -> Loading(if (kind == VodKind.MOVIE) "Aucun film proposé par ce compte." else "Aucune série proposée par ce compte.")
        else -> BrowseLayout(hero, title, listState) {
            categories.forEachIndexed { rowIndex, category ->
                item(key = category.id) {
                    CatalogRow(vm, kind, category, category.name, rowIndex, listState, wantFocus && rowIndex == 0) { hero = it }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(
    vm: AppViewModel,
    kind: VodKind,
    category: Category,
    title: String,
    rowIndex: Int,
    listState: LazyListState,
    requestFirstFocus: Boolean,
    onHero: (HeroData) -> Unit,
) {
    val key = vm.vodKey(kind, category.id)
    LaunchedEffect(key) { vm.loadVodItems(kind, category.id) }
    CardRow(
        title, vm.vodItems[key], rowIndex, listState, POSTER_WIDTH, 2f / 3f, requestFirstFocus,
        key = { i, item -> "${item.id}#$i" },
    ) { _, item, modifier ->
        VodPoster(vm, item, modifier, onHero)
    }
}

@Composable
private fun VodPoster(vm: AppViewModel, item: VodItem, modifier: Modifier, onHero: (HeroData) -> Unit) {
    val resume = if (item.kind == VodKind.MOVIE) vm.resumeOf(vm.movieKey(item)) else null
    PosterCard(
        item = item,
        progress = resume?.takeIf { it.positionMs > 0 && !it.finished }?.fraction,
        inWatchlist = vm.inWatchlist(item),
        onClick = { vm.openVod(item) },
        onFocus = { onHero(item.toHero()) },
        onMenu = { vm.toggleWatchlist(item) },
        modifier = modifier,
    )
}

@Composable
private fun ContinueRow(
    vm: AppViewModel,
    entries: List<HistoryEntry>,
    rowIndex: Int,
    listState: LazyListState,
    requestFirstFocus: Boolean,
    onHero: (HeroData) -> Unit,
) {
    CardRow(
        "Continuer à regarder", entries, rowIndex, listState, LANDSCAPE_WIDTH, 16f / 9f, requestFirstFocus,
        key = { _, e -> e.key },
    ) { _, entry, modifier ->
        val position = vm.resumeOf(entry.key)
        val episode = entry.episodeLabel?.split(" · ")?.take(2)?.joinToString(" · ")
        LandscapeCard(
            title = entry.item.name,
            subtitle = episode ?: position?.let { "Reste ${formatDuration(it.durationMs - it.positionMs)}" },
            image = entry.item.backdrop ?: entry.item.poster,
            progress = position?.fraction ?: 0f,
            onClick = { vm.continueWatching(entry) },
            onFocus = { onHero(entry.item.toHero(episode)) },
            onMenu = { vm.removeFromContinue(entry) },
            modifier = modifier,
        )
    }
}

/** Onglet « Ma liste ». */
@Composable
fun WatchlistGrid(vm: AppViewModel) {
    val items = vm.watchlist
    val focus = remember { FocusRequester() }
    val wantFocus = remember { vm.consumeHomeFocus() }
    LaunchedEffect(items.isNotEmpty()) {
        withFrameNanos { }
        if (wantFocus && items.isNotEmpty()) runCatching { focus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(start = START, end = 48.dp, top = 40.dp)) {
        Text("Ma liste", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        if (items.isEmpty()) {
            Text(
                "Ta liste est vide. Dans Films ou Séries, appuie sur la touche Menu ≡ d'une affiche pour la garder ici.",
                fontSize = 18.sp,
                color = Palette.TextDim,
                modifier = Modifier.padding(top = 16.dp),
            )
            return
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp, end = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items, key = { _, i -> "${i.kind}:${i.id}" }) { index, item ->
                VodPoster(vm, item, if (index == 0) Modifier.focusRequester(focus) else Modifier) { }
            }
        }
    }
}
