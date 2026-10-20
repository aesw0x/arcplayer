package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which copy the Play button hands the player, and which copy ◀ ▶ step to.
 *
 * Every slot is written the way the shelf leaves them: one entry per episode — or one for a whole
 * film — holding that part's files best copy first. The cases are the awkward ones: a viewer who got
 * into a show through the smaller upload and wants to stay there, an episode watched to its last
 * minute, and a run with nothing left to watch.
 */
class PlayOrderTest {

    private fun play(
        slots: List<List<Long>>,
        resume: Map<Long, Long>,
        lengths: Map<Long, Long> = emptyMap(),
        lastPlayed: Map<Long, Long> = emptyMap()
    ) = PlayOrder.nextCopy(slots, resume, lengths, lastPlayed)

    @Test
    fun anUnwatchedShowOpensTheBestCopyOfItsFirstPart() {
        val picked = play(listOf(listOf(12L, 11L), listOf(22L, 21L)), emptyMap())
        assertEquals(PlayOrder.Play(12L, 0L), picked)
    }

    @Test
    fun aFilmIsOneSlotWhoseBestCopyIsTheOneOffered() {
        assertEquals(PlayOrder.Play(3L, 0L), play(listOf(listOf(3L, 1L, 2L)), emptyMap()))
    }

    @Test
    fun aPartWatchedInALesserCopyOpensAgainOnThatCopyAtTheSameMinute() {
        val picked = play(
            slots = listOf(listOf(12L, 11L), listOf(22L, 21L)),
            resume = mapOf(11L to TEN_MINUTES),
            lengths = mapOf(11L to ONE_HOUR, 12L to ONE_HOUR),
            lastPlayed = mapOf(11L to LAST_NIGHT)
        )

        // Twelve minutes were earned in the 720p, and they were earned there because the 4K was the
        // one that would not carry. Resume goes back to the copy the viewer is watching, not the one
        // the shelf ranks highest.
        assertEquals(PlayOrder.Play(11L, TEN_MINUTES), picked)
    }

    @Test
    fun aPartNobodyHasOpenedYetStillGoesToItsBestCopy() {
        val picked = play(
            slots = listOf(listOf(12L, 11L)),
            resume = emptyMap(),
            lastPlayed = mapOf(99L to LAST_NIGHT)
        )

        // A stamp from another title's file is no reason to skip the best copy of this one.
        assertEquals(PlayOrder.Play(12L, 0L), picked)
    }

    @Test
    fun theMostRecentlyOpenedCopyOfAPartIsTheOneThatWins() {
        val picked = play(
            slots = listOf(listOf(13L, 12L, 11L)),
            resume = mapOf(11L to TEN_MINUTES),
            lastPlayed = mapOf(12L to LAST_WEEK, 11L to LAST_NIGHT)
        )

        // Started on the 1080p, moved down to the 720p afterwards: the later choice is the live one.
        assertEquals(PlayOrder.Play(11L, TEN_MINUTES), picked)
    }

    @Test
    fun aWatchedThroughSeriesRestartsOnTheCopyItWasLastWatchedOn() {
        val picked = play(
            slots = listOf(listOf(12L, 11L)),
            resume = mapOf(11L to ONE_HOUR),
            lengths = mapOf(11L to ONE_HOUR, 12L to ONE_HOUR),
            lastPlayed = mapOf(11L to LAST_NIGHT)
        )

        assertEquals(PlayOrder.Play(11L, 0L), picked)
    }

    @Test
    fun theDeepestCopyOfAPartIsThePositionCarriedAcross() {
        val picked = play(
            slots = listOf(listOf(12L, 11L, 10L)),
            resume = mapOf(11L to TEN_MINUTES, 10L to TWENTY_MINUTES)
        )

        assertEquals(PlayOrder.Play(12L, TWENTY_MINUTES), picked)
    }

    @Test
    fun anEpisodeWatchedThroughIsPassedOverForTheNextOne() {
        val picked = play(
            slots = listOf(listOf(12L, 11L), listOf(22L, 21L)),
            resume = mapOf(11L to ONE_HOUR - MINUTE),
            lengths = mapOf(11L to ONE_HOUR, 12L to ONE_HOUR)
        )

        assertEquals(PlayOrder.Play(22L, 0L), picked)
    }

