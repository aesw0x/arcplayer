package com.aeswox.arcplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRateMatchTest {

    private fun fps(num: Int, den: Int) = FrameRateMatch.contentFps(num, den)

    @Test
    fun readsFilmRatesFromTheTrackHeader() {
        assertEquals(23.976f, fps(24_000, 1_001)!!, 0.001f)
        assertEquals(24f, fps(24, 1)!!, 0.0001f)
        assertEquals(29.97f, fps(30_000, 1_001)!!, 0.01f)
    }

    @Test
    fun refusesRatesThatAreNotFilm() {
        // A zero pair means VLC has not parsed the track yet; a 200 fps one is a screen recording.
        assertNull(fps(0, 0))
        assertNull(fps(24, 0))
        assertNull(fps(1, 1))
        assertNull(fps(200, 1))
    }

    @Test
    fun scoresCleanMultiplesAtZero() {
        assertEquals(0f, FrameRateMatch.judderScore(120f, 24f), 0.0001f)
        assertEquals(0f, FrameRateMatch.judderScore(60f, 30f), 0.0001f)
    }

    @Test
    fun scoresTwoThreePulldownAsJudder() {
        // 24 fps on 60 Hz is the classic case: every frame is a fraction short of a whole 2.5.
        assertEquals(0.2f, FrameRateMatch.judderScore(60f, 24f), 0.001f)
    }

    @Test
    fun treatsFiveNineNineFourAsSixty() {
        assertTrue(FrameRateMatch.judderScore(60f, 59.94f) < 0.01f)
    }

    @Test
    fun willNotAskAPanelThatHasOneMode() {
        assertNull(FrameRateMatch.targetRefreshHz(24f, currentHz = 60f, availableHz = listOf(60f)))
    }

    @Test
    fun asksForTheModeThatRemovesThePulldown() {
        assertEquals(120f, FrameRateMatch.targetRefreshHz(24f, 60f, listOf(60f, 120f))!!, 0.001f)
    }

    @Test
    fun staysPutWhenAlreadyClean() {
        assertNull(FrameRateMatch.targetRefreshHz(24f, 120f, listOf(60f, 120f)))
        assertNull(FrameRateMatch.targetRefreshHz(30f, 60f, listOf(60f, 120f)))
    }

    @Test
    fun ignoresAnImprovementTooSmallToPayForAModeChange() {
        // 60 and 120 are both within a thousandth of clean for 59.94 fps content.
        assertNull(FrameRateMatch.targetRefreshHz(59.94f, 60f, listOf(60f, 120f)))
    }

    @Test
    fun prefersTheLowerModeWhenBothAreClean() {
        // 25 fps has no clean 60: 50 Hz is exact, and a lower refresh is less work for the phone.
        assertEquals(50f, FrameRateMatch.targetRefreshHz(25f, 60f, listOf(50f, 60f))!!, 0.001f)
    }

    @Test
    fun refusesToAskForARateSlowerThanTheContent() {
        assertNull(FrameRateMatch.targetRefreshHz(60f, 120f, listOf(60f, 120f)))
    }
}
