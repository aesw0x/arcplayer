package com.aeswox.arcplayer.playback

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Which refresh rate a clip should ask the panel for, and whether to ask at all.
 *
 * A panel running at a rate that is not a whole multiple of the content repeats some frames and
 * drops none, which is the 2:3 pulldown a slow pan shows up as. The score below is that fractional
 * leftover per frame, so 24 fps on 60 Hz scores badly and on 120 Hz scores zero, and no table of
 * "nice" rates is needed to work it out.
 */
object FrameRateMatch {

    /** Switching panels costs a mode change, so a gain this small is not worth it. */
    const val MIN_JUDDER_GAIN = 0.02f

    /** Rates outside this are a variable-rate screen recording or a broken header, not a film. */
    const val PLAUSIBLE_MIN_FPS = 20f
    const val PLAUSIBLE_MAX_FPS = 121f

    fun contentFps(frameRateNum: Int, frameRateDen: Int): Float? {
        if (frameRateNum <= 0 || frameRateDen <= 0) return null
        val fps = frameRateNum.toFloat() / frameRateDen.toFloat()
        return if (fps >= PLAUSIBLE_MIN_FPS && fps <= PLAUSIBLE_MAX_FPS) fps else null
    }

    /**
     * Fractional frames left over per frame when showing [contentFps] at [rateHz].
     * Zero is a clean multiple; the panel is never slower than the content it can't show.
     */
    fun judderScore(rateHz: Float, contentFps: Float): Float {
        if (rateHz <= 0f || contentFps <= 0f) return Float.MAX_VALUE
        val multiple = rateHz / contentFps
        if (multiple < 0.999f) return Float.MAX_VALUE
        val nearest = multiple.roundToInt().coerceAtLeast(1).toFloat()
        return abs(multiple - nearest) / multiple
    }

    /**
     * The rate to request, or null to leave the display on whatever it chose.
     *
     * [availableHz] comes from the panel's advertised modes: one entry means there is nothing to
     * pick, and asking a fixed-rate panel for a mode it does not have is a wasted round trip.
     */
    fun targetRefreshHz(
        contentFps: Float?,
        currentHz: Float,
        availableHz: List<Float>
    ): Float? {
        if (contentFps == null) return null
        val modes = availableHz.filter { it > 0f }.distinct()
        if (modes.size < 2) return null
        val best = modes.minByOrNull { judderScore(it, contentFps) } ?: return null
        if (judderScore(best, contentFps) >= judderScore(currentHz, contentFps) - MIN_JUDDER_GAIN) {
            return null
        }
        return best
    }
}
