package com.aeswox.arcplayer.ui.animations

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

/** Elements in one arriving group, and how far apart their entrances are spaced. */
private const val STAGGERED_MAX_ITEMS = 10
private const val STAGGER_INTERVAL_MS = 40L
private const val ARRIVAL_DURATION_MS = 250
private const val ARRIVAL_RISE_PX = 20f

/**
 * Frosted Staggered Arrival: a group of elements does not appear all at once.
 *
 * Each one fades and rises the height of a line of text, starting [STAGGER_INTERVAL_MS] after the
 * element before it, and only the first [STAGGERED_MAX_ITEMS] are spaced at all — a rail of sixty
 * posters must not hold the last one back two seconds. Wrap a lazy item's content in this so a
 * screen assembling itself after a shelf read assembles in order rather than all in one frame.
 */
@Composable
fun StaggeredListItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        // Only the first ten are spaced. A row scrolled into view later must not sit invisible for
        // the length of the whole stagger, which is what spacing every index would do.
        if (index < STAGGERED_MAX_ITEMS) kotlinx.coroutines.delay(index * STAGGER_INTERVAL_MS)
        arrived = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = tween(ARRIVAL_DURATION_MS, easing = LinearOutSlowInEasing),
        label = "arrival_alpha"
    )
    val rise by animateFloatAsState(
        targetValue = if (arrived) 0f else ARRIVAL_RISE_PX,
        animationSpec = tween(ARRIVAL_DURATION_MS, easing = LinearOutSlowInEasing),
        label = "arrival_rise"
    )
    Box(modifier.graphicsLayer { this.alpha = alpha; translationY = rise }) { content() }
}

/**
 * Frosted Skeleton Sweep: the surface a poster will land on shimmers while it waits.
 *
 * A flat grey box reads as a finished empty slot, which is the opposite of what is happening — the
 * artwork is on its way. The diagonal band is the promise of movement.
 */
fun Modifier.jellySkeletonSweep(durationMillis: Int = 1200): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "skeleton_sweep")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "skeleton_sweep_travel"
    )
    this.background(
        Brush.linearGradient(
            // Light-on-dark: a dark sweep over a black page is the same black, so the band that
            // promises the artwork is coming has to be brighter than the surface it crosses.
            colors = listOf(
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.05f)
            ),
            start = Offset.Zero,
            end = Offset(travel, travel)
        )
    )
}

/** Placeholder art: the tinted box the poster crossfades into once it arrives. */
@Composable
fun JellySkeleton(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)).jellySkeletonSweep())
}

/**
 * Frosted Tab Cross-Slide: a tab arrives from the direction it was walked in from.
 *
 * Tapping right sends the old screen left and brings the new one in from the right, so the dock stays
 * readable as a map of the app rather than a list of unrelated screens. The movement is a no-bounce
 * spring: a page must not wobble at the end of a slide, only the overscroll does that.
 */
fun jellyTabCrossSlide(forward: Boolean): Pair<EnterTransition, ExitTransition> {
    val positional = spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val enter = fadeIn(tween(200)) + slideInHorizontally(initialOffsetX = { if (forward) it else -it }, animationSpec = positional)
    val exit = fadeOut(tween(180)) + slideOutHorizontally(targetOffsetX = { if (forward) -it else it }, animationSpec = positional)
    return enter to exit
}
