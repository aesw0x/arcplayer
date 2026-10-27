package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names here are the shapes found in the actual chat archives this app plays, plus the awkward
 * catalogue cases the rules could easily break on.
 */
class MediaNameParserTest {

    private val thisYear = 2026

    private fun parse(name: String) = MediaNameParser.parse(name, thisYear)

    @Test
    fun aBracketedYearEndsTheNameEvenWhenTheTailIsFullOfWordsTheListDoesNotKnow() {
        // Reported from the shelf. "chi" is the code for Mandarin and also the end of "Shang-Chi", so
        // the first word read as a release tag, the name was cut to nothing, and a file with no name
        // is never asked about at all — which is why it wore a grey box instead of a poster.
        assertEquals(
            MediaGuess.Movie("Shang-Chi and The Legend of The Ten Rings", 2021),
            parse("Shang-Chi and The Legend of The Ten Rings (2021) DSNP WEB-DL.mkv")
        )
        assertEquals(MediaGuess.Movie("Ben-Hur", 1959), parse("Ben-Hur (1959) 1080p BluRay x264-PBS.mkv"))
        assertEquals(MediaGuess.Movie("Kan", 2024), parse("Kan.2024.Tamil.1080p.mkv"))
    }

    @Test
    fun everythingAfterTheYearIsTailWhateverItIsCalled() {
        // The tag list is a bet about how uploaders type. The year is a fact about where the name
        // ends, so an unknown tail costs nothing once the year is read as the boundary.
        assertEquals(
            MediaGuess.Movie("Inception", 2010),
            parse("Inception (2010) ZZ9 UltraPack SomethingNew 1080p.mkv")
        )
    }

    @Test
    fun aTimestampIsNotReadAsAYear() {
        // A device stamping its own export has a date glued together, and the digits inside it are not
        // a year the catalogue can be searched with.
        assertEquals(MediaGuess.Movie("Tokyo Story 20260805", null), parse("Tokyo Story 20260805.mkv"))
    }

    @Test
    fun aYearStatedTwiceIsTheReleaseDateAndNotPartOfTheName() {
        // Both of these are names someone typed with care, and both left the grey box on the shelf: the
        // year written into the title and again in brackets puts the bare one inside the head, where a
        // catalogue reads it as part of the name and answers nothing at all.
        assertEquals(MediaGuess.Movie("Inception", 2010), parse("Inception 2010 (2010) English 720p.mkv"))
        assertEquals(MediaGuess.Movie("Tenkon", 2025), parse("Tenkon 2025 (2025) {Tamil} [HQ] {HDRip}.mkv"))
    }

    @Test
    fun aFranchiseWordInsideTheNameStaysWithIt() {
        // The number that ends a title is its own, and the year comes after it — the cut is the year,
        // not the first digit the tag list happens to recognise.
        assertEquals(
            MediaGuess.Episode("Avatar The Last Airbender", 1, 3, "The Switch Part 2"),
            parse("Avatar The Last Airbender S01E03 The Switch Part 2 720p HEVC x264-EDHD [HQ].mkv")
        )
        assertEquals(
            MediaGuess.Movie("The Book of Eli", 2010),
            parse("The Book of Eli (2010) Multi-aud(Eng+Sp+Fre).mkv")
        )
    }

    @Test
    fun anAmpersandIsPartOfTheNameAndNotNoise() {
        // Deleting the symbol left "Tom Jerry" as the query, which matches neither spelling the
        // catalogues use once TitleScore reads & as the word it stands for.
        assertEquals(MediaGuess.Movie("Tom & Jerry", 2021), parse("Tom.&.Jerry.2021.1080p.x264.mkv"))
        assertEquals(MediaGuess.Movie("Night & Day", 1996), parse("Night & Day (1996) [WEBRip] 720p.mkv"))
    }

    @Test
    fun episodeWithBracketedTagsAndWatermark() {
        assertEquals(
            MediaGuess.Episode("One Piece", null, 206, null),
            parse("One Piece - E206 [Dual] 720p @XANIME_UNIVERSE.mkv")
        )
    }

    @Test
    fun episodeWithDotSeparatedWords() {
        assertEquals(
            MediaGuess.Episode("One Piece", null, 205, null),
            parse("One.Piece.E205.Dual.1080p.x264.mkv")
        )
    }

