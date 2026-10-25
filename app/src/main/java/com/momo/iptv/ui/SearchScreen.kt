package com.momo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.momo.iptv.AppViewModel

/** Recherche : un champ (OK pour taper), puis les résultats en rangées. */
@Composable
fun SearchScreen(vm: AppViewModel) {
    var typing by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val wantFocus = remember { vm.consumeHomeFocus() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (wantFocus || vm.searchQuery.isEmpty()) runCatching { fieldFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 48.dp, top = 40.dp)) {
        Text("Rechercher", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        TvItem(
            onClick = { typing = true },
            modifier = Modifier.fillMaxWidth(0.6f).focusRequester(fieldFocus).background(Palette.Panel, RoundedCornerShape(8.dp)),
        ) { focused ->
            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                vm.searchQuery.ifEmpty { "Chaîne, film, série… (OK pour écrire)" },
                fontSize = 18.sp,
                fontWeight = if (vm.searchQuery.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                color = if (focused) androidx.compose.ui.graphics.Color.Black else if (vm.searchQuery.isEmpty()) Palette.TextDim else Palette.Text,
            )
            if (vm.searching) {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(Modifier.size(20.dp), color = Palette.Accent, strokeWidth = 2.dp)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 60.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (vm.searchChannels.isNotEmpty()) {
                item(key = "channels") {
                    ResultRow("Chaînes") {
                        itemsIndexed(vm.searchChannels, key = { _, c -> c.id }) { index, channel ->
                            LandscapeCard(
                                title = channel.name,
                                subtitle = null,
                                image = channel.logo,
                                progress = null,
                                fit = true,
                                onClick = { vm.play(vm.searchChannels, index) },
                                onMenu = { vm.toggleFavorite(channel) },
                                modifier = Modifier.width(200.dp),
                            )
                        }
                    }
                }
            }
            listOf("Films" to vm.searchMovies, "Séries" to vm.searchSeries).forEach { (title, items) ->
                if (items.isNotEmpty()) {
                    item(key = title) {
                        ResultRow(title) {
                            itemsIndexed(items, key = { i, item -> "${item.id}#$i" }) { _, item ->
                                PosterCard(
                                    item = item,
                                    progress = null,
                                    inWatchlist = vm.inWatchlist(item),
                                    onClick = { vm.openVod(item) },
                                    onMenu = { vm.toggleWatchlist(item) },
                                    modifier = Modifier.width(128.dp),
                                )
                            }
                        }
                    }
                }
            }
            val nothing = vm.searchQuery.length >= 2 && !vm.searching &&
                vm.searchChannels.isEmpty() && vm.searchMovies.isEmpty() && vm.searchSeries.isEmpty()
            if (nothing) {
                item(key = "empty") {
                    Text("Aucun résultat pour « ${vm.searchQuery} ».", fontSize = 18.sp, color = Palette.TextDim)
                }
            }
        }
    }

    if (typing) {
        SearchDialog(
            initial = vm.searchQuery,
            onDone = {
                typing = false
                vm.search(it)
            },
            onCancel = { typing = false },
        )
    }
}

@Composable
private fun ResultRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        SectionTitle(title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 12.dp, end = 48.dp),
            content = content,
        )
    }
}

@Composable
private fun SearchDialog(initial: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
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
            Modifier.width(720.dp).background(Palette.Panel, RoundedCornerShape(14.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Rechercher", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDone(text.text) }, onDone = { onDone(text.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvButton("Rechercher", { onDone(text.text) }, primary = true, icon = Icons.Rounded.Search)
                TvButton("Annuler", onCancel)
            }
        }
    }
}
