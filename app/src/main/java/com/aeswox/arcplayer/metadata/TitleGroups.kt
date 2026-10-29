package com.aeswox.arcplayer.metadata

import java.util.Calendar

/**
 * One file waiting to be sorted into a card.
 *
 * [id] is only carried through into [TitlePart] and never read here, so the caller decides what a
 * file is called by. [sizeBytes] is the only quality signal a file name cannot lie about: two copies
 * of the same film with no resolution in either name still have different sizes.
 *
 * [frameHeight] and [onPhone] are the two claims a name cannot make for itself. The first is there
 * for files whose name says nothing — a video Telegram saved from a chat is called `VID_20240101…`
 * and the only thing known about its picture is how tall it is. The second is what puts the copy
 * already on the phone ahead of an equal one that still has to be fetched.
 */
data class TitleCandidate(
    val id: Long,
    val fileName: String,
    val sizeBytes: Long,
    val frameHeight: Int = 0,
    val onPhone: Boolean = false,
)

/** Whether a group is one work, or a numbered part of a longer one. */
enum class TitleKind { MOVIE, SERIES }

/**
 * One file inside a [TitleGroup].
 *
 * [season] is null for the shows that number their episodes straight from one, which is most of what
 * gets forwarded into an archive. [episode] is only null for a film, whose one reason to appear twice
 * in a group is that two copies of it were uploaded.
 */
data class TitlePart(
    val id: Long,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val sizeBytes: Long,
)

/**
 * One work, gathered from the names of every copy of it the library holds.
 *
 * This is what a poster card shows, so a film uploaded twice becomes one card with two parts, and a
 * series uploaded episode by episode becomes one card with the whole run inside it.
 *
 * [key] identifies the card for one pass over one list of names. It is not a storage id: names are
 * typed by whoever uploaded the file, and nothing here survives a uploader changing theirs.
 */
data class TitleGroup(
    val key: String,
    val kind: TitleKind,
    val displayTitle: String,
    val year: Int?,
    val parts: List<TitlePart>,
) {

    /**
     * The part to hang the card's artwork and its play button on.
     *
     * The best copy of a film, and the first episode of a series, because that is the file a
     * catalogue is most likely to have a poster for and the one a viewer would want opened.
     */
    val representative: TitlePart
        get() = parts.first()

    /** Seasons, oldest first, each holding its episodes in watch order. */
    val seasons: List<Pair<Int, List<TitlePart>>>
        get() = parts
            .groupBy { it.season ?: 1 }
            .toSortedMap()
            .map { (season, episodes) -> season to episodes }

    val sizeBytes: Long
        get() = parts.sumOf { it.sizeBytes }
}

/**
 * Turns a list of file names into cards.
 *
 * Groups on what [MediaNameParser] reads out of a name rather than on any id, because a file that is
 * forwarded into another chat keeps its Telegram file id but changes its message id, and the local
 * keys are built from both. Copies of one film in one archive therefore have nothing in common except
 * the name someone typed twice.
 *
 * Nothing here needs a player, a chat or a network, so it is tested with nothing but names.
 */
object TitleGroups {

    /**
     * How many numbered siblings a name needs before the library calls it a show.
     *
     * Three is a bet about anime archives, which are the numbered runs this exists to catch, and it is
     * low enough that a film franchise's first three sequels will land on one card. Nothing is lost by
     * that: each part is still playable on its own, and only the poster is shared.
     */
    private const val RUN_MIN = 3

    /** Titles come out of the parser as words joined by single spaces, so the number is the last one. */
    private val trailingNumber = Regex("^(.+)\\s(\\d{1,4})$")

    private class MovieEntry(val candidate: TitleCandidate, val at: Int, val title: String, val year: Int?)

    private class EpisodeEntry(
        val candidate: TitleCandidate,
        val at: Int,
        val series: String,
        val season: Int?,
        val episode: Int,
        val episodeTitle: String?,
        val year: Int? = null
    )

