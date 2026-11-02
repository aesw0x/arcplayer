package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Who has the link, and who is waiting for it.
 *
 * Written against a season pressed all at once, because that is the case where the answer matters: the
 * viewer sees six rows move and six rings that stay still, and only the line says which of the two is
 * actually happening.
 */
class KeepLineTest {

    private fun line(vararg ids: Long) = ids.toList()

    @Test
    fun theFirstAskIsTheOneArriving() {
        val standing = KeepLine.ask(emptyList(), 11L)

        assertEquals(11L, KeepLine.head(standing))
        assertEquals(KeepLine.Place.RUNNING, KeepLine.place(standing, 11L))
    }

    @Test
    fun aSeasonPressedAtOnceStandsInTheOrderItWasAsked() {
        val standing = listOf(11L, 12L, 13L).fold(emptyList<Long>()) { line, id -> KeepLine.ask(line, id) }

        // The shelf hands the episodes over earliest first so the viewer can watch along while the rest
        // arrive, and the line keeps that order rather than the one Telegram happens to answer in.
        assertEquals(line(11L, 12L, 13L), standing)
        assertEquals(KeepLine.Place.RUNNING, KeepLine.place(standing, 11L))
        assertEquals(KeepLine.Place.QUEUED, KeepLine.place(standing, 13L))
    }

    @Test
    fun pressingTheSameFilmTwiceDoesNotPutItInLineTwice() {
        val standing = KeepLine.ask(KeepLine.ask(line(11L), 12L), 11L)

        assertEquals(line(11L, 12L), standing)
    }

    @Test
    fun aReAskOfAFilmAlreadyWaitingLeavesItWhereItWas() {
        val standing = KeepLine.ask(line(11L, 12L, 13L), 13L)

        // The button can be pressed again while a keep waits, and jumping that row to the back of its own
        // line would make the wait longer for having been asked about.
        assertEquals(line(11L, 12L, 13L), standing)
        assertEquals(2, KeepLine.ahead(standing, 13L))
    }

    @Test
    fun theNextInLineTakesTheLinkWhenTheFrontLeaves() {
        val standing = KeepLine.release(line(11L, 12L, 13L), 11L)

        assertEquals(12L, KeepLine.head(standing))
        assertEquals(KeepLine.Place.RUNNING, KeepLine.place(standing, 12L))
        assertEquals(0, KeepLine.ahead(standing, 12L))
    }

    @Test
    fun aTakeBackHandsTheLinkToTheSameNextNameAsAFinishWould() {
        val cancelled = KeepLine.release(line(11L, 12L), 11L)
        val finished = KeepLine.release(line(11L, 12L), 11L)

        assertEquals(finished, cancelled)
        assertEquals(line(12L), cancelled)
    }

    @Test
    fun aFilmNobodyAskedAboutIsNotWaiting() {
        val standing = line(11L, 12L)

        // A row drawn from the shelf has no ask behind it, and the difference between that and a queued
        // one is the difference between a download arrow and a ring that never moves.
        assertEquals(KeepLine.Place.NONE, KeepLine.place(standing, 99L))
        assertEquals(0, KeepLine.ahead(standing, 99L))
    }

    @Test
    fun anEmptyLineHasNoHead() {
        assertNull(KeepLine.head(emptyList()))
        assertEquals(KeepLine.Place.NONE, KeepLine.place(emptyList(), 11L))
        assertEquals(emptyList<Long>(), KeepLine.release(emptyList(), 11L))
    }

    @Test
    fun aFilmLeavingFromTheBackChangesNothingInTheFront() {
        val standing = KeepLine.release(line(11L, 12L, 13L), 13L)

        assertEquals(line(11L, 12L), standing)
        assertEquals(KeepLine.Place.RUNNING, KeepLine.place(standing, 11L))
    }
}
