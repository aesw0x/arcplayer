package com.aeswox.arcplayer.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names here are the ones actually sitting in the shelf, because every rule in the matcher is a
 * bet about how one person types a file name and the only worthlessness is a bet that fails quietly.
 */
class SubtitleMatchTest {

    @Test
    fun aCaptionSuffixIsRecognisedAndAContainerIsNot() {
        assertTrue(SubtitleMatch.isSubtitle("Stranger.Things.S01E01.srt"))
        assertTrue(SubtitleMatch.isSubtitle("Stranger.Things.S01E01.ass"))
        assertTrue(SubtitleMatch.isSubtitle("movie.vtt"))
        assertFalse(SubtitleMatch.isSubtitle("Stranger.Things.S01E01.mkv"))
        assertFalse(SubtitleMatch.isSubtitle("notes.txt"))
        assertFalse(SubtitleMatch.isSubtitle("no_extension_at_all"))
    }

    @Test
    fun anEpisodeMatchesItsOwnCaptionAcrossReleaseTags() {
        assertTrue(
            SubtitleMatch.matches(
                video = "Stranger.Things.S01E01.The.Vanishing.2160p.10bit.BluRay.x265.mkv",
                subtitle = "Stranger.Things.S01E01.The.Vanishing.srt"
            )
        )
    }

    @Test
    fun anEpisodeRefusesTheCaptionBelongingToItsNeighbour() {
        assertFalse(
            SubtitleMatch.matches(
                video = "Stranger.Things.S01E01.The.Vanishing.2160p.mkv",
                subtitle = "Stranger.Things.S01E02.The.Madhouse.srt"
            )
        )
    }

    @Test
    fun anUnderscoredNamePairsWithItsOwnUnderscoredCaption() {
        assertTrue(
            SubtitleMatch.matches(
                video = "Stranger_Things_S03E05_2160p_NF_WEB_DL_x265_10bit_SDR_AAC_5_1_FS87.mkv",
                subtitle = "Stranger_Things_S03E05.srt"
            )
        )
    }

    @Test
    fun aShowThatNumbersItsEpisodesStraightAcrossSeasonsStillPairsCorrectly() {
        assertTrue(
            SubtitleMatch.matches(
                video = "One Piece - E1260 [Dual] 720p @XANIME_UNIVERSE.mkv",
                subtitle = "One Piece - E1260.srt"
            )
        )
        assertFalse(
            SubtitleMatch.matches(
                video = "One Piece - E1260 [Dual] 720p.mkv",
                subtitle = "One Piece - E1261.srt"
            )
        )
    }

    @Test
    fun aFilmCaptionNeedsNoneOfTheCodecsInTheVideoName() {
        assertTrue(
            SubtitleMatch.matches(
                video = "Inception.2010.1080p.BluRay.x264.mkv",
                subtitle = "Inception.2010.srt"
            )
        )
    }

    @Test
    fun aCaptionThatOmitsTheYearStillBelongsToTheFilm() {
        assertTrue(
            SubtitleMatch.matches(
                video = "Inception.2010.1080p.BluRay.x264.mkv",
                subtitle = "Inception.srt"
            )
        )
    }

    @Test
    fun aRemakeIsNotTheFilmItSharesANameWith() {
        assertFalse(
            SubtitleMatch.matches(
                video = "Godzilla.2014.1080p.BluRay.mkv",
                subtitle = "Godzilla.1954.srt"
            )
        )
    }

    @Test
    fun aCaptionForSomethingElseIsNotBorrowed() {
        assertFalse(
            SubtitleMatch.matches(
                video = "Ant-Man.And.The.Wasp.2018.720p.BluRay.x265.mkv",
                subtitle = "Premalu.2024.Malayalam.720p.srt"
            )
        )
    }

    @Test
    fun theCaptionInOnesOwnLanguageWins() {
        assertEquals(
            "Stranger.Things.S01E01.esp.srt",
            SubtitleMatch.pick(
                video = "Stranger.Things.S01E01.The.Vanishing.mkv",
                subtitles = listOf("Stranger.Things.S01E01.eng.srt", "Stranger.Things.S01E01.esp.srt"),
                language = "es"
            )
        )
    }

    @Test
    fun aRegionTaggedLanguageStillCountsAsItself() {
        assertEquals(
            "Stranger.Things.S01E01.en-US.srt",
            SubtitleMatch.pick(
                video = "Stranger.Things.S01E01.The.Vanishing.mkv",
                subtitles = listOf("Stranger.Things.S01E01.hind.srt", "Stranger.Things.S01E01.en-US.srt"),
                language = "en-IN"
            )
        )
    }

    @Test
    fun aFourLetterWordInTheTitleIsNotMistakenForALanguage() {
        assertEquals(
            "The.Esports.2019.esp.srt",
            SubtitleMatch.pick(
                video = "The.Esports.2019.1080p.mkv",
                subtitles = listOf("The.Esports.2019.espn.srt", "The.Esports.2019.esp.srt"),
                language = "es"
            )
        )
    }

    @Test
    fun nothingIsPickedWhenNoCaptionBelongsToThePicture() {
        assertNull(
            SubtitleMatch.pick(
                video = "Inception.2010.1080p.BluRay.x264.mkv",
                subtitles = listOf("Stranger.Things.S01E01.srt"),
                language = "en"
            )
        )
    }

    @Test
    fun aSingleCaptionNeedsNoLanguageToBeChosen() {
        assertEquals(
            "Inception.2010.srt",
            SubtitleMatch.pick(
                video = "Inception.2010.1080p.BluRay.x264.mkv",
                subtitles = listOf("Inception.2010.srt"),
                language = null
            )
        )
    }
}
