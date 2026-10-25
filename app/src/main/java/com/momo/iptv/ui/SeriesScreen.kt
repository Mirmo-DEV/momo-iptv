package com.momo.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momo.iptv.AppViewModel
import com.momo.iptv.data.Episode
import com.momo.iptv.data.Season
import com.momo.iptv.data.VodItem

@Composable
fun SeriesScreen(vm: AppViewModel, series: VodItem) {
    BackHandler { vm.closeSeries() }
    val seasons = vm.seasons
    val playFocus = remember { FocusRequester() }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(series.backdrop ?: series.poster)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 40.dp, bottom = 20.dp)) {
            Column(Modifier.padding(horizontal = 64.dp).fillMaxWidth(0.6f)) {
                Text(series.name, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 48.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                MetaChips(
                    listOfNotNull("Série", series.year, series.genre, seasons?.takeIf { it.isNotEmpty() }?.let { "${it.size} saison" + if (it.size > 1) "s" else "" }),
                    series.rating,
                    Modifier.padding(top = 12.dp),
                )
                series.description?.let {
                    Text(it, fontSize = 16.sp, lineHeight = 23.sp, color = Palette.Text.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            when {
                seasons != null && seasons.isNotEmpty() -> SeasonsAndEpisodes(vm, series, seasons, playFocus)
                vm.seasonsError != null -> Box(Modifier.padding(horizontal = 64.dp)) {
                    Retry(vm.seasonsError.orEmpty()) { vm.openVod(series) }
                }
                else -> Row(Modifier.padding(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(5) { Skeleton(Modifier.width(220.dp).height(124.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SeasonsAndEpisodes(vm: AppViewModel, series: VodItem, seasons: List<Season>, playFocus: FocusRequester) {
    val all = remember(seasons) { seasons.flatMap { it.episodes } }

    // Épisode à proposer : celui en cours, sinon le suivant du dernier vu, sinon le tout premier.
    val nextIndex = run {
        val last = all.indexOfLast { (vm.resumeOf(vm.episodeKey(series, it))?.positionMs ?: 0) > 0 }
        when {
            last < 0 -> 0
            vm.resumeOf(vm.episodeKey(series, all[last]))?.finished == true -> (last + 1).coerceAtMost(all.lastIndex)
            else -> last
        }
    }
    val next = all[nextIndex]
    val nextResume = vm.resumeOf(vm.episodeKey(series, next))
    val nextSeason = seasons.indexOfFirst { s -> s.episodes.any { it.id == next.id } }.coerceAtLeast(0)

    var seasonIndex by remember(seasons) { mutableIntStateOf(if (vm.seriesEpisode >= 0) vm.seriesSeason.coerceIn(0, seasons.lastIndex) else nextSeason) }
    val season = seasons[seasonIndex.coerceIn(0, seasons.lastIndex)]
    val episodeState = remember(seasonIndex) { androidx.compose.foundation.lazy.LazyListState() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { playFocus.requestFocus() }
    }

    fun play(episode: Episode) {
        val index = all.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
        vm.seriesSeason = seasons.indexOfFirst { s -> s.episodes.any { it.id == episode.id } }.coerceAtLeast(0)
        vm.seriesEpisode = seasons[vm.seriesSeason].episodes.indexOfFirst { it.id == episode.id }
        vm.playEpisodes(series, all, index)
    }

    Column {
        Row(Modifier.padding(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val resumable = nextResume != null && nextResume.positionMs > 0 && !nextResume.finished
            TvButton(
                (if (resumable) "Reprendre" else "Lecture") + "  S${next.season} · É${next.number}",
                { play(next) },
                Modifier.focusRequester(playFocus),
                primary = true,
                icon = Icons.Rounded.PlayArrow,
            )
            val inList = vm.inWatchlist(series)
            TvButton(if (inList) "Dans ma liste" else "Ma liste", { vm.toggleWatchlist(series) }, icon = if (inList) Icons.Rounded.Check else Icons.Rounded.Add)
        }

        Spacer(Modifier.height(22.dp))

        // Saisons : passer dessus change la liste des épisodes.
        if (seasons.size > 1) {
            LazyRow(
                state = rememberLazyListState(nextSeason),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 64.dp),
            ) {
                itemsIndexed(seasons) { index, s ->
                    SeasonChip(s.name, selected = index == seasonIndex) { seasonIndex = index }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyRow(
            state = episodeState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 12.dp, bottom = 12.dp),
        ) {
            itemsIndexed(season.episodes, key = { _, e -> e.id }) { _, episode ->
                val resume = vm.resumeOf(vm.episodeKey(series, episode))
                FocusCard(
                    onClick = { play(episode) },
                    modifier = Modifier.width(230.dp),
                    aspectRatio = 16f / 9f,
                    onFocus = {},
                    onMenu = null,
                    below = { focused ->
                        Text(
                            episode.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (focused) Palette.Text else Palette.TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            when {
                                resume?.finished == true -> "✓ Vu"
                                resume != null && resume.positionMs > 0 -> "Reste ${formatDuration(resume.durationMs - resume.positionMs)}"
                                else -> "Saison ${episode.season}"
                            },
                            fontSize = 12.sp,
                            color = if (resume?.finished == true) Palette.Success else Palette.TextDim,
                        )
                    },
                ) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A2A30), Color(0xFF121214)))))
                    Text(
                        "${episode.number}",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 4.dp),
                    )
                    Text(
                        "ÉPISODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.TextDim,
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    )
                    if (resume != null && resume.positionMs > 0 && !resume.finished) {
                        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 6.dp)) { ProgressLine(resume.fraction) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        when {
            focused -> Color.White
            selected -> Palette.Surface
            else -> Color.Transparent
        },
        tween(150),
        label = "saison",
    )
    Text(
        label,
        fontSize = 16.sp,
        fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
        color = if (focused) Color.Black else if (selected) Palette.Text else Palette.TextDim,
        modifier = Modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSelect)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}
