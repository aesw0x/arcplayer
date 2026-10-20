package com.aeswox.arcplayer.stream

/**
 * The arithmetic of watching a file that has only partly arrived.
 *
 * Telegram hands over one bounded span of a document at a time, and a player reading the file cannot
 * tell a span from the end of the film: the disk is as long as the furthest byte ever written and
 * every gap in it reads as zeroes. So a reader has to ask a question about the span rather than
 * about the file — is the byte I want inside it, behind it, or in front of it — and the three
 * answers are different jobs: read now, wait for the transfer to reach me, or break the transfer
 * off and point it somewhere else.
 *
 * Pure and total by design. Every rule here is reachable from a unit test with no network in the
 * way, which is the only reason seek behaviour can be trusted to survive a change.
 */
object StreamWindow {

    /**
     * How far in front of a running span a read position may sit before waiting for it stops being
     * the faster way to get there. Below this the transfer will hand over the byte sooner than a
     * fresh request could, so breaking it off would only cost the bytes it was already carrying.
     */
    const val WAIT_BEFORE_REAIM_BYTES = 4L * 1024 * 1024

    /**
     * Telegram serves documents in whole parts of this size, so a request that starts anywhere else
     * spends a second round trip on the part it began inside.
     */
    const val REQUEST_ALIGN_BYTES = 1L shl 20

    /**
     * The margin of already-fetched bytes a re-aim leaves behind the read position, so that rewinding
     * a few seconds is a read rather than a download.
     */
    const val KEEP_BEHIND_BYTES = 2L * 1024 * 1024

    /** The shortest window over which an observed byte count can be called a speed. */
    const val MIN_SAMPLE_MS = 3_000L

    /**
     * What a fresh request has to spend before its first byte can land: the round trip, plus the
     * server seeking to the offset. A judgement call rather than a measurement, with the margin below
     * sized to absorb being wrong about it.
     */
    const val REAIM_ROUND_TRIP_MS = 1_500L

    /** How much sooner a re-aim has to promise the bytes before breaking off a live transfer pays. */
    const val REAIM_MARGIN_MS = 2_000L

    /**
     * Bytes a reader can take starting exactly at [position].
     *
     * A finished document answers for the whole of itself. Otherwise the readable ground is the wider
     * of two spans: the window Telegram is filling right now, and [alreadyDelivered] — the bytes it
     * has already written and stopped reporting. Everything outside both counts as nothing readable,
     * deliberately not the zeroes a blind read would actually get, because a caller needs to tell
     * "not yet" apart from "the file is only this long".
     *
     * The second span is what keeps a stalled player from being told the film ran out. A window that
     * has been pushed forward to extend the file leaves the play head reading behind its start, and
     * judged on the window alone that position has nothing ahead of it — so a watchdog waiting for
     * bytes to resume on never sees any, on a file whose bytes are sitting on disk.
     */
    fun readableBytes(
        position: Long,
        size: Long,
        windowStart: Long,
        windowLength: Long,
        completed: Boolean,
        alreadyDelivered: DeliveredRun = DeliveredRun.NONE
    ): Long {
        if (position < 0L) return 0L
        if (completed) return size - position.coerceAtMost(size)
        val spanEnd = windowStart + windowLength
        val arriving = if (position in windowStart until spanEnd) spanEnd - position else 0L
        val onDisk = if (alreadyDelivered.covers(position)) alreadyDelivered.to - position else 0L
        return maxOf(arriving, onDisk)
    }

    /**
     * Whether the transfer has to be pointed at [position] instead of left to carry on.
     *
     * Three unrelated reasons, cheap to state in the order a reader will hit them: the play head went
     * back out of the span *and* out of the film already on disk (nothing arriving ahead can reach
     * it), the play head is too far in front of the span for patience to pay, or the span is exhausted
     * and no transfer is extending it at all.
     *
     * [alreadyDelivered] is what separates a rewind into a hole from a rewind onto the seam between
     * two windows. A forward extension starts its span at the exact byte the previous one finished
     * delivering, and the play head is always a little behind that seam — so judged on the span alone
     * it reads as "pointed the wrong way", and every extension gets cancelled a second later and
     * aimed back into film that is already on disk. Measured on device: about 8 MB re-downloaded at
     * every window boundary, and the boundary is exactly where the picture then froze.
     */
    fun shouldReaim(
        position: Long,
        windowStart: Long,
        windowLength: Long,
        active: Boolean,
        completed: Boolean,
        alreadyDelivered: DeliveredRun = DeliveredRun.NONE
    ): Boolean {
        if (completed) return false
        if (position < windowStart && !alreadyDelivered.covers(position)) return true
        val spanEnd = windowStart + windowLength
        if (position - spanEnd > WAIT_BEFORE_REAIM_BYTES) return true
        return position >= spanEnd && !active
    }

