package com.aeswox.arcplayer.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferGuardTest {

    private val mb = 1024L * 1024

    private fun running(
        nowMs: Long = 100_000L,
        lastTickMs: Long = 100_000L,
        headMovedAtMs: Long? = null,
        playing: Boolean = true,
        stoppedOnPurpose: Boolean = false,
        seeking: Boolean = false,
        watchedMs: Long = 60_000L,
        durationMs: Long = 1_425_000L,
        fileComplete: Boolean = false,
        playOwedSinceMs: Long = BufferGuard.NEVER,
        firstFrameRendered: Boolean = true
    ) = BufferGuard.Snapshot(
        nowMs = nowMs,
        lastTickMs = lastTickMs,
        headMovedAtMs = headMovedAtMs ?: lastTickMs,
        playing = playing,
        stoppedOnPurpose = stoppedOnPurpose,
        seeking = seeking,
        watchedMs = watchedMs,
        durationMs = durationMs,
        fileComplete = fileComplete,
        playOwedSinceMs = playOwedSinceMs,
        firstFrameRendered = firstFrameRendered
    )

    @Test
    fun aPlayHeadThatKeptMovingIsNotStalled() {
        assertFalse(BufferGuard.isStalled(running(nowMs = 102_000L, lastTickMs = 101_500L)))
    }

    @Test
    fun aBriefSilenceIsNotYetAStall() {
        // A held camera shot or a scene change still ticks within a second or two; three is the
        // shortest gap that cannot be explained by the film itself.
        assertFalse(BufferGuard.isStalled(running(nowMs = 101_000L, lastTickMs = 100_000L)))
        assertTrue(BufferGuard.isStalled(running(nowMs = 104_000L, lastTickMs = 100_000L)))
    }

    @Test
    fun aViewerPauseIsNotAStall() {
        // Without this the spinner comes up over a frame the viewer stopped on deliberately.
        assertFalse(
            BufferGuard.isStalled(
                running(nowMs = 200_000L, lastTickMs = 100_000L, stoppedOnPurpose = true)
            )
        )
    }

    @Test
    fun aSeekInTheWorksIsNotAStall() {
        // The wait already owns the window and already shows a spinner. A restart here cancels the
        // fetch the seek is waiting on.
        assertFalse(
            BufferGuard.isStalled(running(nowMs = 200_000L, lastTickMs = 100_000L, seeking = true))
        )
    }

    @Test
    fun aWholeFileOnDiskIsNotAStall() {
        // Nothing left for the link to deliver, so a quiet player at that point is some other bug
        // and re-opening it would only loop.
        assertFalse(
            BufferGuard.isStalled(
                running(nowMs = 200_000L, lastTickMs = 100_000L, fileComplete = true)
            )
        )
    }

    @Test
    fun aFrameOnScreenWithNoPlayHeadIsNotAStall() {
        // The viewer opened the film and stopped on its first frame. Nothing has ever been reported, and
        // that is a choice rather than a dead player.
        assertFalse(
            BufferGuard.isStalled(
                running(nowMs = 200_000L, lastTickMs = BufferGuard.NEVER, headMovedAtMs = 100_000L)
            )
        )
        // Playing, or not, does not change it: the picture is there.
        assertFalse(
            BufferGuard.isStalled(
                running(
                    nowMs = 200_000L, lastTickMs = BufferGuard.NEVER, headMovedAtMs = 100_000L,
                    playing = false
                )
            )
        )
    }

    @Test
    fun aBlackPlayerThatNeverReportedIsAStallOnceItsOpenIsOverdue() {
        // The resume that ends with controls over a black screen. The container opened, no frame ever
        // arrived, no play head ever came, and every rule in this file was excusing it — so nothing
        // asked Telegram for the bytes that would have produced a picture.
        val openedAt = 100_000L
        assertTrue(
            BufferGuard.isStalled(
                running(
                    nowMs = openedAt + BufferGuard.SILENT_SEEK_MS, lastTickMs = BufferGuard.NEVER,
                    headMovedAtMs = openedAt, playing = false, firstFrameRendered = false
                )
            )
        )
        // An open that has not had its ten seconds yet is still an open, and a re-open spent on it is a
        // film thrown back to its own keyframe. Measured against the constant for the same reason.
        assertFalse(
            BufferGuard.isStalled(
                running(
                    nowMs = openedAt + BufferGuard.SILENT_SEEK_MS - 1L, lastTickMs = BufferGuard.NEVER,
                    headMovedAtMs = openedAt, playing = false, firstFrameRendered = false
                )
            )
        )
    }

    @Test
    fun aFalseEndOfFilmStillCountsAsAStall() {
        // The player read a hole, decided the movie was over and stopped. The scrubber is at the
        // full length by then, so the last frame genuinely shown is what says otherwise.
        assertTrue(
            BufferGuard.isStalled(
                running(nowMs = 200_000L, lastTickMs = 100_000L, playing = false, watchedMs = 900_000L)
            )
        )
    }

    @Test
    fun aFilmThatReallyDidFinishIsNotAStall() {
        assertFalse(
            BufferGuard.isStalled(
                running(
                    nowMs = 200_000L, lastTickMs = 100_000L, playing = false,
                    watchedMs = 1_424_500L, durationMs = 1_425_000L
                )
            )
        )
    }

    @Test
    fun aHeadThatMovesIsNotStalledHoweverLateItsReports() {
        // A decoder running at a fraction of real time — 2160p on a software renderer — still moves its
        // head every second. Re-opening that puts the film back behind its own play head, which is what
        // a resume that never started looked like.
        assertFalse(
            BufferGuard.isStalled(
                running(nowMs = 112_000L, lastTickMs = 100_000L, headMovedAtMs = 111_500L)
            )
        )
        // The other way round: a report that arrives without the head moving in it says nothing, and is
        // the case the old rule mistook for a live player.
        assertTrue(
            BufferGuard.isStalled(
                running(nowMs = 112_000L, lastTickMs = 111_000L, headMovedAtMs = 100_000L)
            )
        )
    }

    @Test
    fun aSeekThePlayerNeverAnsweredIsAStall() {
        // The resume that started this: the bytes were on disk, the position was accepted, and no
        // play head ever came back. Timed from the hand-off, because no tick exists to time from.
        // Written against the constant so a threshold that moves does not quietly change what is
        // being proved here.
        val owedAt = 100_000L
        assertTrue(
            BufferGuard.isStalled(
                running(
                    nowMs = owedAt + BufferGuard.SILENT_SEEK_MS, lastTickMs = BufferGuard.NEVER,
                    playOwedSinceMs = owedAt
                )
            )
        )
        assertFalse(
            BufferGuard.isStalled(
                running(
                    nowMs = owedAt + BufferGuard.SILENT_SEEK_MS - 1L, lastTickMs = BufferGuard.NEVER,
                    playOwedSinceMs = owedAt
                )
            )
        )
        // A swallowed position leaves no Playing event either, so the player's own claim that it is
        // running is not what the debt turns on. The vetoes below are what keep a stopped film out.
        assertTrue(
            BufferGuard.isStalled(
                running(
                    nowMs = owedAt + BufferGuard.SILENT_SEEK_MS, lastTickMs = BufferGuard.NEVER,
                    playing = false, playOwedSinceMs = owedAt
                )
            )
        )
    }

    @Test
    fun aSeekThatBroughtAPlayHeadBackIsPaid() {
        val owedAt = 100_000L
        val nowMs = owedAt + BufferGuard.SILENT_SEEK_MS
        assertFalse(
            BufferGuard.isStalled(
                running(nowMs = nowMs, lastTickMs = nowMs - 1_000L, playOwedSinceMs = owedAt)
            )
        )
    }

    @Test
    fun aDebtTheViewerTookOutOfIsNotAStall() {
        // A frame the viewer stopped on deliberately is a position they chose, not a dead player, and
        // the spinner must not come up over it.
        assertFalse(
            BufferGuard.isStalled(
                running(
                    nowMs = 200_000L, lastTickMs = 100_000L, stoppedOnPurpose = true,
                    playOwedSinceMs = 100_000L
                )
            )
        )
        assertFalse(
            BufferGuard.isStalled(
                running(
                    nowMs = 200_000L, lastTickMs = 100_000L, seeking = true,
                    playOwedSinceMs = 100_000L
                )
            )
        )
        assertFalse(
            BufferGuard.isStalled(
                running(
                    nowMs = 200_000L, lastTickMs = 100_000L, fileComplete = true,
                    playOwedSinceMs = 100_000L
                )
            )
        )
    }

    @Test
    fun anUnpaidSeekPaysNoGraceBecauseThePumpHasNothingToFetch() {
        val owedAt = 100_000L
        val unpaidAt = owedAt + BufferGuard.SILENT_SEEK_MS
        val unpaid = running(nowMs = unpaidAt, lastTickMs = BufferGuard.NEVER, playOwedSinceMs = owedAt)
        val midRoll = running(nowMs = unpaidAt, lastTickMs = unpaidAt - 5_000L)
        assertEquals(0L, BufferGuard.graceMs(unpaid))
        assertEquals(BufferGuard.GRACE_MS, BufferGuard.graceMs(midRoll))
        // The one thing that makes the re-open the right move rather than a retry: the bytes under the
        // target are already readable, so asking Telegram again cannot be what unsticks it.
        assertTrue(
            BufferGuard.shouldRestart(
                nowMs = unpaidAt, stalledSinceMs = unpaidAt, bytesAhead = 12 * mb,
                restarts = 0, lastRestartMs = BufferGuard.NEVER, graceMs = BufferGuard.graceMs(unpaid)
            )
        )
        assertFalse(
            BufferGuard.shouldRestart(
                nowMs = unpaidAt, stalledSinceMs = unpaidAt, bytesAhead = 12 * mb,
                restarts = 0, lastRestartMs = BufferGuard.NEVER, graceMs = BufferGuard.graceMs(midRoll)
            )
        )
    }

    @Test
    fun noRestartBeforeTheBytesHaveArrived() {
        assertFalse(
            BufferGuard.shouldRestart(
                nowMs = 200_000L, stalledSinceMs = 100_000L,
                bytesAhead = 2 * mb, restarts = 0, lastRestartMs = BufferGuard.NEVER
            )
        )
        assertTrue(
            BufferGuard.shouldRestart(
                nowMs = 200_000L, stalledSinceMs = 100_000L,
                bytesAhead = BufferGuard.RECOVER_AHEAD_BYTES, restarts = 0, lastRestartMs = BufferGuard.NEVER
            )
        )
    }

    @Test
    fun thePumpGetsItsGraceBeforeARestart() {
        // Re-opening throws away the container the player already parsed. If simply asking for the
        // next window is going to work, that has to be allowed to happen first.
        assertFalse(
            BufferGuard.shouldRestart(
                nowMs = 100_000L + BufferGuard.GRACE_MS - 1, stalledSinceMs = 100_000L,
                bytesAhead = 20 * mb, restarts = 0, lastRestartMs = BufferGuard.NEVER
            )
        )
        assertTrue(
            BufferGuard.shouldRestart(
                nowMs = 100_000L + BufferGuard.GRACE_MS, stalledSinceMs = 100_000L,
                bytesAhead = 20 * mb, restarts = 0, lastRestartMs = BufferGuard.NEVER
            )
        )
    }

    @Test
    fun aStallInTheLastSecondsRestartsOnTheReadableRemainder() {
        // The final seconds of a file can never show six megabytes ahead, so without the tail rule a
        // picture that dies there stays dead with every remaining byte on disk: measured as a freeze
        // at 23:38 of 23:48 whose tail checksum was real film, not a hole.
        val remainder = 110_437L
        assertTrue(
            BufferGuard.shouldRestart(
                nowMs = 200_000L, stalledSinceMs = 100_000L, bytesAhead = remainder,
                restarts = 0, lastRestartMs = BufferGuard.NEVER, bytesToEof = remainder
            )
        )
        // A remainder only half readable is still a wait on the link, and bytes that are not on disk
        // are not a promise no matter how short the remainder is.
        assertFalse(
            BufferGuard.shouldRestart(
                nowMs = 200_000L, stalledSinceMs = 100_000L, bytesAhead = remainder / 2,
                restarts = 0, lastRestartMs = BufferGuard.NEVER, bytesToEof = remainder
            )
        )
        // Mid-roll the rule is untouched: the same small readable span in front of a long remainder
        // waits for the pump rather than re-opening the container.
        assertFalse(
            BufferGuard.shouldRestart(
                nowMs = 200_000L, stalledSinceMs = 100_000L, bytesAhead = remainder,
                restarts = 0, lastRestartMs = BufferGuard.NEVER, bytesToEof = 40 * mb
            )
        )
    }

    @Test
    fun restartsAreSpacedAndCapped() {
        assertFalse(
            BufferGuard.shouldRestart(
                nowMs = 200_000L, stalledSinceMs = 100_000L, bytesAhead = 20 * mb,
                restarts = 1, lastRestartMs = 190_000L
            )
        )
        assertTrue(
            BufferGuard.shouldRestart(
                nowMs = 200_000L, stalledSinceMs = 100_000L, bytesAhead = 20 * mb,
                restarts = 1, lastRestartMs = 100_000L
            )
        )
        // Past the cap the link is dead rather than slow, and the viewer hears that instead of an
        // endless quiet retry.
        assertFalse(
            BufferGuard.shouldRestart(
                nowMs = 200_000L, stalledSinceMs = 100_000L, bytesAhead = 20 * mb,
                restarts = BufferGuard.MAX_RESTARTS, lastRestartMs = 100_000L
            )
        )
    }

    @Test
    fun aFaultThatKillsThePlayerTwiceIsSteppedPast() {
        // The loop this is the answer to: the film stops at one byte, the watchdog re-opens it at that
        // same byte, a few seconds play, and the same byte stops them again. Repeating the aim repeats
        // the failure, and the viewer watches one spinner come up four times over the same thirty
        // seconds of a film.
        val diedAt = 735_817L
        assertEquals(
            diedAt + BufferGuard.STEP_PAST_FAULT_MS,
            BufferGuard.restartTargetMs(stalledAtMs = diedAt + 3_000L, lastAimMs = diedAt, durationMs = 5_925_000L)
        )
        // The first death has nothing to step past: the aim is where the viewer was, and skipping a
        // scene to be rid of a stall that has not happened twice is a worse trade.
        assertEquals(
            diedAt,
            BufferGuard.restartTargetMs(stalledAtMs = diedAt, lastAimMs = -1L, durationMs = 5_925_000L)
        )
    }

    @Test
    fun aStallSomewhereElseIsItsOwnProblem() {
        // A film that got a minute clear of the last death and then stopped is a new fault, and the
        // viewer's own play head is the right place to re-open it.
        val earlier = 735_817L
        val now = earlier + BufferGuard.FAULT_CLEARED_MS + 1L
        assertEquals(
            now,
            BufferGuard.restartTargetMs(stalledAtMs = now, lastAimMs = earlier, durationMs = 5_925_000L)
        )
    }

    @Test
    fun theStepPastANearEndIsNotTaken() {
        // There is nothing to skip into, and the tail is recoverable on the bytes it has anyway.
        val durationMs = 5_925_000L
        val diedAt = durationMs - 20_000L
        assertEquals(
            diedAt,
            BufferGuard.restartTargetMs(stalledAtMs = diedAt, lastAimMs = diedAt, durationMs = durationMs)
        )
    }

    @Test
    fun aLinkThatSettlesNothingIsAdmittedRatherThanWaitedOnForever() {
        // Restarts are gated on bytes that have landed, so a dead link never spends its three attempts
        // and the cap on its own says nothing. This is the other way a wait ends.
        assertFalse(
            BufferGuard.isWaitingOnTheLink(
                nowMs = 100_000L + BufferGuard.LINK_GIVE_UP_MS - 1, stalledSinceMs = 100_000L,
                settledAhead = 2 * mb
            )
        )
        assertTrue(
            BufferGuard.isWaitingOnTheLink(
                nowMs = 100_000L + BufferGuard.LINK_GIVE_UP_MS, stalledSinceMs = 100_000L,
                settledAhead = 2 * mb
            )
        )
        // Bytes that are arriving are not this wait. Without the pause the pill would come up over a
        // download that is on its way.
        assertFalse(
            BufferGuard.isWaitingOnTheLink(
                nowMs = 200_000L, stalledSinceMs = 100_000L, settledAhead = 20 * mb
            )
        )
    }
}