    @Test
    fun aPositionWithNoLengthOnRecordIsTrustedRatherThanSkipped() {
        val picked = play(
            slots = listOf(listOf(12L, 11L), listOf(22L)),
            resume = mapOf(11L to TEN_MINUTES)
        )

        assertEquals(PlayOrder.Play(12L, TEN_MINUTES), picked)
    }

    @Test
    fun aWatchedThroughSeriesStartsAgainFromTheBestCopy() {
        val picked = play(
            slots = listOf(listOf(12L, 11L)),
            resume = mapOf(11L to ONE_HOUR),
            lengths = mapOf(11L to ONE_HOUR)
        )

        assertEquals(PlayOrder.Play(12L, 0L), picked)
    }

    @Test
    fun everyCopyOfAPartKnowsWhereThatPartWasLeft() {
        val positions = PlayOrder.positionByCopy(
            slots = listOf(listOf(12L, 11L), listOf(22L, 21L)),
            resumeMs = mapOf(11L to TWENTY_MINUTES, 21L to FIVE_MINUTES)
        )

        // Twenty minutes were spent on the 720p of episode 1; the 4K of it is twenty minutes in too.
        assertEquals(TWENTY_MINUTES, positions.getValue(12L))
        assertEquals(TWENTY_MINUTES, positions.getValue(11L))
        assertEquals(FIVE_MINUTES, positions.getValue(22L))
        assertEquals(FIVE_MINUTES, positions.getValue(21L))
    }

    @Test
    fun sharedWithinAPartButNeverAcrossParts() {
        val positions = PlayOrder.positionByCopy(
            slots = listOf(listOf(12L, 11L), listOf(22L)),
            resumeMs = mapOf(11L to TEN_MINUTES)
        )

        assertEquals(0L, positions.getValue(22L))
        assertEquals(TEN_MINUTES, positions.getValue(12L))
    }

    @Test
    fun anEmptyShelfOffersNothing() {
        assertNull(play(emptyList(), emptyMap()))
        assertNull(play(listOf(emptyList()), emptyMap()))
    }

    @Test
    fun theSkipRunHoldsOneEntryPerPart() {
        val steps = PlayOrder.stepping(
            slots = listOf(listOf(12L, 11L), listOf(22L, 21L)),
            playlistIds = listOf(12L, 11L, 22L, 21L),
            currentId = 12L
        )

        assertEquals(listOf(12L, 22L), steps.order)
        assertEquals(0, steps.index)
    }

    @Test
    fun holdingALesserCopyStillSitsAtItsPartsPlaceInTheRun() {
        val steps = PlayOrder.stepping(
            slots = listOf(listOf(12L, 11L), listOf(22L, 21L), listOf(32L)),
            playlistIds = listOf(12L, 11L, 22L, 21L, 32L),
            currentId = 21L
        )

        // Watching the 720p of episode 2, next means the best upload of episode 3, not the 1080p of 2.
        assertEquals(listOf(12L, 22L, 32L), steps.order)
        assertEquals(1, steps.index)
        assertEquals(32L, steps.order.getOrNull(steps.index + 1))
        assertEquals(12L, steps.order.getOrNull(steps.index - 1))
    }

    @Test
    fun aFilmGivesNothingToStepTo() {
        val steps = PlayOrder.stepping(
            slots = listOf(listOf(3L, 1L, 2L)),
            playlistIds = listOf(3L, 1L, 2L),
            currentId = 1L
        )

        assertEquals(listOf(3L), steps.order)
        assertEquals(0, steps.index)
    }

    @Test
    fun anUngroupedQueueStepsAsThePlainPlaylist() {
        val steps = PlayOrder.stepping(emptyList(), listOf(7L, 8L, 9L), currentId = 8L)

        assertEquals(listOf(7L, 8L, 9L), steps.order)
        assertEquals(1, steps.index)
    }

    @Test
    fun aFileNoPartClaimsKeepsItsOwnEntry() {
        val steps = PlayOrder.stepping(
            slots = listOf(listOf(12L, 11L)),
            playlistIds = listOf(12L, 11L, 99L),
            currentId = 12L
        )

        assertEquals(listOf(12L, 99L), steps.order)
    }

