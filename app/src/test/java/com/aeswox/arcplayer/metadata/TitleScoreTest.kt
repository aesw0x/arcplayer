package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleScoreTest {

    private fun candidate(title: String, year: Int? = null) = TitleScore.Candidate(title, year)

    @Test
    fun punctuationAndCaseDoNotMatter() {
        assertEquals(TitleScore.EXACT, TitleScore.of(candidate("Spider-Man"), "Spider Man", null))
        assertEquals(TitleScore.EXACT, TitleScore.of(candidate("one piece"), "One Piece", null))
    }

    @Test
    fun aNameThatCarriesTheWantedNameIsAPrefixNotJustAFragment() {
        assertEquals(TitleScore.PREFIX, TitleScore.of(candidate("Blade Runner 2049"), "Blade Runner", null))
        assertEquals(TitleScore.PREFIX, TitleScore.of(candidate("Rush"), "Rush Hour", null))
    }

    @Test
    fun anUnrelatedNameScoresNothing() {
        assertEquals(0, TitleScore.of(candidate("The Office"), "One Piece", null))
    }

    @Test
    fun yearAgreementIsABonusAndDisagreementACost() {
        assertEquals(TitleScore.EXACT + TitleScore.SAME_YEAR, TitleScore.of(candidate("Inception", 2010), "Inception", 2010))
        assertEquals(TitleScore.EXACT + TitleScore.NEAR_YEAR, TitleScore.of(candidate("Inception", 2011), "Inception", 2010))
        assertEquals(TitleScore.EXACT + TitleScore.WRONG_YEAR, TitleScore.of(candidate("Inception", 1997), "Inception", 2010))
    }

    @Test
    fun anAbsentYearIsNeutralRatherThanWrong() {
        assertEquals(TitleScore.EXACT, TitleScore.of(candidate("Inception", 2010), "Inception", null))
        assertEquals(TitleScore.EXACT, TitleScore.of(candidate("Inception"), "Inception", 2010))
    }

    @Test
    fun theClosestNameWinsEvenWhenItIsNotFirst() {
        val rows = listOf(candidate("Blade Runner", 1982), candidate("Blade Runner 2049", 2017))
        assertEquals(1, TitleScore.bestIndex(rows, "Blade Runner 2049", 2017))
        assertEquals(0, TitleScore.bestIndex(rows, "Blade Runner", 1982))
    }

    @Test
    fun tiesKeepTheCataloguesOwnOrder() {
        val rows = listOf(candidate("One Piece", 1999), candidate("One Piece", 2000))
        assertEquals(0, TitleScore.bestIndex(rows, "One Piece", null))
    }

    @Test
    fun nothingIsAcceptedBelowTheFloor() {
        val rows = listOf(candidate("The Story of Us"), candidate("Spirited Away"))
        assertEquals(-1, TitleScore.bestIndex(rows, "One Piece", 2001))
    }

    @Test
    fun spacedOutInitialsStillMatchTheRealTitle() {
        val rows = listOf(candidate("E.T. the Extra-Terrestrial", 1982))
        assertEquals(0, TitleScore.bestIndex(rows, "E T", 1982))
    }

    @Test
    fun anAmpersandStandsForTheWordAnd() {
        // IMDb writes "Night & Day" and the uploader typed "Night and Day" for the same film. Left as
        // punctuation-that-vanishes the two names never meet and the row is refused like a stranger.
        assertEquals(
            TitleScore.EXACT + TitleScore.SAME_YEAR,
            TitleScore.of(candidate("Virginia Woolf's Night & Day", 2026), "Virginia Woolfs Night and Day", 2026)
        )
        assertEquals(TitleScore.EXACT, TitleScore.of(candidate("Law & Order"), "Law and Order", null))
        assertEquals(TitleScore.EXACT, TitleScore.of(candidate("Lilo and Stitch"), "Lilo & Stitch", null))
    }

    @Test
    fun aNumberingWordDoesNotMakeTwoNamesOfTheSameFilm() {
        // Measured against the live catalogues: the file says "John.Wick.4", TMDB, IMDb and Wikipedia
        // all answer "John Wick: Chapter 4", and that gap alone refused the film.
        assertEquals(
            TitleScore.EXACT + TitleScore.SAME_YEAR,
            TitleScore.of(candidate("John Wick: Chapter 4", 2023), "John Wick 4", 2023)
        )
        assertEquals(
            TitleScore.EXACT + TitleScore.SAME_YEAR,
            TitleScore.of(candidate("John Wick: Chapter 3 - Parabellum", 2019), "John Wick 3 Parabellum", 2019)
        )
        assertEquals(TitleScore.key("John Wick Chapter 2"), TitleScore.key("John Wick 2"))
    }

    @Test
    fun theDigitStillTellsTheSequelsApart() {
        // Dropping "Chapter" must not turn the franchise into one name: 2014's film is not 2017's,
        // and the year is the only thing left disagreeing once the numbering word is gone.
        val rows = listOf(candidate("John Wick", 2014), candidate("John Wick: Chapter 2", 2017))
        assertEquals(0, TitleScore.bestIndex(rows, "John Wick", 2014))
        assertEquals(1, TitleScore.bestIndex(rows, "John Wick 2", 2017))
        assertEquals(-1, TitleScore.bestIndex(listOf(candidate("John Wick: Chapter 2", 2017)), "John Wick", 2014))
    }

    @Test
    fun theFileThatFellThroughToWikipediaIsTheSameFilmImdbNamed() {
        val guess = MediaNameParser.parse("Virginia.Woolf's.Night.and.Day.2026.720p.10bit.WEBRip.6C.mkv")
        val rows = listOf(
            TitleScore.Candidate("Virginia Woolf's Night & Day", 2026),
            TitleScore.Candidate("Woolf", 2018),
            TitleScore.Candidate("Night", 2021)
        )
        assertEquals(
            0,
            TitleScore.bestIndex(
                rows,
                (guess as MediaGuess.Movie).title,
                guess.year
            )
        )
    }

    @Test
    fun aLongerNameThatStartsLikeOursNeedsTheYearToBackItUp() {
        // AniList really does have an anime called "Interstellar Wanderer", and it is not the 2014 film.
        val rows = listOf(candidate("Interstellar Wanderer"))
        assertEquals(-1, TitleScore.bestIndex(rows, "Interstellar", 2014))
        assertEquals(0, TitleScore.bestIndex(listOf(candidate("Demon Slayer", 2020)), "Demon Slayer", 2020))
    }
}
