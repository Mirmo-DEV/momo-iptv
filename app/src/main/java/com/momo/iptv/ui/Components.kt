package com.momo.iptv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    val Bg = Color(0xFF07090F)
    val BgTop = Color(0xFF111A2B)
    val Panel = Color(0xFF111827)
    val Surface = Color(0x1AFFFFFF)
    val Accent = Color(0xFF3D8BFF)
    val AccentDeep = Color(0xFF6D5BFF)
    val Selected = Color(0xFF1C2638)
    val Text = Color(0xFFF2F5FA)
    val TextDim = Color(0xFF8C97AB)
    val Favorite = Color(0xFFFFC94D)
    val Error = Color(0xFFFF6B6B)
    val Success = Color(0xFF4ADE80)
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

private val focusSpring = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

/** Fond de l'appli : léger dégradé bleu nuit. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.Bg, Palette.Bg))),
        content = content,
    )
}

/**
 * Image d'ambiance plein écran (affiche de l'élément sélectionné), très atténuée,
 * qui change en fondu.
 */
@Composable
fun AmbientBackdrop(url: String?, alpha: Float = 0.28f) {
    Crossfade(targetState = url, animationSpec = tween(600), label = "ambiance") { image ->
        Box(Modifier.fillMaxSize()) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                )
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(listOf(Palette.Bg, Palette.Bg.copy(alpha = 0.75f), Palette.Bg.copy(alpha = 0.35f)))),
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Bg.copy(alpha = 0.6f), Palette.Bg))))
}

/**
 * Élément sélectionnable à la télécommande : grossit légèrement et s'éclaire
 * quand il a le focus. OK = [onClick], touche Menu = [onMenu].
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
            focused -> Palette.Accent
            selected -> Palette.Selected
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "fond",
    )
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, focusSpring, label = "zoom")
    Row(
        modifier
            .onFocusChanged { state ->
                if (state.isFocused != focused) {
                    focused = state.isFocused
                    if (state.isFocused) onFocus()
                }
            }
            .menuKey(onMenu)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = { content(focused) },
    )
}

/** Bouton : blanc quand il a le focus (texte foncé), bleu s'il est principal, discret sinon. */
@Composable
fun TvButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        when {
            focused -> Color.White
            primary -> Palette.Accent
            else -> Palette.Surface
        },
        animationSpec = tween(160),
        label = "bouton",
    )
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, focusSpring, label = "zoom")
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (focused) Palette.Bg else Palette.Text)
    }
}

/** Affiche de film ou de série. Menu ≡ = ajouter/retirer de « Ma liste ». */
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
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, focusSpring, label = "zoom")
    val border by animateColorAsState(if (focused) Color.White else Color.Transparent, tween(160), label = "bord")
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
                .shadow(if (focused) 18.dp else 0.dp, RoundedCornerShape(10.dp))
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Palette.Selected, Palette.Panel)))
                .border(3.dp, border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.name, fontSize = 13.sp, textAlign = TextAlign.Center, color = Palette.TextDim, modifier = Modifier.padding(10.dp))
            if (item.poster != null) {
                AsyncImage(item.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (inWatchlist) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Palette.Accent),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            }
            if (progress != null) {
                Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 6.dp)) { ProgressLine(progress) }
            }
        }
        Text(
            item.name,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Palette.Text else Palette.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp),
        )
        val details = subtitle ?: listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString("  ·  ")
        if (details.isNotEmpty()) {
            Text(details, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
        }
    }
}

/** Vignette de chaîne (logo + programme en cours) pour les rangées de l'accueil. */
@Composable
fun ChannelCard(
    channel: Channel,
    now: Programme?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, focusSpring, label = "zoom")
    val border by animateColorAsState(if (focused) Color.White else Color.Transparent, tween(160), label = "bord")
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
                .shadow(if (focused) 18.dp else 0.dp, RoundedCornerShape(10.dp))
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Palette.Selected, Palette.Panel)))
                .border(3.dp, border, RoundedCornerShape(10.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logo != null) {
                AsyncImage(channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text(channel.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        Text(
            channel.name,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Palette.Text else Palette.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp),
        )
        if (now != null) {
            Text(now.title, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
        }
    }
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

/** Barre de progression fine et animée (programme en cours, position de reprise…). */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(400), label = "progression")
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.18f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Palette.Accent, Palette.AccentDeep))),
        )
    }
}

/** Bloc gris qui « respire » pendant un chargement. */
@Composable
fun Skeleton(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "chargement")
    val alpha by transition.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulsation")
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Selected.copy(alpha = alpha)))
}

/** Grille d'affiches fantômes pendant le chargement d'un catalogue. */
@Composable
fun PosterSkeletonGrid(columns: Int) {
    Column {
        repeat(2) {
            Row {
                repeat(columns) { index ->
                    Skeleton(Modifier.weight(1f).aspectRatio(2f / 3f))
                    if (index < columns - 1) Spacer(Modifier.width(18.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** Titre de section. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = modifier)
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
    Text(
        number.toString(),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = if (focused) Palette.Text else Palette.TextDim,
        modifier = Modifier.width(52.dp),
    )
    Box(
        Modifier
            .size(64.dp, 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.15f) else Palette.Surface)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (channel.logo != null) {
            AsyncImage(channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
    Spacer(Modifier.width(14.dp))
    Text(
        channel.name,
        fontSize = 18.sp,
        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    if (favorite) Text("★", fontSize = 18.sp, color = if (focused) Palette.Text else Palette.Favorite)
}
