package com.momo.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momo.iptv.AppViewModel
import com.momo.iptv.data.Account
import com.momo.iptv.data.SourceType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun AccountsScreen(vm: AppViewModel) {
    val focus = remember { FocusRequester() }
    val focusIndex = vm.accounts.indexOfFirst { it.id == vm.current?.id }.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 120.dp, vertical = 48.dp)) {
        Text("Momo IPTV", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            if (vm.accounts.isEmpty()) "Ajoutez votre premier compte pour commencer" else "Choisissez un compte",
            fontSize = 18.sp,
            color = Palette.TextDim,
        )
        Spacer(Modifier.height(28.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(vm.accounts, key = { _, a -> a.id }) { index, account ->
                TvItem(
                    onClick = { vm.open(account) },
                    onMenu = { vm.editAccount(account) },
                    selected = account.id == vm.current?.id,
                    modifier = Modifier.fillMaxWidth().then(if (index == focusIndex) Modifier.focusRequester(focus) else Modifier),
                ) { focused ->
                    Column(Modifier.weight(1f)) {
                        Text(account.name, fontSize = 20.sp)
                        Text(describe(account), fontSize = 14.sp, color = if (focused) Color.Black.copy(alpha = 0.65f) else Palette.TextDim)
                    }
                }
            }
            item(key = "add") {
                TvItem(
                    onClick = { vm.editAccount(null) },
                    modifier = Modifier.fillMaxWidth().then(if (vm.accounts.isEmpty()) Modifier.focusRequester(focus) else Modifier),
                ) {
                    Text("＋  Ajouter un compte", fontSize = 20.sp)
                }
            }
            item(key = "update") {
                TvItem(onClick = { vm.checkForUpdate(manual = true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("⟳  Rechercher une mise à jour", fontSize = 20.sp)
                }
            }
        }
        Text(
            (if (vm.accounts.isNotEmpty()) "Touche Menu ≡ sur un compte : modifier ou supprimer   ·   " else "") +
                "Version ${vm.installedVersionName}",
            fontSize = 15.sp,
            color = Palette.TextDim,
        )
    }
}

private fun describe(account: Account): String = when (account.type) {
    SourceType.XTREAM -> "Xtream Codes · " + host(account.server)
    SourceType.M3U -> "Liste M3U · " + host(account.m3uUrl)
    SourceType.STALKER -> "Portail Stalker · " + host(account.server)
}

private fun host(url: String): String {
    val u = url.trim()
    val withScheme = if (u.startsWith("http", ignoreCase = true)) u else "http://$u"
    return withScheme.toHttpUrlOrNull()?.host ?: u
}