    /**
     * The cards this set of files describes, newest first.
     *
     * [candidates] has to arrive in the order the library holds them, because a file name says nothing
     * about when it was uploaded and that ordering is the only date available. A group is then placed
     * wherever its newest member was, so a chat read in recency order stays a grid in recency order.
     *
     * Every copy survives, best first. Dropping a duplicate here would be a decision about which one
     * the viewer wants, and only they can see why there are two.
     */
    fun group(
        candidates: List<TitleCandidate>,
        thisYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): List<TitleGroup> {
        val movies = LinkedHashMap<String, MutableList<MovieEntry>>()
        val shows = LinkedHashMap<String, MutableList<EpisodeEntry>>()
        val built = ArrayList<Pair<TitleGroup, Int>>()

        candidates.forEachIndexed { index, candidate ->
            when (val guess = MediaNameParser.parse(candidate.fileName, thisYear)) {
                is MediaGuess.Movie -> movies
                    .getOrPut(TitleScore.key(guess.title)) { ArrayList() }
                    .add(MovieEntry(candidate, index, guess.title, guess.year))

                is MediaGuess.Episode -> shows
                    .getOrPut(TitleScore.key(guess.series)) { ArrayList() }
                    .add(
                        EpisodeEntry(
                            candidate, index, guess.series, guess.season, guess.episode, guess.episodeTitle, guess.year
                        )
                    )

                // A name that said nothing is still one work on its own, and merging two of them
                // because neither could be read would be worse than two lonely cards.
                is MediaGuess.Unnamed -> built += TitleGroup(
                    key = "u:" + candidate.id,
                    kind = TitleKind.MOVIE,
                    displayTitle = guess.fallback,
                    year = null,
                    parts = listOf(TitlePart(candidate.id, null, null, null, candidate.sizeBytes))
                ) to index
            }
        }

        val consumed = HashSet<String>()
        numberedRuns(movies, thisYear).forEach { (stemKey, buckets) ->
            val moved = ArrayList<EpisodeEntry>()
            buckets.forEach { (bucketKey, numbered) ->
                consumed += bucketKey
                movies.getValue(bucketKey).forEach { entry ->
                    moved += EpisodeEntry(entry.candidate, entry.at, numbered.stem, null, numbered.number, null, entry.year)
                }
            }
            // Filed into the bucket that is already there rather than started as a new one, which is
            // what puts "One Piece 1000" and "One Piece - E1000" on the same card.
            shows.getOrPut(stemKey) { ArrayList() }.addAll(moved)
        }

        movies.forEach { (key, entries) -> if (key !in consumed) built += movieGroups(entries) }
        shows.values.forEach { built += episodeGroup(it) }

        return built.sortedBy { it.second }.map { it.first }
    }

    private class Numbered(val stem: String, val number: Int)

    /**
     * Numbered films that are really one show, filed under the name in front of the number.
     *
     * Only a name with at least [RUN_MIN] numbered siblings is returned. One file called "Rocky 2" is a
     * sequel and stays its own film; a hundred called "One Piece 800" through "One Piece 1000" are an
     * anime, and the archive saying so is evidence the parser would never read out of one name alone.
     */
    private fun numberedRuns(
        movies: Map<String, MutableList<MovieEntry>>,
        thisYear: Int
    ): Map<String, List<Pair<String, Numbered>>> {
        val runs = LinkedHashMap<String, MutableList<Pair<String, Numbered>>>()
        movies.forEach { (bucketKey, entries) ->
            val numbered = trailingNumberOf(entries.first().title, thisYear) ?: return@forEach
            runs.getOrPut(TitleScore.key(numbered.stem)) { ArrayList() }.add(bucketKey to numbered)
        }
        return runs.filter { it.value.size >= RUN_MIN }
    }

    /** The name and the part number of a title that ends in a number, or null when it does not. */
    private fun trailingNumberOf(title: String, thisYear: Int): Numbered? {
        val match = trailingNumber.find(title.trim()) ?: return null
        val number = match.groupValues[2].toIntOrNull() ?: return null
        // A number in this range is a year someone left at the end of the name, not a part of a series.
        if (number in 1900..(thisYear + 1)) return null
        val stem = match.groupValues[1].trim { it in " -_.·" }
        if (stem.isBlank()) return null
        return Numbered(stem, number)
    }

