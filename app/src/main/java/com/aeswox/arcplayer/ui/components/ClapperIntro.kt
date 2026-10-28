package com.aeswox.arcplayer.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.aeswox.arcplayer.ui.haptics.AppHaptics
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.first

// ============================================================================
// THE INTRO — black, the mark draws itself on, the clapper falls, the home arrives
//
// The geometry is the launcher icon's, transcribed stroke for stroke from the same SVG, so the mark the
// intro draws and the mark sitting in the app drawer are the same drawing. Change one and the other has
// to follow or the two visibly disagree at the handover: app/src/main/res/drawable/ic_launcher_foreground.xml.
// ============================================================================

/** The 24 unit grid the icon is authored on, so every number below is the SVG's own. */
private const val StrokeUnits = 2f

/** Where the arm and the board share a point — the hinge the arm falls about. */
private val Hinge = Offset(3.49827f, 10.5f)

/**
 * Degrees the arm turns clockwise to lie flat along the board's top edge.
 *
 * The arm's lower edge runs from the hinge to (18.6616, 6.36003), which is 15.28° above horizontal, and
 * the board's top edge is horizontal at y = 10.5 — so that angle is exactly how far the arm has to come
 * down to close. The pose the launcher icon wears is the open one; this is the closed one.
 */
internal const val ClosedArmDeg = 15.28f

/** How far the arm lifts back before it falls. Without the wind-up the clap reads as a twitch. */
private const val WindUpDeg = 7f

/** The art's own middle on the 24 grid, worked out from the ink bounds stroke included. */
private val ArtCentre = Offset(11.98f, 11.95f)

/**
 * Whether the mark is still on screen.
 *
 * The home composes behind the curtain, and that is the busiest stretch in the app's life: every poster
 * that has not arrived yet shimmers and every element on the page fades and rises, both asking for a
 * frame sixty times a second over a page nobody can see yet. On the phone that competition was the whole
 * of the intro's stutter. So those two animations wait for this to clear, which also gives the home its
 * entrance after the clap rather than hidden behind it — the hero's shutter is a third thing that asks,
 * because a reveal spent under an opaque panel is a reveal nobody saw.
 */
internal object AppIntro {
    var running by mutableStateOf(true)
        internal set

    /** Suspends until the mark has gone, and returns at once for anything built after the intro. */
    suspend fun awaitHomeRelease() {
        if (!running) return
        snapshotFlow { running }.first { !it }
    }
}

/** The whole intro in milliseconds from the first frame, up to the clap, and then on the app's say-so. */
internal object IntroBeats {
    // The pen lays the strokes down in the order a hand would: the board, its slate line, the arm,
    // then the two hatches across the arm. Each window overlaps the next so there is no gap to read
    // as a stall — the mark is never not being drawn until it is finished.
    const val BoardStart = 0L
    const val BoardEnd = 520L
    const val SlateStart = 380L
    const val SlateEnd = 640L
    const val ArmStart = 140L
    const val ArmEnd = 700L
    const val HatchLeftStart = 500L
    const val HatchLeftEnd = 780L
    const val HatchRightStart = 580L
    const val HatchRightEnd = 860L

    // Everything the mark does, it does before the shelf exists. Measured on the Note: the ink draws one
    // frame every 16 ms while the home is not composed yet, and once it is, the app delivers one frame
    // per 350-650 ms for the next two seconds — so a clap scheduled after that lands as one frame, which
    // is what read as rushed.
    const val WindUpStart = 900L
    const val WindUpEnd = 1020L

    /** The instant the arm reaches the board, and the haptic. */
    const val Impact = 1115L
    const val SettleEnd = 1320L

    /**
     * The mark's own exit, and the last thing the intro animates: it recedes into the black behind it.
     *
     * This is the only frame budget the app has left, so the handover cannot be a crossfade. A fade from
     * the mark to the home was measured dissolving over three frames — the shelf was already composing —
     * which lands as a cut from a bright mark to a dark, half-built page. Fading the mark against black
     * instead spends frames that cost 16 ms, so it plays properly, and what remains is a plain cut from
     * black to a home that then does its own arriving.
     */
    const val MarkOutStart = SettleEnd + 40L
    const val MarkOutEnd = MarkOutStart + 400L

