package com.aeswox.arcplayer.scroll

/**
 * The thresholds behind the two-stop header: where a released header settles, and how hard a
 * gesture has to be to mean something.
 *
 * Kept free of Compose so the numbers the reference app was measured against can be pinned down
 * by tests. The one rule that matters is that a single threshold governs snapping in both
 * directions: below it a release falls to the line, above it the header returns to one-handed.
 */
object HeaderFling {

    /** Past this many pixels the header counts as open; a stray subpixel is not an open header. */
    const val OPEN_PX = 0.5f

    /** The release point that decides between the line and one-handed. */
    const val SNAP_FRACTION = 0.5f

    /** Slower than this and the flick is a drift, not an intent to open or close. */
    const val MIN_FLICK_VELOCITY_PX = 220f

    /** An upward flick this fast takes the list with it instead of only closing the header. */
    const val LIST_FLING_VELOCITY_PX = 2200f

    /** An upward flick at the very top of the list this fast opens the header from the line. */
    const val OPEN_FLICK_VELOCITY_PX = 350f

    /** Below this there is nothing left to decay; the gesture was a tap. */
    const val MIN_DECAY_VELOCITY_PX = 1f

    /** Velocity surviving the whole list decay that still earns an opening header. */
    const val TAIL_EXPAND_VELOCITY_PX = 1800f

    fun isOpen(offsetPx: Float): Boolean = offsetPx > OPEN_PX

    /** Where a header released at [offsetPx] settles. */
    fun snapTargetPx(offsetPx: Float, maxOffsetPx: Float): Float =
        if (offsetPx >= maxOffsetPx * SNAP_FRACTION) maxOffsetPx else 0f
}