    /**
     * Cards for every copy that claims the same title.
     *
     * Two films can genuinely share a name and only a year tells them apart, so a bucket holding more
     * than one is split. A copy whose name carried no year then joins the version the library holds
     * most of, because a fourth copy of the film that exists four times is far likelier than a fourth
     * copy of the remake that exists once.
     */
    private fun movieGroups(entries: List<MovieEntry>): List<Pair<TitleGroup, Int>> {
        val titleKey = TitleScore.key(entries.first().title)
        val knownYears = entries.mapNotNull { it.year }
        if (knownYears.distinct().size <= 1) {
            val year = knownYears.firstOrNull()
            return listOf(movieGroup(entries, "m:$titleKey:${year ?: 0}", year) to entries.minOf { it.at })
        }

        val dominant = mostCommonYear(knownYears)
        val together = entries.filter { it.year == null || it.year == dominant }
        val result = ArrayList<Pair<TitleGroup, Int>>()
        result += movieGroup(together, "m:$titleKey:$dominant", dominant) to together.minOf { it.at }
        entries.filter { it.year != null && it.year != dominant }
            .groupBy { it.year }
            .forEach { (year, copies) ->
                result += movieGroup(copies, "m:$titleKey:${year ?: 0}", year) to copies.minOf { it.at }
            }
        return result
    }

    private fun movieGroup(entries: List<MovieEntry>, key: String, year: Int?): TitleGroup {
        // What the names claim about how each copy was made, with size as the tiebreak they leave. A
        // claim only counts where one exists, so two silent names still fall back to the old rule and
        // the heavier file goes first; equal claims and equal sizes are the same upload twice, and the
        // copy that arrived first keeps its place.
        val sorted = entries
            .map { it to rank(it.candidate) }
            .sortedWith(
                compareByDescending<Pair<MovieEntry, CopyRank>> { it.second }.thenBy { it.first.at }
            )
            .map { it.first }
        return TitleGroup(
            key = key,
            kind = TitleKind.MOVIE,
            displayTitle = entries.minBy { it.at }.title,
            year = year,
            parts = sorted.map { TitlePart(it.candidate.id, null, null, null, it.candidate.sizeBytes) }
        )
    }

    private fun episodeGroup(entries: List<EpisodeEntry>): Pair<TitleGroup, Int> {
        // Season is deliberately absent from the key: a show is one card, and its seasons are the
        // thing the info page lists underneath it.
        val key = "s:" + TitleScore.key(entries.first().series)
        // Watch order first, then within one episode the copy whose name claims the better picture.
        val sorted = entries
            .map { it to rank(it.candidate) }
            .sortedWith(
                compareBy<Pair<EpisodeEntry, CopyRank>>({ it.first.season ?: 1 }, { it.first.episode })
                    .thenByDescending { it.second }
            )
            .map { it.first }
        val knownYears = entries.mapNotNull { it.year }
        val year = if (knownYears.isEmpty()) null else mostCommonYear(knownYears)
        return TitleGroup(
            key = key,
            kind = TitleKind.SERIES,
            displayTitle = entries.minBy { it.at }.series,
            year = year,
            parts = sorted.map {
                TitlePart(
                    id = it.candidate.id,
                    season = it.season,
                    episode = it.episode,
                    episodeTitle = it.episodeTitle,
                    sizeBytes = it.candidate.sizeBytes
                )
            }
        ) to entries.minOf { it.at }
    }

    /**
     * What one file says about its own quality: the claims in its name, plus the two it cannot make.
     *
     * [TitleCandidate.frameHeight] reaches [CopyRank] whichever way the name reads, because
     * [ReleaseInfo] already takes it only where the name states no resolution — a Telegram download
     * called `VID_20240101…` has nothing else to go on, and beside a name that says "720p" the height
     * of the file that happens to be here is not evidence about the film.
     */
    private fun rank(candidate: TitleCandidate): CopyRank =
        CopyRank.of(
            fileName = candidate.fileName,
            sizeBytes = candidate.sizeBytes,
            frameHeight = candidate.frameHeight,
            onPhone = candidate.onPhone
        )

    /** The year with the most copies, and the newest of them when two are tied. */
    private fun mostCommonYear(years: List<Int>): Int {
        var best = years.first()
        var bestCount = 0
        for (year in years.distinct()) {
            val count = years.count { it == year }
            if (count > bestCount || (count == bestCount && year > best)) {
                best = year
                bestCount = count
            }
        }
        return best
    }
}
