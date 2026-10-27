package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The single number a download notification is allowed to show.
 *
 * A bar is a promise, and the two ways to break it are the two ways this feature has already been burnt:
 * showing a film that has not started arriving as though it were finished, and repainting the shade for a
 * tick nobody could see. Both are arithmetic rather than Android, so both get said once here instead of
 * being re-derived at every call to `notify`.
 */
class DownloadNoticesTest {

    private val kb = 1L * 1024
    private val mb = 1024 * kb
    private val gb = 1024 * mb

    private fun entry(
        stableId: Long = -4100L,
        fileId: Int = 1259,
        sizeBytes: Long = 3 * gb,
        isComplete: Boolean = false,
    ) = DownloadEntry(
        stableId = stableId,
        chatId = -1007L,
        messageId = 4821L,
        fileId = fileId,
        sizeBytes = sizeBytes,
        path = "content://media/external/video/media/412",
        isComplete = isComplete,
    )

    private fun facts(fileId: Int = 1259, bytesOnDisk: Long) = CloudProgress(
        downloadedBytes = 0L,
        totalBytes = 3 * gb,
        isDownloading = true,
        isComplete = false,
        bytesOnDisk = bytesOnDisk,
    )

    /** Everything a test below asserts is about a download that is running, so null is always a failure. */
    private fun notice(
        entries: Map<Long, DownloadEntry>,
        progress: Map<Int, CloudProgress>,
        titleOf: (Long) -> String = { "Noelle" },
    ): DownloadNotices.Running = checkNotNull(DownloadNotices.running(entries, progress, titleOf)) {
        "a film is still arriving, so the shade has something to say"
    }

    @Test
    fun aFilmTelegramHasNotAnsweredForYetIsAtZeroAndNotAtFull() {
        // The trap in the making of this: an empty progress map sums to zero over zero, and zero over zero
        // is either a crash or a bar that claims the film is already there.
        val running = notice(mapOf(-4100L to entry()), emptyMap())
        assertEquals(0f, running.fraction, 0f)
        assertEquals(0, running.percent)
        assertEquals(0L, running.doneBytes)
        assertEquals(3 * gb, running.totalBytes)
    }

    @Test
    fun theBarIsTheFilmsTogetherRatherThanTheLastOneAlone() {
        val first = entry(stableId = -4100L, fileId = 10, sizeBytes = 100L * mb)
        val second = entry(stableId = -4101L, fileId = 11, sizeBytes = 300L * mb)
        val running = notice(
            entries = mapOf(first.stableId to first, second.stableId to second),
            progress = mapOf(10 to facts(10, 40L * mb)),
        )
        assertEquals(2, running.films)
        assertEquals(40L * mb, running.doneBytes)
        assertEquals(400L * mb, running.totalBytes)
        assertEquals(10, running.percent)
    }

    @Test
    fun aFilmThatReportedMoreThanItPromisedCountsOnlyAsFarAsItPromised() {
        // TDLib's byte count is every byte taken for the file, window fetches included, so a film that was
        // streamed before the viewer asked to keep it can report further along than its own length.
        val running = notice(
            entries = mapOf(-4100L to entry(sizeBytes = 100L * mb)),
            progress = mapOf(1259 to facts(bytesOnDisk = 140L * mb)),
        )
        assertEquals(100L * mb, running.doneBytes)
        assertEquals(100, running.percent)
    }

    @Test
    fun aFilmWithNoLengthIsLeftOutOfBothSidesOfTheFraction() {
        // Counting it as nothing landed would freeze the bar on a download that is moving; counting it as
        // landed would lie the other way. Leaving it out lets the other film say how far the work has come.
        val unknown = entry(stableId = -4101L, fileId = 11, sizeBytes = 0L)
        val known = entry(stableId = -4100L, fileId = 10, sizeBytes = 200L * mb)
        val running = notice(
            entries = mapOf(unknown.stableId to unknown, known.stableId to known),
            progress = mapOf(10 to facts(10, 50L * mb)),
        )
        assertEquals(50L * mb, running.doneBytes)
        assertEquals(200L * mb, running.totalBytes)
        assertEquals(2, running.films)
        assertEquals(25, running.percent)
    }

    @Test
    fun nothingArrivingMeansNothingToSay() {
        assertNull(DownloadNotices.running(emptyMap(), emptyMap(), { "Noelle" }))
        assertNull(
            DownloadNotices.running(
                mapOf(-4100L to entry(isComplete = true)),
                mapOf(1259 to facts(bytesOnDisk = 3 * gb)),
                { "Noelle" },
            )
        )
    }

