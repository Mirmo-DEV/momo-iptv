package com.momo.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momo.iptv.AppViewModel
import com.momo.iptv.Screen
import com.momo.iptv.data.VodItem

@Composable
fun MovieDetailScreen(vm: AppViewModel, initial: VodItem) {
    val item = vm.movieDetail?.takeIf { it.id == initial.id } ?: initial
    BackHandler { vm.closeMovie() }
    val key = vm.movieKey(item)
    val resume = vm.resumeOf(key)
    val canResume = resume != null && resume.positionMs > 30_000 && !resume.finished
    val playFocus = remember { FocusRequester() }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        withFrameNanos { }
        runCatching { playFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(item.backdrop ?: item.poster)
        AnimatedVisibility(visible, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 20 }) {
            Column(
                Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 70.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                Column(Modifier.fillMaxWidth(0.58f)) {
                    Text(item.name, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 50.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    MetaChips(listOfNotNull(item.year, item.duration, item.genre), item.rating, Modifier.padding(top = 14.dp))
                    if (canResume) {
                        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(240.dp)) { ProgressLine(resume!!.fraction) }
                            Text("   Reste ${formatDuration(resume!!.durationMs - resume.positionMs)}", fontSize = 15.sp, color = Palette.TextDim)
                        }
                    }
                    item.description?.let {
                        Text(it, fontSize = 17.sp, lineHeight = 25.sp, color = Palette.Text.copy(alpha = 0.88f), maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp))
                    }
                    item.director?.let { Credit("Réalisation", it) }
                    item.cast?.let { Credit("Avec", it) }
                }
                Spacer(Modifier.height(30.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton(
                        if (canResume) "Reprendre" else "Lecture",
                        { vm.playMovie(item, Screen.MovieDetail(item), autoResume = true) },
                        Modifier.focusRequester(playFocus),
                        primary = true,
                        icon = Icons.Rounded.PlayArrow,
                    )
                    if (canResume) {
                        TvButton("Depuis le début", {
                            vm.saveResume(key, 0, resume!!.durationMs)
                            vm.playMovie(item, Screen.MovieDetail(item))
                        }, icon = Icons.Rounded.Replay)
                    }
                    val inList = vm.inWatchlist(item)
                    TvButton(
                        if (inList) "Dans ma liste" else "Ma liste",
                        { vm.toggleWatchlist(item) },
                        icon = if (inList) Icons.Rounded.Check else Icons.Rounded.Add,
                    )
                }
            }
        }
    }
}

@Composable
private fun Credit(label: String, value: String) {
    Text(
        "$label : $value",
        fontSize = 15.sp,
        color = Palette.TextDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 10.dp),
    )
}
