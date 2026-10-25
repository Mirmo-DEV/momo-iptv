package com.momo.iptv.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.momo.iptv.data.Channel
import com.momo.iptv.data.Http
import com.momo.iptv.data.StreamRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Lecteur réglé pour le direct : démarrage rapide, buffer confortable,
 * et reconnexion automatique tant que l'écran est ouvert. Le lien est redemandé
 * via [resolve] à chaque (re)connexion, car certains (Stalker) expirent.
 */
@OptIn(UnstableApi::class)
class LivePlayer(context: Context, private val resolve: suspend (Channel) -> StreamRequest) {

    sealed interface Status {
        data object Idle : Status
        data object Buffering : Status
        data object Playing : Status
        data class Reconnecting(val attempt: Int, val reason: String) : Status
        data class Failed(val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    private val scope = MainScope()
    private var job: Job? = null
    private var channel: Channel? = null
    private var attempts = 0

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> if (_status.value !is Status.Reconnecting) _status.value = Status.Buffering
                Player.STATE_READY -> {
                    attempts = 0
                    _status.value = Status.Playing
                }
                // Un direct ne doit jamais « finir » : le serveur a coupé, on se reconnecte.
                Player.STATE_ENDED -> scheduleRetry("le flux s'est arrêté")
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) = scheduleRetry(describe(error))
    }

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(Http.USER_AGENT)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(true)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 15_000,
                    /* maxBufferMs = */ 50_000,
                    /* bufferForPlaybackMs = */ 1_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 4_000,
                )
                .build(),
        )
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(httpFactory)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6)),
        )
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .build()
        .also {
            it.addListener(listener)
            it.playWhenReady = true
        }

    fun play(channel: Channel) {
        job?.cancel()
        attempts = 0
        this.channel = channel
        _status.value = Status.Buffering
        job = scope.launch { open(channel) }
    }

    fun stop() {
        job?.cancel()
        channel = null
        player.stop()
        player.clearMediaItems()
        _status.value = Status.Idle
    }

    /** Appli en arrière-plan : on coupe le flux mais on garde la chaîne en mémoire. */
    fun pauseForBackground() {
        job?.cancel()
        player.stop()
    }

    fun resumeFromBackground() {
        channel?.let(::play)
    }

    fun release() {
        scope.cancel()
        channel = null
        player.removeListener(listener)
        player.release()
    }

    private suspend fun open(channel: Channel) {
        val request = try {
            resolve(channel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            scheduleRetry(e.message ?: "lien de lecture indisponible")
            return
        }
        httpFactory.setUserAgent(request.userAgent).setDefaultRequestProperties(request.headers)
        // Recharger l'élément repart du point « direct ».
        player.setMediaItem(mediaItem(request.url))
        player.prepare()
        player.play()
    }

    private fun scheduleRetry(reason: String) {
        val current = channel ?: return
        attempts++
        if (attempts > MAX_ATTEMPTS) {
            _status.value = Status.Failed(reason)
            return
        }
        _status.value = Status.Reconnecting(attempts, reason)
        val wait = 1_000L shl (attempts - 1).coerceAtMost(3)
        job?.cancel()
        job = scope.launch {
            delay(wait)
            open(current)
        }
    }

    private fun mediaItem(url: String): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        val path = Uri.parse(url).path.orEmpty()
        if (path.endsWith(".m3u8", ignoreCase = true) || url.contains("m3u8", ignoreCase = true)) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "connexion perdue"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "le serveur ne répond pas"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "refus du serveur (HTTP ${(e.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: "?"})"
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "retard sur le direct"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "flux illisible"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "format non supporté par l'appareil"
        else -> e.errorCodeName
    }

    private companion object {
        const val MAX_ATTEMPTS = 30
    }
}
