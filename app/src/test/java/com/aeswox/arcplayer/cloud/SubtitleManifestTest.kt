package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The captions are the one part of the shelf allowed to fail alone, so these read for the cases where
 * this file is wrong, stale or from somebody else's chat — and for the guarantee that nothing it says
 * can take a video card down with it.
 */
class SubtitleManifestTest {

    @Test
    fun aCaptionSurvivesTheRoundTrip() {
        val written = SubtitleManifest.encode(CHAT, listOf(caption))
        val read = SubtitleManifest.decode(written, CHAT)

        assertEquals(1, read.size)
        assertEquals(caption.copy(isFromManifest = true), read.first())
    }

    @Test
    fun punctuationThatWouldBreakTheFormatIsFlattenedRatherThanRefused() {
        val messy = caption.copy(fileName = "Stranger.Things.S01E01\tThe.Vanishing\n.srt")
        val read = SubtitleManifest.decode(SubtitleManifest.encode(CHAT, listOf(messy)), CHAT)

        assertEquals(1, read.size)
        assertEquals("Stranger.Things.S01E01 The.Vanishing .srt", read.first().fileName)
    }

    @Test
    fun aFileFromAnotherChatAnswersWithNothing() {
        val written = SubtitleManifest.encode(CHAT, listOf(caption))

        assertTrue(SubtitleManifest.decode(written, CHAT + 1).isEmpty())
    }

    @Test
    fun oneBadLineCostsThatCaptionAndNotTheFile() {
        val written = SubtitleManifest.encode(CHAT, listOf(caption)) +
            "only\ta\tfew\tcolumns\n" +
            SubtitleManifest.encode(CHAT, listOf(caption.copy(messageId = 2))).lineSequence().drop(1).joinToString("")

        val read = SubtitleManifest.decode(written, CHAT)

        assertEquals(listOf(1L, 2L), read.map { it.messageId })
    }

    @Test
    fun aNameThatStoppedBeingACaptionIsNotCarriedForward() {
        val written = SubtitleManifest.encode(CHAT, listOf(caption.copy(fileName = "notes.txt")))

        assertTrue(SubtitleManifest.decode(written, CHAT).isEmpty())
    }

    @Test
    fun aChannelWithNoCaptionsStillWritesAFileThatReadsBackEmpty() {
        val written = SubtitleManifest.encode(CHAT, emptyList())

        assertTrue(SubtitleManifest.decode(written, CHAT).isEmpty())
    }

    private companion object {
        const val CHAT = -1004426602699L

        val caption = CloudSubtitleItem(
            stableId = -8123L,
            chatId = CHAT,
            messageId = 1L,
            fileId = 44,
            fileName = "Stranger.Things.S01E01.The.Vanishing.srt",
            sizeBytes = 41_233L,
        )
    }
}