    /** A breath of empty black after the mark has gone, before anything is asked of the home. */
    private const val BlackBreath = 120L

    /** Where the shelf's own enormous cost belongs: behind a black that has nothing left to animate. */
    const val HomeBuildAt = MarkOutEnd + BlackBreath

    // The reveal is not a beat on the intro's clock at all. It is the app's answer to "is it there yet",
    // which on a cold start is not a time anyone can promise in advance.
    const val RevealMinHold = 260L
}

/**
 * Everything on screen at one moment of the intro, as a pure function of elapsed time.
 *
 * The alternative — a chain of `animateTo` calls each driving its own value — cannot be stepped to a
 * known time, and every one of these beats has to be checked against the others. With the frame derived
 * from a single clock, the whole intro is one linear animation and the numbers below are the schedule.
 */
internal class IntroFrame(
    /** How far each stroke has been drawn, 0 = untouched, 1 = complete. */
    val board: Float,
    val slate: Float,
    val arm: Float,
    val hatchLeft: Float,
    val hatchRight: Float,
    /** The arm's rotation about [Hinge] in degrees; 0 is the pose the launcher icon wears. */
    val armDeg: Float,
    /** 1 at the instant the clapper lands, decaying to nothing — the squash the board takes. */
    val jolt: Float,
    /** The mark's own presence: it lets go and recedes into the black it is drawn on. */
    val markAlpha: Float,
    /** The mark's size, shrinking with [markAlpha] so the exit reads as distance rather than as a dimming. */
    val markScale: Float,
)

/** A fraction of the way between two beats, eased. */
private fun beat(at: Long, from: Long, to: Long, ease: (Float) -> Float = FastOutSlowInEasing::transform): Float {
    if (to <= from) return if (at >= to) 1f else 0f
    val raw = (at - from).toFloat() / (to - from).toFloat()
    return ease(raw.coerceIn(0f, 1f))
}

internal fun introFrameAt(at: Long): IntroFrame {
    val t = at.coerceAtLeast(0L)
    val settle = beat(t, IntroBeats.Impact, IntroBeats.SettleEnd, EaseOut::transform)
    // The rock is the clapper bouncing back off the board: up, and home again, in one decaying arc.
    val rock = if (t < IntroBeats.Impact) 0f else sin(PI.toFloat() * settle) * (1f - settle)
    val armDeg = when {
        t < IntroBeats.WindUpStart -> 0f
        t < IntroBeats.WindUpEnd -> -WindUpDeg * beat(t, IntroBeats.WindUpStart, IntroBeats.WindUpEnd)
        t < IntroBeats.Impact -> {
            // Falling accelerates. An eased fall would decelerate into the board and land like a feather.
            val fall = beat(t, IntroBeats.WindUpEnd, IntroBeats.Impact, FastOutLinearInEasing::transform)
            -WindUpDeg + (WindUpDeg + ClosedArmDeg) * fall
        }
        else -> ClosedArmDeg - 2.2f * rock
    }
    val out = beat(t, IntroBeats.MarkOutStart, IntroBeats.MarkOutEnd, EaseInOut::transform)
    return IntroFrame(
        board = beat(t, IntroBeats.BoardStart, IntroBeats.BoardEnd),
        slate = beat(t, IntroBeats.SlateStart, IntroBeats.SlateEnd),
        arm = beat(t, IntroBeats.ArmStart, IntroBeats.ArmEnd),
        hatchLeft = beat(t, IntroBeats.HatchLeftStart, IntroBeats.HatchLeftEnd),
        hatchRight = beat(t, IntroBeats.HatchRightStart, IntroBeats.HatchRightEnd),
        armDeg = armDeg,
        jolt = if (t < IntroBeats.Impact) 0f else (1f - settle) * (1f - settle),
        markAlpha = 1f - out,
        markScale = 1f - 0.12f * out,
    )
}

// ============================================================================
// DRAWING
// ============================================================================

private val Butt = Stroke(width = StrokeUnits, cap = StrokeCap.Butt)
private val Round = Stroke(width = StrokeUnits, cap = StrokeCap.Round)

