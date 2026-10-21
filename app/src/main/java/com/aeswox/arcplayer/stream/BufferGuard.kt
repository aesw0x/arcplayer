package com.aeswox.arcplayer.stream

/**
 * Tells a cloud video that has run out of bytes ahead of it apart from one the viewer stopped on
 * purpose, and says when the download has caught up enough to try the player again.
 *
 * This exists because the download window is dragged along by play-head ticks: when the picture
 * stops, the ticking stops, and so does the only thing that asked Telegram for the next window. The
 * bytes that would restart the picture are never requested. A watchdog outside that circle has to
 * decide what it is looking at, and this is the part of that decision which can be settled without
 * a Telegram connection.
 */
object BufferGuard {

    /** Returned by "the player has never said where it was". */
    const val NEVER = Long.MIN_VALUE

    /** A play head that has not moved in this long is waiting on something, not on a quiet scene. */
    const val STALL_CONFIRM_MS = 3_000L

    /** Do not press play again until at least this much sits in front of the play head. */
    const val RECOVER_AHEAD_BYTES = 6L * 1024 * 1024

    /** How long the pump alone gets to fix it before a restart is considered. */
    const val GRACE_MS = 12_000L

    /** Spacing between restarts, and how many are tried before the wait is admitted out loud. */
    const val RESTART_SPACING_MS = 20_000L
    const val MAX_RESTARTS = 3

    /**
     * How long a play head can be owed before the media is re-opened at the position it was asked for.
     *
     * A seek only reaches the player once its bytes are on disk, so a player that then never moves is
     * stuck on the position rather than on the link. Measured on a cold resume: 12 MB sat readable past
     * the target, the position was accepted, and the picture stayed frozen for the twenty seconds the
     * mid-roll rules took to reach the same conclusion. Movement is what is asked for, not reports, so
     * a decoder running slower than real time still pays the debt. Ten seconds because that is how long
     * this file's first frame after a resume seek took on a software renderer, and a re-open spent there
     * is a re-open that threw the film back behind itself.
     */
    const val SILENT_SEEK_MS = 10_000L

    /** A film within this of its own length has genuinely finished; that is not a stall. */
    const val FINISHED_TOLERANCE_MS = 2_000L

    /**
     * How far the play head has to get past the point a restart was aimed at before the attempt
     * count is forgiven. Re-opening makes the player tick for a moment even when it then stalls
     * again, and counting that as recovery would let the cap be reset forever.
     */
    const val PROGRESS_AFTER_RESTART_MS = 2_000L

    data class Snapshot(
        val nowMs: Long,
        val lastTickMs: Long,
        /**
         * When the play head was last seen at a different moment in the film than it had been.
         *
         * This, and not the arrival of a report, is what says the picture is moving: a decoder working
         * at a fraction of real time still moves its head every second, and calling that a stall starts
         * re-opening a film that is playing.
         */
        val headMovedAtMs: Long,
        val playing: Boolean,
        val stoppedOnPurpose: Boolean,
        val seeking: Boolean,
        val watchedMs: Long,
        val durationMs: Long,
        val fileComplete: Boolean,
        /**
         * When a seek that ended in pressing play left the player owing a play head, or
         * [BufferGuard.NEVER]. A seek the viewer made while stopped owes nothing and is not timed.
         */
        val playOwedSinceMs: Long = NEVER,
    )

    /**
     * Whether the picture is quiet because the bytes are.
     *
     * A seek counts as quiet deliberately: the play head waits there by design, and calling that a
     * stall would start a restart on top of a fetch already running and cancel it.
     *
     * The stopped-and-not-playing branch is the other shape the same failure takes. The player reads
     * a hole, believes the film is over, and reports an end. Only the last frame it genuinely showed
     * tells that lie from a real ending, because the end report moves the position to the full
     * length.
     */
    fun isStalled(fact: Snapshot): Boolean {
        if (fact.stoppedOnPurpose || fact.seeking || fact.fileComplete) return false
        if (owesAPlayHead(fact)) return true
        if (fact.lastTickMs == NEVER) return false
        if (fact.nowMs - fact.headMovedAtMs < STALL_CONFIRM_MS) return false
        if (fact.playing) return true
        return fact.durationMs > 0L && fact.watchedMs < fact.durationMs - FINISHED_TOLERANCE_MS
    }

    /**
     * Whether play was pressed towards a position and the play head has not moved since.
     *
     * The debt is paid by the first movement after it was owed, so nothing else has to be tracked: a
     * player that woke up stops counting the moment it says where it is. Whether the player claims to
     * be running is deliberately not asked here — a position swallowed while the player still believes
     * it is opening reports neither a play head nor a Playing event.
     */
    fun owesAPlayHead(fact: Snapshot): Boolean =
        fact.playOwedSinceMs != NEVER && fact.headMovedAtMs <= fact.playOwedSinceMs &&
            fact.nowMs - fact.playOwedSinceMs >= SILENT_SEEK_MS

    /**
     * How long the pump gets to fix things before the media is re-opened.
     *
     * A play head that stopped mid-roll may simply be out of bytes, so asking Telegram for the next
     * window is worth the wait. An unpaid seek is not: its bytes were already on disk when the position
     * was handed over, so there is nothing left for the pump to bring in and the grace only holds the
     * viewer on one frozen frame.
     */
    fun graceMs(fact: Snapshot): Long = if (owesAPlayHead(fact)) 0L else GRACE_MS

    /**
     * Whether to hand the play head back to the player.
     *
     * A restart costs a cold open of the container, so the pump gets its grace first and the bytes
     * have to actually have arrived before either attempt. Once the attempts are spent the wait is
     * real rather than slow, and saying so beats quietly retrying forever.
     *
     * [bytesToEof] keeps the last seconds of a file recoverable. The ahead rule asks for a full
     * [RECOVER_AHEAD_BYTES] of film to restart into, and a head parked ten seconds from the end can
     * never show that much even with every remaining byte on disk — measured on device as a picture
     * frozen at 23:38 of 23:48 while the tail's checksum proved the bytes were there and nothing in
     * the app was left that could act on it. When the whole remainder is that small and all of it is
     * readable, the remainder is the same promise the six megabytes are.
     */
    fun shouldRestart(
        nowMs: Long,
        stalledSinceMs: Long,
        bytesAhead: Long,
        restarts: Int,
        lastRestartMs: Long,
        graceMs: Long = GRACE_MS,
        bytesToEof: Long = Long.MAX_VALUE
    ): Boolean {
        if (restarts >= MAX_RESTARTS) return false
        val tailIsTheWholePromise = bytesToEof <= RECOVER_AHEAD_BYTES && bytesAhead >= bytesToEof
        if (bytesAhead < RECOVER_AHEAD_BYTES && !tailIsTheWholePromise) return false
        if (nowMs - stalledSinceMs < graceMs) return false
        return restarts == 0 || nowMs - lastRestartMs >= RESTART_SPACING_MS
    }
}
