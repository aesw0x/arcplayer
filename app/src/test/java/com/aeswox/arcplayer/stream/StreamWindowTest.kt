package com.aeswox.arcplayer.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamWindowTest {

    private val mb = 1024L * 1024

    @Test
    fun readInsideTheWindowStopsAtItsEnd() {
        assertEquals(600L, StreamWindow.readableBytes(position = 500L, size = 10_000L, windowStart = 100L, windowLength = 1_000L, completed = false))
    }

    @Test
    fun readBelowTheWindowIsNothingNotTheHole() {
        // The file on disk is 10 MB long because the fetch reached 1.5 MB once. Seeking back below
        // the window must read as unavailable, or the player gets zeroes and dies on them.
        assertEquals(0L, StreamWindow.readableBytes(500L, 10_000_000L, 1_000_000L, 500_000L, false))
    }

    @Test
    fun readPastTheWindowIsNothing() {
        assertEquals(0L, StreamWindow.readableBytes(2_000L, 10_000L, 100L, 1_000L, false))
    }

    @Test
    fun aFinishedFileAnswersForAllOfItself() {
        assertEquals(6_000L, StreamWindow.readableBytes(4_000L, 10_000L, 0L, 0L, true))
        assertEquals(0L, StreamWindow.readableBytes(10_000L, 10_000L, 0L, 0L, true))
    }

    @Test
    fun aHeadBehindTheWindowStillReadsWhatAlreadyLanded() {
        // The steady state after a forward extension: the window is filling 1000..2000 while the play
        // head reads at 500, which is film the previous window delivered. Answering zero here is what
        // tells a stalled player the movie ended while its bytes are sitting on disk.
        assertEquals(
            500L,
            StreamWindow.readableBytes(
                position = 500L, size = 10_000L, windowStart = 1_000L, windowLength = 1_000L,
                completed = false, alreadyDelivered = DeliveredRun(0L, 1_000L)
            )
        )
    }

    @Test
    fun aHeadInARealHoleStillReadsNothingEvenWithARun() {
        assertEquals(
            0L,
            StreamWindow.readableBytes(
                position = 500L, size = 10_000L, windowStart = 1_000L, windowLength = 1_000L,
                completed = false, alreadyDelivered = DeliveredRun(1_000L, 2_000L)
            )
        )
    }

    @Test
    fun theWiderOfTheTwoSpansIsTheAnswer() {
        // A run that stops short of the live window must not cap what the window can still hand over.
        assertEquals(
            1_600L,
            StreamWindow.readableBytes(
                position = 500L, size = 10_000L, windowStart = 100L, windowLength = 2_000L,
                completed = false, alreadyDelivered = DeliveredRun(0L, 1_000L)
            )
        )
    }

    @Test
    fun askingForWhatHasLandedExcludesWhatIsStillComing() {
        // The same span as above, asked as "what can the player read right now": 1,600 of it is inside
        // a window Telegram is still carrying, and a player re-opened past the last real byte reads the
        // zeroes behind it and reports them as the end of the film.
        assertEquals(
            500L,
            StreamWindow.readableBytes(
                position = 500L, size = 10_000L, windowStart = 100L, windowLength = 2_000L,
                completed = false, alreadyDelivered = DeliveredRun(0L, 1_000L), includeInFlight = false
            )
        )
        // A completed file has no promise in it; everything is on disk.
        assertEquals(
            6_000L,
            StreamWindow.readableBytes(
                position = 4_000L, size = 10_000L, windowStart = 0L, windowLength = 0L,
                completed = true, includeInFlight = false
            )
        )
    }

    @Test
    fun seekingBackIntoBytesThatNeverArrivedMovesTheFetch() {
        assertTrue(StreamWindow.shouldReaim(500L, 1_000L, 5_000L, active = true, completed = false))
    }

    @Test
    fun seekingBackOntoDeliveredBytesLeavesTheFetchAlone() {
        // The seam between two windows: a forward extension starts where the previous one finished,
        // and the play head is always a little behind that. Pointing back into film that is already
        // on disk cancels the transfer that was about to deliver the part which is not.
        val delivered = DeliveredRun(0L, 1_000L)
        assertFalse(StreamWindow.shouldReaim(500L, 1_000L, 5_000L, active = true, completed = false, delivered))
    }

    @Test
    fun onlyThePartCoveredByTheRunIsSpared() {
        val delivered = DeliveredRun(0L, 1_000L)
        assertFalse(StreamWindow.shouldReaim(999L, 1_000L, 5_000L, active = true, completed = false, delivered))
        assertTrue(StreamWindow.shouldReaim(1_000L, 1_001L, 5_000L, active = true, completed = false, delivered))
    }

    @Test
    fun readingInsideARunningFetchNeedsNothing() {
        assertFalse(StreamWindow.shouldReaim(3_000L, 1_000L, 5_000L, active = true, completed = false))
    }

    @Test
    fun aShortHopAheadIsLeftToTheRunningFetch() {
        // Restarting here would throw away the window that is about to deliver the byte.
        val end = 4_000_000L
        assertFalse(StreamWindow.shouldReaim(end + mb, end - 1_000_000L, 1_000_000L, active = true, completed = false))
    }

    @Test
    fun aLongHopAheadWaitsTooLong() {
        val end = 4_000_000L
        assertTrue(StreamWindow.shouldReaim(end + 5 * mb, end - 1_000_000L, 1_000_000L, active = true, completed = false))
    }

    @Test
    fun anIdleFetchAtTheEdgeIsADeadFetch() {
        val windowStart = 1_000_000L
        assertTrue(StreamWindow.shouldReaim(4_000_000L, windowStart, 3_000_000L, active = false, completed = false))
        assertFalse(StreamWindow.shouldReaim(4_000_000L, windowStart, 3_000_000L, active = true, completed = false))
    }

    @Test
    fun aCompleteFileNeverRestarts() {
        assertFalse(StreamWindow.shouldReaim(9_000_000L, 0L, 0L, active = false, completed = true))
    }

    @Test
    fun aRestartKeepsRoomBehindItOnAPartBoundary() {
        val wanted = 12_000_000L
        val from = StreamWindow.reaimStartFor(wanted)
        assertTrue(from < wanted)
        assertTrue(wanted - from >= StreamWindow.KEEP_BEHIND_BYTES)
        assertEquals(0L, from % StreamWindow.REQUEST_ALIGN_BYTES)
        assertEquals(9_437_184L, from)
    }

    @Test
    fun aRestartNearTheStartClampsToZero() {
        assertEquals(0L, StreamWindow.reaimStartFor(500_000L))
    }

    // The link measured on device: about 350 KB/s, so three seconds carries roughly a megabyte.
    private val landedInThreeSeconds = 1_050_000L
    private val threeSeconds = StreamWindow.MIN_SAMPLE_MS
    private val target = 100_000_000L

    @Test
    fun aWindowFarBehindTheTargetWillNotAnswerInTime() {
        // 75 MB still to arrive at a megabyte every three seconds is two and a half minutes, against
        // a restart that has 2.4 MB to go.
        assertTrue(
            StreamWindow.needsRedirect(
                position = target, windowStart = 20_000_000L, windowLength = 5_000_000L,
                landedBytes = landedInThreeSeconds, waitedMs = threeSeconds, budgetLeftMs = 40_000L
            )
        )
    }

    @Test
    fun aFetchAimedAtTheTargetIsLeftAlone() {
        // The restart offset a correct aim already chose, with nothing landed yet: breaking it off
        // would only pay for a fresh round trip.
        assertFalse(
            StreamWindow.needsRedirect(
                position = target, windowStart = StreamWindow.reaimStartFor(target), windowLength = 0L,
                landedBytes = landedInThreeSeconds, waitedMs = threeSeconds, budgetLeftMs = 40_000L
            )
        )
    }

    @Test
    fun aFetchHalfWayThroughItsBackBufferIsNotRestarted() {
        assertFalse(
            StreamWindow.needsRedirect(
                position = target, windowStart = StreamWindow.reaimStartFor(target),
                windowLength = 1_000_000L,
                landedBytes = landedInThreeSeconds, waitedMs = threeSeconds, budgetLeftMs = 40_000L
            )
        )
    }

    @Test
    fun aDeadLinkIsNeverBrokenOff() {
        // Nothing has landed, so there is no speed to project and a cancel only discards the range the
        // server was part way through sending.
        assertFalse(
            StreamWindow.needsRedirect(
                position = target, windowStart = 20_000_000L, windowLength = 5_000_000L,
                landedBytes = 0L, waitedMs = 30_000L, budgetLeftMs = 40_000L
            )
        )
    }

    @Test
    fun aWindowPastTheTargetGoesBackEvenWhileItCarriesNothing() {
        // Measured on the emulator: a fetch aimed past the seek target delivered nothing at all, and
        // the wait spent its whole budget beside it and refused the seek. Point the wrong way is not
        // the same as slow, so it does not have to earn a speed first.
        assertTrue(
            StreamWindow.needsRedirect(
                position = target, windowStart = target + 40_000_000L, windowLength = 0L,
                landedBytes = 0L, waitedMs = threeSeconds, budgetLeftMs = 40_000L
            )
        )
    }

    @Test
    fun twoSecondsOfSamplesIsNotEnoughToProjectAnything() {
        assertFalse(
            StreamWindow.needsRedirect(
                position = target, windowStart = 20_000_000L, windowLength = 5_000_000L,
                landedBytes = 700_000L, waitedMs = 2_000L, budgetLeftMs = 40_000L
            )
        )
    }

    @Test
    fun aWindowThatHasRunPastTheTargetGoesBack() {
        // Bytes arriving ahead of a rewound play head never reach it: only a fresh request goes back.
        assertTrue(
            StreamWindow.needsRedirect(
                position = 50_000_000L, windowStart = 80_000_000L, windowLength = 5_000_000L,
                landedBytes = landedInThreeSeconds, waitedMs = threeSeconds, budgetLeftMs = 40_000L
            )
        )
    }

    @Test
    fun bytesAlreadyAtTheTargetAreNotADirectionProblem() {
        assertFalse(
            StreamWindow.needsRedirect(
                position = 30_000_000L, windowStart = 20_000_000L, windowLength = 20_000_000L,
                landedBytes = landedInThreeSeconds, waitedMs = threeSeconds, budgetLeftMs = 40_000L
            )
        )
    }

    @Test
    fun aFetchBarelyBehindTheRestartIsNotWorthACancel() {
        // Only 875 KB worse off than a fresh aim, which the round trip and the margin eat.
        val windowEnd = target - 3_357_432L
        assertFalse(
            StreamWindow.needsRedirect(
                position = target, windowStart = windowEnd - 642_568L, windowLength = 642_568L,
                landedBytes = landedInThreeSeconds, waitedMs = threeSeconds, budgetLeftMs = 40_000L
            )
        )
    }

    @Test
    fun aWaitAboutToExpireKeepsTheFetchItHas() {
        // A restart cannot hand over bytes inside its own round trip, so a redirect here is a lie.
        assertFalse(
            StreamWindow.needsRedirect(
                position = target, windowStart = 20_000_000L, windowLength = 5_000_000L,
                landedBytes = landedInThreeSeconds, waitedMs = threeSeconds, budgetLeftMs = 1_000L
            )
        )
    }

    @Test
    fun aWindowPushedForwardExtendsTheRunInsteadOfErasingIt() {
        // The report that matters: Telegram blanks the landed prefix when a new window starts, so the
        // reply naming a forward window carrying nothing must keep the memory of the one it replaced,
        // and the first byte to land then grows it.
        val seam = DeliveredRun(80 * mb, 100 * mb)
        assertEquals(seam, seam.extendedBy(100 * mb, 100 * mb))
        assertEquals(DeliveredRun(80 * mb, 172 * mb), seam.extendedBy(100 * mb, 172 * mb))
    }

    @Test
    fun aRunOnlyPromisesWhatItHasWatchedLand() {
        // A span with nothing in it says nothing, and one far away is not a bridge — keeping the old
        // end would claim bytes over the gap that no reply has ever covered.
        assertEquals(DeliveredRun(80 * mb, 100 * mb), DeliveredRun(80 * mb, 100 * mb).extendedBy(50 * mb, 50 * mb))
        assertEquals(DeliveredRun(10 * mb, 20 * mb), DeliveredRun(80 * mb, 100 * mb).extendedBy(10 * mb, 20 * mb))
    }

    @Test
    fun anEmptyRunCoversNothingAndStartsAtTheFirstSpan() {
        assertFalse(DeliveredRun.NONE.covers(0L))
        assertFalse(DeliveredRun.NONE.covers(1L))
        assertEquals(DeliveredRun(0L, 8 * mb), DeliveredRun.NONE.extendedBy(0L, 8 * mb))
        assertEquals(DeliveredRun(100 * mb, 108 * mb), DeliveredRun.NONE.extendedBy(100 * mb, 108 * mb))
    }
}
