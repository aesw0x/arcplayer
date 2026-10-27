package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grouping, tested with nothing but names.
 *
 * The names are the shapes real archives carry: a careful uploader's brackets and release tail, a
 * careless one's dots, and a channel that renamed nothing at all. The year is pinned so a test cannot
 * start failing in January.
 *
 * Several of the tests below put a phone copy beside a channel copy of the same work, which is what the
 * merged library hands this object: one list, files from two places, and no id in common. Grouping has
 * to ignore where a file came from and still find the copies of one film — then let the shelf put the
 * one already downloaded first.
 */
class TitleGroupsTest {

    private val interstellar = "Interstellar (2014) 1080p BluRay x264.mkv"
    private val interstellarSmall = "Interstellar.2014.720p.WEB-DL.mkv"

    private fun file(
        id: Long,
        name: String,
        sizeBytes: Long = ONE_GB,
        onPhone: Boolean = false,
        frameHeight: Int = 0
    ) = TitleCandidate(id, name, sizeBytes, frameHeight, onPhone)

    @Test
    fun twoCopiesOfOneFilmBecomeOneCard() {
        val groups = TitleGroups.group(
            listOf(file(1L, interstellar), file(2L, interstellarSmall)),
            thisYear = THIS_YEAR
        )

        assertEquals(1, groups.size)
        assertEquals(TitleKind.MOVIE, groups[0].kind)
        assertEquals("Interstellar", groups[0].displayTitle)
        assertEquals(2014, groups[0].year)
        assertEquals(listOf(1L, 2L), groups[0].parts.map { it.id })
    }

    @Test
    fun theBestCopyIsTheOneTheCardPlays() {
        val groups = TitleGroups.group(
            listOf(file(1L, interstellar, 2L * ONE_GB), file(2L, interstellarSmall, 8L * ONE_GB)),
            thisYear = THIS_YEAR
        )

        // The smaller file. A name that states 1080p off a disc outranks one that states 720p off a
        // store download, however much bigger the second one turned out to be.
        assertEquals(1L, groups[0].representative.id)
    }

    @Test
    fun sizeStillDecidesWhenNeitherNameSaysAnything() {
        val group = TitleGroups.group(
            listOf(
                file(1L, "A Quiet Place (2018).mkv", ONE_GB),
                file(2L, "A.Quiet.Place.2018.mkv", 6L * ONE_GB)
            ),
            thisYear = THIS_YEAR
        ).single()

        assertEquals(2L, group.representative.id)
    }

