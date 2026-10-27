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
import com.aeswox.arcplayer.ui.components.AppIntro
import kotlinx.coroutines.delay

/** Elements in one arriving group, and how far apart their entrances are spaced. */
private const val STAGGERED_MAX_ITEMS = 10
private const val STAGGER_INTERVAL_MS = 40L
private const val ARRIVAL_DURATION_MS = 250
private const val ARRIVAL_RISE_PX = 20f

/**
 * Which entrances this session has already shown.
 *
 * A tab switch throws the leaving page away and builds the entering one from nothing, so an element
 * has to be told it has been seen before or it plays its entrance all over again. A plain set rather
 * than snapshot state on purpose: an element reads it exactly once, in the composition that creates
 * it, and nothing has to redraw when it grows.
 */
private val entrancesShown = HashSet<String>()

/**
 * Frosted Staggered Arrival: a group of elements does not appear all at once.
 *
 * Each one fades and rises the height of a line of text, starting [STAGGER_INTERVAL_MS] after the
 * element before it, and only the first [STAGGERED_MAX_ITEMS] are spaced at all — a rail of sixty
 * posters must not hold the last one back two seconds. Wrap a lazy item's content in this so a
 * screen assembling itself after a shelf read assembles in order rather than all in one frame.
 *
 * [arrivalKey] makes that entrance a once-per-element thing. Pass the same string the lazy list uses
 * as the item's key and the fade plays the first time that element is ever shown and never again, so
 * coming back to a tab lands on a page that is already filled rather than one refilling itself.
 */
@Composable
fun StaggeredListItem(
    index: Int,
    arrivalKey: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Every element of a page composes before any of them reaches its effect, so a first visit still
    // finds the set empty and staggers as a group. Only a return visit finds its own key already in.
    val seenBefore = arrivalKey != null && arrivalKey in entrancesShown
    var arrived by remember { mutableStateOf(seenBefore) }
    LaunchedEffect(index) {
        if (arrivalKey != null) entrancesShown.add(arrivalKey)
        // The clapperboard holds the entrance until it is off screen. Playing it under the curtain costs
        // the intro the frames it is asking for and shows the page nobody looked at.
        if (!seenBefore) AppIntro.awaitHomeRelease()
        // Only the first ten are spaced. A row scrolled into view later must not sit invisible for
        // the length of the whole stagger, which is what spacing every index would do.
        if (!seenBefore && index < STAGGERED_MAX_ITEMS) delay(index * STAGGER_INTERVAL_MS)
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
    // A slot does not shimmer while the clapperboard is still up: the band is a promise made to someone
    // who cannot see it, and every slot holding one takes a frame the intro is asking for.
    val travel = if (AppIntro.running) 0f else sweepTravel(durationMillis)
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

/** The sweep's own position, in a composable so the infinite transition only exists once it is visible. */
@Composable
private fun sweepTravel(durationMillis: Int): Float {
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
    return travel
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
 * spring: a page must not wobble at the end of a slide, only the overscroll does that. Every number
 * here — both fade lengths, the full-width travel and the stiffness — is Arc Music's tab transition
 * unchanged, which is the point: the two apps must switch pages the same way.
 */
fun jellyTabCrossSlide(forward: Boolean): Pair<EnterTransition, ExitTransition> {
    val positional = spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val enter = fadeIn(tween(200)) + slideInHorizontally(initialOffsetX = { if (forward) it else -it }, animationSpec = positional)
    val exit = fadeOut(tween(180)) + slideOutHorizontally(targetOffsetX = { if (forward) -it else it }, animationSpec = positional)
    return enter to exit
}
