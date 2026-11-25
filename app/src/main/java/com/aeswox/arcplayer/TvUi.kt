package com.aeswox.arcplayer

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeswox.arcplayer.ui.components.HugeIcons

internal fun Context.isTelevision(): Boolean =
    (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType ==
        Configuration.UI_MODE_TYPE_TELEVISION

/**
 * The TV reads the same Frosted Jelly foundation as the phone: AMOLED black page, lifted
 * containers, soft white text, one muted accent. Focus is white — the phone's primary —
 * because a remote user needs the loudest possible "this is the one" mark.
 */
internal enum class TvShellMode { TELEVISION, EXPANDED_TOUCH }

internal fun expandedShellMode(maxWidth: androidx.compose.ui.unit.Dp, maxHeight: androidx.compose.ui.unit.Dp): TvShellMode? =
    if (maxWidth >= 600.dp && maxHeight >= 480.dp) TvShellMode.EXPANDED_TOUCH else null

internal object TvColors {    val Background = Color(0xFF000000)
    val Panel = Color(0xFF141414)
    val Focus = Color(0xFFFFFFFF)
    val Text = Color(0xFFEEEEEE)
    val Muted = Color(0xFFAAAAAA)
    val Accent = Color(0xFF8594B7)
    val Outline = Color(0xFF444444)
}

internal val TvPanelShape = RoundedCornerShape(28.dp)
internal val TvCardShape = RoundedCornerShape(16.dp)

/**
 * The jelly glass recipe's TV degradation: translucent black tint plus the outline-role edge,
 * the same fallback the phone draws when neither Haze nor LayerBackdrop is usable. TV sticks
 * rarely have the GPU headroom for a live blur over 4K artwork, and a blur over the player's
 * SurfaceView would sample black anyway.
 */
internal fun Modifier.tvGlass(shape: Shape = TvPanelShape, alpha: Float = 0.6f): Modifier =
    this.background(Color.Black.copy(alpha = alpha), shape)
        .border(1.dp, TvColors.Outline, shape)

/** Frosted Jelly Press, focus-driven: the remote's focus is the TV's finger. */
@Composable
internal fun Modifier.tvJellyFocus(focused: Boolean, scaleTo: Float = 1.03f): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleTo else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tv_jelly_focus",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
internal fun TvAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    val active = focused || pressed
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = presses,
        modifier = modifier.heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvJellyFocus(active && enabled),
        shape = CircleShape,
        color = when {
            !enabled -> Color.White.copy(alpha = .04f)
            active -> TvColors.Focus
            else -> Color.White.copy(alpha = .08f)
        },
        contentColor = when {
            !enabled -> TvColors.Muted.copy(alpha = .5f)
            active -> Color.Black
            else -> TvColors.Text
        },
        border = BorderStroke(2.dp, if (active && enabled) TvColors.Focus else Color.Transparent),
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    }
}

@Composable
internal fun TvChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    val active = focused || pressed
    Surface(
        onClick = onClick,
        interactionSource = presses,
        modifier = modifier.heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvJellyFocus(active, 1.05f)
            .semantics { this.selected = selected },
        shape = CircleShape,
        color = when {
            active -> TvColors.Focus
            selected -> Color.White.copy(alpha = .16f)
            else -> Color.White.copy(alpha = .06f)
        },
        contentColor = if (active) Color.Black else TvColors.Text,
        border = BorderStroke(2.dp, if (active) TvColors.Focus else Color.Transparent),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp))
    }
}

/**
 * The phone's bottom navigation stood up on its left edge: same sliding capsule, same
 * outlined-to-filled crossfade, same spring stand-up. The capsule travels to the active
 * destination instead of a disc arriving next to it.
 */
@Composable
internal fun TvRail(
    active: TvPage,
    onSelect: (TvPage) -> Unit,
    authorized: Boolean,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester? = null,
    mode: TvShellMode = TvShellMode.TELEVISION,
) {
    val cell = 56.dp
    val touch = mode == TvShellMode.EXPANDED_TOUCH
    val offset by animateDpAsState(
        targetValue = cell * TvPage.entries.indexOf(active).coerceAtLeast(0),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tv_rail_pill",
    )
    Column(
        modifier.width(if (touch) 136.dp else 84.dp).fillMaxHeight().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ARC", color = TvColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            letterSpacing = 3.sp, modifier = Modifier.padding(bottom = 18.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier.align(Alignment.TopCenter)
                    .offset { IntOffset(0, offset.roundToPx()) }
                    .width(if (touch) 120.dp else cell)
                    .height(cell)
                    .background(Color.White.copy(alpha = .09f), CircleShape)
            )
            Column(Modifier.align(Alignment.TopCenter)) {
                TvPage.entries.forEach { page ->
                    TvRailButton(
                        page = page,
                        selected = page == active,
                        onSelect = onSelect,
                        modifier = Modifier.height(cell),
                        focusRequester = if (page == active && !touch) initialFocus else null,
                        showLabel = touch,
                    )
                }
            }
        }
        Box(
            Modifier.size(8.dp).background(
                if (authorized) TvColors.Accent else TvColors.Muted.copy(alpha = .5f),
                CircleShape,
            ).semantics { contentDescription = if (authorized) "Telegram connected" else "Local library" }
        )
    }
}

@Composable
private fun TvRailButton(
    page: TvPage,
    selected: Boolean,
    onSelect: (TvPage) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    showLabel: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    val active = focused || pressed
    val (outline, filled) = when (page) {
        TvPage.HOME -> HugeIcons.Home to HugeIcons.HomeFilled
        TvPage.SEARCH -> HugeIcons.Search to HugeIcons.SearchFilled
        TvPage.LIBRARY -> HugeIcons.Library to HugeIcons.LibraryFilled
        TvPage.SETTINGS -> HugeIcons.Settings to HugeIcons.SettingsFilled
    }
    val tint by animateColorAsState(
        targetValue = if (selected || active) TvColors.Focus else TvColors.Muted,
        animationSpec = spring<Color>(stiffness = Spring.StiffnessLow),
        label = "tv_rail_tint",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tv_rail_scale",
    )
    Box(
        modifier.fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = presses, indication = null) { onSelect(page) }
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = page.label
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Crossfade(targetState = selected, animationSpec = tween(200), label = "tv_rail_icon") { showFill ->
                Icon(
                    imageVector = if (showFill) filled else outline,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                )
            }
            if (showLabel) {
                Text(page.label, color = tint, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (focused) {
            Box(Modifier.size(48.dp).border(2.dp, TvColors.Focus, CircleShape))
        }
    }
}

/** The phone's panel reveal: arrives already moving, rises the last step and clears into place. */
@Composable
internal fun TvReveal(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(400, easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "tv_reveal",
    )
    Box(modifier.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 24.dp.toPx()
    }) { content() }
}

@Composable
internal fun TvArtwork(url: String?, description: String?, modifier: Modifier = Modifier, widthPx: Int = 780) {
    val bitmap = rememberCachedArt(url, widthPx)
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = description, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(TvColors.Panel))
    }
}
