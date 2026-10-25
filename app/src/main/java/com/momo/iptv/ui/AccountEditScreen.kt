package com.momo.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.momo.iptv.AppViewModel
import com.momo.iptv.data.Account
import com.momo.iptv.data.SourceType
import com.momo.iptv.data.StalkerClient
import com.momo.iptv.data.XtreamSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

private const val MAC_PREFIX = "00:1A:79:"

@Composable
fun AccountEditScreen(vm: AppViewModel, existing: Account?) {
    var type by remember { mutableStateOf(existing?.type ?: SourceType.XTREAM) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var server by remember { mutableStateOf(existing?.server ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var m3uUrl by remember { mutableStateOf(existing?.m3uUrl ?: "") }
    var mac by remember { mutableStateOf(existing?.mac?.ifEmpty { null } ?: MAC_PREFIX) }
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
        // Adresse Xtream collée sous forme d'URL complète : on récupère identifiant et mot de passe.
        if (type == SourceType.XTREAM) {
            val parsed = XtreamSource.parseServer(server)
            if (username.isBlank()) parsed.username?.let { username = it }
            if (password.isBlank()) parsed.password?.let { password = it }
        }
        error = when (type) {
            SourceType.XTREAM -> when {
                server.isBlank() -> "Indiquez l'adresse du serveur"
                "${XtreamSource.parseServer(server).base}/".toHttpUrlOrNull() == null -> "Adresse du serveur invalide"
                username.isBlank() -> "Indiquez le nom d'utilisateur"
                password.isBlank() -> "Indiquez le mot de passe"
                else -> null
            }
            SourceType.M3U ->
                if (m3uUrl.trim().toHttpUrlOrNull() == null) "URL M3U invalide (elle doit commencer par http:// ou https://)" else null
            SourceType.STALKER -> when {
                server.isBlank() -> "Indiquez l'adresse du portail"
                !StalkerClient.isValidMac(mac) -> "Adresse MAC invalide (format 00:1A:79:XX:XX:XX)"
                else -> null
            }
        }
        if (error != null) return
        val defaultNames = mapOf(SourceType.XTREAM to "Xtream", SourceType.M3U to "Liste M3U", SourceType.STALKER to "Portail Stalker")
        val defaultName = defaultNames.getValue(type)
        // Un nom donné automatiquement suit le type du compte.
        if (name.trim() in defaultNames.values) name = ""
        val usesServer = type == SourceType.XTREAM || type == SourceType.STALKER
        vm.saveAccount(
            Account(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { defaultName },
                type = type,
                server = if (usesServer) server.trim() else "",
                username = if (type == SourceType.XTREAM) username.trim() else "",
                password = if (type == SourceType.XTREAM) password.trim() else "",
                m3uUrl = if (type == SourceType.M3U) m3uUrl.trim() else "",
                useHls = type == SourceType.XTREAM && useHls,
                mac = if (type == SourceType.STALKER) StalkerClient.normalizeMac(mac) else "",
            ),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 140.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (existing == null) "Nouveau compte" else "Modifier le compte", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice("Xtream Codes", type == SourceType.XTREAM, Modifier.focusRequester(firstFocus)) { type = SourceType.XTREAM }
            Choice("Liste M3U", type == SourceType.M3U) { type = SourceType.M3U }
            Choice("Portail Stalker (MAC)", type == SourceType.STALKER) { type = SourceType.STALKER }
        }
        FieldRow("Nom du compte (facultatif)", name) { name = it }
        when (type) {
            SourceType.XTREAM -> {
                FieldRow("Adresse du serveur (ex : http://exemple.com:8080)", server, KeyboardType.Uri) { server = it }
                FieldRow("Nom d'utilisateur", username) { username = it }
                FieldRow("Mot de passe", password, hidden = true) { password = it }
                Choice(if (useHls) "Format du flux : HLS (.m3u8)" else "Format du flux : MPEG-TS (.ts) — recommandé", false) {
                    useHls = !useHls
                }
            }
            SourceType.M3U -> FieldRow("URL de la liste M3U", m3uUrl, KeyboardType.Uri) { m3uUrl = it }
            SourceType.STALKER -> {
                FieldRow("Adresse du portail (ex : http://exemple.com:8080/c/)", server, KeyboardType.Uri) { server = it }
                FieldRow("Adresse MAC (ex : 00:1A:79:12:34:56)", mac) { mac = it.uppercase() }
            }
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
            "▲▼ pour choisir un champ, OK pour le remplir. Vos identifiants restent uniquement sur cet appareil.",
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

/**
 * Champ affiché comme une ligne ordinaire : le focus peut passer dessus sans rien ouvrir.
 * OK ouvre une fenêtre de saisie avec le clavier.
 */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    hidden: Boolean = false,
    onChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    TvItem(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { focused ->
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = if (focused) Color.Black.copy(alpha = 0.65f) else Palette.TextDim)
            Text(
                when {
                    value.isEmpty() -> "Appuyez sur OK pour saisir"
                    hidden -> "•".repeat(value.length)
                    else -> value
                },
                fontSize = 19.sp,
                color = if (value.isEmpty() && !focused) Palette.TextDim else if (focused) Color.Black else Palette.Text,
            )
        }
        Text("✎", fontSize = 18.sp)
    }
    if (editing) {
        TextEntryDialog(
            label = label,
            initial = value,
            keyboardType = keyboardType,
            onDone = {
                onChange(it)
                editing = false
            },
            onCancel = { editing = false },
        )
    }
}

@Composable
private fun TextEntryDialog(
    label: String,
    initial: String,
    keyboardType: KeyboardType,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    Dialog(onDismissRequest = onCancel) {
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
        Column(
            Modifier
                .width(760.dp)
                .background(Palette.Panel, RoundedCornerShape(16.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(label, fontSize = 20.sp, color = Palette.Text)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(text.text.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Valider", { onDone(text.text.trim()) })
                TvButton("Annuler", onCancel)
            }
        }
    }
}
