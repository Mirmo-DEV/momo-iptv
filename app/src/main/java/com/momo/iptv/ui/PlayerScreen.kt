package com.momo.iptv.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.momo.iptv.AppViewModel
import com.momo.iptv.Screen
import com.momo.iptv.data.Channel
import com.momo.iptv.player.LivePlayer
import com.momo.iptv.player.LivePlayer.Status
import kotlinx.coroutines.delay

/** Pendant le zapping, on attend ce délai sans nouvel appui avant d'ouvrir le flux. */
private const val ZAP_DELAY_MS = 350L
private const val INFO_DURATION_MS = 4_000L

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, screen: Screen.Player) {
    val channels = screen.channels
    val context = LocalContext.current
    val live = remember { LivePlayer(context, vm::resolveStream) }
    val status by live.status.collectAsState()
    var index by remember { mutableIntStateOf(screen.startIndex.coerceIn(0, channels.lastIndex)) }
    var infoTick by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    val firstPlay = remember { booleanArrayOf(true) }
    val rootFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val panelState = rememberLazyListState()
    val favoriteSet = remember(vm.favorites) { vm.favorites.toSet() }
    val channel = channels[index]

    BackHandler { if (panelOpen) panelOpen = false else vm.exitPlayer() }

    DisposableEffect(Unit) { onDispose { live.release() } }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> live.pauseForBackground()
                Lifecycle.Event.ON_START -> live.resumeFromBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Zapping : le nom s'affiche tout de suite, le flux s'ouvre quand on arrête d'appuyer.
    LaunchedEffect(index) {
        vm.onChannelShown(channels[index])
        if (firstPlay[0]) {
            firstPlay[0] = false
        } else {
            live.stop()
            delay(ZAP_DELAY_MS)
        }
        live.play(channels[index])
    }

    LaunchedEffect(index, infoTick) {
        showInfo = true
        delay(INFO_DURATION_MS)
        showInfo = false
    }

    LaunchedEffect(panelOpen) {
        withFrameNanos { }
        if (panelOpen) {
            scrollIntoView(panelState, index)
            runCatching { panelFocus.requestFocus() }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (panelOpen) return false
        val down = event.type == KeyEventType.KeyDown
        val step = when (event.key) {
            Key.DirectionUp, Key.ChannelDown -> -1
            Key.DirectionDown, Key.ChannelUp -> 1
            else -> 0
        }
        if (step != 0) {
            if (down) index = (index + step + channels.size) % channels.size
            return true
        }
        return when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (event.type == KeyEventType.KeyUp) panelOpen = true
                true
            }
            Key.Menu -> {
                if (event.type == KeyEventType.KeyUp) vm.toggleFavorite(channels[index])
                true
            }
            Key.DirectionLeft, Key.DirectionRight, Key.Info -> {
                if (down) infoTick++
                true
            }
            else -> false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onKeyEvent(::handleKey)
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    player = live.player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (status is Status.Buffering || status is Status.Reconnecting) {
            CircularProgressIndicator(color = Palette.Accent, modifier = Modifier.align(Alignment.Center).size(56.dp))
        }

        if (showInfo || status !is Status.Playing) {
            InfoBar(
                number = index + 1,
                channel = channel,
                favorite = channel.id in favoriteSet,
                status = status,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (panelOpen) {
            LazyColumn(
                state = panelState,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(460.dp)
                    .background(Color(0xE60B0F17))
                    .padding(horizontal = 12.dp, vertical = 24.dp),
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                    TvItem(
                        onClick = {
                            index = i
                            panelOpen = false
                        },
                        onMenu = { vm.toggleFavorite(ch) },
                        selected = i == index,
                        modifier = Modifier.fillMaxWidth().then(if (i == index) Modifier.focusRequester(panelFocus) else Modifier),
                    ) { focused ->
                        ChannelRowContent(i + 1, ch, ch.id in favoriteSet, focused)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBar(number: Int, channel: Channel, favorite: Boolean, status: Status, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 32.dp)
            .background(Color(0xD90B0F17), RoundedCornerShape(14.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number.toString(), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Accent)
        Spacer(Modifier.width(20.dp))
        if (channel.logo != null) {
            AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(80.dp, 50.dp))
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                (if (favorite) "★ " else "") + channel.name,
                fontSize = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Palette.Text,
            )
            val statusText = when (status) {
                Status.Idle, Status.Buffering -> "Chargement…"
                Status.Playing -> "▲▼ zapper  ·  OK liste des chaînes  ·  Menu ≡ favori"
                is Status.Reconnecting -> "Reconnexion… (essai ${status.attempt}) — ${status.reason}"
                is Status.Failed -> "Lecture impossible : ${status.reason}. Changez de chaîne ou réessayez plus tard."
            }
            Text(
                statusText,
                fontSize = 15.sp,
                color = if (status is Status.Failed || status is Status.Reconnecting) Palette.Error else Palette.TextDim,
            )
        }
    }
}
