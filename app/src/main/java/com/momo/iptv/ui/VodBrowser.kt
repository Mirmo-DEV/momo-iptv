package com.momo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.momo.iptv.AppViewModel
import com.momo.iptv.data.VodItem
import com.momo.iptv.data.VodKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COLUMNS = 4

@Composable
fun VodBrowser(vm: AppViewModel, kind: VodKind) {
    LaunchedEffect(kind) { vm.loadVodCategories(kind) }
    val categoriesKey = "$kind|categories"
    val categories = vm.vodCategories[kind]

    if (categories == null) {
        val error = vm.vodErrors[categoriesKey]
        if (error != null) {
            Retry(error) { vm.loadVodCategories(kind, force = true) }
        } else {
            Loading("Chargement du catalogue…")
        }
        return
    }
    if (categories.isEmpty()) {
        Text(
            if (kind == VodKind.MOVIE) "Aucun film proposé par ce compte." else "Aucune série proposée par ce compte.",
            color = Palette.TextDim,
            fontSize = 18.sp,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    val selected = vm.vodSelected[kind]?.takeIf { id -> categories.any { it.id == id } } ?: categories.first().id
    val itemsKey = vm.vodKey(kind, selected)
    // Petit délai : parcourir les catégories à la télécommande ne déclenche pas un chargement à chaque pas.
    LaunchedEffect(itemsKey) {
        if (!vm.vodItems.containsKey(itemsKey)) delay(350)
        vm.loadVodItems(kind, selected)
    }
    val items = vm.vodItems[itemsKey]

    val categoryIndex = categories.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val categoryState = rememberLazyListState((categoryIndex - 3).coerceAtLeast(0))
    var gridIndex by remember(itemsKey) { mutableIntStateOf(vm.vodGridIndex[itemsKey] ?: 0) }
    val gridState = remember(itemsKey) { LazyGridState((gridIndex - COLUMNS).coerceAtLeast(0)) }
    val categoryFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun focusCategories() = scope.launch {
        scrollIntoView(categoryState, categoryIndex)
        runCatching { categoryFocus.requestFocus() }
    }

    fun focusGrid() = scope.launch {
        val list = items ?: return@launch
        if (list.isEmpty()) return@launch
        val index = gridIndex.coerceIn(0, list.lastIndex)
        gridIndex = index
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) gridState.scrollToItem(index)
        withFrameNanos { }
        runCatching { gridFocus.requestFocus() }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!vm.consumeHomeFocus()) return@LaunchedEffect
        if (vm.vodGridIndex.containsKey(itemsKey) && !items.isNullOrEmpty()) focusGrid() else focusCategories()
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = categoryState,
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(Palette.Panel, RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            itemsIndexed(categories, key = { _, c -> c.id }) { _, category ->
                TvItem(
                    onClick = { focusGrid() },
                    onFocus = { vm.vodSelected[kind] = category.id },
                    selected = category.id == selected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (category.id == selected) Modifier.focusRequester(categoryFocus) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionRight) {
                                if (event.type == KeyEventType.KeyDown) focusGrid()
                                true
                            } else {
                                false
                            }
                        },
                ) {
                    Text(category.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                items == null && vm.vodErrors[itemsKey] != null ->
                    Retry(vm.vodErrors[itemsKey].orEmpty()) { vm.loadVodItems(kind, selected, force = true) }
                items == null -> Loading("Chargement…")
                items.isEmpty() -> Text("Rien dans cette catégorie.", color = Palette.TextDim, fontSize = 18.sp, modifier = Modifier.padding(16.dp))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(items, key = { i, item -> "${item.id}#$i" }) { index, item ->
                        val resume = if (item.kind == VodKind.MOVIE) vm.resumeOf(vm.movieKey(item)) else null
                        PosterCard(
                            item = item,
                            progress = resume?.takeIf { it.positionMs > 0 }?.fraction,
                            onClick = {
                                vm.vodGridIndex[itemsKey] = index
                                vm.openVod(item)
                            },
                            onFocus = { gridIndex = index },
                            modifier = Modifier
                                .then(if (index == gridIndex) Modifier.focusRequester(gridFocus) else Modifier)
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.DirectionLeft && index % COLUMNS == 0) {
                                        if (event.type == KeyEventType.KeyDown) focusCategories()
                                        true
                                    } else {
                                        false
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterCard(
    item: VodItem,
    progress: Float?,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Palette.Accent else Palette.Panel)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.Selected),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.name, fontSize = 13.sp, textAlign = TextAlign.Center, color = Palette.TextDim, modifier = Modifier.padding(8.dp))
            if (item.poster != null) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (progress != null) ProgressLine(progress)
        Text(
            item.name,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        val details = listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString("  ·  ")
        if (details.isNotEmpty()) {
            Text(details, fontSize = 12.sp, color = if (focused) Palette.Text else Palette.TextDim, maxLines = 1)
        }
    }
}

@Composable
internal fun Loading(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Palette.Accent)
            Spacer(Modifier.height(16.dp))
            Text(text, color = Palette.TextDim)
        }
    }
}

@Composable
internal fun Retry(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = Palette.Error, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        TvButton("Réessayer", onRetry)
    }
}
