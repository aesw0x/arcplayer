package com.aeswox.arcplayer.ui.animations

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Frosted Header Action Retreat: whether an immersive page's top actions should be standing or have
 * stepped aside.
 *
 * Direction decides, never position. The actions retire once the page has travelled [threshold] down
 * and return once it has travelled the same distance back up, so a fling that coasts past a point, or
 * a snap releasing, cannot bring them back on its own. The reading is an accumulator rather than an
 * instantaneous one, which is what stops the sub-pixel jitter at the tail of a fling from strobing
 * the row.
 *
 * The overscroll bounce cannot reach this: it moves the surface through a layer, not the scroll
 * offset, so a jiggle at the top of the page is not mistaken for travel.
 */
@Composable
fun rememberHeaderActionsVisible(
    scrollState: ScrollState,
    threshold: Dp = 24.dp
): Boolean {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val visible = remember { mutableStateOf(true) }

    LaunchedEffect(scrollState, thresholdPx) {
        var previous = scrollState.value
        var drift = 0f
        snapshotFlow { scrollState.value }.collect { current ->
            drift += (current - previous).toFloat()
            previous = current
            if (abs(drift) >= thresholdPx) {
                visible.value = drift < 0f
                drift = 0f
            }
        }
    }
    return visible.value
}

/**
 * Steps a header action aside: it fades, shrinks to 0.7 of itself and travels half its own width
 * outward, on the same medium-bouncy spring the jelly press uses.
 *
 * It is a layer rather than a visibility change on purpose. Removing the button from the row would
 * drag the title next to it across the screen every time the viewer scrolls, and the title is the one
 * thing in that bar that is meant to arrive, not move.
 *
 * @param outward which way is off-screen for this action: true for a leading button, false for a
 * trailing one.
 */
fun Modifier.headerActionRetreat(standing: Boolean, outward: Boolean = true): Modifier = composed {
    val retreat by animateFloatAsState(
        targetValue = if (standing) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "header_retreat"
    )
    val direction = if (outward) -1f else 1f

    this.graphicsLayer {
        // The spring overshoots, which is the bounce the caller asked for — but alpha has nowhere to
        // overshoot to, so it is clamped while scale and travel are left to ring.
        alpha = (1f - retreat).coerceIn(0f, 1f)
        val scale = 1f - 0.3f * retreat
        scaleX = scale
        scaleY = scale
        translationX = direction * size.width * 0.5f * retreat
    }
}
