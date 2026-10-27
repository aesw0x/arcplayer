package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promise an install makes to keep a channel copy, written down and read back.
 *
 * Two separate jobs get tested here, and the second is the one the whole feature rests on. The format
 * has to survive a file name, a rebuild of Telegram's database and one bad line in a hundred. The
 * completeness rule has to tell a film that really arrived apart from one that only *looks* like it
 * did — which is not a question `File.length()` can answer, because a streamed file is stretched to its
 * full length on purpose so that a seek is never "past the end".
 *
 * The naming rule gets tested alongside them because it decides the same thing the format does: whether
 * two records can end up pointing at one file.
 */
class DownloadLedgerTest {

    private val mb = 1L * 1024 * 1024
    private val gb = 1024 * mb

    private fun entry(
        stableId: Long = -4100L,
        path: String = "content://media/external/video/media/412",
        sizeBytes: Long = 3 * gb,
        isComplete: Boolean = true,
    ) = DownloadEntry(
        stableId = stableId,
        chatId = -1007L,
        messageId = 4821L,
        fileId = 1259,
        sizeBytes = sizeBytes,
        path = path,
        isComplete = isComplete,
    )

    @Test
    fun aKeptFileSurvivesTheRoundTrip() {
        val kept = entry()
        val restored = DownloadLedger.decode(DownloadLedger.encode(listOf(kept)))

        assertEquals(mapOf(kept.stableId to kept), restored)
    }

    @Test
    fun severalKeptFilesComeBackAsTheyWereFiled() {
        val files = listOf(entry(stableId = -11L, isComplete = false), entry(stableId = -12L))
        val restored = DownloadLedger.decode(DownloadLedger.encode(files))

        assertEquals(2, restored.size)
        assertFalse(restored.getValue(-11L).isComplete)
        assertTrue(restored.getValue(-12L).isComplete)
    }

    @Test
    fun aPathCarryingTabsCannotForgeADifferentRow() {
        // The path is Telegram's own text. Stripping the separator is what keeps one file per line, and
        // a forged second column would read back as a size or a flag rather than as part of a name.
        val sneaky = entry(path = "/tmp/a\tb.mkv")

        val restored = DownloadLedger.decode(DownloadLedger.encode(listOf(sneaky)))

        assertEquals(1, restored.size)
        assertEquals("/tmp/a b.mkv", restored.getValue(sneaky.stableId).path)
    }

    @Test
    fun aFileFromSomethingElseIsRefusedRatherThanRead() {
        assertTrue(DownloadLedger.decode("arc-shelf-manifest\t1\t-1007\n").isEmpty())
        assertTrue(DownloadLedger.decode("not a ledger at all").isEmpty())
        assertTrue(DownloadLedger.decode("").isEmpty())
    }

    @Test
    fun oneBadLineCostsOnePromiseRatherThanEveryOne() {
        val text = DownloadLedger.encode(listOf(entry(-11L), entry(-12L))) +
            "-13\t-1007\t4821\t1259\n"

        val restored = DownloadLedger.decode(text)

        assertEquals(setOf(-11L, -12L), restored.keys)
    }

    @Test
    fun aStreamedShellIsNotADownload() {
        // The false positive the whole rule exists to refuse: the open stretched the file to 3 GB and
        // one 64 MB window has landed behind it. Asked as a length, that file is the whole film.
        assertFalse(DownloadLedger.hasEveryByte(sizeBytes = 3 * gb, allocatedBytes = 64 * mb))
        assertFalse(DownloadLedger.hasEveryByte(sizeBytes = 3 * gb, allocatedBytes = 4 * 1024))
    }

    @Test
    fun aFileWithNothingAllocatedIsRefusedEvenAtTheRightLength() {
        assertFalse(DownloadLedger.hasEveryByte(sizeBytes = 0L, allocatedBytes = 3 * gb))
        assertFalse(DownloadLedger.hasEveryByte(sizeBytes = 3 * gb, allocatedBytes = 0L))
        assertFalse(DownloadLedger.hasEveryByte(sizeBytes = 3 * gb, allocatedBytes = -1L))
    }

    @Test
    fun aWholeFileCountsWholeIncludingWhatTheFilesystemRounds() {
        assertTrue(DownloadLedger.hasEveryByte(sizeBytes = 3 * gb, allocatedBytes = 3 * gb))
        // Block rounding and a not-yet-committed tail: both land inside one block plus a percent.
        assertTrue(DownloadLedger.hasEveryByte(sizeBytes = 3 * gb, allocatedBytes = 3 * gb - 4096))
        assertTrue(DownloadLedger.hasEveryByte(sizeBytes = 3 * gb, allocatedBytes = 3 * gb - 30 * mb - 4096))
        // And the margin stops there, so a genuinely short file is never waved through.
        assertFalse(DownloadLedger.hasEveryByte(sizeBytes = 3 * gb, allocatedBytes = 3 * gb - 60 * mb))
    }