/** One stroke of the mark, and the measure that lets it be revealed a little at a time. */
private class Ink(val path: Path, val style: Stroke = Butt) {
    private val measure = PathMeasure().apply { setPath(path, false) }
    private val length = measure.length
    private val scratch = Path()

    /** The first [fraction] of this stroke's own length, as a path in the 24 unit grid. */
    fun reveal(fraction: Float): Path {
        if (fraction >= 0.999f) return path
        scratch.reset()
        measure.getSegment(0f, length * fraction.coerceIn(0f, 1f), scratch, true)
        return scratch
    }
}

private class ClapperMark {
    val board = Ink(Path().apply {
        moveTo(3.5f, 10.5f)
        lineTo(17.9118f, 10.5f)
        cubicTo(19.3676f, 10.5f, 20.0955f, 10.5f, 20.5477f, 10.9393f)
        cubicTo(21f, 11.3787f, 21f, 12.0858f, 21f, 13.5f)
        lineTo(21f, 15f)
        cubicTo(21f, 18.2998f, 21f, 19.9497f, 19.9447f, 20.9749f)
        cubicTo(18.8894f, 22f, 17.191f, 22f, 13.7941f, 22f)
        lineTo(10.7059f, 22f)
        cubicTo(7.309f, 22f, 5.61055f, 22f, 4.55528f, 20.9749f)
        cubicTo(3.5f, 19.9497f, 3.5f, 18.2998f, 3.5f, 15f)
        lineTo(3.5f, 10.5f)
        close()
    })

    val slate = Ink(
        Path().apply {
            moveTo(8f, 18f)
            lineTo(11f, 18f)
        },
        style = Round
    )

    val arm = Ink(Path().apply {
        moveTo(3.49827f, 10.5f)
        cubicTo(3.14118f, 9.14207f, 2.96264f, 8.46311f, 3.00654f, 7.86611f)
        cubicTo(3.08518f, 6.79682f, 3.63742f, 5.82221f, 4.50691f, 5.21816f)
        cubicTo(4.99236f, 4.88092f, 5.6587f, 4.69899f, 6.99138f, 4.33514f)
        lineTo(14.7321f, 2.22172f)
        cubicTo(15.0767f, 2.12763f, 15.2491f, 2.08058f, 15.3977f, 2.05386f)
        cubicTo(17.0504f, 1.75694f, 18.6737f, 2.71192f, 19.2477f, 4.31874f)
        cubicTo(19.2993f, 4.4633f, 19.3455f, 4.63888f, 19.4378f, 4.99006f)
        cubicTo(19.4642f, 5.09039f, 19.4774f, 5.14056f, 19.4849f, 5.18385f)
        cubicTo(19.5682f, 5.66498f, 19.3004f, 6.13757f, 18.8498f, 6.30467f)
        cubicTo(18.8093f, 6.3197f, 18.7601f, 6.33314f, 18.6616f, 6.36003f)
        lineTo(3.49827f, 10.5f)
        close()
    })

    val hatchLeft = Ink(Path().apply {
        moveTo(7f, 10f)
        lineTo(9f, 4f)
    })

    val hatchRight = Ink(Path().apply {
        moveTo(14f, 8f)
        lineTo(16f, 2f)
    })

    fun draw(scope: DrawScope, frame: IntroFrame, color: Color) {
        if (frame.markAlpha <= 0f) return
        // 26 units of grid in the canvas's short side: the mark's 24, plus the margin the icon keeps.
        val unit = scope.size.minDimension / 26f * frame.markScale
        // Dimming the ink is the same thing as fading it into the black it sits on, and unlike an alpha on
        // the panel it costs no layer of its own.
        val ink = color.copy(alpha = color.alpha * frame.markAlpha)
        scope.withTransform({
            translate(scope.size.width / 2f, scope.size.height / 2f)
            // The squash is about the mark's middle, so the clap compresses it rather than sliding it.
            scale(1f + 0.02f * frame.jolt, 1f - 0.025f * frame.jolt, Offset.Zero)
            scale(unit, unit, Offset.Zero)
            translate(-ArtCentre.x, -ArtCentre.y)
        }) {
            stroke(board, frame.board, ink)
            stroke(slate, frame.slate, ink)
            withTransform({ rotate(frame.armDeg, Hinge) }) {
                stroke(arm, frame.arm, ink)
                stroke(hatchLeft, frame.hatchLeft, ink)
                stroke(hatchRight, frame.hatchRight, ink)
            }
        }
    }

