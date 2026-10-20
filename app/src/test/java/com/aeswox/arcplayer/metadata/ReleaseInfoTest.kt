package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The names here are the shapes found in the chat archives this app plays: a scene release tail, a
 * bracketed anime upload signed with a channel handle, and the bare file names that state nothing.
 */
class ReleaseInfoTest {

    private fun parse(name: String, frameHeight: Int = 0) = ReleaseInfo.parse(name, frameHeight)

    @Test
    fun sceneStyleMovieTail() {
        val read = parse("Ant.Man.and.the.Wasp.Quantumania.2018.1080p.WEB-DL.DDP5.1.Atmos.H.264-FraMeSToR.mkv")
        assertEquals("1080p", read.quality)
        assertEquals(listOf("H.264", "WEB-DL", "Dolby Atmos", "Dolby", "5.1"), read.tags)
        assertEquals("FraMeSToR", read.group)
    }

    @Test
    fun bracketedTagsAndChannelHandle() {
        val read = parse("One Piece - E126 [Dual] 720p @XANIME_UNIVERSE.mkv")
        assertEquals("720p", read.quality)
        assertEquals(listOf("Dual audio"), read.tags)
        assertEquals("XANIME_UNIVERSE", read.group)
    }

    @Test
    fun aChannelNameThatContainsAResolutionIsNotTheFilesResolution() {
        val read = parse("Some Movie (2020) @Group_4k.mkv")
        assertNull(read.quality)
        assertEquals("Group_4k", read.group)
    }

    @Test
    fun frameHeightFillsInOnlyWhereTheNameIsSilent() {
        assertEquals("720p", parse("Big.Buck.Bunny.720p.x264.mkv", 1080).quality)
        assertEquals("1080p", parse("Home Video March.mkv", 1080).quality)
        assertEquals("4K", parse("Home Video March.mkv", 2160).quality)
        assertNull(parse("Home Video March.mkv", 0).quality)
    }

    @Test
    fun aTitleThatMerelyEndsInAHyphenIsNotSignedByAGroup() {
        val read = parse("Ant-Man and the Wasp.mkv")
        assertNull(read.quality)
        assertEquals(emptyList<String>(), read.tags)
        assertNull(read.group)
    }

    @Test
    fun newerRangeLabelsDoNotAlsoClaimTheOlderOnes() {
        val read = parse("Dune.Part.Two.2024.2160p.WEB-DL.HDR10Plus.x265.10bit.DTS-HD.5.1.mkv")
        assertEquals("4K", read.quality)
        assertEquals(listOf("HEVC", "WEB-DL", "HDR10+", "10-bit", "DTS", "5.1"), read.tags)
    }

    @Test
    fun aLongTailIsCutWhereChoosingStopsBeingUseful() {
        val read = parse("Movie.2019.1080p.Bluray.x264.HDR.Atmos.DTS.5.1.Hindi.Subs.mkv")
        assertEquals(6, read.tags.size)
        assertEquals(listOf("H.264", "Blu-ray", "HDR", "Dolby Atmos", "DTS", "5.1"), read.tags)
    }

    @Test
    fun languagesAndCutsAreReadWhenNothingBiggerCrowdsThemOut() {
        val read = parse("Jai.Bhim.2021.Hindi.1080p.WEBRip.x265.Telugu.Dual.Audio.mkv")
        assertEquals("1080p", read.quality)
        assertEquals(
            listOf("HEVC", "WEBRip", "Dual audio", "Hindi", "Telugu"),
            read.tags
        )
    }

    @Test
    fun anEpisodeNumberIsNotAGroupSignature() {
        val read = parse("One Piece - E126.mkv")
        assertNull(read.quality)
        assertEquals(emptyList<String>(), read.tags)
        assertNull(read.group)
    }

    @Test
    fun aCodecSizedNumberIsNotAChannelCount() {
        val read = parse("Movie.2019.1080p.x265.10bit.mkv")
        assertEquals(listOf("HEVC", "10-bit"), read.tags)
    }
}
