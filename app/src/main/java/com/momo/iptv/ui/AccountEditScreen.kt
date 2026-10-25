package com.momo.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momo.iptv.AppViewModel
import com.momo.iptv.data.Account
import com.momo.iptv.data.SourceType
import com.momo.iptv.data.XtreamSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

@Composable
fun AccountEditScreen(vm: AppViewModel, existing: Account?) {
    var type by remember { mutableStateOf(existing?.type ?: SourceType.XTREAM) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var server by remember { mutableStateOf(existing?.server ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var m3uUrl by remember { mutableStateOf(existing?.m3uUrl ?: "") }
    var useHls by remember { mutableStateOf(existing?.useHls ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    BackHandler { vm.closeEditor() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    fun save() {
        error = when (type) {
            SourceType.XTREAM -> when {
                server.isBlank() -> "Indiquez l'adresse du serveur"
                "${XtreamSource.normalizeServer(server)}/".toHttpUrlOrNull() == null -> "Adresse du serveur invalide"
                username.isBlank() -> "Indiquez le nom d'utilisateur"
                password.isBlank() -> "Indiquez le mot de passe"
                else -> null
            }
            SourceType.M3U -> if (m3uUrl.trim().toHttpUrlOrNull() == null) "URL M3U invalide (elle doit commencer par http:// ou https://)" else null
        }
        if (error != null) return
        val defaultName = if (type == SourceType.XTREAM) "Xtream" else "Liste M3U"
        vm.saveAccount(
            Account(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { defaultName },
                type = type,
                server = if (type == SourceType.XTREAM) server.trim() else "",
                username = if (type == SourceType.XTREAM) username.trim() else "",
                password = if (type == SourceType.XTREAM) password.trim() else "",
                m3uUrl = if (type == SourceType.M3U) m3uUrl.trim() else "",
                useHls = type == SourceType.XTREAM && useHls,
            ),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 140.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(if (existing == null) "Nouveau compte" else "Modifier le compte", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice("Xtream Codes", type == SourceType.XTREAM, Modifier.focusRequester(firstFocus)) { type = SourceType.XTREAM }
            Choice("Liste M3U", type == SourceType.M3U) { type = SourceType.M3U }
        }
        Field("Nom du compte (facultatif)", name) { name = it }
        if (type == SourceType.XTREAM) {
            Field("Adresse du serveur (ex : http://exemple.com:8080)", server, KeyboardType.Uri) { server = it }
            Field("Nom d'utilisateur", username) { username = it }
            Field("Mot de passe", password, KeyboardType.Password, isPassword = true) { password = it }
            Choice(if (useHls) "Format du flux : HLS (.m3u8)" else "Format du flux : MPEG-TS (.ts) — recommandé", false) {
                useHls = !useHls
            }
        } else {
            Field("URL de la liste M3U", m3uUrl, KeyboardType.Uri) { m3uUrl = it }
        }
        error?.let { Text(it, color = Palette.Error, fontSize = 16.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Enregistrer", ::save)
            TvButton("Annuler", vm::closeEditor)
            if (existing != null) {
                TvButton(if (confirmDelete) "Confirmer la suppression" else "Supprimer", {
                    if (confirmDelete) vm.deleteAccount(existing) else confirmDelete = true
                })
            }
        }
        Text(
            "OK sur un champ pour ouvrir le clavier. Vos identifiants restent uniquement sur cet appareil.",
            fontSize = 14.sp,
            color = Palette.TextDim,
        )
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TvItem(onClick = onClick, selected = selected, modifier = modifier) {
        Text((if (selected) "● " else "○ ") + label, fontSize = 17.sp)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        modifier = Modifier.fillMaxWidth(),
    )
}
