package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shelf list written down, and read back before Telegram has answered.
 *
 * The manifest only has to be honest about which file belongs to which card, because everything it
 * says is replaced the moment the shelf walk lands. So these are the cases that would be invisible
 * until the grid is wrong: a file name that survives intact, a list belonging to another channel, a
 * bad line that costs one card rather than the whole grid, and a restored item that admits its
 * Telegram number is a claim from the last session.
 */
class ShelfManifestTest {

    private fun item(
        stableId: Long,
        title: String,
        chatId: Long = SHELF_CHAT,
        sizeBytes: Long = 3_800_000_000L,
    ) = CloudVideoItem(
        stableId = stableId,
        chatId = chatId,
        chatTitle = "Arc Player Server",
        messageId = 400L + stableId,
        fileId = 1259,
        title = title,
        sizeBytes = sizeBytes,
        durationMs = 0L,
        width = 0,
        height = 0,
        mimeType = "video/x-matroska",
        supportsStreaming = true,
    )

    @Test
    fun aRestoredCardCarriesEverythingThatDrewItTheFirstTime() {
        val files = listOf(
            item(-11L, "Batman.v.Superman.Dawn.of.Justice.2016.720p.NF.WEB-DL.DD.mkv"),
            item(-12L, "Interstellar.2014.PROPER.IMAX.1080p.UHD.BluRay.x265.HDR.DV.D.mkv"),
        )

        val restored = ShelfManifest.decode(ShelfManifest.encode(SHELF_CHAT, files), SHELF_CHAT)

        assertEquals(files, restored.map { it.copy(isFromManifest = false) })
    }

    @Test
    fun aRestoredItemAdmitsItsTelegramNumberIsFromTheLastSession() {
        val restored = ShelfManifest.decode(
            ShelfManifest.encode(SHELF_CHAT, listOf(item(-11L, "F1.2025.1080p.WEBRip.mkv"))),
            SHELF_CHAT
        )

        assertTrue(restored.single().isFromManifest)
    }

    @Test
    fun aListWrittenForAnotherChannelDrawsNothingHere() {
        // Two installs, two shelf channels with the same name: the files belong to the one they were
        // read from, and drawing the other's cards would offer films this sign-in cannot open.
        val text = ShelfManifest.encode(SHELF_CHAT, listOf(item(-11L, "Premalu.2024.1080p.WEB-DL.mkv")))

        assertEquals(emptyList<CloudVideoItem>(), ShelfManifest.decode(text, chatId = 999L))
    }

    @Test
    fun oneBadLineCostsItsOwnCardAndNothingElse() {
        val files = listOf(
            item(-11L, "Premalu.2024.1080p.WEB-DL.mkv"),
            item(-12L, "Cirkus.2022.720p.WEB-DL.mkv"),
        )
        val lines = ShelfManifest.encode(SHELF_CHAT, files).lines()
        val broken = (lines.take(2) + "this line is not a file" + lines.drop(2)).joinToString("\n")

        val restored = ShelfManifest.decode(broken, SHELF_CHAT)

        assertEquals(listOf(-11L, -12L), restored.map { it.stableId })
    }

    @Test
    fun aFileNameKeepsEveryCharacterTheGroupingReads() {
        // The brackets and the run of dots are what TitleGroups keys a card on, and a name that lost
        // them would arrive as a different film. A tab is the one thing the format cannot carry, so it
        // goes down as a space rather than shifting every column after it.
        val files = listOf(
            item(-11L, "Stranger.Things.S04.E03.The.Monster.and.the.Superhero.2160p..mkv"),
            item(-12L, "Stranger_Things_S03E01_2160p_NF_WEB_DL_x265_10bit_SDR_AAC_5_1_FS87.mkv"),
            item(-13L, "Anora\t2024.1080p.WEB-DL.mkv"),
        )

        val restored = ShelfManifest.decode(ShelfManifest.encode(SHELF_CHAT, files), SHELF_CHAT)

        assertEquals(files[0].title, restored[0].title)
        assertEquals(files[1].title, restored[1].title)
        assertEquals("Anora 2024.1080p.WEB-DL.mkv", restored[2].title)
        assertEquals(3_800_000_000L, restored[2].sizeBytes)
    }

    @Test
    fun aShelfEmptiedInTelegramStaysEmptiedOnTheNextLaunch() {
        val restored = ShelfManifest.decode(ShelfManifest.encode(SHELF_CHAT, emptyList()), SHELF_CHAT)

        assertEquals(emptyList<CloudVideoItem>(), restored)
    }

    private companion object {
        const val SHELF_CHAT = -10001234567890L
    }
}
