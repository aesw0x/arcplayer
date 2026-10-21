package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSweepTest {

    private val gb = 1L * 1024 * 1024 * 1024

    private fun claim(fileId: Int = 40, sizeBytes: Long = 3 * gb) =
        DownloadSweep.Claim(fileId, sizeBytes)

    private fun record(
        claimedSizeBytes: Long = 3 * gb,
        reportsLocalBytes: Boolean = true,
        isDownloading: Boolean = false,
    ) = DownloadSweep.Record(claimedSizeBytes, reportsLocalBytes, isDownloading)

    @Test
    fun aCopyNoWatchOfThisSessionOwnsIsStranded() {
        assertTrue(DownloadSweep.isStranded(claim(), record(), inUse = false))
    }

    @Test
    fun theCopyInThePlayerIsKept() {
        assertFalse(DownloadSweep.isStranded(claim(), record(), inUse = true))
    }

    @Test
    fun aCopyStillBeingFilledIsKept() {
        assertFalse(
            DownloadSweep.isStranded(
                claim(),
                record(reportsLocalBytes = true, isDownloading = true),
                inUse = false,
            )
        )
    }

    @Test
    fun aRecordWithNothingLocalHasNothingToReclaim() {
        assertFalse(
            DownloadSweep.isStranded(claim(), record(reportsLocalBytes = false), inUse = false)
        )
    }

    @Test
    fun aCompletionFlagOverGoneBytesIsStillPutDown() {
        // The record this whole sweep exists to retire: it reports the film complete, and whether any
        // bytes are left under its path is not something the app gets to check by hand.
        assertTrue(
            DownloadSweep.isStranded(
                claim(),
                DownloadSweep.Record(3 * gb, reportsLocalBytes = true, isDownloading = false),
                inUse = false,
            )
        )
    }

    @Test
    fun anIdTelegramHasHandedOutAgainIsNotThisFilm() {
        // A stale id resolves to something else — measured, a three-kilobyte thumbnail. Deleting that
        // costs a poster and reclaims nothing, so a size that disagrees means the id is not believed.
        assertFalse(
            DownloadSweep.isStranded(claim(sizeBytes = 3 * gb), record(claimedSizeBytes = 3_538L), inUse = false)
        )
    }

    @Test
    fun aFileTelegramDidNotAnswerForIsLeftAlone() {
        assertFalse(DownloadSweep.isStranded(claim(), null, inUse = false))
    }

    @Test
    fun onlyTheUnclaimedCopiesAreSelectedAndInOrder() {
        val shelf = listOf(
            DownloadSweep.Claim(11, 3 * gb),
            DownloadSweep.Claim(12, 2 * gb),
            DownloadSweep.Claim(13, 900 * 1024 * 1024),
        )
        val records = mapOf(
            11 to record(3 * gb),
            12 to record(2 * gb, isDownloading = true),
            13 to record(claimedSizeBytes = 4_096L),
        )
        assertEquals(
            listOf(DownloadSweep.Claim(11, 3 * gb)),
            DownloadSweep.stranded(shelf, records, inUseFileIds = emptySet())
        )
        assertEquals(
            emptyList<DownloadSweep.Claim>(),
            DownloadSweep.stranded(shelf, records, inUseFileIds = setOf(11))
        )
    }

    @Test
    fun everyRefusalSaysWhichRuleRefused() {
        // "Put down 0 of 61" on a device still holding 134 MB of leftovers is not a result anyone can
        // act on. Each refusal names itself so the tally says where to look next.
        assertEquals(DownloadSweep.Verdict.STRANDED, DownloadSweep.verdict(claim(), record(), inUse = false))
        assertEquals(DownloadSweep.Verdict.IN_USE, DownloadSweep.verdict(claim(), record(), inUse = true))
        assertEquals(DownloadSweep.Verdict.NO_ANSWER, DownloadSweep.verdict(claim(), null, inUse = false))
        assertEquals(
            DownloadSweep.Verdict.STILL_ARRIVING,
            DownloadSweep.verdict(claim(), record(isDownloading = true), inUse = false)
        )
        assertEquals(
            DownloadSweep.Verdict.NOTHING_LOCAL,
            DownloadSweep.verdict(claim(), record(reportsLocalBytes = false), inUse = false)
        )
        assertEquals(
            DownloadSweep.Verdict.NOT_THIS_FILM,
            DownloadSweep.verdict(claim(), record(claimedSizeBytes = 3_538L), inUse = false)
        )
    }

    @Test
    fun aFilmThePlayerIsOpeningOutranksEverythingTelegramSays() {
        // The claim is made at the top of an open, not at the end of it: a head can take twelve seconds
        // to land and the sweep walks the whole shelf inside that, so an open in progress has to win
        // over whatever the record was saying a round trip ago.
        assertEquals(
            DownloadSweep.Verdict.IN_USE,
            DownloadSweep.verdict(claim(), record(isDownloading = true), inUse = true)
        )
        assertEquals(DownloadSweep.Verdict.IN_USE, DownloadSweep.verdict(claim(), null, inUse = true))
    }
}
