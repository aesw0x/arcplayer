package com.aeswox.arcplayer.ui.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The spring the [physicsBounceOverscroll] jiggle is built from, in physics units rather than pixels.
 *
 * A forced damped harmonic oscillator: mass and stiffness set how fast it wants to return, the
 * damping ratio sets how many times it swings past the resting place, and gravity is the pull the
 * amplitude is derived from. Every screen in the app reads the same values out of
 * [LocalJigglePhysicsSettings] so the Home rails and a copies sheet stop wobbling together.
 */
data class JigglePhysicsSettings(
    val mass: Float = 0.2f,
    val stiffness: Float = 100.0f,
    val dampingRatio: Float = 0.25f,
    val amplitudeMultiplier: Float = 1.0f,
    val gravity: Float = 9.81f
)

val LocalJigglePhysicsSettings = staticCompositionLocalOf { JigglePhysicsSettings() }

/** Drive frequency of the jiggle: three full swings across the 1.2s release window. */
private const val DRIVE_FREQUENCY_HZ = 2.5f

/** The window the oscillator is allowed to run in, long enough to carry three swings. */
private const val BOUNCE_DURATION_NS = 1_200_000_000L

/** How far the content gives per pixel of overscroll, and the point past which it stops giving. */
private const val STRETCH_GRIP = 0.4f
private const val STRETCH_LIMIT_PX = 200f

/** Velocity under this is a lifted finger rather than a flick, so it must not launch a swing. */
private const val FLING_VELOCITY_MIN = 500f

/**
 * Frosted Jelly Physics Bounce: the whole surface stretches against a bound and springs back
 * through it, instead of the platform glow or a hard stop.
 *
 * A scrollable that has reached its end hands its leftover scroll to this connection, which spends
 * it on moving the content sideways or downward rather than dropping it, with soft resistance so the
 * further it has given the less each extra pixel costs. When the finger leaves, the displacement
 * becomes the initial condition of a damped oscillator and the surface swings back through rest.
 *
 * Apply it to the scrollable itself (`LazyColumn`, `LazyRow`, `LazyVerticalGrid`, a `verticalScroll`
 * column) — the modifier sits outside that node, so it sees the scroll it cannot consume.
 *
 * Pass [displacement] when a sibling layer outside the scrollable must know how far the surface is
 * stretched — the Home hero cover rides it so the page colour meets the hero edge mid-stretch.
 */
fun Modifier.physicsBounceOverscroll(isHorizontal: Boolean = false, displacement: Animatable<Float, *>? = null): Modifier = composed {
    val settings = LocalJigglePhysicsSettings.current
    val scope = rememberCoroutineScope()
    val displacementAnim = displacement ?: remember { Animatable(0f) }

    val omega0 = sqrt(settings.stiffness / settings.mass)
    val naturalFrequency = omega0 / (2 * Math.PI).toFloat()
    val staticDisplacement = settings.mass * settings.gravity / settings.stiffness
    val frequencyRatio = DRIVE_FREQUENCY_HZ / naturalFrequency
    val detuning = 1f - frequencyRatio * frequencyRatio
    val damping = 2f * settings.dampingRatio * frequencyRatio
    val amplitude = staticDisplacement / sqrt(detuning * detuning + damping * damping)
    val decayRate = settings.dampingRatio * omega0
    val drivePulse = (2 * Math.PI * DRIVE_FREQUENCY_HZ).toFloat()

    val connection = remember(scope, isHorizontal, settings, displacementAnim) {
        object : NestedScrollConnection {
            var swing: Job? = null

            /**
             * A fling is [NestedScrollSource.SideEffect] in the Compose this app builds against —
             * `Fling` is an alias of it, and `Drag` is an alias of `UserInput` — so this one
             * comparison separates a finger from momentum and from a programmatic scroll.
             */
            private fun isFinger(source: NestedScrollSource) = source != NestedScrollSource.SideEffect

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isFinger(source)) return Offset.Zero
                val current = displacementAnim.value
                if (current == 0f) return Offset.Zero
                // Pulling back towards rest is charged to the stretch first, so the surface unwinds
                // under the finger 1:1 with the grip instead of snapping flat the moment it reverses.
                val along = if (isHorizontal) available.x else available.y
                if (along == 0f || sign(current) == sign(along)) return Offset.Zero
                val proposed = current + along * STRETCH_GRIP
                val crossedRest = sign(proposed) != sign(current)
                swing?.cancel()
                swing = scope.launch { displacementAnim.snapTo(if (crossedRest) 0f else proposed) }
                return if (crossedRest) {
                    if (isHorizontal) Offset(-current / STRETCH_GRIP, 0f) else Offset(0f, -current / STRETCH_GRIP)
                } else {
                    if (isHorizontal) Offset(along, 0f) else Offset(0f, along)
                }
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Never stretch frame by frame during a fling: that steals the momentum that the
                // release swing is supposed to be built from. Hand it to onPostFling whole.
                if (!isFinger(source)) return Offset.Zero
                val leftover = if (isHorizontal) available.x else available.y
                if (leftover == 0f) return Offset.Zero
                val current = displacementAnim.value
                val resistance = (1f - abs(current) / STRETCH_LIMIT_PX).coerceIn(0.05f, STRETCH_GRIP)
                swing?.cancel()
                swing = scope.launch { displacementAnim.snapTo(current + leftover * resistance) }
                return if (isHorizontal) Offset(available.x, 0f) else Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                swing?.cancel()
                val leftoverVelocity = if (isHorizontal) available.x else available.y
                val releasedFrom = displacementAnim.value
                if (abs(leftoverVelocity) == 0f && abs(releasedFrom) == 0f) return Velocity.Zero

                // Reaching a bound with speed left carries on swinging in that direction; a drag
                // merely lets go of a stretched surface, and the spring pulls it back the other way.
                val reachedBound = abs(releasedFrom) < 1f && abs(leftoverVelocity) > 0f
                val swung = reachedBound || abs(leftoverVelocity) > FLING_VELOCITY_MIN
                val direction = if (swung) sign(leftoverVelocity) else -sign(releasedFrom)
                val force = if (swung) {
                    (abs(leftoverVelocity) / 3000f).coerceIn(0.05f, 1f)
                } else {
                    (abs(releasedFrom) / 100f).coerceIn(0.1f, 1f)
                }
                val pixelScale = 3000f * settings.amplitudeMultiplier
                val startedAt = withFrameNanos { it }

                try {
                    while (true) {
                        val elapsed = withFrameNanos { it } - startedAt
                        if (elapsed > BOUNCE_DURATION_NS) {
                            displacementAnim.snapTo(0f)
                            break
                        }
                        val t = elapsed / 1_000_000_000f
                        val decay = exp(-decayRate * t)
                        // The swing that starts from rest plus the stretched surface falling back.
                        val seconds = t
                        val swingPx = force * direction * pixelScale * amplitude * decay * sin(drivePulse * seconds)
                        val settlePx = releasedFrom * decay * cos(drivePulse * seconds)
                        displacementAnim.snapTo(swingPx + settlePx)
                    }
                } catch (intercepted: CancellationException) {
                    // A finger that lands mid-swing takes the surface from where it stands, so the
                    // displacement is left as it is rather than snapped flat.
                    throw intercepted
                }
                return if (isHorizontal) Velocity(leftoverVelocity, 0f) else Velocity(0f, leftoverVelocity)
            }
        }
    }

    this.nestedScroll(connection).graphicsLayer {
        if (isHorizontal) translationX = displacementAnim.value else translationY = displacementAnim.value
    }
}
