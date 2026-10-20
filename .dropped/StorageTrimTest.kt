package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageTrimTest {

    private fun copy(id: Long, bytes: Long, watchedAt: Long, deletable: Boolean = true) =
        StorageTrim.Copy(stableId = id, bytesOnDisk = bytes, watchedAtMs = watchedAt, canBeDeleted = deletable)

    @Test
    fun aPileInsideTheBudgetLosesNothing() {
        val plan = StorageTrim.plan(
            listOf(copy(-1L, 600L, 10L), copy(-2L, 400L, 20L)),
            budgetBytes = 2_000L
        )
        assertTrue(plan.delete.isEmpty())
        assertEquals(0L, plan.bytesFreed)
        assertEquals(1_000L, plan.bytesAfter)
    }

    @Test
    fun theOldestWatchGoesFirst() {
        val plan = StorageTrim.plan(
            listOf(copy(-1L, 700L, 900L), copy(-2L, 700L, 100L), copy(-3L, 700L, 500L)),
            budgetBytes = 1_000L
        )
        assertEquals(listOf(-2L, -3L), plan.delete)
    }

    @Test
    fun trimmingStopsTheMomentTheBudgetIsMet() {
        // Dropping the oldest 600 bytes takes the pile from 1500 to 900, which is inside the budget,
        // so the two newer copies are left alone: everything on this list is something the viewer
        // watched, and there is no reason to lose more of it than the cap asks for.
        val plan = StorageTrim.plan(
            listOf(copy(-1L, 600L, 100L), copy(-2L, 600L, 200L), copy(-3L, 300L, 300L)),
            budgetBytes = 1_000L
        )
        assertEquals(listOf(-1L), plan.delete)
        assertEquals(900L, plan.bytesAfter)
    }

    @Test
    fun theFilmOnScreenIsNeverChosen() {
        val plan = StorageTrim.plan(
            listOf(copy(-1L, 900L, 100L), copy(-2L, 900L, 200L)),
            budgetBytes = 0L,
            keepStableId = -1L
        )
        assertEquals(listOf(-2L), plan.delete)
        assertEquals(900L, plan.bytesAfter)
    }

    @Test
    fun aCopyTelegramWillNotReleaseIsSkippedNotReportedAsGone() {
        val plan = StorageTrim.plan(
            listOf(copy(-1L, 900L, 100L, deletable = false), copy(-2L, 100L, 200L)),
            budgetBytes = 0L
        )
        assertEquals(listOf(-2L), plan.delete)
        assertEquals(1_000L, plan.bytesBefore)
        assertEquals(900L, plan.bytesAfter)
    }

    @Test
    fun aZeroBudgetClearsEverythingItIsAllowedTo() {
        val plan = StorageTrim.plan(
            listOf(copy(-1L, 1L, 100L), copy(-2L, 2L, 200L), copy(-3L, 3L, 300L)),
            budgetBytes = 0L
        )
        assertEquals(listOf(-1L, -2L, -3L), plan.delete)
        assertEquals(0L, plan.bytesAfter)
    }

    @Test
    fun anUnwatchedFileHoldingNoBytesIsNotWastedOn() {
        val plan = StorageTrim.plan(
            listOf(copy(-1L, 0L, 100L), copy(-2L, 500L, 200L)),
            budgetBytes = 0L
        )
        assertEquals(listOf(-2L), plan.delete)
    }
}