    @Test
    fun episodesOfOneShowBecomeOneCardInWatchOrder() {
        val groups = TitleGroups.group(
            listOf(
                file(1L, "Breaking Bad S02E01 1080p.mkv"),
                file(2L, "Breaking Bad S01E02 720p.mkv"),
                file(3L, "Breaking Bad S01E01 Pilot 1080p.mkv")
            ),
            thisYear = THIS_YEAR
        )

        assertEquals(1, groups.size)
        assertEquals(TitleKind.SERIES, groups[0].kind)
        assertEquals("Breaking Bad", groups[0].displayTitle)
        assertEquals(listOf(3L, 2L, 1L), groups[0].parts.map { it.id })
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), groups[0].parts.map { it.season to it.episode })
    }

    @Test
    fun seasonsAreListedOldestFirstUnderOneCard() {
        val group = TitleGroups.group(
            listOf(
                file(1L, "Breaking Bad S02E02 1080p.mkv"),
                file(2L, "Breaking Bad S02E01 1080p.mkv"),
                file(3L, "Breaking Bad S01E01 1080p.mkv")
            ),
            thisYear = THIS_YEAR
        ).single()

        assertEquals(listOf(1, 2), group.seasons.map { it.first })
        assertEquals(listOf(3L), group.seasons[0].second.map { it.id })
        assertEquals(listOf(2L, 1L), group.seasons[1].second.map { it.id })
    }

    @Test
    fun aShowNumberedWithoutSeasonsAssumesTheFirstOne() {
        val group = TitleGroups.group(
            listOf(
                file(1L, "Cowboy Bebop - Episode 06.mkv"),
                file(2L, "Cowboy Bebop - Episode 05.mkv")
            ),
            thisYear = THIS_YEAR
        ).single()

        assertEquals(listOf(5, 6), group.parts.map { it.episode })
        assertTrue(group.parts.all { it.season == null })
        assertEquals(listOf(1), group.seasons.map { it.first })
    }

    @Test
    fun aSequelKeepsItsOwnCard() {
        val groups = TitleGroups.group(
            listOf(
                file(1L, "Blade Runner 2049 (2017) 2160p.mkv"),
                file(2L, "Blade Runner (1982) 1080p.mkv")
            ),
            thisYear = THIS_YEAR
        )

        assertEquals(setOf("Blade Runner 2049", "Blade Runner"), groups.map { it.displayTitle }.toSet())
        assertEquals(listOf(2017, 1982), groups.map { it.year })
    }

    @Test
    fun remakesSharingANameStayApart() {
        val groups = TitleGroups.group(
            listOf(
                file(1L, "King Kong (1933) 720p.mkv"),
                file(2L, "King Kong (2005) 1080p.mkv"),
                file(3L, "King Kong 1080p.mkv")
            ),
            thisYear = THIS_YEAR
        )

        assertEquals(2, groups.size)
        assertEquals(listOf(1933, 2005), groups.map { it.year })
    }

    @Test
    fun aYearlessCopyJoinsTheVersionTheLibraryHoldsMostOf() {
        val groups = TitleGroups.group(
            listOf(
                file(1L, "King Kong (1933) 720p.mkv"),
                file(2L, "King Kong (1933) 1080p.mkv"),
                file(3L, "King Kong (2005) 1080p.mkv"),
                file(4L, "King Kong 1080p.mkv")
            ),
            thisYear = THIS_YEAR
        )

        val thousandNineHundred = groups.first { it.year == 1933 }
        val twoThousandFive = groups.first { it.year == 2005 }
        assertEquals(listOf(2L, 4L, 1L), thousandNineHundred.parts.map { it.id })
        assertEquals(listOf(3L), twoThousandFive.parts.map { it.id })
    }

    @Test
    fun filesThatRefuseToBeNamedNeverMerge() {
        val groups = TitleGroups.group(
            listOf(
                file(1L, "VID_20240101_121314.mp4"),
                file(2L, "VID_20240102_090807.mp4")
            ),
            thisYear = THIS_YEAR
        )

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.parts.size == 1 })
        assertEquals(setOf("u:1", "u:2"), groups.map { it.key }.toSet())
    }

    @Test
    fun punctuationAndCaseDoNotSplitAName() {
        val group = TitleGroups.group(
            listOf(
                file(1L, "One Piece - E1000.mkv"),
                file(2L, "one.piece.e1000.mkv")
            ),
            thisYear = THIS_YEAR
        ).single()

        assertEquals("s:onepiece", group.key)
        assertEquals(TitleKind.SERIES, group.kind)
        assertEquals(setOf(1L, 2L), group.parts.map { it.id }.toSet())
    }

    @Test
    fun aLoneNumberedTitleIsStillAFilm() {
        val group = TitleGroups.group(
            listOf(file(1L, "Rocky II 1080p.mkv"), file(2L, "Creed 2015 1080p.mkv")),
            thisYear = THIS_YEAR
        ).first()

        // One sequel is not evidence of a run, and the parser is sequel-safe on purpose.
        assertEquals(TitleKind.MOVIE, group.kind)
        assertEquals(1, TitleGroups.group(listOf(file(1L, "Rocky 2.mkv")), thisYear = THIS_YEAR).size)
    }

    @Test
    fun anUnderscoredCopyJoinsTheSeriesItsDottedSiblingBuilt() {
        // Two uploaders, two habits, one episode: the dotted name reached the show card and the
        // underscored one became its own film, because \b does not treat an underscore as a boundary.
        val group = TitleGroups.group(
            listOf(
                file(1L, "Stranger.Things.S03.E01.Suzie.Do.You.Copy?.2160p.10bit.BluRa.mkv"),
                file(2L, "Stranger_Things_S03E01_2160p_NF_WEB_DL_x265_10bit_SDR_AAC_5_1_FS87.mkv"),
                file(3L, "Stranger_Things_S03E02_2160p_NF_WEB_DL_x265_10bit_SDR_AAC_5_1_FS87.mkv")
            ),
            thisYear = THIS_YEAR
        ).single()

        assertEquals("s:strangerthings", group.key)
        assertEquals(TitleKind.SERIES, group.kind)
        assertEquals(listOf(1L, 2L, 3L), group.parts.map { it.id })
        assertEquals(listOf(3 to 1, 3 to 1, 3 to 2), group.parts.map { it.season to it.episode })
    }

    @Test
    fun aRunOfNumbersBecomesOneShow() {
        val group = TitleGroups.group(
            listOf(
                file(1L, "Naruto 202.mkv"),
                file(2L, "Naruto 200.mkv"),
                file(3L, "Naruto 201.mkv")
            ),
            thisYear = THIS_YEAR
        ).single()

        assertEquals(TitleKind.SERIES, group.kind)
        assertEquals("Naruto", group.displayTitle)
        assertEquals(listOf(200, 201, 202), group.parts.map { it.episode })
    }

    @Test
    fun aNumberedRunJoinsTheSeriesThatAlreadyUsesEpisodeLabels() {
        val groups = TitleGroups.group(
            listOf(
                file(1L, "One Piece - E1002.mkv"),
                file(2L, "One Piece 999.mkv"),
                file(3L, "One Piece 1000.mkv"),
                file(4L, "One Piece 1001.mkv")
            ),
            thisYear = THIS_YEAR
        )

        assertEquals(1, groups.size)
        assertEquals(listOf(999, 1000, 1001, 1002), groups[0].parts.map { it.episode })
    }

    @Test
    fun aYearAtTheEndOfANameIsNotAPartNumber() {
        val groups = TitleGroups.group(
            listOf(
                file(1L, "2001 A Space Odyssey 1968.mkv"),
                file(2L, "2010 The Year We Make Contact 1984.mkv"),
                file(3L, "Apollo 13 1995.mkv")
            ),
            thisYear = THIS_YEAR
        )

        // Three different films whose names happen to end in numbers somebody could read as a run.
        assertEquals(3, groups.size)
        assertTrue(groups.all { it.kind == TitleKind.MOVIE })
    }

    @Test
    fun newestUploadDecidesWhereACardSits() {
        val candidates = listOf(file(1L, interstellar), file(2L, "Breaking Bad S01E01 Pilot 1080p.mkv"))

        val newestFirst = TitleGroups.group(candidates, thisYear = THIS_YEAR)
        assertEquals(listOf("Interstellar", "Breaking Bad"), newestFirst.map { it.displayTitle })

        val oldestFirst = TitleGroups.group(candidates.reversed(), thisYear = THIS_YEAR)
        assertEquals(listOf("Breaking Bad", "Interstellar"), oldestFirst.map { it.displayTitle })
    }

    @Test
    fun duplicateEpisodesBothSurviveBestFirst() {
        val group = TitleGroups.group(
            listOf(
                file(1L, "Lain S01E03 720p.mkv", ONE_GB),
                file(2L, "Lain S01E03 1080p.mkv", 4L * ONE_GB)
            ),
            thisYear = THIS_YEAR
        ).single()

        assertEquals(2, group.parts.size)
        assertEquals(listOf(2L, 1L), group.parts.map { it.id })
    }

    @Test
    fun aCopyOnThePhoneAndACopyOnTheChannelBecomeOneCard() {
        val group = TitleGroups.group(
            listOf(
                file(1L, interstellar, 8L * ONE_GB),
                file(2L, interstellar, 2L * ONE_GB, onPhone = true)
            ),
            thisYear = THIS_YEAR
        ).single()

        // One card, and the file already sitting on the phone is the one it plays: two names that claim
        // the same picture claim the same picture, whatever either was downloaded for.
        assertEquals(2, group.parts.size)
        assertEquals(listOf(2L, 1L), group.parts.map { it.id })
        assertEquals(2L, group.representative.id)
    }

    @Test
    fun oneEpisodesTwoCopiesSortWithThePhonesFirst() {
        val group = TitleGroups.group(
            listOf(
                file(1L, "Lain S01E01 1080p.mkv", 6L * ONE_GB),
                file(2L, "Lain S01E01 1080p.mkv", ONE_GB, onPhone = true),
                file(3L, "Lain S01E02 1080p.mkv")
            ),
            thisYear = THIS_YEAR
        ).single()

        // Watch order still governs the list; the phone only decides between two copies of one episode.
        assertEquals(listOf(2L, 1L, 3L), group.parts.map { it.id })
        assertEquals(listOf(1, 1, 2), group.parts.map { it.episode })
    }

    @Test
    fun theHeightOfACopyIsOnlyUsedWhenNeitherNameStatesAResolution() {
        val group = TitleGroups.group(
            listOf(
                file(1L, "Arrival (2016).mkv", 6L * ONE_GB),
                file(2L, "Arrival (2016).mkv", ONE_GB, frameHeight = 2160)
            ),
            thisYear = THIS_YEAR
        ).single()

        // A file whose name says nothing is still measured by what its pixels are, which is how a
        // download from a chat gets ranked at all.
        assertEquals(2L, group.representative.id)
    }

    @Test
    fun aCardsSizeIsTheSizeOfEveryCopyItHolds() {
        val group = TitleGroups.group(
            listOf(file(1L, interstellar, 3L * ONE_GB), file(2L, interstellarSmall, 1L * ONE_GB)),
            thisYear = THIS_YEAR
        ).single()

        assertEquals(4L * ONE_GB, group.sizeBytes)
    }

    private companion object {
        const val ONE_GB = 1073741824L
        const val THIS_YEAR = 2026
    }
}
