package com.momo.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.momo.iptv.AppViewModel
import com.momo.iptv.data.Season
import com.momo.iptv.data.VodItem
import kotlinx.coroutines.launch

@Composable
fun SeriesScreen(vm: AppViewModel, series: VodItem) {
    BackHandler { vm.closeSeries() }
    val seasons = vm.seasons

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (series.poster != null) {
                AsyncImage(
                    model = series.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(60.dp, 90.dp).clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(series.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(series.year, series.rating?.let { "★ $it" }).joinToString("  ·  ")
                if (details.isNotEmpty()) Text(details, fontSize = 15.sp, color = Palette.TextDim)
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            seasons != null && seasons.isNotEmpty() -> SeasonsAndEpisodes(vm, series, seasons)
            vm.seasonsError != null -> Retry(vm.seasonsError.orEmpty()) { vm.openVod(series) }
            else -> Loading("Chargement des épisodes…")
        }
    }
}

@Composable
private fun SeasonsAndEpisodes(vm: AppViewModel, series: VodItem, seasons: List<Season>) {
    val seasonIndex = vm.seriesSeason.coerceIn(0, seasons.lastIndex)
    val season = seasons[seasonIndex]
    val episodeIndex = vm.seriesEpisode.coerceIn(0, season.episodes.lastIndex)
    val seasonState = rememberLazyListState()
    val episodeState = remember(seasonIndex) { LazyListState((episodeIndex - 3).coerceAtLeast(0)) }
    val seasonFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun focusSeasons() = scope.launch {
        scrollIntoView(seasonState, seasonIndex)
        runCatching { seasonFocus.requestFocus() }
    }

    fun focusEpisodes() = scope.launch {
        scrollIntoView(episodeState, episodeIndex)
        runCatching { episodeFocus.requestFocus() }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (vm.seriesEpisode >= 0) focusEpisodes() else focusSeasons()
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = seasonState,
            modifier = Modifier
                .width(230.dp)
                .fillMaxHeight()
                .background(Palette.Panel, RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            itemsIndexed(seasons) { index, s ->
                TvItem(
                    onClick = { focusEpisodes() },
                    onFocus = {
                        if (vm.seriesSeason != index) {
                            vm.seriesSeason = index
                            vm.seriesEpisode = -1
                        }
                    },
                    selected = index == seasonIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == seasonIndex) Modifier.focusRequester(seasonFocus) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionRight) {
                                if (event.type == KeyEventType.KeyDown) focusEpisodes()
                                true
                            } else {
                                false
                            }
                        },
                ) { focused ->
                    Text(s.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(s.episodes.size.toString(), fontSize = 14.sp, color = if (focused) Palette.Text else Palette.TextDim)
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(state = episodeState, modifier = Modifier.weight(1f)) {
                itemsIndexed(season.episodes, key = { _, e -> e.id }) { index, episode ->
                    val resume = vm.resumeOf(vm.episodeKey(series, episode))
                    TvItem(
                        onClick = {
                            vm.seriesEpisode = index
                            // Tous les épisodes à la suite : la lecture enchaîne sur le suivant, même dans la saison d'après.
                            val all = seasons.flatMap { it.episodes }
                            val offset = seasons.take(seasonIndex).sumOf { it.episodes.size }
                            vm.playEpisodes(series, all, offset + index)
                        },
                        onFocus = { vm.seriesEpisode = index },
                        selected = index == vm.seriesEpisode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == episodeIndex) Modifier.focusRequester(episodeFocus) else Modifier)
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.DirectionLeft) {
                                    if (event.type == KeyEventType.KeyDown) focusSeasons()
                                    true
                                } else {
                                    false
                                }
                            },
                    ) { focused ->
                        Text(
                            "É${episode.number}",
                            fontSize = 15.sp,
                            color = if (focused) Palette.Text else Palette.TextDim,
                            modifier = Modifier.width(56.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(episode.title, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (resume != null && resume.positionMs > 0 && !resume.finished) {
                                Box(Modifier.width(220.dp)) { ProgressLine(resume.fraction) }
                            }
                        }
                        if (resume?.finished == true) Text("✓ vu", fontSize = 14.sp, color = if (focused) Palette.Text else Palette.TextDim)
                    }
                }
            }
            Text(
                "OK : lire   ·   la lecture reprend où vous l'aviez laissée   ·   Retour : catalogue",
                fontSize = 14.sp,
                color = Palette.TextDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
