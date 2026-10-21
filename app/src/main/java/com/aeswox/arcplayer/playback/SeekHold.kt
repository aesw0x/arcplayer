package com.aeswox.arcplayer.playback

/**
 * A cloud seek spends tens of seconds waiting for Telegram to deliver, and the player keeps running
 * the whole time. Every tick it reports during that wait belongs to the position the play head has
 * already left, so writing them straight onto the scrubber drags it back to the old spot for the
 * length of the fetch, which looks exactly like the seek being ignored.
 *
 * A hold is armed with where the seek started and where it is going, and lets a tick through only
 * when that tick can no longer be explained by the old play head. A real arrival therefore passes
 * while a stale one cannot, and no side of the player has to report completion.
 *
 * An arrival does not retire the hold, because the queue behind it is not empty. libVLC reports the
 * position it was sent to and then delivers whatever was already waiting, so the tick that lands a
 * seek can be followed by one from the position the seek left — measured on a cold 1.9 GB film as a
 * report from 3:34 arriving 4.6 s after the head had landed at 1:57:00. Believed, it writes the old
 * position back onto the scrubber and re-aims the one download window at bytes nobody is watching,
 * which is a seek that appears to have been refused. Only [isExpired] ends a hold.
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
     * Whether the clock has run this hold down, after which the player's reports are believed again
     * for good. Separate from [isStale] because that one also answers no for a tick that has arrived,
     * and an arrival is not the end of the queue behind it.
     */
    fun isExpired(nowMs: Long): Boolean =
        (nowMs - startedAtMs).coerceAtLeast(0L) >= TIMEOUT_MS

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
            // The mirror of the branch above, and it has to be measured against the clock the same
            // way. The old head is at or above where the seek started, so anything below that is the
            // new position; but a short backward hop is walked back over by real playback within
            // seconds, and a hop's own target is no defence. Ten seconds back, the play head reaches
            // the old position ten seconds later, so the only thing that separates that tick from a
            // queued one is how far the new head could have got by now.
            timeMs >= fromMs - DRIFT_MS &&
                timeMs > targetMs + elapsedMs * MAX_SPEED + DRIFT_MS
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
