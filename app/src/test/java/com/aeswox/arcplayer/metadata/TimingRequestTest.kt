package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which part a file name turns into a question about.
 *
 * The rule worth protecting is the one that looks like a mistake: a show numbered by its place in the
 * whole run is asked with that place, not with a count from its season's start. That is how the catalogue
 * files those shows and how the timing services mark them, so One Piece 126 is season four episode 126 —
 * asking for its thirty-fifth episode returns a part that does not exist.
 */
class TimingRequestTest {

    private fun season(number: Int, episodeCount: Int) = SeasonCard(number, "S$number", null, episodeCount)

    /** One Piece's real early season lengths, which is the run that forces the rule above. */
    private val onePiece = listOf(
        season(0, 39),
        season(1, 61),
        season(2, 16),
        season(3, 14),
        season(4, 39),
        season(5, 13)
    )

    private fun episode(series: String, season: Int?, number: Int) =
        MediaGuess.Episode(series, season, number, null)

    @Test
    fun aNameThatWritesItsSeasonNeedsNoLengthsToBeAsked() {
        val guess = episode("Dexter", 4, 8)
        assertEquals(TimingRequest(1396, 4, 8), timingRequestFor(1396, guess, emptyList()))
    }

    @Test
    fun aRunNumberIsAskedWithItsOwnNumberUnderTheSeasonItFallsIn() {
        val request = timingRequestFor(37854, episode("One Piece", null, 126), onePiece)
        assertEquals(TimingRequest(37854, 4, 126), request)
    }

    @Test
    fun aRunNumberNobodyHasGivenLengthsForIsNotAskedAtAll() {
        // Half a pair is a question the service refuses outright, and the pass would count that as a part
        // nobody answered rather than as a part nobody could place.
        assertNull(timingRequestFor(37854, episode("One Piece", null, 126), emptyList()))
        assertNull(timingRequestFor(37854, episode("One Piece", null, 126), onePiece.map { season(it.number, 0) }))
    }

    @Test
    fun aRunNumberPastTheEndOfTheShowIsNotPlacedAnywhere() {
        assertNull(timingRequestFor(37854, episode("One Piece", null, 196), onePiece))
    }

    @Test
    fun aFilmIsAskedWithNeitherHalfOfThePair() {
        assertEquals(TimingRequest(27205, null, null), timingRequestFor(27205, MediaGuess.Movie("Inception", 2010), onePiece))
    }

    @Test
    fun nothingIsAskedWithoutACatalogueId() {
        // The service is keyed by the TMDB id, and a title matched elsewhere has none to ask with.
        assertNull(timingRequestFor(null, episode("Dexter", 4, 8), onePiece))
        assertNull(timingRequestFor(null, MediaGuess.Movie("Inception", 2010), onePiece))
        assertNull(timingRequestFor(null, MediaGuess.Unnamed("dexter.s04e08.1080p.mkv"), onePiece))
    }

    @Test
    fun twoCopiesOfOnePartAskTheSameQuestion() {
        // Three uploads of one episode are one work with one opening, which is why the answer is filed
        // against the part rather than against whichever file happened to be tapped.
        val best = timingRequestFor(1396, MediaNameParser.parse("Dexter.S04E08.1080p.BluRay.x264.mkv"), onePiece)
        val worse = timingRequestFor(1396, MediaNameParser.parse("Dexter.S04E08.480p.WEBRip.x264.mkv"), onePiece)
        assertEquals(best, worse)
        assertEquals("1396|4|8", best?.key)
    }
}