    @Test
    fun theOldestRequestIsTheOneInTheHeadline() {
        val first = entry(stableId = -4100L, fileId = 10)
        val second = entry(stableId = -4101L, fileId = 11)
        val running = notice(
            entries = mapOf(first.stableId to first, second.stableId to second),
            progress = emptyMap(),
            titleOf = { stableId -> if (stableId == -4100L) "Heat" else "Noelle" },
        )
        assertEquals("Heat", running.leadingTitle)
    }

    @Test
    fun oneFilmsRowUsesTheSameBytesAsTheShadeDoes() {
        // The Downloads page reads [landedBytes] per film and the notification reads it summed over all of
        // them. Two arithmetic rules in one file only stay honest while they agree, so one film has to
        // report the same number through both doors.
        val promised = 700L * mb
        val facts = CloudProgress(
            downloadedBytes = 640L * mb,
            totalBytes = promised,
            isDownloading = true,
            isComplete = false,
            bytesOnDisk = 610L * mb,
        )
        assertEquals(640L * mb, DownloadNotices.landedBytes(promised, facts))
        val running = notice(
            entries = mapOf(-4100L to entry(sizeBytes = promised)),
            progress = mapOf(1259 to facts),
        )
        assertEquals(640L * mb, running.doneBytes)
    }

    @Test
    fun aFilmWithNothingOnDiskYetIsAtZeroWhetherOrNotTelegramHasAnswered() {
        assertEquals(0L, DownloadNotices.landedBytes(3 * gb, null))
        assertEquals(0f, DownloadNotices.progressOf(3 * gb, 0L), 0f)
        // A length the shelf never learned is the one case a bar must not guess at.
        assertEquals(0f, DownloadNotices.progressOf(0L, 500L * mb), 0f)
        assertEquals(0L, DownloadNotices.landedBytes(0L, facts(bytesOnDisk = 500L * mb)))
    }

    @Test
    fun aRowNeverReadsPastFullEvenWhenTheFactsDo() {
        val running = facts(bytesOnDisk = 2 * gb)
        assertEquals(100L * mb, DownloadNotices.landedBytes(100L * mb, running))
        assertEquals(1f, DownloadNotices.progressOf(100L * mb, 200L * mb), 0f)
    }

    @Test
    fun aTickNobodyCouldSeeIsNotWorthRedrawingTheShade() {
        // Four kilobytes out of a hundred gigabyte moves no digit on the screen, and Telegram pushes an
        // update like this several times a second.
        val first = running(doneBytes = 1 * gb)
        assertFalse(DownloadNotices.needsRepaint(first, first.copy(doneBytes = first.doneBytes + 4 * kb)))
    }

    @Test
    fun aTickThatMovesTheReadoutDoesGetDrawn() {
        val first = running(doneBytes = 1 * gb)
        assertTrue(DownloadNotices.needsRepaint(first, first.copy(doneBytes = first.doneBytes + 11 * mb)))
        // A different set of films is a different sentence whatever the byte count says.
        assertTrue(DownloadNotices.needsRepaint(first, first.copy(films = 2)))
        assertTrue(DownloadNotices.needsRepaint(first, first.copy(leadingTitle = "Heat")))
        // And the first state of a download has nothing on screen to be measured against.
        assertTrue(DownloadNotices.needsRepaint(null, first))
    }

    @Test
    fun aByteCountThatRunsBackwardsIsWorthDrawing() {
        // Not a theory: the window a film was streamed through gets re-aimed, and the total TDLib reports
        // for the file can come back smaller than the number the bar was showing a second ago. A rule that
        // only redrew on an increase would leave the shade claiming a film is further along than it is.
        val first = DownloadNotices.Running(
            films = 1,
            leadingTitle = "Noelle",
            doneBytes = 5 * gb,
            totalBytes = 10 * gb,
        )
        assertTrue(DownloadNotices.needsRepaint(first, first.copy(doneBytes = 4 * gb)))
    }

    /** A hundred gigabyte total, so that eleven megabytes is a byte rule and not a percentage rule. */
    private fun running(doneBytes: Long) = DownloadNotices.Running(
        films = 1,
        leadingTitle = "Noelle",
        doneBytes = doneBytes,
        totalBytes = 100 * gb,
    )
}
