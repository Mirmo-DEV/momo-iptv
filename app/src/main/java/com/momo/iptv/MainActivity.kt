package com.momo.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momo.iptv.ui.AccountEditScreen
import com.momo.iptv.ui.AccountsScreen
import com.momo.iptv.ui.AppBackground
import com.momo.iptv.ui.HomeScreen
import com.momo.iptv.ui.MomoTypography
import com.momo.iptv.ui.MovieDetailScreen
import com.momo.iptv.ui.Palette
import com.momo.iptv.ui.PlayerScreen
import com.momo.iptv.ui.SeriesScreen
import com.momo.iptv.ui.UpdateDialog
import com.momo.iptv.ui.VodPlayerScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Palette.Accent,
                    background = Palette.Bg,
                    surface = Palette.Bg,
                    onSurface = Palette.Text,
                    onBackground = Palette.Text,
                ),
                typography = MomoTypography,
            ) {
                Surface(Modifier.fillMaxSize(), color = Palette.Bg, contentColor = Palette.Text) {
                    AppBackground { App() }
                }
            }
        }
    }
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = vm.screen,
            // Même écran avec d'autres données (ex. zapping) : pas d'animation, sauf pour un nouveau lecteur.
            contentKey = { screen -> if (screen is Screen.VodPlayer) screen else screen::class },
            transitionSpec = {
                val toPlayer = targetState is Screen.Player || targetState is Screen.VodPlayer
                if (toPlayer) {
                    fadeIn(tween(250)) togetherWith fadeOut(tween(150))
                } else {
                    (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.96f)) togetherWith fadeOut(tween(160))
                }
            },
            label = "écran",
        ) { screen ->
            when (screen) {
                Screen.Accounts -> AccountsScreen(vm)
                is Screen.EditAccount -> AccountEditScreen(vm, screen.account)
                Screen.Home -> HomeScreen(vm)
                is Screen.Player -> PlayerScreen(vm, screen)
                is Screen.Series -> SeriesScreen(vm, screen.series)
                is Screen.MovieDetail -> MovieDetailScreen(vm, screen.item)
                is Screen.VodPlayer -> VodPlayerScreen(vm, screen)
            }
        }
        MessageBar(vm.message, onTimeout = vm::clearMessage, modifier = Modifier.align(Alignment.BottomCenter))
        // Pas de pop-up par-dessus la vidéo : elle apparaîtra en sortant du lecteur.
        if (vm.screen !is Screen.Player && vm.screen !is Screen.VodPlayer) UpdateDialog(vm)
    }
}

/** Petit message qui glisse depuis le bas puis disparaît. */
@Composable
private fun MessageBar(message: String?, onTimeout: () -> Unit, modifier: Modifier) {
    var lastMessage by remember { mutableStateOf("") }
    if (message != null) lastMessage = message
    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            onTimeout()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250)),
        modifier = modifier,
    ) {
        Text(
            lastMessage,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.Text,
            modifier = Modifier
                .padding(bottom = 36.dp)
                .shadow(16.dp, RoundedCornerShape(50))
                .background(Palette.Selected, RoundedCornerShape(50))
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
