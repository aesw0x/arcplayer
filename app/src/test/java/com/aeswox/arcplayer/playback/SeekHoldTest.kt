package com.aeswox.arcplayer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekHoldTest {

    private fun hold(fromMs: Long, targetMs: Long) = SeekHold(fromMs, targetMs, START)

    @Test
    fun aTickFromTheOldPlayHeadIsNotTheSeek() {
        // Forty seconds into a fetch the head sits just past where the seek started, nowhere near 45:00.
        val stale = hold(600_000L, 2_700_000L)
            .isStale(600_000L + FETCH_MS + 4_000L, START + FETCH_MS)
        assertTrue(stale)
    }

    @Test
    fun aTickAtTheTargetEndsTheHold() {
        assertFalse(hold(600_000L, 2_700_000L).isStale(2_700_000L, START + FETCH_MS))
    }

    @Test
    fun aTickJustShortOfTheTargetIsTheLanding() {
        // A second and a half short is close enough that holding any longer only fights the player.
        assertFalse(hold(600_000L, 2_700_000L).isStale(2_700_000L - 1_000L, START + FETCH_MS))
    }

    @Test
    fun aForwardJumpTheOldHeadCouldNotHaveReachedIsBelieved() {
        // Fast seeking stops on the keyframe before the target, and a three minute gap is one the old
        // play head cannot have covered, so the scrubber follows it instead of waiting for the target.
        assertFalse(hold(600_000L, 2_700_000L).isStale(2_700_000L - 180_000L, START + FETCH_MS))
        assertTrue(hold(600_000L, 2_700_000L).isStale(660_000L, START + FETCH_MS))
    }

    @Test
    fun aShortForwardHopGivesTheOldHeadRoomToRun() {
        // Seeking from 10:00 to 10:20 at 3x: the old head really can reach the target during the
        // fetch, and once it reports there the hold is over rather than a fight.
        assertFalse(hold(600_000L, 620_000L).isStale(600_000L + FETCH_MS * SeekHold.MAX_SPEED, START + FETCH_MS))
        assertTrue(hold(600_000L, 620_000L).isStale(601_000L, START + FETCH_MS))
    }

    @Test
    fun aSeekBackwardIgnoresTicksClimbingAwayFromTheTarget() {
        // A long way back, so the old head is far outside anything the new one could have walked to
        // by now, and every report from up there is the position the seek left.
        val back = hold(2_700_000L, 600_000L)
        assertTrue(back.isStale(2_700_000L + FETCH_MS + 4_000L, START + FETCH_MS))
        assertTrue(back.isStale(2_700_000L + 1_000L, START + FETCH_MS))
        assertFalse(back.isStale(600_000L, START + FETCH_MS))
        assertFalse(back.isStale(601_000L, START + FETCH_MS))
    }

    @Test
    fun aBackwardLandingShortOfTheTargetIsBelieved() {
        val back = hold(2_700_000L, 600_000L)
        assertFalse(back.isStale(597_000L, START + FETCH_MS))
        assertFalse(back.isStale(400_000L, START + FETCH_MS))
    }

    @Test
    fun aSeekToWhereWeAlreadyAreFollowsPlayback() {
        val same = hold(600_000L, 600_000L)
        assertFalse(same.isStale(601_000L, START))
        assertTrue(same.isStale(590_000L, START))
    }

    @Test
    fun aHoldWithoutTicksExpires() {
        // A fetch that ends while paused, or a player that stops reporting, must not leave the
        // scrubber frozen at the target forever.
        assertFalse(hold(600_000L, 2_700_000L).isStale(601_000L, START + SeekHold.TIMEOUT_MS))
        assertTrue(hold(600_000L, 2_700_000L).isStale(601_000L, START + SeekHold.TIMEOUT_MS - 1_000L))
    }

    @Test
    fun anImpossibleClockReadingStillLetsTheArrivalThrough() {
        assertFalse(hold(600_000L, 2_700_000L).isStale(2_640_000L, START - 10_000L))
        assertTrue(hold(600_000L, 2_700_000L).isStale(601_000L, START - 10_000L))
    }

    @Test
    fun aReportQueuedBehindTheLandingIsStillTheOldPlayHead() {
        // The tick that lands a seek is followed by whatever the player already had waiting. Measured
        // on a cold 1.9 GB film: the head arrived at 1:57:00, and 4.6 s later a report from 3:34 was
        // believed, which put the scrubber back and turned the one download window around.
        val hold = hold(213_000L, 7_058_000L)
        assertFalse(hold.isStale(7_056_000L, START + 4_500L))
        assertTrue(hold.isStale(214_600L, START + 4_660L))
    }

    @Test
    fun onlyTheClockRetiresAHold() {
        val hold = hold(600_000L, 2_700_000L)
        assertFalse(hold.isStale(2_700_000L, START + FETCH_MS))
        assertFalse(hold.isExpired(START + FETCH_MS))
        assertFalse(hold.isExpired(START + SeekHold.TIMEOUT_MS - 1_000L))
        assertTrue(hold.isExpired(START + SeekHold.TIMEOUT_MS))
    }

    @Test
    fun aShortHopBackDoesNotSwallowTheWalkOverTheOldPosition() {
        // Ten seconds back, and ten seconds later real playback is standing where the seek started.
        // Only the clock separates that tick from a queued one, and a hold that now outlives its
        // arrival would freeze the scrubber for the rest of the fetch if it got this wrong.
        val hop = hold(600_000L, 590_000L)
        assertTrue(hop.isStale(600_500L, START + 200L))
        assertFalse(hop.isStale(598_000L, START + 8_000L))
        assertFalse(hop.isStale(600_000L, START + 8_000L))
    }

    @Test
    fun aReportQueuedBehindABackwardLandingIsStillTheOldPlayHead() {
        // Measured on a 3.6 GB film: a drag from 1:13:02 back to 38:59, and the position the drag
        // left is what the player reports next.
        val back = hold(4_382_000L, 2_339_000L)
        assertFalse(back.isStale(2_340_000L, START + 1_000L))
        assertTrue(back.isStale(4_382_500L, START + 1_200L))
    }

    private companion object {
        val START = 10_000_000L
        const val FETCH_MS = 40_000L
    }
}
