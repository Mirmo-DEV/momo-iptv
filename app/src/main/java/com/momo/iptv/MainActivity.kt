package com.momo.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momo.iptv.ui.AccountEditScreen
import com.momo.iptv.ui.AccountsScreen
import com.momo.iptv.ui.HomeScreen
import com.momo.iptv.ui.Palette
import com.momo.iptv.ui.PlayerScreen
import com.momo.iptv.ui.UpdateDialog
import com.momo.iptv.ui.SeriesScreen
import com.momo.iptv.ui.VodPlayerScreen
import androidx.compose.runtime.key
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
            ) {
                Surface(Modifier.fillMaxSize(), color = Palette.Bg, contentColor = Palette.Text) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        when (val screen = vm.screen) {
            Screen.Accounts -> AccountsScreen(vm)
            is Screen.EditAccount -> AccountEditScreen(vm, screen.account)
            Screen.Home -> HomeScreen(vm)
            is Screen.Player -> PlayerScreen(vm, screen)
            is Screen.Series -> SeriesScreen(vm, screen.series)
            is Screen.VodPlayer -> key(screen) { VodPlayerScreen(vm, screen) }
        }
        MessageBar(vm.message, onTimeout = vm::clearMessage, modifier = Modifier.align(Alignment.BottomCenter))
        // Pas de pop-up par-dessus la vidéo : elle apparaîtra en sortant du lecteur.
        if (vm.screen !is Screen.Player && vm.screen !is Screen.VodPlayer) UpdateDialog(vm)
    }
}

@Composable
private fun MessageBar(message: String?, onTimeout: () -> Unit, modifier: Modifier) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(5_000)
        onTimeout()
    }
    Text(
        message,
        fontSize = 16.sp,
        color = Palette.Text,
        modifier = modifier
            .padding(bottom = 32.dp)
            .background(Palette.Panel, RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
