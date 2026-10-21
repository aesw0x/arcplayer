package com.aeswox.arcplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** The curve the whole panel family travels on: out fast, then settles without a bounce. */
private val PanelEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val PANEL_OPEN_MS = 400
private const val PANEL_CLOSE_MS = 350
private val PANEL_RISE = 93.5.dp
private val PANEL_BLUR = 2.dp

/**
 * Frosted Modal Panel Reveal, for the inside of a Material modal sheet.
 *
 * The sheet brings its own surface up; this is what the content does once that surface exists. It
 * rises the last [PANEL_RISE] and clears a [PANEL_BLUR] blur as it does, so the panel arrives already
 * moving rather than snapping into a finished state — and leaves the same way, a little quicker.
 *
 * The reference also crossfades between two contents over 300ms when a sheet switches what it holds.
 * Nothing here does that yet: the one sheet in the app is dismissed before another is opened, so a
 * crossfade would have nothing to crossfade.
 */
@Composable
fun FrostedModalPanel(
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val risePx = with(LocalDensity.current) { PANEL_RISE.roundToPx() }

    AnimatedVisibility(
        visible = revealed,
        enter = slideInVertically(tween(PANEL_OPEN_MS, easing = PanelEase)) { risePx } +
            fadeIn(tween(PANEL_OPEN_MS, easing = PanelEase)),
        exit = slideOutVertically(tween(PANEL_CLOSE_MS, easing = PanelEase)) { risePx } +
            fadeOut(tween(PANEL_CLOSE_MS, easing = PanelEase)),
        modifier = modifier
    ) {
        val blur by transition.animateDp(
            transitionSpec = {
                val duration = if (targetState == EnterExitState.Visible) PANEL_OPEN_MS else PANEL_CLOSE_MS
                tween(duration, easing = PanelEase)
            },
            label = "panel_blur"
        ) { state -> if (state == EnterExitState.Visible) 0.dp else PANEL_BLUR }

        Box(modifier = Modifier.fillMaxWidth().blur(blur)) { content() }
    }
}

private val DialogShape = RoundedCornerShape(28.dp)

/**
 * A dialog card that arrives instead of appearing: it rises from a quarter of the screen up with a
 * medium-bouncy spring while it fades in over 300ms, and grows in place if what it holds changes.
 *
 * The scrim stays the platform's own. Compose gives a dialog window no dim amount to animate, and
 * drawing a second one here would only stack two scrims, so the fade this owns is the card's.
 */
@Composable
fun FrostedDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        // The card has to be able to start a quarter of the screen below where it lands, which the
        // platform's content-width dialog window would clip.
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }

        val rise by animateFloatAsState(
            targetValue = if (shown) 0f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "dialog_rise"
        )
        val alpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(300),
            label = "dialog_alpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                // The window now covers the whole screen, so the platform has no "outside" left to
                // hand back as a cancel: tapping the space around the card has to dismiss it here.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Dismiss",
                    onClick = onDismissRequest
                )
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = DialogShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = modifier
                    .widthIn(max = 560.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        translationY = rise * screenHeightDp.dp.toPx() * 0.25f
                    }
                    // Swallows presses on the card itself. Without it a tap on the title would fall
                    // through to the dismiss handler sitting behind the card.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(24.dp)
                ) {
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                    }
                    if (text != null) {
                        Spacer(modifier = Modifier.height(if (title != null) 16.dp else 0.dp))
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}
