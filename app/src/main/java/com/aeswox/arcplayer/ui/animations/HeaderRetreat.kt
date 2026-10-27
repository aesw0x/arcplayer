package com.aeswox.arcplayer.ui.animations

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
 *
 * What a caller does with the answer is its own motion — the info page fades, shrinks and slides its
 * floating circle away through an `AnimatedVisibility`, which is the recipe the design system's
 * detail screens use.
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
