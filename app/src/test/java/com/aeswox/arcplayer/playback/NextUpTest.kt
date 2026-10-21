package com.aeswox.arcplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class NextUpTest {

    private val episode = NextUp.Snapshot(
        lastShownMs = 44 * 60_000L + 56_000L,
        durationMs = 45 * 60_000L,
        waitingForBytes = false,
        knowsItsParts = true,
        hasNextPart = true
    )

    private fun offer(block: (NextUp.Snapshot) -> NextUp.Snapshot) = NextUp.shouldOffer(block(episode))

    @Test
    fun offersWhenAnEpisodeRunsOutOnItsOwn() {
        assertEquals(true, offer { it })
    }

    @Test
    fun aHoleReadAsAnEndIsNotAnEnd() {
        // Telegram has not delivered the middle of the file. libVLC calls that an ending, and the
        // watchdog is already trying to carry it — jumping to the next episode would cover it up.
        assertEquals(
            false,
            offer { it.copy(lastShownMs = 20 * 60_000L) }
        )
    }

    @Test
    fun nothingIsOfferedWhileBytesAreStillArriving() {
        assertEquals(false, offer { it.copy(waitingForBytes = true) })
    }

    @Test
    fun aFolderQueueIsNotASeries() {
        // The next file in a folder is whatever was uploaded beside this one, not a sequel.
        assertEquals(false, offer { it.copy(knowsItsParts = false) })
    }

    @Test
    fun theLastPartHasNothingAfterIt() {
        assertEquals(false, offer { it.copy(hasNextPart = false) })
    }

    @Test
    fun aFileWhoseLengthIsStillUnknownIsNotFinished() {
        // A document sent to Telegram carries no duration, and the player reports 1 until the
        // container opens. Anything read off that is a guess about a film nobody has seen yet.
        assertEquals(false, NextUp.shouldOffer(episode.copy(lastShownMs = 0L, durationMs = 1L)))
    }

    @Test
    fun aShortClipQueuesNothingAfterIt() {
        assertEquals(false, NextUp.shouldOffer(episode.copy(lastShownMs = 12_000L, durationMs = 12_000L)))
    }

    @Test
    fun theToleranceReachesTheLastFewSecondsOfTheTick() {
        val length = 45 * 60_000L
        assertEquals(true, NextUp.shouldOffer(episode.copy(lastShownMs = length - NextUp.HONEST_END_TOLERANCE_MS)))
        assertEquals(false, NextUp.shouldOffer(episode.copy(lastShownMs = length - NextUp.HONEST_END_TOLERANCE_MS - 1)))
    }

    @Test
    fun theSkipIsInReachBeforeTheEndArrives() {
        val length = 45 * 60_000L
        assertEquals(true, NextUp.withinEarlyWindow(episode))
        assertEquals(true, NextUp.withinEarlyWindow(episode.copy(lastShownMs = length - NextUp.EARLY_OFFER_MS)))
        assertEquals(
            false,
            NextUp.withinEarlyWindow(episode.copy(lastShownMs = length - NextUp.EARLY_OFFER_MS - 1))
        )
    }

    @Test
    fun aStallInsideTheEarlyWindowStillShowsTheWayOut() {
        // The freeze this window exists for is reported as waiting-for-bytes, so the early offer cannot
        // disqualify on it: that would take the skip off the screen at the moment it is the only option.
        assertEquals(false, NextUp.shouldOffer(episode.copy(waitingForBytes = true)))
        assertEquals(true, NextUp.withinEarlyWindow(episode.copy(waitingForBytes = true)))
    }

    @Test
    fun theEarlyWindowHandsOverToTheEndOffer() {
        // At the length itself the player has reported an ending, and that offer is the one counting down.
        assertEquals(false, NextUp.withinEarlyWindow(episode.copy(lastShownMs = episode.durationMs)))
        assertEquals(false, NextUp.withinEarlyWindow(episode.copy(lastShownMs = episode.durationMs + 1)))
    }

    @Test
    fun theEarlyOfferKeepsTheQualificationsOfTheRealOne() {
        assertEquals(false, NextUp.withinEarlyWindow(episode.copy(knowsItsParts = false)))
        assertEquals(false, NextUp.withinEarlyWindow(episode.copy(hasNextPart = false)))
        assertEquals(
            false,
            NextUp.withinEarlyWindow(episode.copy(lastShownMs = 8_000L, durationMs = 12_000L))
        )
    }

    @Test
    fun anEpisodeIsNamedByItsNumber() {
        assertEquals(
            "S1 E2",
            NextUp.labelFor("Stranger.Things.S01E02.1080p.WEB-DL.x264.mkv")
        )
    }

    @Test
    fun anEpisodeWithANameOfItsOwnCarriesIt() {
        assertEquals(
            "S2 E7 · The Trainee",
            NextUp.labelFor("The.Office.S02E07.The.Trainee.720p.AMZN.WEB-DL.mkv")
        )
    }

    @Test
    fun anEpisodeNumberedAcrossTheWholeShowKeepsItsNumber() {
        assertEquals("E1260", NextUp.labelFor("One.Piece.E1260.720p.mp4"))
    }

    @Test
    fun aFilmIsNamedByItsTitleAndYear() {
        assertEquals("Inception (2010)", NextUp.labelFor("Inception.2010.1080p.BluRay.x264.mp4"))
    }
}
