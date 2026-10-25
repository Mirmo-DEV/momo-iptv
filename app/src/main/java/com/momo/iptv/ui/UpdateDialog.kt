package com.momo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.momo.iptv.AppViewModel
import com.momo.iptv.UpdateState
import com.momo.iptv.update.Updater

@Composable
fun UpdateDialog(vm: AppViewModel) {
    val state = vm.update
    if (state == UpdateState.None) return
    val context = LocalContext.current
    val primary = remember { FocusRequester() }

    // Fichier téléchargé : on ouvre directement l'écran d'installation du système.
    if (state is UpdateState.Ready) {
        LaunchedEffect(state.file) {
            runCatching { Updater.install(context, state.file) }
        }
    }
    LaunchedEffect(state::class) {
        withFrameNanos { }
        runCatching { primary.requestFocus() }
    }

    Dialog(onDismissRequest = vm::dismissUpdate) {
        Column(
            Modifier
                .width(620.dp)
                .background(Palette.Panel, RoundedCornerShape(16.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Mise à jour disponible", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            val version = when (state) {
                is UpdateState.Available -> state.release.versionName
                is UpdateState.Downloading -> state.release.versionName
                is UpdateState.Ready -> state.release.versionName
                is UpdateState.Failed -> state.release.versionName
                UpdateState.None -> ""
            }
            Text("Version $version (installée : ${vm.installedVersionName})", fontSize = 17.sp, color = Palette.TextDim)

            when (state) {
                is UpdateState.Available -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Mettre à jour", vm::downloadUpdate, Modifier.focusRequester(primary))
                    TvButton("Plus tard", vm::dismissUpdate)
                }
                is UpdateState.Downloading -> {
                    Text("Téléchargement… ${state.percent} %", fontSize = 17.sp, color = Palette.Text)
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        color = Palette.Accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is UpdateState.Ready -> {
                    Text(
                        "Confirmez l'installation sur l'écran suivant. Vos comptes et favoris sont conservés.",
                        fontSize = 17.sp,
                        color = Palette.Text,
                    )
                    if (!Updater.canInstall(context)) {
                        Text(
                            "Première fois : autorisez Momo IPTV dans Paramètres → Ma Fire TV → Options pour les développeurs → " +
                                "Installer des applis inconnues, puis revenez appuyer sur « Installer ».",
                            fontSize = 15.sp,
                            color = Palette.Favorite,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Installer", { runCatching { Updater.install(context, state.file) } }, Modifier.focusRequester(primary))
                        TvButton("Plus tard", vm::dismissUpdate)
                    }
                }
                is UpdateState.Failed -> {
                    Text("Échec : ${state.reason}", fontSize = 17.sp, color = Palette.Error)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Réessayer", vm::downloadUpdate, Modifier.focusRequester(primary))
                        TvButton("Plus tard", vm::dismissUpdate)
                    }
                }
                UpdateState.None -> Unit
            }
        }
    }
}
