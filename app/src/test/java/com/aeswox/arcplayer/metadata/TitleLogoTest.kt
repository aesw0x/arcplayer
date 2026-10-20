package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ranking rules for the wordmark, written against the shapes TMDB actually returns for one title:
 * a voted-on hero image, several zero-vote uploads of varying size, other languages, and the
 * occasional vector-only entry.
 */
class TitleLogoTest {

    private fun rendition(
        path: String,
        language: String? = "en",
        widthPx: Int = 1200,
        voteAverage: Double = 0.0,
        voteCount: Int = 0
    ) = TitleLogo.Rendition(path, language, widthPx, voteAverage, voteCount)

    @Test
    fun vectorOnlyTitlesGoWithoutRatherThanPullInADecoder() {
        assertNull(
            TitleLogo.best(
                listOf(
                    rendition("/spiderman.svg", widthPx = 5000),
                    rendition("/spiderman-small.svg", widthPx = 900)
                )
            )
        )
    }

    @Test
    fun aWordmarkTooSmallToReadAtFullScreenIsNotWorthShowing() {
        assertNull(TitleLogo.best(listOf(rendition("/tiny.png", widthPx = 320))))
        assertEquals("/justmakesit.png", TitleLogo.best(listOf(rendition("/justmakesit.png", widthPx = 400)))?.filePath)
    }

    @Test
    fun theCommunitysChoiceBeatsALargerUploadNobodyRated() {
        val chosen = TitleLogo.best(
            listOf(
                rendition("/unvotedHuge.png", widthPx = 14000),
                rendition("/votedSmall.png", widthPx = 794, voteAverage = 10.0, voteCount = 3)
            )
        )
        assertEquals("/votedSmall.png", chosen?.filePath)
    }

    @Test
    fun unratedRenditionsFallBackToTheSharpestOne() {
        val chosen = TitleLogo.best(
            listOf(
                rendition("/wide.png", widthPx = 5000, voteAverage = 0.0, voteCount = 0),
                rendition("/narrow.png", widthPx = 800, voteAverage = 0.0, voteCount = 0)
            )
        )
        assertEquals("/wide.png", chosen?.filePath)
    }

    @Test
    fun equalRatingsAreBrokenByHowManyPeopleVoted() {
        val chosen = TitleLogo.best(
            listOf(
                rendition("/oneVote.png", widthPx = 787, voteAverage = 0.958, voteCount = 4),
                rendition("/oneVoter.png", widthPx = 7776, voteAverage = 0.958, voteCount = 1)
            )
        )
        assertEquals("/oneVote.png", chosen?.filePath)
    }

    @Test
    fun aTitleOwnsItsLanguageVersionOfTheWordmarkWhenThereIsOne() {
        val chosen = TitleLogo.best(
            listOf(
                rendition("/english.png", language = "en", widthPx = 14481, voteAverage = 8.0, voteCount = 10),
                rendition("/german.png", language = "de", widthPx = 900, voteAverage = 1.0, voteCount = 1)
            ),
            preferredLanguage = "de"
        )
        assertEquals("/german.png", chosen?.filePath)
    }

    @Test
    fun aMissingPreferredLanguageFallsBackToEnglishThenToAnything() {
        val toEnglish = TitleLogo.best(
            listOf(
                rendition("/english.png", language = "en", voteAverage = 1.0),
                rendition("/japanese.png", language = "ja", voteAverage = 9.0)
            ),
            preferredLanguage = "ml"
        )
        assertEquals("/english.png", toEnglish?.filePath)

        val anything = TitleLogo.best(
            listOf(
                rendition("/japanese.png", language = "ja", voteAverage = 9.0),
                rendition("/untagged.png", language = null, voteAverage = 2.0)
            ),
            preferredLanguage = "ml"
        )
        assertEquals("/japanese.png", anything?.filePath)
    }

    @Test
    fun noRenditionsAtAllIsAnAnswerNotAFailure() {
        assertNull(TitleLogo.best(emptyList()))
    }
}