    @Test
    fun aPartStepsToTheCopyTheQueueActuallyHolds() {
        val steps = PlayOrder.stepping(
            slots = listOf(listOf(12L, 11L), listOf(22L, 21L)),
            playlistIds = listOf(11L, 21L),
            currentId = 11L
        )

        assertEquals(listOf(11L, 21L), steps.order)
        assertEquals(0, steps.index)
    }

    @Test
    fun aFileOutsideTheQueueHasNoPlaceToStepFrom() {
        val steps = PlayOrder.stepping(
            slots = listOf(listOf(12L, 11L)),
            playlistIds = listOf(12L, 11L),
            currentId = 5L
        )

        assertEquals(-1, steps.index)
    }

    @Test
    fun aPartOfOneCopyStepsIntoItsNeighboursBothCopies() {
        val steps = PlayOrder.stepping(
            slots = listOf(listOf(12L, 11L), listOf(22L)),
            playlistIds = listOf(12L, 11L, 22L),
            currentId = 11L
        )

        assertEquals(listOf(12L, 22L), steps.order)
        assertEquals(0, steps.index)
    }

    @Test
    fun nothingWatchedIsNotSomethingToContinue() {
        assertNull(
            PlayOrder.progress(
                slots = listOf(listOf(12L, 11L), listOf(22L, 21L)),
                resumeMs = emptyMap(),
                lengthsMs = mapOf(12L to ONE_HOUR, 11L to ONE_HOUR)
            )
        )
    }

    @Test
    fun aPartWatchedToItsLastMinuteIsFinishedRatherThanContinued() {
        assertNull(
            PlayOrder.progress(
                slots = listOf(listOf(11L)),
                resumeMs = mapOf(11L to ONE_HOUR - MINUTE),
                lengthsMs = mapOf(11L to ONE_HOUR)
            )
        )
    }

    @Test
    fun aFinishedPartIsPassedOverForTheNextOneStillOpen() {
        val gone = PlayOrder.progress(
            slots = listOf(listOf(11L), listOf(21L), listOf(31L)),
            resumeMs = mapOf(11L to ONE_HOUR, 21L to FIVE_MINUTES, 31L to TEN_MINUTES),
            lengthsMs = mapOf(11L to ONE_HOUR, 21L to ONE_HOUR, 31L to ONE_HOUR)
        )
        assertEquals(PlayOrder.Progress(21L, FIVE_MINUTES, ONE_HOUR, 55 * MINUTE), gone)
    }

    @Test
    fun aPartOpenedOnTheLesserCopyContinuesOnThatCopy() {
        val gone = PlayOrder.progress(
            slots = listOf(listOf(12L, 11L)),
            resumeMs = mapOf(11L to TEN_MINUTES),
            lengthsMs = mapOf(12L to ONE_HOUR, 11L to ONE_HOUR),
            lastPlayedMs = mapOf(11L to LAST_NIGHT, 12L to LAST_WEEK)
        )
        assertEquals(PlayOrder.Progress(11L, TEN_MINUTES, ONE_HOUR, 50 * MINUTE), gone)
    }

    @Test
    fun aPartWithNoKnownLengthStillCountsAsUnfinishedButSaysNothingAboutItsEnd() {
        val gone = PlayOrder.progress(
            slots = listOf(listOf(11L)),
            resumeMs = mapOf(11L to TEN_MINUTES),
            lengthsMs = emptyMap()
        )
        assertEquals(PlayOrder.Progress(11L, TEN_MINUTES, 0L, 0L), gone)
    }

    @Test
    fun aLengthLearnedFromOneCopyIsReadAcrossItsPart() {
        val gone = PlayOrder.progress(
            slots = listOf(listOf(12L, 11L)),
            resumeMs = mapOf(11L to TEN_MINUTES),
            // The smaller upload is the one that was played, so the one that reports a length.
            lengthsMs = mapOf(11L to ONE_HOUR)
        )
        assertEquals(PlayOrder.Progress(12L, TEN_MINUTES, ONE_HOUR, 50 * MINUTE), gone)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val FIVE_MINUTES = 5 * MINUTE
        const val TEN_MINUTES = 10 * MINUTE
        const val TWENTY_MINUTES = 20 * MINUTE
        const val ONE_HOUR = 60 * MINUTE

        // Wall-clock stamps for "when this copy was last opened"; only their order is read.
        const val LAST_WEEK = 1_760_000_000_000L
        const val LAST_NIGHT = 1_761_000_000_000L
    }
}