    @Test
    fun aSmallFileNeedsItsBytesRatherThanItsMargin() {
        // The slack has to stay proportionate: on a 2 MB caption-sized transfer, one percent is 20 KB,
        // so the block is what dominates — and half a file is still half a file.
        assertFalse(DownloadLedger.hasEveryByte(sizeBytes = 2 * mb, allocatedBytes = mb))
        assertTrue(DownloadLedger.hasEveryByte(sizeBytes = 2 * mb, allocatedBytes = 2 * mb - 4 * 1024))
    }

    @Test
    fun aPromiseNamingSomethingOtherThanARowIsNotStillThere() {
        assertFalse(DownloadLedger.isCatalogHandle(""))
        // Both shapes this feature used before it filed through the catalog: Telegram's own temporary
        // file, which Telegram deleted, and a path in the app's private corner, which nothing else on
        // the phone can see.
        assertFalse(DownloadLedger.isCatalogHandle("/data/user/0/com.aeswox.arcplayer/files/telegram/files/temp/123"))
        assertFalse(DownloadLedger.isCatalogHandle("/storage/emulated/0/Android/data/com.aeswox.arcplayer/files/Arc Player/Film.mkv"))
        assertTrue(DownloadLedger.isCatalogHandle("content://media/external/video/media/412"))
    }

    @Test
    fun onlyAFinishedDownloadIsOfferedAsOffline() {
        val entries = mapOf(
            -11L to entry(-11L, isComplete = true),
            -12L to entry(-12L, isComplete = false),
        )

        assertEquals(setOf(-11L), DownloadLedger.completeIds(entries))
    }

    @Test
    fun aChannelFileNameIsUsedAsItStands() {
        assertEquals(
            "The.Guilty.2021.1080p.NF.WEB-DL.x264.mkv",
            DownloadLedger.fileNameFor("The.Guilty.2021.1080p.NF.WEB-DL.x264.mkv"),
        )
    }

    @Test
    fun whatAFileSystemWouldObjectToBecomesAnUnderscoreAndADotIsNotHidden() {
        // Brackets are legal on Android and not on the Windows box the phone gets plugged into, and a
        // leading dot would put the film somewhere the viewer cannot see it in a file manager.
        assertEquals("hidden _2020_.mkv", DownloadLedger.fileNameFor(".hidden [2020].mkv"))
        assertEquals(
            "Sacred Games_S01E01 1080p NF WEBRip.mp4",
            DownloadLedger.fileNameFor("Sacred Games/S01E01 1080p NF WEBRip.mp4"),
        )
    }

    @Test
    fun aNameWithNothingLeftInItStillGetsOne() {
        assertEquals("download", DownloadLedger.fileNameFor("..."))
        assertEquals("download", DownloadLedger.fileNameFor("   "))
    }

    @Test
    fun aVeryLongNameIsCutWithoutLosingWhatTellsItApart() {
        val long = "Dune.2021." + "a".repeat(90) + ".mkv"

        val name = DownloadLedger.fileNameFor(long)

        assertEquals(60, name.length)
        assertTrue(name.endsWith(".mkv"))
        assertEquals(long.take(56) + ".mkv", name)
    }

    @Test
    fun aKeptFilmIsFoundAgainByTheRowItsBytesSitIn() {
        // The shelf knows a film by the negative key it is played under and the device knows the same
        // bytes by the catalog row they were published into. Nothing else holds both, and a card that
        // cannot cross from one to the other names a film by its file name.
        val stable = -5703635162549997138L
        val kept = mapOf(stable to entry(stableId = stable, path = "content://media/external_primary/video/media/20369"))

        assertEquals(mapOf(20369L to stable), DownloadLedger.keptRows(kept))
    }

    @Test
    fun aRowIsPromisedOnlyOnceTheBytesHaveLanded() {
        // An in-flight fetch already has a row, and naming it would offer a film that plays to nothing.
        // A path from before the catalog is not a row, and a handle that ends in a word is not a number:
        // guessing an id from a file name is how two uploads of one release get called the same film.
        val entries = mapOf(
            -11L to entry(stableId = -11L, isComplete = false),
            -12L to entry(stableId = -12L, path = "/storage/emulated/0/Download/Film.mkv"),
            -13L to entry(stableId = -13L, path = "content://media/external/video/media/"),
        )

        assertEquals(emptyMap<Long, Long>(), DownloadLedger.keptRows(entries))
    }
}
