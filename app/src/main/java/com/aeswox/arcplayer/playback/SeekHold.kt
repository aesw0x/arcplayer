package com.aeswox.arcplayer.playback

/**
 * A cloud seek spends tens of seconds waiting for Telegram to deliver, and the player keeps running
 * the whole time. Every tick it reports during that wait belongs to the position the play head has
 * already left, so writing them straight onto the scrubber drags it back to the old spot for the
 * length of the fetch, which looks exactly like the seek being ignored.
 *
 * A hold is armed with where the seek started and where it is going, and lets a tick through only
 * when that tick can no longer be explained by the old play head. A real arrival therefore ends the
 * hold by itself, while a stale one cannot, and no side of the player has to report completion.
 */
class SeekHold(
    private val fromMs: Long,
    val targetMs: Long,
    /**
     * Wall clock when the seek started. Only compared against the [isStale] argument, so any
     * monotonic clock works as long as both are the same one.
     */
    private val startedAtMs: Long
) {

    /**
     * Whether [timeMs] is still the old play head and must be kept off the scrubber.
     */
    fun isStale(timeMs: Long, nowMs: Long): Boolean {
        val elapsedMs = (nowMs - startedAtMs).coerceAtLeast(0L)
        if (elapsedMs >= TIMEOUT_MS) return false
        return if (targetMs >= fromMs) {
            // Walking up from the old position, at most three times real time, is the only route the
            // old head has. A tick past that reach is the player having moved, even when it sits
            // short of the target: a fast seek lands on the keyframe before it.
            timeMs < targetMs - ARRIVAL_MS &&
                timeMs <= fromMs + elapsedMs * MAX_SPEED + DRIFT_MS
        } else {
            // Behind the position the seek started from, the old head can never be, so anything it
            // reports there is the new position climbing toward the target.
            timeMs > targetMs + ARRIVAL_MS && timeMs >= fromMs - DRIFT_MS
        }
    }

    companion object {
        /** A tick this close to the target is the seek landing, not the play head still walking. */
        const val ARRIVAL_MS = 1_500L

        /** Room for the old head to disagree with exact real time before its ticks stop counting. */
        const val DRIFT_MS = 2_000L

        /** Fastest the app lets playback run, so the old head's furthest reachable tick. */
        const val MAX_SPEED = 3L

        /**
         * Longer than the cue-table fetch and the byte window fetch put together. Without it a fetch
         * that ends while paused, or a player that simply stops reporting, would leave the scrubber
         * frozen at the target for good.
         */
        const val TIMEOUT_MS = 120_000L
    }
}
