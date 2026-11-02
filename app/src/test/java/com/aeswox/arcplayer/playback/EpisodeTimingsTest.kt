package com.aeswox.arcplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeTimingsTest {

    private val length = 45 * 60_000L

    private fun believe(
        introEndMs: Long? = null,
        outroStartMs: Long? = null,
        outroEndMs: Long? = null,
        source: TimingSource = TimingSource.IntroDb,
        claimedLengthMs: Long? = null,
        durationMs: Long = length
    ) = TimingTrust.believe(
        TimingAnswer(source, introEndMs, outroStartMs, outroEndMs, claimedLengthMs),
        durationMs
    )

    @Test
    fun anOpeningOfTheUsualLengthIsBelieved() {
        assertEquals(90_000L, believe(introEndMs = 90_000L).introEndMs)
    }

    @Test
    fun anAnswerThatSaysNothingAboutAnOpeningPlacesNothingThere() {
        assertEquals(null, believe().introEndMs)
    }

    @Test
    fun anOpeningShorterThanAFewSecondsIsAColdOpenRatherThanATitleSequence() {
        assertEquals(null, believe(introEndMs = 4_000L).introEndMs)
        assertEquals(null, believe(introEndMs = TimingTrust.MIN_INTRO_MS - 1).introEndMs)
        assertEquals(TimingTrust.MIN_INTRO_MS, believe(introEndMs = TimingTrust.MIN_INTRO_MS).introEndMs)
    }

    @Test
    fun anOpeningPastAQuarterOfThePartIsNotAboutThisPart() {
        assertEquals(null, believe(introEndMs = length / 4 + 1).introEndMs)
        assertEquals(length / 4, believe(introEndMs = length / 4).introEndMs)
    }

    @Test
    fun creditsInTheLastMinuteAreBelieved() {
        val start = 43 * 60_000L + 30_000L
        assertEquals(start, believe(outroStartMs = start).outroStartMs)
    }

    @Test
    fun creditsThatBeginBeforeThePartIsHalfOverAreNotCredits() {
        // Each of these carries its own stop, because without one a mark at half time is refused by the
        // width of the roll it leaves rather than by where it begins, and this is about where it begins.
        val half = length / 2
        assertEquals(null, believe(outroStartMs = 20 * 60_000L, outroEndMs = 21 * 60_000L).outroStartMs)
        assertEquals(half, believe(outroStartMs = half, outroEndMs = half + 60_000L).outroStartMs)
        assertEquals(null, believe(outroStartMs = half - 1, outroEndMs = half + 59_000L).outroStartMs)
    }

    @Test
    fun creditsMarkedAtTheVeryEndOfThePartHaveNoRoomForACard() {
        assertEquals(null, believe(outroStartMs = length - TimingTrust.MIN_OUTRO_TAIL_MS + 1).outroStartMs)
        assertEquals(
            length - TimingTrust.MIN_OUTRO_TAIL_MS,
            believe(outroStartMs = length - TimingTrust.MIN_OUTRO_TAIL_MS).outroStartMs
        )
    }

    @Test
    fun aRollThatRunsToTheEndOfTheFileNeedsNoSubmittedStop() {
        val start = 43 * 60_000L
        assertEquals(length, believe(outroStartMs = start).outroEndMs)
    }

    @Test
    fun aLongCreditRollNobodyPinnedTheEndOfIsStillACreditRoll() {
        // The shape of most of what the service actually answers — `credits [{start → null}]` — and the
        // one that used to defeat the width rule: measuring a roll with no end against the whole of the
        // part refused the start, which is the only number the Next up card is made of, and the card went
        // back to waiting for the last seconds of an episode whose credits were known.
        val halfHourPlus = 50 * 60_000L
        val credits = 41 * 60_000L + 53_000L
        assertEquals(credits, believe(outroStartMs = credits, durationMs = halfHourPlus).outroStartMs)
        // The same start, once somebody has also filed where it stops, is a pair, and a nine-minute roll
        // is a different cut being described.
        assertEquals(
            null,
            believe(outroStartMs = credits, outroEndMs = halfHourPlus, durationMs = halfHourPlus).outroStartMs
        )
    }

    @Test
    fun aRollThatStopsBeforeTheEndKeepsItsStop() {
        val start = 43 * 60_000L
        val stop = 44 * 60_000L + 20_000L
        assertEquals(stop, believe(outroStartMs = start, outroEndMs = stop).outroEndMs)
    }

    @Test
    fun aStopThatRunsPastTheEndOfTheFileIsPulledBackToIt() {
        assertEquals(length, believe(outroStartMs = 43 * 60_000L, outroEndMs = length + 60_000L).outroEndMs)
    }

    @Test
    fun aRollThatEndsBeforeItBeganIsTwoNumbersAboutDifferentFiles() {
        val timings = believe(outroStartMs = 30 * 60_000L, outroEndMs = 20 * 60_000L)
        assertEquals(null, timings.outroStartMs)
        assertEquals(null, timings.outroEndMs)
    }

    @Test
    fun anImpossiblyLongRollIsRefusedRatherThanCutShort() {
        // Five minutes of credits on a forty-five minute part is either a submission about a different
        // cut or a start typed in the wrong place, and moving the start later would be the app inventing
        // the one number it has just said it does not trust.
        val timings = believe(outroStartMs = 40 * 60_000L, outroEndMs = 45 * 60_000L)
        assertEquals(null, timings.outroStartMs)
        assertEquals(null, timings.outroEndMs)
        // Three minutes of credits is the widest a show is allowed to be and is believed to the second.
        assertEquals(
            43 * 60_000L,
            believe(outroStartMs = 40 * 60_000L, outroEndMs = 43 * 60_000L).outroEndMs
        )
    }

    @Test
    fun oneBadMarkDoesNotHideAGoodOne() {
        val timings = believe(introEndMs = 90_000L, outroStartMs = 20 * 60_000L)
        assertEquals(90_000L, timings.introEndMs)
        assertEquals(null, timings.outroStartMs)
    }

    @Test
    fun anOpeningAndACreditRollBelievedTogetherAreTwoMarksAndNotOne() {
        val timings = believe(introEndMs = 90_000L, outroStartMs = 43 * 60_000L)
        assertEquals(90_000L, timings.introEndMs)
        assertEquals(43 * 60_000L, timings.outroStartMs)
    }

    @Test
    fun aPartTooShortToTrackHasNoMarksAtAll() {
        val timings = believe(introEndMs = 6_000L, outroStartMs = 15_000L, durationMs = 20_000L)
        assertEquals(null, timings.introEndMs)
        assertEquals(null, timings.outroStartMs)
    }

    @Test
    fun aFileWhoseLengthIsStillUnknownHasNoMarksAtAll() {
        // The placeholder a document carries until its container opens, and every fraction of it is zero.
        val timings = believe(introEndMs = 90_000L, outroStartMs = 43 * 60_000L, durationMs = 1L)
        assertEquals(null, timings.introEndMs)
        assertEquals(null, timings.outroStartMs)
    }

    @Test
    fun anAniSkipAnswerAboutADifferentLengthIsRefusedEntirely() {
        // The episode number is the one thing a show on Telegram and the same show on MyAnimeList do not
        // agree about, and this is the only check available against getting it wrong.
        val timings = believe(
            introEndMs = 90_000L,
            outroStartMs = 43 * 60_000L,
            source = TimingSource.AniSkip,
            claimedLengthMs = 30 * 60_000L
        )
        assertEquals(null, timings.introEndMs)
        assertEquals(null, timings.outroStartMs)
    }

    @Test
    fun anAniSkipAnswerWithinTheRoundingOfASubmissionIsTheSamePart() {
        val margin = length * TimingTrust.MAX_ANISKIP_LENGTH_ERROR_PERCENT / 100L
        assertEquals(
            90_000L,
            believe(introEndMs = 90_000L, source = TimingSource.AniSkip, claimedLengthMs = length - margin)
                .introEndMs
        )
        assertEquals(
            null,
            believe(introEndMs = 90_000L, source = TimingSource.AniSkip, claimedLengthMs = length - margin - 1)
                .introEndMs
        )
    }

    @Test
    fun anAniSkipAnswerThatNamesNoLengthCannotBePlacedOnThisPart() {
        assertEquals(
            null,
            believe(introEndMs = 90_000L, source = TimingSource.AniSkip, claimedLengthMs = null).introEndMs
        )
        assertEquals(
            null,
            believe(introEndMs = 90_000L, source = TimingSource.AniSkip, claimedLengthMs = 0L).introEndMs
        )
    }

    @Test
    fun theOtherSourceIsNotHeldToALengthItNeverGives() {
        assertEquals(90_000L, believe(introEndMs = 90_000L, claimedLengthMs = null).introEndMs)
    }

    @Test
    fun anAnswerWithNothingBelievableIsStillAnAnswer() {
        // What gets remembered against a part nobody has marked, so that the next watch of it does not ask
        // the same question again: "nobody knows" has to be a value rather than the absence of one, and a
        // length this app declines to act on is not a reason to go and ask the service a second time.
        assertEquals(EpisodeTimings(), believe(introEndMs = 90_000L, durationMs = 1L))
    }
}
