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
        // The old head only moves up and the target is below it, so every upward tick is the stale one
        // until the player reports at or below the target.
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

    private companion object {
        val START = 10_000_000L
        const val FETCH_MS = 40_000L
    }
}
