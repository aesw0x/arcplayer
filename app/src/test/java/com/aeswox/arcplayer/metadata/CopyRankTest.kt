package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weighing of several copies of one thing, done with nothing but names.
 *
 * The pairs are the ones a shelf really holds: two groups uploading the same episode at different
 * resolutions, a disc rip beside a bigger web rip, and the clean master beside the same picture with
 * subtitles burnt into it. Each test moves exactly one claim and expects the order to follow, because
 * a cascade that several tiers could each explain is a cascade nobody can debug.
 */
class CopyRankTest {

    private fun rank(name: String, sizeGb: Long = 1, frameHeight: Int = 0, onPhone: Boolean = false) =
        CopyRank.of(name, sizeGb * ONE_GB, frameHeight, onPhone)

    @Test
    fun resolutionIsTheFirstThingADecisionTurnsOn() {
        val fourK = rank("Dune.2021.2160p.WEB-DL.x265.HDR10.mkv", 6)
        val fullHdFromADisc = rank("Dune.2021.1080p.BluRay.x264.Atmos.7.1.mkv", 30)

        // The 4K wins even though the disc copy is five times the size and better in every tier below it.
        assertTrue(fourK > fullHdFromADisc)
    }

    @Test
    fun aBrighterRangeWinsInsideOneResolution() {
        val dolbyVision = rank("Movie.2022.1080p.WEB-DL.x265.DoVi.10bit.mkv")
        val hdr10Plus = rank("Movie.2022.1080p.WEB-DL.x265.HDR10+.10bit.mkv")
        val statedSdr = rank("Movie.2022.1080p.WEB-DL.x265.SDR.10bit.mkv")

        assertTrue(dolbyVision > hdr10Plus)
        // A name that admits an SDR grade says what it is; one that says nothing has not.
        assertTrue(hdr10Plus > statedSdr)
    }

    @Test
    fun whereThePictureCameFromDecidesWhenThePictureMatches() {
        val disc = rank("Killers.2023.1080p.BluRay.x264.mkv")
        val store = rank("Killers.2023.1080p.WEB-DL.x264.mkv")
        val reencoded = rank("Killers.2023.1080p.WEBRip.x264.mkv")

        assertTrue(disc > store)
        assertTrue(store > reencoded)
    }

    @Test
    fun anAudioBedIsWorthMoreThanSilenceAboutIt() {
        val atmos = rank("Movie.2023.1080p.WEB-DL.x264.Atmos.7.1.mkv")
        val dts = rank("Movie.2023.1080p.WEB-DL.x264.DTS.5.1.mkv")
        val lossy = rank("Movie.2023.1080p.WEB-DL.x264.AAC.mkv")
        val silent = rank("Movie.2023.1080p.WEB-DL.x264.mkv")

        assertTrue(atmos > dts)
        assertTrue(dts > lossy)
        // A name that says nothing about audio is not an accusation. It ties with the stated lossy
        // track, and the two are then weighed on everything else they say.
        assertEquals(0, silent.compareTo(lossy))
    }

    @Test
    fun burntInSubtitlesCostTheCopyTheTie() {
        val clean = rank("Movie.2019.1080p.x265.10bit.DTS.5.1.mkv")
        val hardsubbed = rank("Movie.2019.1080p.x265.10bit.DTS.5.1.HardSubs.mkv")

        assertTrue(clean > hardsubbed)
    }

    @Test
    fun sizeStillDecidesBetweenTwoIdenticalClaims() {
        val big = rank("Inception.2010.mkv", 9)
        val small = rank("Inception.2010.mkv", 1)

        assertTrue(big > small)
    }

    @Test
    fun aCopyAlreadyOnThePhoneWinsAThatSizeWouldDecide() {
        val fetchedButBigger = rank("Inception.2010.mkv", 9)
        val alreadyHere = rank("Inception.2010.mkv", 1, onPhone = true)

        // Two names that say the same thing describe the same picture, and one of them needs no
        // download to open.
        assertTrue(alreadyHere > fetchedButBigger)
    }

    @Test
    fun beingOnThePhoneDoesNotMakeAWorsePictureIntoABetterOne() {
        val highDefinitionOnThePhone = rank("Dune.2021.1080p.WEB-DL.x264.mkv", onPhone = true)
        val ultraDefinitionStillOnTheChannel = rank("Dune.2021.2160p.WEB-DL.x264.mkv")

        assertTrue(ultraDefinitionStillOnTheChannel > highDefinitionOnThePhone)
    }

    @Test
    fun aCleanCopyOnTheChannelOutweighsASubtitledOneAlreadyHere() {
        val hardsubbedOnThePhone = rank("Movie.2019.1080p.x265.10bit.DTS.5.1.HardSubs.mkv", onPhone = true)
        val cleanOnTheChannel = rank("Movie.2019.1080p.x265.10bit.DTS.5.1.mkv")

        // Burnt-in captions are a worse picture, not merely a different one, and being already
        // downloaded cannot make up for that.
        assertTrue(cleanOnTheChannel > hardsubbedOnThePhone)
    }

    @Test
    fun whichLanguageADubCarriesIsNotAJudgementOfTheCopy() {
        val hindiFourK = rank("Jailer.2023.2160p.x265.Hindi.mkv")
        val englishFullHd = rank("Jailer.2023.1080p.x264.English.mkv")

        assertTrue(hindiFourK > englishFullHd)
    }

    @Test
    fun telegramsFrameSizeFillsOnlyAGapInTheName() {
        val tall = rank("VID_20240101.mkv", frameHeight = 1080)
        val statedSmall = rank("Movie.2019.720p.mkv")

        assertTrue(tall > statedSmall)

        // Beside a name that states a resolution, the frame Telegram was given is not evidence of anything.
        val namedLow = rank("Movie.2019.720p.mkv", frameHeight = 1080)
        assertEquals(rank("Movie.2019.720p.mkv"), namedLow)
    }

    @Test
    fun sortingAWholePileLeavesTheBestOnTop() {
        val copies = listOf(
            "Lain S01E03 720p BluRay x264.mkv" to 1L,
            "Lain S01E03 1080p WEBRip x264.mkv" to 9L,
            "Lain S01E03 1080p BluRay x265 10bit HDR.mkv" to 4L
        ).map { (name, gb) -> rank(name, gb) }

        val order = copies.sortedDescending()
        assertEquals(2, copies.indexOf(order.first()))
        assertEquals(1, copies.indexOf(order[1]))
        assertEquals(0, copies.indexOf(order[2]))
    }

    private companion object {
        const val ONE_GB = 1073741824L
    }
}
