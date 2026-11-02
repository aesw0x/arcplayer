package com.aeswox.arcplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class NextUpTest {

    private val episode = NextUp.Snapshot(
        lastShownMs = 44 * 60_000L + 56_000L,
        durationMs = 45 * 60_000L,
        waitingForBytes = false,
        knowsItsParts = true,
        hasNextPart = true,
        headBelongsToThisPart = true
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

    /**
     * A part watched to its end: five reports a second apart inside the last five seconds, each one
     * carrying the play head the second of film it had until then.
     */
    private val travelledToTheEnd = NextUp.Ending(
        painted = true,
        nearEndTickCount = 5,
        dwellMs = 5_000L,
        travelledMs = 4_000L,
        isWholeFileOnDisk = false
    )

    private fun ending(block: (NextUp.Ending) -> NextUp.Ending) = NextUp.isHonestEnding(block(travelledToTheEnd))

    @Test
    fun aPlayHeadThatCrossedTheLastSecondsIsAnEnding() {
        assertEquals(true, ending { it })
    }

    @Test
    fun aHeadThatLeaptToTheEndHasNotWatchedItsWayThere() {
        // The hole: the demuxer runs through the space where bytes never arrived and says where it
        // landed. One report, two hundred milliseconds after the last one the file can be believed for.
        assertEquals(false, ending { it.copy(nearEndTickCount = 1, dwellMs = 200L, travelledMs = 0L) })
    }

    @Test
    fun twoReportsAreTheLeastThatCountsAsCrossing() {
        assertEquals(false, ending { it.copy(nearEndTickCount = 1) })
        assertEquals(true, ending { it.copy(nearEndTickCount = 2) })
    }

    @Test
    fun theDwellHasAFloorAndItIsExact() {
        assertEquals(true, ending { it.copy(nearEndTickCount = 2, dwellMs = NextUp.HONEST_END_DWELL_MS) })
        assertEquals(
            false,
            ending { it.copy(nearEndTickCount = 2, dwellMs = NextUp.HONEST_END_DWELL_MS - 1) }
        )
    }

    @Test
    fun aHeadThatRacesTheFilmsOwnClockIsNotWatchingIt() {
        // Two reports a second and a half apart, and between them the head covers more than the band it
        // sits in. That is a hole being read at skip speed, and it is the one shape of this failure that
        // still gets a report every second.
        assertEquals(false, ending { it.copy(nearEndTickCount = 2, dwellMs = 1_500L, travelledMs = 5_000L) })
    }

    @Test
    fun anEndingFromAFileThatNeverPaintedIsNotAnEnding() {
        // libVLC opened the container, found nothing to draw, and called that the end of the media. The
        // screen is black and the viewer is on their way to the next episode.
        assertEquals(false, ending { it.copy(painted = false) })
    }

    @Test
    fun aFileWholeOnThisDeviceIsBelievedWithoutProofOfTravel() {
        // Every byte of a kept film is on the disk it was saved on, so there is no hole left to read as
        // an ending and asking it to show its work would only risk missing the one it has.
        assertEquals(
            true,
            ending {
                it.copy(nearEndTickCount = 0, dwellMs = 0L, travelledMs = 0L, isWholeFileOnDisk = true)
            }
        )
    }

    @Test
    fun aFileWithNoKnownLengthHasNoBandToBeInsideOf() {
        // A document sent to Telegram carries no duration and the player reports 1 until the container
        // opens, and every position there is is past the edge of a five-second band on such a length.
        assertEquals(false, NextUp.isInsideEndBand(positionMs = 0L, durationMs = 1L))
        assertEquals(false, NextUp.isInsideEndBand(positionMs = 60_000L, durationMs = 1L))
    }

    @Test
    fun theBandIsTheLastFewSecondsAndNoEarlier() {
        val length = 45 * 60_000L
        assertEquals(true, NextUp.isInsideEndBand(length - 4_000L, length))
        assertEquals(true, NextUp.isInsideEndBand(length - NextUp.HONEST_END_TOLERANCE_MS, length))
        assertEquals(false, NextUp.isInsideEndBand(length - NextUp.HONEST_END_TOLERANCE_MS - 1, length))
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
    fun aPartProvenMissingItsTailOpensTheWayOutFromAnywhere() {
        // Once the watchdog has said the rest of this part is not coming, the position is the one number
        // the app refuses to believe: the head sits at the far end of a hole, and the resume point is
        // held back at the last frame that really played. The viewer still needs a route on.
        val diedNinetyPercentIn = episode.copy(
            lastShownMs = 40 * 60_000L,
            theTailNeverArrived = true
        )
        assertEquals(true, NextUp.withinEarlyWindow(diedNinetyPercentIn))
    }

    @Test
    fun aProvenMissingTailIsNoReasonToHandTheNextEpisodeOver() {
        // The countdown that starts the next part by itself reads a position on the understanding that
        // the position has already been proven honest, and the flag is not read here at all. What keeps
        // a hole from becoming a handover is the refusal of the arrival, not a relaxation of this band.
        val diedNinetyPercentIn = episode.copy(lastShownMs = 40 * 60_000L)
        assertEquals(false, NextUp.shouldOffer(diedNinetyPercentIn))
        assertEquals(false, NextUp.shouldOffer(diedNinetyPercentIn.copy(theTailNeverArrived = true)))
    }

    @Test
    fun aProvenMissingTailStillNeedsAPartToGoOnTo() {
        assertEquals(
            false,
            NextUp.withinEarlyWindow(
                episode.copy(lastShownMs = 40 * 60_000L, hasNextPart = false, theTailNeverArrived = true)
            )
        )
        assertEquals(
            false,
            NextUp.withinEarlyWindow(
                episode.copy(lastShownMs = 40 * 60_000L, knowsItsParts = false, theTailNeverArrived = true)
            )
        )
        // And still needs a file long enough to be worth resuming, because the length that has not been
        // read is not evidence of anything.
        assertEquals(
            false,
            NextUp.withinEarlyWindow(
                episode.copy(lastShownMs = 9_000L, durationMs = 12_000L, theTailNeverArrived = true)
            )
        )
    }

    @Test
    fun creditsGiveTheCardAStartTheAppCouldOnlyHaveGuessedAt() {
        val length = 45 * 60_000L
        val creditsAt = episode.copy(outroStartMs = 43 * 60_000L)
        assertEquals(false, NextUp.withinEarlyWindow(creditsAt.copy(lastShownMs = length / 2)))
        assertEquals(false, NextUp.withinEarlyWindow(creditsAt.copy(lastShownMs = 43 * 60_000L - 1)))
        assertEquals(true, NextUp.withinEarlyWindow(creditsAt.copy(lastShownMs = 43 * 60_000L)))
    }

    @Test
    fun aCreditedMarkNearTheEndDoesNotWithdrawTheWindowTheAppAlreadyHad() {
        // Twenty seconds was what the app reached for with nothing to go on, and timings are an
        // improvement on that rather than a replacement: the card arrives at whichever comes first.
        val length = 45 * 60_000L
        val creditsNearlyAtTheEnd = episode.copy(outroStartMs = length - 4_000L)
        assertEquals(
            true,
            NextUp.withinEarlyWindow(creditsNearlyAtTheEnd.copy(lastShownMs = length - NextUp.EARLY_OFFER_MS))
        )
    }

    @Test
    fun arrivingOnAnEpisodeIsNotTheSameAsReachingItsCredits() {
        // Measured on the emulator: a handover names the new part and its timings arrive with the name,
        // while the player is still fetching its bytes and its head still reports the last second of the
        // part just left. Read against credits a minute before the end, that number is already past them,
        // and the card for the episode *after* the one being opened goes up the moment you arrive.
        val length = 45 * 60_000L
        val stillFetching = episode.copy(
            outroStartMs = 43 * 60_000L,
            lastShownMs = length - 40_000L,
            headBelongsToThisPart = false
        )
        assertEquals(true, NextUp.withinEarlyWindow(stillFetching.copy(headBelongsToThisPart = true)))
        assertEquals(false, NextUp.withinEarlyWindow(stillFetching))
        // The countdown that starts the part by itself reads the same position, and a stale one there can
        // carry the viewer past the episode they asked for.
        assertEquals(false, NextUp.shouldOffer(stillFetching.copy(lastShownMs = length)))
        // The proven hole opens the window from anywhere, so it is the one route this could silently
        // break: a tail that never arrived is a fact about the part that was watched, not the fetch.
        assertEquals(false, NextUp.withinEarlyWindow(stillFetching.copy(theTailNeverArrived = true)))
    }

    @Test
    fun aCreditedWindowStillClosesWhenThePartReportsItsEnd() {
        // The countdown offer is the one that owns the length itself, and two cards asking at once is one
        // too many.
        assertEquals(
            false,
            NextUp.withinEarlyWindow(episode.copy(outroStartMs = 43 * 60_000L, lastShownMs = 45 * 60_000L))
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
