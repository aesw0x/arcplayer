package com.aeswox.arcplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeRulesTest {

    private fun decide(positionMs: Long, durationMs: Long) = ResumeRules.decide(positionMs, durationMs)

    @Test
    fun keepsMidVideo() {
        assertEquals(ResumeOutcome.STORE, decide(600_000L, 1_800_000L))
    }

    @Test
    fun forgetsShortClipsEntirely() {
        assertEquals(ResumeOutcome.DROP, decide(9_000L, 12_000L))
    }

    @Test
    fun leavesAStoredPointAloneAtTheVeryStart() {
        // A tick 1s in must not wipe the point from 40 minutes in, which is what the viewer left.
        assertEquals(ResumeOutcome.KEEP, decide(1_000L, 1_800_000L))
    }

    @Test
    fun dropsInsideTheFourSecondContainerSlack() {
        assertEquals(ResumeOutcome.DROP, decide(1_796_500L, 1_800_000L))
    }

    @Test
    fun dropsAtEightPercentOfATenMinuteVideo() {
        // Under the cap eight percent governs the tail: 48s here, so 40s from the end is finished.
        assertEquals(48_000L, ResumeRules.tailMs(600_000L))
        assertEquals(ResumeOutcome.DROP, decide(560_000L, 600_000L))
        assertEquals(ResumeOutcome.STORE, decide(540_000L, 600_000L))
    }

    @Test
    fun capsTheTailSoALongFilmKeepsItsPlace() {
        val threeHours = 10_800_000L
        // Uncapped, eight percent here is 14.4 minutes and the resume point would vanish early.
        assertEquals(90_000L, ResumeRules.tailMs(threeHours))
        assertEquals(ResumeOutcome.STORE, decide(threeHours - 600_000L, threeHours))
        assertEquals(ResumeOutcome.DROP, decide(threeHours - 30_000L, threeHours))
    }

    @Test
    fun ignoresAnUnknownDuration() {
        // VLC reports a length only once the demuxer has one, and a tick can arrive before it.
        assertEquals(ResumeOutcome.KEEP, decide(120_000L, 0L))
        assertEquals(ResumeOutcome.KEEP, decide(-1L, 1_800_000L))
    }

    @Test
    fun treatsAPositionPastTheEndAsFinished() {
        // A stale duration from the container against a position VLC walked past on a seek.
        assertEquals(ResumeOutcome.DROP, decide(2_000_000L, 1_800_000L))
    }
}
