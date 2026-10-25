package com.momo.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInHorizontally(tween(400)) { -it / 12 },
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.poster != null) {
                    AsyncImage(
                        model = item.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(190.dp, 285.dp)
                            .shadow(24.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.width(40.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 44.sp)
                    MetaChips(listOfNotNull(item.year, item.duration, item.genre), item.rating, Modifier.padding(top = 12.dp))
                    if (canResume) {
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(220.dp)) { ProgressLine(resume!!.fraction) }
                            Text(
                                "  Reste ${formatDuration(resume!!.durationMs - resume.positionMs)}",
                                fontSize = 14.sp,
                                color = Palette.TextDim,
                            )
                        }
                    }
                    item.description?.let {
                        Text(it, fontSize = 16.sp, color = Palette.Text.copy(alpha = 0.88f), lineHeight = 24.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp))
                    }
                    item.director?.let { Credit("Réalisation", it) }
                    item.cast?.let { Credit("Avec", it) }
                    Spacer(Modifier.height(26.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TvButton(
                            if (canResume) "▶  Reprendre" else "▶  Lecture",
                            { vm.playMovie(item, Screen.MovieDetail(item), autoResume = true) },
                            Modifier.focusRequester(playFocus),
                            primary = true,
                        )
                        if (canResume) {
                            TvButton("↺  Depuis le début", {
                                vm.saveResume(key, 0, resume!!.durationMs)
                                vm.playMovie(item, Screen.MovieDetail(item))
                            })
                        }
                        TvButton(if (vm.inWatchlist(item)) "✓  Dans ma liste" else "＋  Ma liste", { vm.toggleWatchlist(item) })
                    }
                }
            }
        }
    }
}

/** Grande image de fond, fondue vers la gauche et vers le bas pour garder le texte lisible. */
@Composable
fun HeroBackdrop(image: String?, height: Dp? = null) {
    val sizeModifier = if (height != null) Modifier.fillMaxWidth().height(height) else Modifier.fillMaxSize()
    Box(sizeModifier) {
        Crossfade(targetState = image, animationSpec = tween(500), label = "fond") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f },
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Palette.Bg, Palette.Bg.copy(alpha = 0.85f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Palette.Bg))))
    }
}

@Composable
fun MetaChips(values: List<String>, rating: String?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        rating?.let {
            Text(
                "★ $it",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Bg,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.Favorite).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        values.forEach { value ->
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.Surface).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun Credit(label: String, value: String) {
    Text(
        "$label : $value",
        fontSize = 14.sp,
        color = Palette.TextDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp),
    )
}
