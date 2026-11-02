package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepQueueTest {

    private val budget = 30L * 60L * 1000L

    private fun decide(watch: Int?, stance: KeepQueue.Stance, nowMs: Long) =
        KeepQueue.decide(watch, stance, nowMs, budget)

    @Test
    fun aWatchOnAnotherFileTakesTheLinkFromAKeep() {
        val decision = decide(watch = 7, stance = KeepQueue.Stance(), nowMs = 1_000L)
        assertEquals(KeepQueue.Turn.STEP_ASIDE, decision.turn)
        assertTrue(decision.stance.standingDown)
    }

    @Test
    fun nothingOnScreenAndNothingStandingDownIsAnOrdinaryLap() {
        val decision = decide(watch = null, stance = KeepQueue.Stance(), nowMs = 1_000L)
        assertEquals(KeepQueue.Turn.FILL, decision.turn)
        assertFalse(decision.stance.standingDown)
    }

    @Test
    fun aStandingDownKeepAsksAgainTheMomentThePlayerLetsGo() {
        val stance = KeepQueue.Stance(watchFileId = 7, standingDown = true, untilMs = 31_000L)
        val decision = decide(watch = null, stance = stance, nowMs = 5_000L)
        assertEquals(KeepQueue.Turn.STEP_BACK_IN, decision.turn)
        assertEquals(KeepQueue.Stance(), decision.stance)
    }

    @Test
    fun aHoldIsNotExtendedByWaiting() {
        val stance = KeepQueue.Stance(watchFileId = 7, standingDown = true, untilMs = 1_000L + budget)
        val decision = decide(watch = 7, stance = stance, nowMs = 20_000L)
        assertEquals(KeepQueue.Turn.HOLD, decision.turn)
        assertEquals(stance, decision.stance)
    }

    @Test
    fun aWatchThatOutlivesItsBudgetIsNotOwedAnInfiniteHold() {
        // The claim the player hands out is taken back when a watch closes, and a watch that closed by
        // the Activity being recreated under it never ran that. Standing down for a claim whose watch is
        // gone would be the difference between slow downloads and no downloads at all.
        val held = KeepQueue.Stance(watchFileId = 7, standingDown = true, untilMs = 1_000L + budget)
        val spent = decide(watch = 7, stance = held, nowMs = 1_000L + budget + 9_000L)
        assertEquals(KeepQueue.Turn.STEP_BACK_IN, spent.turn)
        assertFalse(spent.stance.standingDown)
        assertEquals(
            KeepQueue.Turn.FILL,
            decide(watch = 7, stance = spent.stance, nowMs = 1_000L + budget + 10_000L).turn
        )
        assertEquals(
            KeepQueue.Turn.FILL,
            decide(watch = 7, stance = spent.stance, nowMs = 9_000_000L).turn
        )
    }

    @Test
    fun aSecondWatchBuysASecondHold() {
        val first = decide(watch = 7, stance = KeepQueue.Stance(), nowMs = 1_000L)
        val ended = decide(watch = null, stance = first.stance, nowMs = 6_000L)
        val second = decide(watch = 8, stance = ended.stance, nowMs = 7_000L)
        assertEquals(KeepQueue.Turn.STEP_BACK_IN, ended.turn)
        assertEquals(KeepQueue.Turn.STEP_ASIDE, second.turn)
        assertEquals(7_000L + budget, second.stance.untilMs)
    }

    @Test
    fun theSameFilmComingBackAfterThePlayerEmptiesIsAFreshWatch() {
        // The memory of which watch this keep gave up on goes when nothing is on screen, so a viewer who
        // closes the player and re-opens the same episode is owed the hold again rather than getting a
        // queue that has decided never to pause for that file.
        val held = KeepQueue.Stance(watchFileId = 7, standingDown = true, untilMs = 1_000L + budget)
        val spent = decide(watch = 7, stance = held, nowMs = 1_000L + budget + 9_000L)
        val quiet = decide(watch = null, stance = spent.stance, nowMs = 1_000L + budget + 10_000L)
        assertEquals(KeepQueue.Turn.FILL, quiet.turn)
        assertEquals(KeepQueue.Stance(), quiet.stance)
        assertEquals(
            KeepQueue.Turn.STEP_ASIDE,
            decide(watch = 7, stance = quiet.stance, nowMs = 1_000L + budget + 11_000L).turn
        )
    }

    @Test
    fun aNewWatchArrivingMidHoldExtendsTheWaitAndItsBudget() {
        // The viewer stepped from one episode to another while this keep was already paused. The hold
        // carries on — the picture is still there — and the new watch is owed its own budget from now,
        // so the stale id it replaced cannot leave the queue holding its breath forever.
        val held = KeepQueue.Stance(watchFileId = 7, standingDown = true, untilMs = 1_000L + budget)
        val decision = decide(watch = 8, stance = held, nowMs = 20_000L)
        assertEquals(KeepQueue.Turn.HOLD, decision.turn)
        assertEquals(
            KeepQueue.Stance(watchFileId = 8, standingDown = true, untilMs = 20_000L + budget),
            decision.stance
        )
    }

    @Test
    fun oneWatchIsHeldOffOnceAndOnlyOnce() {
        // Two laps in a row must not send two cancels and two re-asks: TDLib is told on the edges, and
        // the pump only runs a normal lap in between them.
        val aside = decide(watch = 7, stance = KeepQueue.Stance(), nowMs = 1_000L)
        val hold = decide(watch = 7, stance = aside.stance, nowMs = 2_000L)
        val back = decide(watch = null, stance = hold.stance, nowMs = 3_000L)
        val filled = decide(watch = null, stance = back.stance, nowMs = 4_000L)
        assertEquals(
            listOf(
                KeepQueue.Turn.STEP_ASIDE,
                KeepQueue.Turn.HOLD,
                KeepQueue.Turn.STEP_BACK_IN,
                KeepQueue.Turn.FILL,
            ),
            listOf(aside.turn, hold.turn, back.turn, filled.turn)
        )
    }
}
