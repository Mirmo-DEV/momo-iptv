package com.momo.iptv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.momo.iptv.R
import com.momo.iptv.data.Channel
import com.momo.iptv.data.Programme
import com.momo.iptv.data.VodItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Palette {
    val Bg = Color(0xFF000000)
    val BgSoft = Color(0xFF0E0E10)
    val Panel = Color(0xFF16161A)
    val Surface = Color(0x26FFFFFF)
    val Accent = Color(0xFFE50914)
    val Selected = Color(0xFF242428)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFA3A3A8)
    val Favorite = Color(0xFFF5C518)
    val Error = Color(0xFFFF6B6B)
    val Success = Color(0xFF46D369)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** Toute l'appli utilise Inter. */
val MomoTypography: Typography = Typography().let { t ->
    fun TextStyle.inter() = copy(fontFamily = Inter)
    Typography(
        displayLarge = t.displayLarge.inter(), displayMedium = t.displayMedium.inter(), displaySmall = t.displaySmall.inter(),
        headlineLarge = t.headlineLarge.inter(), headlineMedium = t.headlineMedium.inter(), headlineSmall = t.headlineSmall.inter(),
        titleLarge = t.titleLarge.inter(), titleMedium = t.titleMedium.inter(), titleSmall = t.titleSmall.inter(),
        bodyLarge = t.bodyLarge.inter(), bodyMedium = t.bodyMedium.inter(), bodySmall = t.bodySmall.inter(),
        labelLarge = t.labelLarge.inter(), labelMedium = t.labelMedium.inter(), labelSmall = t.labelSmall.inter(),
    )
}

/** Animation de focus courte et nette : fluide même sur une Fire Stick. */
private val focusTween = tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)

@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Bg), content = content)
}

/**
 * Élément de liste sélectionnable : s'éclaire quand il a le focus.
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
    val background by animateColorAsState(
        when {
            focused -> Color.White
            selected -> Palette.Selected
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "fond",
    )
    Row(
        modifier
            .onFocusChanged { state ->
                if (state.isFocused != focused) {
                    focused = state.isFocused
                    if (state.isFocused) onFocus()
                }
            }
            .menuKey(onMenu)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Texte noir sur fond blanc quand l'élément a le focus.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (focused) Color.Black else Palette.Text,
        ) {
            content(focused)
        }
    }
}

/** Bouton arrondi : blanc avec texte noir quand il a le focus. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        when {
            focused -> Color.White
            primary -> Color.White.copy(alpha = 0.9f)
            else -> Palette.Surface
        },
        animationSpec = tween(150),
        label = "bouton",
    )
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, focusTween, label = "zoom")
    val content = if (focused || primary) Color.Black else Palette.Text
    Row(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

/** Cadre commun des cartes : zoom + contour blanc au focus. */
@Composable
internal fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier,
    aspectRatio: Float,
    onFocus: () -> Unit,
    onMenu: (() -> Unit)?,
    below: @Composable (focused: Boolean) -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, focusTween, label = "zoom")
    Column(
        modifier
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
            }
            .menuKey(onMenu)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.Panel)
                .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { content(focused) }
        below(focused)
    }
}

/** Affiche de film ou de série (format portrait). Menu ≡ = ajouter/retirer de « Ma liste ». */
@Composable
fun PosterCard(
    item: VodItem,
    progress: Float?,
    inWatchlist: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
    subtitle: String? = null,
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier,
        aspectRatio = 2f / 3f,
        onFocus = onFocus,
        onMenu = onMenu,
        below = { focused ->
            Text(
                item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (focused) Palette.Text else Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    ) {
        Text(item.name, fontSize = 13.sp, textAlign = TextAlign.Center, color = Palette.TextDim, modifier = Modifier.padding(10.dp))
        if (item.poster != null) AsyncImage(item.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (inWatchlist) CornerCheck(Modifier.align(Alignment.TopEnd))
        if (progress != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 6.dp)) { ProgressLine(progress) }
        }
    }
}

/** Carte paysage (« Continuer à regarder ») : image, titre par-dessus et progression. */
@Composable
fun LandscapeCard(
    title: String,
    subtitle: String?,
    image: String?,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
    fit: Boolean = false,
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier,
        aspectRatio = 16f / 9f,
        onFocus = onFocus,
        onMenu = onMenu,
        below = { focused ->
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (focused) Palette.Text else Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Palette.TextDim, modifier = Modifier.padding(12.dp))
        if (image != null) {
            AsyncImage(
                image,
                null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (fit) Modifier.padding(18.dp) else Modifier),
            )
        }
        if (progress != null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 8.dp)) { ProgressLine(progress) }
        }
    }
}

@Composable
private fun CornerCheck(modifier: Modifier) {
    Box(
        modifier.padding(6.dp).size(24.dp).clip(CircleShape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) { Text("✓", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold) }
}

/** Touche Menu ≡ : action secondaire (favori, Ma liste…). */
fun Modifier.menuKey(onMenu: (() -> Unit)?): Modifier =
    if (onMenu == null) this
    else onPreviewKeyEvent { event ->
        if (event.key == Key.Menu) {
            if (event.type == KeyEventType.KeyUp) onMenu()
            true
        } else {
            false
        }
    }

/** Barre de progression fine (programme en cours, position de reprise…). */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Palette.Accent))
    }
}

/** Bloc gris qui « respire » pendant un chargement. */
@Composable
fun Skeleton(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "chargement")
    val alpha by transition.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulsation")
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Palette.Selected.copy(alpha = alpha)))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = modifier)
}

@Composable
fun MetaChips(values: List<String>, rating: String?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        rating?.let {
            Text(
                "★ $it",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.Favorite).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        values.forEach { value ->
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Palette.Text.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun Loading(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Palette.Accent)
            Spacer(Modifier.height(16.dp))
            Text(text, color = Palette.TextDim)
        }
    }
}

@Composable
internal fun Retry(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = Palette.Error, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        TvButton("Réessayer", onRetry)
    }
}

fun timeRange(p: Programme): String = "${clock(p.start)}–${clock(p.end)}"

private fun clock(millis: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(millis))

/** 1:05:09 ou 5:09. */
fun formatDuration(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun RowScope.ChannelRowContent(number: Int, channel: Channel, favorite: Boolean, focused: Boolean) {
    val dim = if (focused) Color.Black.copy(alpha = 0.6f) else Palette.TextDim
    Text(number.toString(), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = dim, modifier = Modifier.width(52.dp))
    Box(
        Modifier
            .size(64.dp, 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.Black.copy(alpha = 0.08f) else Palette.Surface)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (channel.logo != null) AsyncImage(channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
    Spacer(Modifier.width(14.dp))
    Text(
        channel.name,
        fontSize = 18.sp,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    if (favorite) Text("★", fontSize = 18.sp, color = if (focused) Color.Black else Palette.Favorite)
}
