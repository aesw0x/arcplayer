package com.aeswox.arcplayer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a part counts as watched through, on the strength of where its play head got to.
 *
 * [TimingTrust] decides whether the credits have a position this app will act on, and [EpisodeTimingsTest]
 * covers that. These tests are the question the shelf asks afterwards: given marks it has accepted, which
 * seconds of the file are past the story.
 */
class StoryIsOverTest {

    private val length = 45 * 60_000L
    private val credits = 43 * 60_000L

    private fun over(
        positionMs: Long,
        outroStartMs: Long? = credits,
        outroEndMs: Long? = null
    ) = StoryIsOver.isOver(
        EpisodeTimings(outroStartMs = outroStartMs, outroEndMs = outroEndMs),
        positionMs
    )

    @Test
    fun theFirstSecondOfTheRollIsTheEndOfTheStory() {
        assertFalse(over(credits - 1))
        assertTrue(over(credits))
    }

    @Test
    fun aHeadFurtherThanTheRollStillSaysTheStoryEnded() {
        // Both the filed stop and the length itself are past the story, and the second one is the case the
        // player's own end report lands in — so the two routes to a finish can never disagree.
        assertTrue(over(credits + 60_000L, outroEndMs = credits + 90_000L))
        assertTrue(over(length))
    }

    @Test
    fun aRollNobodyFiledKeepsTheOlderRule() {
        // No mark means the app has nothing to say about where this part's story stops, and a position
        // ninety seconds from the end is not evidence of a finish — that is what [NextUp] is for.
        assertFalse(over(length - 90_000L, outroStartMs = null))
        assertFalse(over(length, outroStartMs = null))
    }

    @Test
    fun anAnswerThatOnlyKnowsWhereTheCreditsStopIsStillAnAnswer() {
        // The common submission: `credits {2580000 → null}`. Reading only the start is what leaves the
        // roll that runs to the end of the file able to finish a part at all.
        assertTrue(over(credits + 1L, outroEndMs = null))
    }
}
