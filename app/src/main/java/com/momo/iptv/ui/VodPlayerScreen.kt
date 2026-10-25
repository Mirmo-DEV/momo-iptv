package com.momo.iptv.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.momo.iptv.AppViewModel
import com.momo.iptv.Screen
import com.momo.iptv.data.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val RESUME_MIN_MS = 30_000L
private const val MAX_RETRIES = 5

@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(vm: AppViewModel, screen: Screen.VodPlayer) {
    val items = screen.items
    val context = LocalContext.current
    val httpFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
    }
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(httpFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6)),
            )
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
            .apply { playWhenReady = true }
    }

    var index by remember { mutableIntStateOf(screen.startIndex.coerceIn(0, items.lastIndex)) }
    val item = items[index]
    /** Position de départ choisie (null tant que l'on n'a pas décidé : reprise ou début). */
    var startAt by remember { mutableStateOf<Long?>(null) }
    var askResume by remember { mutableStateOf<Long?>(null) }
    var loadTick by remember { mutableIntStateOf(0) }
    var retries by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    val rootFocus = remember { FocusRequester() }

    fun saveNow() {
        val d = player.duration
        val p = player.currentPosition
        if (d > 0 && p > 0) vm.saveResume(items[index].key, p, d)
    }

    fun exit() {
        saveNow()
        vm.exitVodPlayer(screen.back)
    }

    BackHandler { exit() }

    // Choix de la position de départ à chaque nouvel élément.
    LaunchedEffect(index) {
        error = null
        retries = 0
        startAt = null
        val saved = vm.resumeOf(items[index].key)
        if (saved != null && saved.positionMs > RESUME_MIN_MS && !saved.finished) askResume = saved.positionMs else startAt = 0L
    }

    LaunchedEffect(index, startAt, loadTick) {
        val start = startAt ?: return@LaunchedEffect
        buffering = true
        val request = try {
            vm.resolveVod(items[index])
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Lecture impossible : ${e.message ?: "lien indisponible"}"
            return@LaunchedEffect
        }
        httpFactory.setUserAgent(request.userAgent).setDefaultRequestProperties(request.headers)
        player.setMediaItem(MediaItem.fromUri(request.url), start)
        player.prepare()
        player.play()
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    retries = 0
                    error = null
                }
                if (state == Player.STATE_ENDED) {
                    val d = player.duration
                    if (d > 0) vm.saveResume(items[index].key, d, d)
                    if (index < items.lastIndex) index++ else vm.exitVodPlayer(screen.back)
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                // Coupure : on rouvre au même endroit (le lien est redemandé).
                if (retries < MAX_RETRIES) {
                    retries++
                    startAt = position.coerceAtLeast(0L)
                    loadTick++
                } else {
                    error = "Lecture impossible : ${e.errorCodeName}"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveNow()
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveNow()
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Suivi de la position + sauvegarde régulière pour la reprise.
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            playing = player.isPlaying
            buffering = player.playbackState == Player.STATE_BUFFERING
            if (++ticks % 20 == 0) saveNow()
            delay(500)
        }
    }

    LaunchedEffect(overlayTick, index) {
        overlay = true
        delay(4_000)
        overlay = false
    }

    LaunchedEffect(askResume) {
        if (askResume == null) {
            withFrameNanos { }
            runCatching { rootFocus.requestFocus() }
        }
    }

    fun seekBy(deltaMs: Long) {
        val d = player.duration
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L).let { if (d > 0) it.coerceAtMost(d - 1_000) else it }
        player.seekTo(target)
        position = target
        overlayTick++
    }

    fun handleKey(event: KeyEvent): Boolean {
        val down = event.type == KeyEventType.KeyDown
        val repeat = event.nativeKeyEvent.repeatCount
        // Maintenir la touche accélère : 10 s, puis 30 s, puis 1 min par pas.
        val step = when {
            repeat > 12 -> 60_000L
            repeat > 4 -> 30_000L
            else -> 10_000L
        }
        return when (event.key) {
            Key.DirectionLeft -> {
                if (down) seekBy(-step)
                true
            }
            Key.DirectionRight -> {
                if (down) seekBy(step)
                true
            }
            Key.MediaRewind -> {
                if (down) seekBy(-30_000)
                true
            }
            Key.MediaFastForward -> {
                if (down) seekBy(30_000)
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                if (event.type == KeyEventType.KeyUp) {
                    if (player.isPlaying) player.pause() else player.play()
                    overlayTick++
                }
                true
            }
            Key.MediaNext, Key.ChannelUp -> {
                if (down && index < items.lastIndex) {
                    saveNow()
                    index++
                }
                true
            }
            Key.MediaPrevious, Key.ChannelDown -> {
                if (down && index > 0) {
                    saveNow()
                    index--
                }
                true
            }
            Key.DirectionUp, Key.DirectionDown, Key.Info -> {
                if (down) overlayTick++
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
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        when {
            error != null -> Text(
                error.orEmpty(),
                color = Palette.Error,
                fontSize = 20.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xD90B0F17), RoundedCornerShape(12.dp))
                    .padding(24.dp),
            )
            buffering -> CircularProgressIndicator(color = Palette.Accent, modifier = Modifier.align(Alignment.Center).size(56.dp))
            !playing && askResume == null -> Text("❚❚", fontSize = 56.sp, color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        if (overlay || !playing || error != null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 32.dp)
                    .background(Color(0xD90B0F17), RoundedCornerShape(14.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(item.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.subtitle?.let { Text(it, fontSize = 16.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                ProgressLine(if (duration > 0) position.toFloat() / duration else 0f, Modifier.padding(top = 8.dp))
                Row {
                    Text(
                        "${formatDuration(position)} / ${if (duration > 0) formatDuration(duration) else "--:--"}",
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "◀▶ reculer/avancer (maintenir = plus vite)  ·  OK pause" + if (items.size > 1) "  ·  CH+/CH− épisode" else "",
                        fontSize = 14.sp,
                        color = Palette.TextDim,
                    )
                }
            }
        }
    }

    askResume?.let { savedPosition ->
        val resumeFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = {
            askResume = null
            startAt = savedPosition
        }) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { resumeFocus.requestFocus() }
            }
            Column(
                Modifier
                    .width(560.dp)
                    .background(Palette.Panel, RoundedCornerShape(16.dp))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(item.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 2)
                item.subtitle?.let { Text(it, fontSize = 15.sp, color = Palette.TextDim) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Reprendre à ${formatDuration(savedPosition)}", {
                        askResume = null
                        startAt = savedPosition
                    }, Modifier.focusRequester(resumeFocus))
                    TvButton("Depuis le début", {
                        askResume = null
                        startAt = 0L
                    })
                }
            }
        }
    }
}
