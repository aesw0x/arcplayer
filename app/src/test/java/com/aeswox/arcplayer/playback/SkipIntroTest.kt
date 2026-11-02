package com.aeswox.arcplayer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the way past an opening is worth offering.
 *
 * The marks themselves are judged by [TimingTrust], whose tests live in [EpisodeTimingsTest]; these tests
 * are about the other question — whether a viewer standing at a given second has anything ahead of them to
 * jump over.
 */
class SkipIntroTest {

    private val opening = 90_000L

    private fun timings(introEndMs: Long? = opening) = EpisodeTimings(introEndMs = introEndMs)

    private fun offered(positionMs: Long, introEndMs: Long? = opening) =
        SkipIntro.isOffered(timings(introEndMs), positionMs)

    @Test
    fun anOpeningStillAheadOfThePlayHeadIsOffered() {
        assertTrue(offered(0L))
        assertTrue(offered(60_000L))
    }

    @Test
    fun anOpeningAlreadyBehindThePlayHeadIsNotOfferedAgain() {
        // A tap means "take me forward", so once the title sequence is gone the button has nothing left to
        // promise — and offering it anyway would hand the viewer a way to rewind into the credits.
        assertFalse(offered(90_001L))
        assertFalse(offered(20 * 60_000L))
    }

    @Test
    fun anOpeningAboutToEndStandsDownRatherThanJumpALongWayRound() {
        val grace = SkipIntro.LEAD_MS
        assertTrue(offered(opening - grace))
        assertFalse(offered(opening - grace + 1))
    }

    @Test
    fun aPartNobodyMarkedOffersNothing() {
        assertFalse(offered(0L, introEndMs = null))
    }
}