    @Test
    fun seasonAndEpisode() {
        assertEquals(
            MediaGuess.Episode("Attack on Titan", 4, 5, null),
            parse("Attack.on.Titan.S04E05.720p.WEB-DL.mkv")
        )
    }

    @Test
    fun episodeTitleAfterTheNumber() {
        assertEquals(
            MediaGuess.Episode("Friends", 1, 5, "The One With White Horses"),
            parse("Friends.S01E05-The.One.With.White.Horses.1080p.mkv")
        )
    }

    @Test
    fun americanShorthandForSeasonAndEpisode() {
        assertEquals(
            MediaGuess.Episode("Breaking Bad", 2, 7, null),
            parse("Breaking.Bad.2x07.720p.mkv")
        )
    }

    @Test
    fun wordSpelledEpisode() {
        assertEquals(
            MediaGuess.Episode("Dark", 1, 3, null),
            parse("Dark Episode 3 (Season 1) [1080p].mkv")
        )
    }

    @Test
    fun seasonWithoutEpisodeStaysWithTheEpisodeNumber() {
        assertEquals(
            MediaGuess.Episode("Naruto Shippuden", 5, 112, null),
            parse("Naruto Shippuden Season 5 E112 720p.mkv")
        )
    }

    @Test
    fun plainMovieWithYearAndTagTail() {
        assertEquals(
            MediaGuess.Movie("Inception", 2010),
            parse("Inception.2010.1080p.BluRay.x264-PLAYREC.mkv")
        )
    }

    @Test
    fun yearInsideBrackets() {
        assertEquals(
            MediaGuess.Movie("Interstellar", 2014),
            parse("Interstellar (2014) [1080p BluRay].mkv")
        )
    }

    @Test
    fun titleThatEndsInANumberIsNotAReleaseYear() {
        // 2017 sits after 2049, so the last plausible year is the real one and 2049 stays in the name.
        assertEquals(
            MediaGuess.Movie("Blade Runner 2049", 2017),
            parse("Blade.Runner.2049.2017.2160p.HDR.x265.mkv")
        )
    }

    @Test
    fun titledOnlyWithAYearKeepsItsOwnName() {
        assertEquals(MediaGuess.Movie("300", null), parse("300.mkv"))
        assertEquals(MediaGuess.Movie("300", 2006), parse("300.2006.1080p.mkv"))
    }

    @Test
    fun yearIsTheRightmostOneThatLeavesATitleBehind() {
        assertEquals(MediaGuess.Movie("2012", 2009), parse("2012.2009.1080p.BluRay.mkv"))
    }

    @Test
    fun aYearInsideTheTitleSurvivesWhenNothingFollowsIt() {
        assertEquals(
            MediaGuess.Movie("2001 A Space Odyssey", null),
            parse("2001.A.Space.Odyssey.mkv")
        )
        assertEquals(
            MediaGuess.Movie("2001 A Space Odyssey", 1968),
            parse("2001.A.Space.Odyssey.1968.Remastered.1080p.mkv")
        )
    }

    @Test
    fun hyphensInsideTitlesAreKept() {
        assertEquals(
            MediaGuess.Movie("Spider-Man Into the Spider-Verse", 2018),
            parse("Spider-Man.Into.the.Spider-Verse.2018.1080p.mkv")
        )
        assertEquals(MediaGuess.Movie("WALL-E", 2008), parse("WALL-E.2008.BluRay.mkv"))
    }

    @Test
    fun resolutionNeverMasksTheYear() {
        assertEquals(MediaGuess.Movie("Ghost in the Shell", 2017), parse("Ghost.in.the.Shell.1920x1080.2017.mkv"))
    }

    @Test
    fun languageTagsAndSizesAreDropped() {
        assertEquals(
            parse("Vikram.Verma.2017.Hindi.Dual.Audio.1080p.6.5GB.mkv"),
            MediaGuess.Movie("Vikram Verma", 2017)
        )
    }

    @Test
    fun spacesAndUnderscoresBothSeparateWords() {
        assertEquals(MediaGuess.Movie("The Dark Knight", 2008), parse("The_Dark_Knight_2008_REMASTERED_1080p.mkv"))
    }

