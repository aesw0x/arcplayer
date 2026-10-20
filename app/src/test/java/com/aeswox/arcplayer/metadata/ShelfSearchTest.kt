package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a typed word has to be able to find on a shelf of names somebody else typed.
 *
 * The haystacks are the real shapes a channel holds: a release name dotted through with quality tags,
 * an episode title with its number, and the catalogue's own title glued on top of the parsed one.
 */
class ShelfSearchTest {

    private val batman =
        "Batman v Superman: Dawn of Justice 2016 " +
            "Batman.v.Superman.Dawn.of.Justice.2016.IMAX.Remastered.2160p.10bit.x265"

    private val stranger =
        "Stranger Things 2016 S01E03 The Body " +
            "Stranger.Things.S01.E03.The.Body.2160p.10bit.BluRay " +
            "Stranger_Things_S01E03_2160p_NF_WEB_DL_x265"

    @Test
    fun anEmptyQueryIsNotAFilter() {
        assertTrue(ShelfSearch.matches("", batman))
        assertTrue(ShelfSearch.matches("   ", batman))
    }

    @Test
    fun aNameSaidInAnyCaseAndAnyPunctuationFindsTheFile() {
        assertTrue(ShelfSearch.matches("stranger things", stranger))
        assertTrue(ShelfSearch.matches("STRANGER-THINGS", stranger))
        assertTrue(ShelfSearch.matches("things stranger", stranger))
    }

    @Test
    fun anEpisodeNumberTypedTheWayPeopleTypeItFindsTheFile() {
        assertTrue(ShelfSearch.matches("s01e03", stranger))
        assertTrue(ShelfSearch.matches("s01 e03", stranger))
        assertTrue(ShelfSearch.matches("1e3", stranger))
    }

    @Test
    fun anEpisodeTitleIsEnoughOnItsOwn() {
        assertTrue(ShelfSearch.matches("the body", stranger))
    }

    @Test
    fun aYearSeparatesTwoTitlesThatShareAName() {
        assertTrue(ShelfSearch.matches("dawn of justice 2016", batman))
        assertFalse(ShelfSearch.matches("dawn of justice 1997", batman))
    }

    @Test
    fun everyWordOfTheQueryHasToLandSomewhere() {
        assertFalse(ShelfSearch.matches("batman superman justice league", batman))
    }

    @Test
    fun aWordHalfInsideTwoNamesStillCounts() {
        assertTrue(ShelfSearch.matches("superma", batman))
    }

    @Test
    fun aQualityTagIsNotMistakenForATitle() {
        assertFalse(ShelfSearch.matches("imax stranger things", stranger))
    }
}