    private fun DrawScope.stroke(ink: Ink, fraction: Float, color: Color) {
        if (fraction <= 0f) return
        drawPath(ink.reveal(fraction), color, style = ink.style)
    }
}

/**
 * The intro itself: pure black, the mark drawn in the same white as the launcher icon.
 *
 * It is an overlay rather than a replacement for the home, and it is never transparent: the mark fades
 * against the black it stands on and the panel is then simply gone. Nothing in the app is ever behind an
 * alpha here, which matters because an ancestor alpha over the subtree that records the frosted glass is
 * exactly the case where Compose paints nothing.
 *
 * Dropping a panel of `#000000` onto a page of `#000000` is not a transition at all — it is one frame the
 * viewer cannot tell apart from the last. That is the whole of the handover's budget spent on the mark,
 * and it is what lets the home do its arriving in the open: [com.aeswox.arcplayer.ui.animations.rememberHeroArrival]
 * lifts the hero's cover off the picture the moment this panel goes, so the first thing on screen after
 * the clap is a picture coming up out of the same black the clapperboard was drawn on.
 */
@Composable
private fun ClapperIntroOverlay(clock: IntroClock) {
    // Observed here rather than in the gate, so the handover recomposes this panel and not the app.
    if (!AppIntro.running) return
    val mark = remember { ClapperMark() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(100.dp)) {
            mark.draw(this, introFrameAt(clock.at()), Color.White)
        }
    }
}

/** The intro's clock. Nothing else in the app needs to know what time it is. */
private class IntroClock {
    val elapsedMs = mutableFloatStateOf(0f)
    val building = mutableStateOf(false)

    fun at(): Long = elapsedMs.floatValue.toLong()
}

/**
 * Wraps the app in the intro and lets go of it when the app is genuinely there to be let go of.
 *
 * The home is not composed until the mark has left the screen, because the shelf's frames are the most
 * expensive in the app and the clock is wall time: frames it eats are skipped, not delayed. The reveal is
 * then a cut, on the first frame that proves the home is on screen — a dissolve was tried and the shelf
 * swallowed it, so what you saw was a hard cut in the middle of a fade.
 *
 * The clock lives in snapshot state read only inside the draw blocks, so a tick invalidates a layer rather
 * than a composition — the alternative would recompose the entire shelf sixty times a second, on the app's
 * own launch. [onFirstFrame] fires the moment the app exists, which is the caller's cue to drop the system
 * splash panel.
 */
@Composable
internal fun ClapperIntroGate(
    onFirstFrame: () -> Unit,
    content: @Composable () -> Unit,
) {
    val clock = remember { IntroClock() }

    LaunchedEffect(Unit) {
        AppIntro.running = true
        onFirstFrame()
        // The clock is set against the first frame the app actually produces, not against the tap.
        val startedAt = withFrameNanos { it }
        var builtAt = -1L
        var framesOfHome = 0
        var clapped = false
        while (true) {
            val at = withFrameNanos { (it - startedAt) / 1_000_000L }
            clock.elapsedMs.floatValue = at.toFloat()
            if (!clapped && at >= IntroBeats.Impact) {
                clapped = true
                AppHaptics.click()
            }
            if (builtAt < 0L && at >= IntroBeats.HomeBuildAt) {
                builtAt = at
                clock.building.value = true
            }
            // Two frames after the home was asked for: the first is the one that composes and draws it,
            // the second proves that frame finished.
            if (builtAt >= 0L && at > builtAt) framesOfHome++
            if (builtAt >= 0L && framesOfHome >= 2 && at - builtAt >= IntroBeats.RevealMinHold) break
        }
        AppIntro.running = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (clock.building.value) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
        ClapperIntroOverlay(clock)
    }
}
