package com.momo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.momo.iptv.data.Channel

object Palette {
    val Bg = Color(0xFF0B0F17)
    val Panel = Color(0xFF151C29)
    val Accent = Color(0xFF2F7CF6)
    val Selected = Color(0xFF223047)
    val Text = Color(0xFFE8ECF3)
    val TextDim = Color(0xFF8A94A6)
    val Favorite = Color(0xFFFFC107)
    val Error = Color(0xFFFF6B6B)
}

/**
 * Élément sélectionnable à la télécommande : surligné quand il a le focus,
 * OK = [onClick], touche Menu = [onMenu].
 */
@Composable
fun TvItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onFocus: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .onFocusChanged { state ->
                if (state.isFocused != focused) {
                    focused = state.isFocused
                    if (state.isFocused) onFocus()
                }
            }
            .onPreviewKeyEvent { event ->
                if (onMenu != null && event.key == Key.Menu) {
                    if (event.type == KeyEventType.KeyUp) onMenu()
                    true
                } else {
                    false
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> Palette.Accent
                    selected -> Palette.Selected
                    else -> Color.Transparent
                },
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = { content(focused) },
    )
}

@Composable
fun TvButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Palette.Accent else Palette.Selected)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text, fontSize = 17.sp, color = Palette.Text)
    }
}

@Composable
fun RowScope.ChannelRowContent(number: Int, channel: Channel, favorite: Boolean, focused: Boolean) {
    Text(
        number.toString(),
        fontSize = 15.sp,
        color = if (focused) Palette.Text else Palette.TextDim,
        modifier = Modifier.width(52.dp),
    )
    Box(Modifier.size(64.dp, 40.dp), contentAlignment = Alignment.Center) {
        if (channel.logo != null) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    Spacer(Modifier.width(14.dp))
    Text(
        channel.name,
        fontSize = 18.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    if (favorite) Text("★", fontSize = 18.sp, color = if (focused) Palette.Text else Palette.Favorite)
}