    /**
     * Where a transfer aimed at [position] should actually start: [KEEP_BEHIND_BYTES] behind it,
     * floored onto a [REQUEST_ALIGN_BYTES] boundary so the response needs no follow-up request for
     * the part it began inside.
     */
    fun reaimStartFor(position: Long): Long {
        val withRoomBehind = position - KEEP_BEHIND_BYTES
        if (withRoomBehind <= 0L) return 0L
        return withRoomBehind / REQUEST_ALIGN_BYTES * REQUEST_ALIGN_BYTES
    }

    /**
     * Whether the running fetch has to be broken off and pointed at [position] again.
     *
     * The source says a download is live, and it is: [landedBytes] have arrived in [waitedMs]. But a
     * window that was aimed somewhere else keeps arriving somewhere else, and a seek spends its whole
     * budget beside a transfer that never stops. So the question is not where the bytes are, it is
     * when they will get here: a fresh request starting [reaimStartFor] behind the target wins only if
     * it answers [position] clearly sooner than the current trajectory will.
     *
     * A link carrying nothing is left alone only while it is still carrying toward us. [landedBytes]
     * of zero gives no speed to project, and a cancel throws away the range the server was part way
     * through sending — but a window that has already passed the target is not slow, it is pointed
     * the wrong way, and no amount of patience turns it into the bytes being waited for.
     */
    fun needsRedirect(
        position: Long,
        windowStart: Long,
        windowLength: Long,
        landedBytes: Long,
        waitedMs: Long,
        budgetLeftMs: Long
    ): Boolean {
        if (waitedMs < MIN_SAMPLE_MS) return false
        // A restart has to be given time to reach the target before the wait is abandoned anyway.
        if (budgetLeftMs < MIN_SAMPLE_MS) return false
        // Behind a window that has already moved past it, nothing arriving can help: only a fresh
        // request goes backwards. This is a direction problem rather than a speed one, so it holds
        // with nothing landed at all — and a fetch aimed past the target that carries nothing is the
        // worst case there is, because the wait is certain to expire beside it.
        if (position < windowStart) return true
        if (landedBytes <= 0L) return false
        val distance = position - (windowStart + windowLength)
        // Inside the part already delivered: the fetch is aimed correctly and only has to finish.
        if (distance <= 0L) return false
        // Both sides are turned into milliseconds from the observed byte rate, so the comparison is
        // plain integer arithmetic and no float decides a restart.
        val arrivingInMs = distance * waitedMs / landedBytes
        val fromARestartMs = (position - reaimStartFor(position)) * waitedMs / landedBytes + REAIM_ROUND_TRIP_MS
        return arrivingInMs - fromARestartMs >= REAIM_MARGIN_MS
    }
}

/**
 * The one run of a file's bytes that has actually been seen land on disk.
 *
 * Telegram describes only the window it is filling right now, and a fresh ranged fetch blanks that
 * report before its first byte arrives — which hides the fact that the span it just replaced is still
 * there. This is the memory the report lacks, so a position behind the current window can be told
 * apart from a position that is genuinely missing.
 *
 * Deliberately a single run, not a set. It only ever promises contiguity it has watched happen, and
 * guessing that a seek left a bridge of bytes behind is how a hole ends up read as film.
 */
data class DeliveredRun(val from: Long, val to: Long) {

    /** Whether [position] is a byte this run has already handed over. */
    fun covers(position: Long): Boolean = position in from until to

    /**
     * The run known about after one more report of a span ending at [spanTo] that started at
     * [spanFrom].
     *
     * A span touching the current run extends it, which is what lets the memory survive the window
     * being pushed forward — the new span starts where the old one stopped. A span anywhere else
     * replaces it: keeping a stale run would claim bytes that no report has ever covered.
     */
    fun extendedBy(spanFrom: Long, spanTo: Long): DeliveredRun {
        if (spanTo <= spanFrom) return this
        val touches = spanFrom <= to + 1 && spanTo >= from - 1
        return if (touches) {
            DeliveredRun(minOf(from, spanFrom), maxOf(to, spanTo))
        } else {
            DeliveredRun(spanFrom, spanTo)
        }
    }

    companion object {

        /** Nothing has been seen on disk yet. */
        val NONE = DeliveredRun(0L, 0L)
    }
}
