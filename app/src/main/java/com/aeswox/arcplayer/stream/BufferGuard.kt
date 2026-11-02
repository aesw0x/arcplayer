package com.aeswox.arcplayer.stream

import com.aeswox.arcplayer.playback.NextUp

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
     * How far past the position a restart was aimed at the play head has to get before that aim counts
     * as recovered, and how close a new stall has to land to be the same fault rather than a fresh one.
     *
     * One number does both jobs because they are the same question: did the film get clear of the spot
     * it died in? Two seconds was the old answer, and it is what made a stall loop — re-opening at the
     * failing byte plays a few seconds before the same byte fails again, which forgave the attempt count
     * every time and let three restarts become thirty. Measured on the phone as three rounds of "stops,
     * buffers, resumes a few seconds behind, stops where it did before" on one film in one sitting.
     */
    const val FAULT_CLEARED_MS = 30_000L

    /**
     * How far past a fault the media is aimed once it has already killed the player there.
     *
     * A stall with film on disk in front of it is not the link, and a re-open that hands the same byte
     * back asks the same question of the same player. Thirty seconds is roughly one skipped scene:
     * enough to leave a region the decoder cannot read behind, short enough that a viewer who comes
     * back to it later has not lost the plot.
     */
    const val STEP_PAST_FAULT_MS = 30_000L

    /**
     * How long a stall can sit with nothing settling onto disk before the link is admitted rather than
     * waited on. Restarts are gated on bytes that have actually landed, so a dead link never reaches
     * the attempt cap and the wait would otherwise be silent forever.
     */
    const val LINK_GIVE_UP_MS = 90_000L

    /**
     * How long the player's own "I have run out of file" gets to be a window still filling before it is
     * said out loud.
     *
     * A hole is more often bytes that have not arrived yet than bytes that never will, and the stall pump
     * aims the window at the position the play head died on within a second of the stall. Six seconds is
     * a few rounds of that: long enough for a link that is merely slow to clear the claim before the app
     * makes it, short enough that nobody has to sit out the minute and a half the link's own give-up
     * waits for before the screen admits the film is not coming.
     */
    const val STARVED_SPEECH_MS = 6_000L

    /**
     * How far past real time one report may carry the play head and still be a moment that was watched.
     *
     * Jitter belongs in here rather than in the rate: two reports a tenth of a second apart, which is
     * what a decoder resync looks like, legitimately differ by more than a strict multiple allows.
     */
    const val TICK_JUMP_SLACK_MS = 2_000L

    /**
     * How far past the position a seek was aimed at one report may sit and still be that seek landing.
     *
     * libVLC comes back from a seek at the keyframe before its target, so the report that pays a seek's
     * debt is at or short of where the head was sent. Anything meaningfully past the aim did not get
     * there by being sent there — and a cloud file's empty space is precisely the thing that carries a
     * head past an aim in a single report, which is how a re-open aimed at nine tenths of a part came
     * back reporting its full length.
     */
    const val SEEK_LANDING_SLACK_MS = 2_000L

    /**
     * Whether a play-head report moved further than the film's own clock could have carried it.
     *
     * The head can only be believed as watched when it travelled to a position at about the speed of
     * the film. A hole is read at however fast the demuxer can run through empty space, and a cloud
     * file is stretched on disk to the length the shelf promised, so the far end of it is reached in a
     * few milliseconds. Three things turn on that one fact: the resume point, which otherwise marks a
     * part with most of it missing as finished; the next-up offer, which otherwise hands the viewer to
     * the following episode; and this guard's own [isStalled], which reads a head parked at the length
     * as a film that genuinely ended, and lets a black screen say nothing at all.
     *
     * [isLandingFromASeek] excuses a report because the viewer rather than the file is what moved the
     * head there, and a first report is excused because there is no previous one to measure a jump
     * against. Neither exemption is open-ended: a seek only excuses the position it was aimed at, which
     * is [previousPositionMs] in that moment because the target is written onto the head before the
     * player is handed it, so an exemption bought by a seek cannot also pay for a leap the seek never
     * asked for.
     *
     * [leaptToMs] is where the last refused jump left the head, or [NEVER] when no jump is standing. A
     * refused position has to stay refused until the head is seen somewhere else, because the file going
     * quiet at the far end of a hole reports that same position again a second later — and measured
     * against the report before it, a head parked on one spot travels nothing at all. Believed on the
     * strength of that, the second report is the one that starts the end-band count, spends the dwell an
     * honest ending asks for, and turns a missing tail into a finished part.
     */
    fun hasOutrunTheFilm(
        positionMs: Long,
        previousPositionMs: Long,
        sinceTickMs: Long,
        isLandingFromASeek: Boolean,
        leaptToMs: Long = NEVER
    ): Boolean {
        if (leaptToMs != NEVER && positionMs == leaptToMs) return true
        if (isLandingFromASeek) return positionMs - previousPositionMs > SEEK_LANDING_SLACK_MS
        if (sinceTickMs <= 0L) return false
        return positionMs - previousPositionMs > sinceTickMs * NextUp.MAX_END_TRAVEL_RATE + TICK_JUMP_SLACK_MS
    }

    data class Snapshot(
        val nowMs: Long,
        /** When the player last reported a position, or [BufferGuard.NEVER] before it ever said one. */
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
        /** Whether the player has ever put a frame on screen. A black player is not a quiet one. */
        val firstFrameRendered: Boolean = true,
    )

    /**
     * How long ago a stamp was stamped, with [NEVER] answered as the oldest possible age rather than as
     * a subtraction.
     *
     * The sentinel is [Long.MIN_VALUE] so that `now - stamp` wraps past zero and comes back a large
     * negative number, which every "is this gap long enough" comparison reads as "it just happened". A
     * player that has never said where it is then looks freshly alive forever — and a film that never
     * got its first frame is exactly the case where nothing has ever been reported. Measured on device
     * as a `tickedAgo=-9223372036851414642` in the stall line of a black player.
     */
    private fun ageMs(nowMs: Long, stamp: Long): Long =
        if (stamp == NEVER) Long.MAX_VALUE else nowMs - stamp

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
     *
     * A player that has never reported a position at all used to be excused here outright, on the
     * reasoning that no play head means nothing to measure. That is the one case the whole rule exists
     * for: the container opened, no frame ever appeared, and nothing was left asking Telegram for the
     * bytes that would have produced one. It is timed as a silence now, but only from a black screen —
     * a first frame with no ticks is a film the viewer stopped on deliberately — and only after an open
     * is overdue, because a container that takes ten seconds to parse has always looked like this.
     */
    fun isStalled(fact: Snapshot): Boolean {
        if (fact.stoppedOnPurpose || fact.seeking || fact.fileComplete) return false
        if (owesAPlayHead(fact)) return true
        if (fact.lastTickMs == NEVER) {
            return !fact.firstFrameRendered && ageMs(fact.nowMs, fact.headMovedAtMs) >= SILENT_SEEK_MS
        }
        if (ageMs(fact.nowMs, fact.headMovedAtMs) < STALL_CONFIRM_MS) return false
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
     * A restart costs a cold open of the container, so the pump gets its grace first and [bytesAhead] has
     * to be film that has actually landed — a re-open pointed at bytes that are still in flight reads the
     * hole they leave behind and reports it as the end of the movie. Once the attempts are spent the wait
     * is real rather than slow, and saying so beats quietly retrying forever.
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

    /**
     * Where to hand the play head back to the player.
     *
     * [lastAimMs] is where the previous restart was aimed, or -1 when nothing has been restarted. A stall
     * that lands on the same spot the last re-open was aimed at says the re-open asked the same question
     * and got the same answer, so this aims past it instead of back into it — otherwise the recovery is
     * one repeat of the failure with a spinner in between.
     *
     * The step is refused near the end of the film: there is nothing to skip into, and the tail is already
     * recoverable on the bytes it has.
     */
    fun restartTargetMs(stalledAtMs: Long, lastAimMs: Long, durationMs: Long): Long {
        if (lastAimMs < 0L || kotlin.math.abs(stalledAtMs - lastAimMs) > FAULT_CLEARED_MS) return stalledAtMs
        val pastTheFault = lastAimMs + STEP_PAST_FAULT_MS
        if (durationMs > 1L && pastTheFault > durationMs - FINISHED_TOLERANCE_MS) return stalledAtMs
        return maxOf(pastTheFault, stalledAtMs)
    }

    /**
     * Whether the wait is on bytes that are not coming rather than on a player that needs a push.
     *
     * [settledAhead] counts film that has actually landed. A link delivering nothing never trips the
     * restart cap, because a restart is not what it needs, so this is the other way the silence ends.
     */
    fun isWaitingOnTheLink(nowMs: Long, stalledSinceMs: Long, settledAhead: Long): Boolean =
        settledAhead < RECOVER_AHEAD_BYTES && ageMs(nowMs, stalledSinceMs) >= LINK_GIVE_UP_MS

    /**
     * Whether the picture is quiet because the file it is reading has a hole in it.
     *
     * Two facts held together, one from the player and one from its play head: the player says it has run
     * out of film, and the head has not travelled to the length to get there. This is the shape of the
     * wait that the other two admissions cannot answer. A restart needs six megabytes to have landed in
     * front of the head, so a file that stopped short of its own length with nothing arriving never spends
     * an attempt; and the link give-up is ninety seconds of that. Meanwhile the player has already said
     * something precise, and the screen can say it back.
     */
    fun isStarvedAtTheEnd(nowMs: Long, stalledSinceMs: Long, playHeadRanIntoAHole: Boolean): Boolean =
        playHeadRanIntoAHole && ageMs(nowMs, stalledSinceMs) >= STARVED_SPEECH_MS
}