    @Test
    fun anUnderscoreDoesNotHideThePartNumberFromTheSeasonMarker() {
        // \b reads an underscore as part of a word, so this name has to be spaced out before any
        // marker pattern looks at it. Left as it is, the file becomes a film called "S03E01".
        assertEquals(
            MediaGuess.Episode("Stranger Things", 3, 1, null),
            parse("Stranger_Things_S03E01_2160p_NF_WEB_DL_x265_10bit_SDR_AAC_5_1_FS87.mkv")
        )
    }

    @Test
    fun anUnderscoreDoesNotTurnTheEndOfAWordIntoAnEpisodeMarker() {
        assertEquals(MediaGuess.Movie("WALL E", 2008), parse("WALL_E.2008.BluRay.mkv"))
        assertEquals(MediaGuess.Movie("WALL E", 2008), parse("WALL_E_2008_BluRay.mkv"))
    }

    @Test
    fun movieWithNoPunctuationAtAll() {
        assertEquals(MediaGuess.Movie("Parasite", null), parse("Parasite.mkv"))
    }

    @Test
    fun directoryPrefixIsIgnored() {
        assertEquals(
            MediaGuess.Movie("Casablanca", 1942),
            parse("Archive/Movies/Casablanca.1942.BluRay.mkv")
        )
    }

    @Test
    fun aNameThatGivesNothingKeepsItselfAsTheFallback() {
        assertEquals(MediaGuess.Movie("Unknown", null), parse("Unknown"))
        assertEquals(MediaGuess.Unnamed("@XANIME_UNIVERSE"), parse("@XANIME_UNIVERSE.mkv"))
    }

    @Test
    fun episodeDisplaySaysWhichEpisodeItIs() {
        val guess = parse("One Piece - E206 [Dual] 720p @XANIME_UNIVERSE.mkv")
        assertEquals("One Piece · Episode 206", guess.fallbackTitle)
    }

    @Test
    fun movieDisplayCarriesTheYear() {
        assertEquals("Inception (2010)", parse("Inception.2010.1080p.mkv").fallbackTitle)
    }

    @Test
    fun aBareLetterBeforeDigitsDoesNotMakeAnEpisodeOfAWord() {
        // The e of "Step" and of "E.T." is not the start of a word followed by a part number.
        assertEquals(MediaGuess.Movie("Step Brothers", 2008), parse("Step.Brothers.2008.mkv"))
        // Dots are word separators, so the title arrives spaced out; the catalogue puts the dots back.
        assertEquals(MediaGuess.Movie("E T", 1982), parse("E.T.1982.1080p.mkv"))
    }

    @Test
    fun extensionOnlyStrippedWhenItIsAContainer() {
        assertEquals(MediaGuess.Movie("Blade Runner 2049", null), parse("Blade.Runner.2049"))
    }

    @Test
    fun aDeviceCountingItsOwnExportsIsNotATitle() {
        // These cost a request to a free catalogue for a name that can never match, so they are
        // recognised as nothing and left with the file name on the card.
        assertTrue(parse("Video 485490688") is MediaGuess.Unnamed)
        assertTrue(parse("IMG_7636.mp4") is MediaGuess.Unnamed)
        assertTrue(parse("WhatsApp Video 2026-08-25 at 1.41.15 PM.mp4") is MediaGuess.Unnamed)
        assertTrue(parse("Screen_Recording_20260805_214627.mp4") is MediaGuess.Unnamed)
        assertTrue(parse("MIUI 12 620") is MediaGuess.Unnamed)
    }

    @Test
    fun aRealTitleThatMerelyStartsLikeAFileNumberStillGetsAsked() {
        assertEquals(MediaGuess.Movie("300", null), parse("300.mkv"))
        assertEquals(MediaGuess.Movie("Picnic", 1956), parse("Picnic.1956.1080p.mkv"))
    }

    @Test
    fun anUnclosedBracketFromACutOffNameIsNoiseNotPartOfTheTitle() {
        // The release block loses its closing bracket in transit, and an opener left behind is not a
        // separator: "[English" is not the tag "english", so the cut that ends the title never fires
        // and the app asks the catalogues for a film called "Ballerina [English".
        assertEquals(
            MediaGuess.Movie("Ballerina", 2025),
            parse("Ballerina (2025) [English - 1080p HQ HDRip - x264 - [DDP 5..mkv")
        )
        assertEquals(MediaGuess.Movie("Inception", 2010), parse("Inception (2010) [English 1080p.mkv"))
    }
}
