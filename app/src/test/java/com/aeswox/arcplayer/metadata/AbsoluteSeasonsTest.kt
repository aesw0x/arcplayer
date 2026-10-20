package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Placing a run-numbered file under the season the catalogue files that number in.
 *
 * Written against One Piece's real season lengths, because it is the show that surfaced the rule: it
 * is uploaded as "E126" with no season named, and the catalogue numbers its episodes absolutely
 * inside each arc season rather than restarting at one.
 */
class AbsoluteSeasonsTest {

    private fun season(number: Int, episodeCount: Int, title: String = "S$number") =
        SeasonCard(number, title, null, episodeCount)

    private val onePiece = listOf(
        season(0, 39, "Specials"),
        season(1, 61),
        season(2, 16),
        season(3, 14),
        season(4, 39),
        season(5, 13),
        season(6, 52)
    )

    @Test
    fun aNumberLandsInsideTheSeasonThatCountsIt() {
        assertEquals(1, AbsoluteSeasons.seasonFor(1, onePiece))
        assertEquals(1, AbsoluteSeasons.seasonFor(61, onePiece))
        assertEquals(2, AbsoluteSeasons.seasonFor(62, onePiece))
    }

    @Test
    fun theEpisodeThatPromptedTheRuleFindsItsArcSeason() {
        assertEquals(4, AbsoluteSeasons.seasonFor(126, onePiece))
        assertEquals(5, AbsoluteSeasons.seasonFor(131, onePiece))
        assertEquals(6, AbsoluteSeasons.seasonFor(144, onePiece))
    }

    @Test
    fun specialsDoNotShiftTheNumbering() {
        // Season 0 carries 39 episodes of its own, numbered from one again. Counting them into the run
        // would push every later file two seasons off, so the answer must not change with or without
        // them, or with the rows arriving out of order.
        val withoutSpecials = onePiece.filter { it.number > 0 }
        assertEquals(
            AbsoluteSeasons.seasonFor(126, withoutSpecials),
            AbsoluteSeasons.seasonFor(126, onePiece)
        )
        assertEquals(4, AbsoluteSeasons.seasonFor(126, onePiece.reversed()))
    }

    @Test
    fun aNumberPastTheEndOfTheRunIsNotPlacedAnywhere() {
        assertNull(AbsoluteSeasons.seasonFor(196, onePiece))
        assertNull(AbsoluteSeasons.seasonFor(0, onePiece))
        assertNull(AbsoluteSeasons.seasonFor(-3, onePiece))
    }

    @Test
    fun unknownSeasonLengthsYieldNoGuessRatherThanAWrongShelfRow() {
        val lengthsMissing = onePiece.map { it.copy(episodeCount = 0) }
        assertNull(AbsoluteSeasons.seasonFor(126, lengthsMissing))
        assertNull(AbsoluteSeasons.seasonFor(126, emptyList()))
    }
}
