package com.aeswox.arcplayer.metadata

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Catalogue answers for the things this app plays, over plain HTTP. */

internal fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

internal fun yearOf(date: String?): Int? =
    date?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()

internal fun positiveYear(value: Int): Int? = if (value in 1800..2999) value else null

/** The line under a series name on a card. Null when the file is not part of a series. */
internal fun episodeLabel(guess: MediaGuess): String? = when (guess) {
    is MediaGuess.Episode -> {
        val season = guess.season
        if (season != null) "S%02d · Episode %d".format(season, guess.episode) else "Episode ${guess.episode}"
    }
    else -> null
}

/**
 * The Movie Database.
 *
 * The widest single catalogue and free to register, so it is asked first — though it is not in the
 * chain at all until a key is compiled in, which is the normal state of this build. Two requests for
 * a title it recognises: the search that finds it, then its wordmark, which search results do not
 * carry. Nothing else in the chain has a wordmark at all, so an anime or a Wikipedia-only film just
 * goes without.
 */
internal class TmdbSource(private val apiKey: String) : MediaSource {

    override val name = "tmdb"

    private val base = "https://api.themoviedb.org/3"

    override fun lookup(guess: MediaGuess): MediaOutcome = when (guess) {
        is MediaGuess.Movie -> movie(guess)
        is MediaGuess.Episode -> episode(guess)
        is MediaGuess.Unnamed -> MediaOutcome.NoMatch
    }

    private fun movie(guess: MediaGuess.Movie): MediaOutcome {
        val params = mutableListOf(
            "api_key" to apiKey,
            "language" to "en-US",
            "query" to guess.title,
            "include_adult" to "false"
        )
        guess.year?.let { params.add("year" to it.toString()) }
        val response = HttpJson.get(HttpJson.url("$base/search/movie", *params.toTypedArray()))
            ?: return MediaOutcome.Unreachable
        val rows = response.optJSONArray("results").objects()
        val candidates = rows.map { row ->
            TitleScore.Candidate(
                row.optString("title").ifBlank { row.optString("original_title") },
                yearOf(row.optString("release_date"))
            )
        }
        val chosen = TitleScore.bestIndex(candidates, guess.title, guess.year)
        if (chosen < 0) return MediaOutcome.NoMatch
        val row = rows[chosen]
        return MediaOutcome.Found(
            MediaMatch(
                title = row.optString("title").ifBlank { guess.title },
                subtitle = yearOf(row.optString("release_date"))?.toString(),
                posterUrl = poster(row.optString("poster_path")),
                source = name,
                logoUrl = logo("movie", row.optInt("id")),
                providerId = row.optInt("id").takeIf { it > 0 },
                isSeries = false
            )
        )
    }

    private fun episode(guess: MediaGuess.Episode): MediaOutcome {
        val response = HttpJson.get(
            HttpJson.url(
                "$base/search/tv",
                "api_key" to apiKey,
                "language" to "en-US",
                "query" to guess.series,
                "include_adult" to "false"
            )
        ) ?: return MediaOutcome.Unreachable
        val rows = response.optJSONArray("results").objects()
        val candidates = rows.map { row ->
            TitleScore.Candidate(
                row.optString("name").ifBlank { row.optString("original_name") },
                yearOf(row.optString("first_air_date"))
            )
        }
        val chosen = TitleScore.bestIndex(candidates, guess.series, null)
        if (chosen < 0) return MediaOutcome.NoMatch
        val row = rows[chosen]
        return MediaOutcome.Found(
            MediaMatch(
                title = row.optString("name").ifBlank { guess.series },
                subtitle = episodeLabel(guess),
                posterUrl = poster(row.optString("poster_path")),
                source = name,
                logoUrl = logo("tv", row.optInt("id")),
                providerId = row.optInt("id").takeIf { it > 0 },
                isSeries = true
            )
        )
    }

    /**
     * The title one IMDb id names, when TMDB knows it at all.
     *
     * A film whose name search misses on one romanisation is often still in the catalogue under
     * another, and the id is the only way to reach it. Null is the ordinary answer for a regional
     * release TMDB has never heard of, and costs nothing: the caller keeps the IMDb row.
     */
    internal fun byImdbId(imdbId: String, guess: MediaGuess): MediaMatch? {
        if (!imdbId.startsWith("tt")) return null
        val series = guess is MediaGuess.Episode
        val response = HttpJson.get(
            HttpJson.url(
                "$base/find/$imdbId",
                "api_key" to apiKey,
                "language" to "en-US",
                "external_source" to "imdb_id"
            )
        ) ?: return null
        val row = response.optJSONArray(if (series) "tv_results" else "movie_results").objects()
            .firstOrNull() ?: return null
        val title = row.optString(if (series) "name" else "title").nullIfBlank()
            ?: row.optString(if (series) "original_name" else "original_title").nullIfBlank()
            ?: return null
        val id = row.optInt("id")
        return MediaMatch(
            title = title,
            subtitle = episodeLabel(guess) ?: yearOf(
                row.optString(if (series) "first_air_date" else "release_date")
            )?.toString(),
            posterUrl = poster(row.optString("poster_path")),
            source = name,
            logoUrl = logo(if (series) "tv" else "movie", id),
            providerId = id.takeIf { it > 0 },
            isSeries = series
        )
    }

    /**
     * The wordmark for one entry, or null when TMDB has none worth showing.
     *
     * A missing or failed images call is not a failed lookup: the name and poster already earned
     * their place on the card, and a wordmark is the one piece of artwork a viewer can do without.
     */
    private fun logo(kind: String, id: Int): String? {
        if (id <= 0) return null
        val response = HttpJson.get(
            HttpJson.url(
                "$base/$kind/$id/images",
                "api_key" to apiKey,
                "include_image_language" to "en,null"
            )
        ) ?: return null
        val renditions = response.optJSONArray("logos").objects().mapNotNull { row ->
            val path = row.optString("file_path").nullIfBlank() ?: return@mapNotNull null
            TitleLogo.Rendition(
                filePath = path,
                language = row.optString("iso_639_1").nullIfBlank(),
                widthPx = row.optInt("width"),
                voteAverage = row.optDouble("vote_average", 0.0),
                voteCount = row.optInt("vote_count")
            )
        }
        val chosen = TitleLogo.best(renditions, preferredLanguage = "en") ?: return null
        // The largest preset TMDB rasterises. `original` is a 12 MB scan of a logo that is never shown
        // wider than the screen, and this runs for every file in a chat.
        return "https://image.tmdb.org/t/p/w1280${chosen.filePath}"
    }

    private fun poster(path: String): String? =
        if (path.isBlank()) null else "https://image.tmdb.org/t/p/w500$path"
}

/**
 * AniList, which needs no key.
 *
 * The reason it is here at all: the archive this app plays is anime, and the catalogues that cost
 * nothing else either have no entries for it or number their episodes per season rather than the
 * straight count an uploaded file carries.
 */
internal class AniListSource : MediaSource {

    override val name = "anilist"

    private val query = """
        query(${'$'}search: String) {
          Page(page: 1, perPage: 8) {
            media(type: ANIME, search: ${'$'}search, sort: SEARCH_MATCH, isAdult: false) {
              id
              title { romaji english native }
              coverImage { large }
              seasonYear
            }
          }
        }
    """.trimIndent()

    override fun lookup(guess: MediaGuess): MediaOutcome {
        val wanted = when (guess) {
            is MediaGuess.Movie -> guess.title
            is MediaGuess.Episode -> guess.series
            is MediaGuess.Unnamed -> return MediaOutcome.NoMatch
        }
        val body = JSONObject()
            .put("query", query)
            .put("variables", JSONObject().put("search", wanted))
        val response = HttpJson.post("https://graphql.anilist.co", body.toString())
            ?: return MediaOutcome.Unreachable
        val rows = response.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media").objects()
        val candidates = rows.map { row ->
            TitleScore.Candidate(
                bestTitle(row.optJSONObject("title")).orEmpty(),
                positiveYear(row.optInt("seasonYear"))
            )
        }
        val chosen = TitleScore.bestIndex(candidates, wanted, (guess as? MediaGuess.Movie)?.year)
        if (chosen < 0) return MediaOutcome.NoMatch
        val row = rows[chosen]
        return MediaOutcome.Found(
            MediaMatch(
                title = bestTitle(row.optJSONObject("title")) ?: wanted,
                subtitle = episodeLabel(guess) ?: positiveYear(row.optInt("seasonYear"))?.toString(),
                posterUrl = row.optJSONObject("coverImage")?.optString("large")?.nullIfBlank(),
                source = name
            )
        )
    }

    /** English where a studio published one, romaji otherwise, since that is what files get named after. */
    private fun bestTitle(titles: JSONObject?): String? =
        titles?.optString("english").nullIfBlank() ?: titles?.optString("romaji").nullIfBlank()
            ?: titles?.optString("native").nullIfBlank()
}

/**
 * Wikipedia, which needs no key and knows the films and shows that AniList has no entry for.
 *
 * The disambiguation bracket is cut off the page title before scoring, because "Ghost in the Shell
 * (1995 film)" and "(2017 film)" are the same name to a viewer and only the year tells them apart.
 * A page is only eligible when its short description calls it a work rather than a soundtrack,
 * novel or list, which is what stops a scoreless name match landing the wrong artwork.
 *
 * Artwork is the part Wikipedia is weakest at: a film's poster is fair-use, and the API leaves
 * fair-use files out of a page's thumbnail. So a card from here often carries the correct name and
 * no poster, which the grid answers by keeping the frame Telegram already sent.
 */
internal class WikipediaSource : MediaSource {

    override val name = "wikipedia"

    private val qualifier = Regex("\\(([^()]*)\\)\\s*$")
    private val fourDigits = Regex("\\b(19|20)\\d{2}\\b")

    private val work = Regex(
        "\\b(film|movie|anime|television series|tv series|web series|animated series|original video animation)\\b",
        RegexOption.IGNORE_CASE
    )
    private val notWork = Regex(
        "\\b(soundtrack|album|novel|video game|list of|characters|award|magazine)\\b",
        RegexOption.IGNORE_CASE
    )

    override fun lookup(guess: MediaGuess): MediaOutcome {
        val wanted = when (guess) {
            is MediaGuess.Movie -> guess.title
            is MediaGuess.Episode -> guess.series
            is MediaGuess.Unnamed -> return MediaOutcome.NoMatch
        }
        val wantedYear = (guess as? MediaGuess.Movie)?.year
        val kind = if (guess is MediaGuess.Episode) "television series" else "film"
        val response = HttpJson.get(
            HttpJson.url(
                "https://en.wikipedia.org/w/api.php",
                "action" to "query",
                "format" to "json",
                "generator" to "search",
                "gsrsearch" to listOfNotNull(wanted, wantedYear?.toString(), kind).joinToString(" "),
                "gsrlimit" to "8",
                "prop" to "pageimages|pageprops",
                "piprop" to "thumbnail",
                "pithumbsize" to "600",
                "ppprop" to "wikibase-shortdesc"
            )
        ) ?: return MediaOutcome.Unreachable
        // A reply with no pages in it is the API having heard of none of this, which is an answer.
        val query = response.optJSONObject("query") ?: return MediaOutcome.NoMatch
        val pages = query.optJSONObject("pages") ?: return MediaOutcome.NoMatch
        // A generator query keys its pages by id and puts the relevance rank in each page's "index".
        val works = pages.keys().asSequence()
            .mapNotNull { pages.optJSONObject(it) }
            .sortedBy { it.optInt("index", Int.MAX_VALUE) }
            .mapNotNull { page ->
                val summary = page.optJSONObject("pageprops")?.optString("wikibase-shortdesc").nullIfBlank()
                    ?: return@mapNotNull null
                if (work.containsMatchIn(summary) && !notWork.containsMatchIn(summary)) page to summary else null
            }
            .toList()
        val candidates = works.map { (page, summary) ->
            val (title, year) = baseTitle(page.optString("title"))
            TitleScore.Candidate(title, year ?: firstYear(summary))
        }
        val chosen = TitleScore.bestIndex(candidates, wanted, wantedYear)
        if (chosen < 0) return MediaOutcome.NoMatch
        val (page, summary) = works[chosen]
        val (title, year) = baseTitle(page.optString("title"))
        return MediaOutcome.Found(
            MediaMatch(
                title = title,
                subtitle = episodeLabel(guess) ?: (year ?: firstYear(summary))?.toString(),
                posterUrl = page.optJSONObject("thumbnail")?.optString("source").nullIfBlank(),
                source = name
            )
        )
    }

    private fun baseTitle(pageTitle: String): Pair<String, Int?> {
        val match = qualifier.find(pageTitle) ?: return pageTitle to null
        val year = firstYear(match.groupValues[1])
        return pageTitle.removeSuffix(match.value).trim().ifBlank { pageTitle } to year
    }

    private fun firstYear(text: String): Int? =
        fourDigits.find(text)?.value?.toIntOrNull()?.let { positiveYear(it) }
}

/**
 * IMDb's own search box, which needs no key.
 *
 * The reason it is here: the regional cinema this library is full of is indexed by Amazon far better
 * than the name searches above match it. Measured on twelve Malayalam titles, ten answered real key
 * art on the first ask, and the two that did not were misspelled by the asker — while the same titles
 * returned nothing at all from Apple's store and nothing from Wikipedia, which leaves fair-use
 * posters out of its API by design.
 *
 * One request carries its own answer: the name, the year, the poster and the `tt` id, and that id is
 * the more valuable half. [TmdbSource.byImdbId] spends it on the film TMDB could not find by name,
 * which recovers the wordmark, the backdrop and the runtime IMDb has no equivalent of.
 *
 * The endpoint is what the website calls rather than a published API, so it can change without
 * notice. A reply that is not the shape expected is [MediaOutcome.Unreachable] and not a refusal, so
 * a moved URL leaves the cards askable instead of naming a whole chat after nothing.
 */
internal class ImdbSource(private val tmdb: TmdbSource? = null) : MediaSource {

    override val name = "imdb"

    override fun lookup(guess: MediaGuess): MediaOutcome {
        val wanted = when (guess) {
            is MediaGuess.Movie -> guess.title
            is MediaGuess.Episode -> guess.series
            is MediaGuess.Unnamed -> return MediaOutcome.NoMatch
        }
        val url = imdbTypeaheadUrl(wanted) ?: return MediaOutcome.NoMatch
        val response = HttpJson.get(url) ?: return MediaOutcome.Unreachable
        val works = response.optJSONArray("d").objects().filter { row ->
            val id = row.optString("id").nullIfBlank() ?: return@filter false
            id.startsWith("tt") && row.optString("qid").nullIfBlank() in
                (if (guess is MediaGuess.Episode) seriesKinds else filmKinds)
        }
        val candidates = works.map { row -> TitleScore.Candidate(row.optString("l"), releaseYear(row)) }
        val chosen = TitleScore.bestIndex(candidates, wanted, (guess as? MediaGuess.Movie)?.year)
        if (chosen < 0) return MediaOutcome.NoMatch
        val row = works[chosen]
        val poster = imdbPoster(row.optJSONObject("i")?.optString("imageUrl"))
        val bridged = row.optString("id").nullIfBlank()?.let { tmdb?.byImdbId(it, guess) }
        if (bridged != null) return MediaOutcome.Found(bridged.copy(posterUrl = bridged.posterUrl ?: poster))
        return MediaOutcome.Found(
            MediaMatch(
                title = row.optString("l").nullIfBlank() ?: wanted,
                subtitle = episodeLabel(guess) ?: releaseYear(row)?.toString(),
                posterUrl = poster,
                source = name,
                isSeries = guess is MediaGuess.Episode
            )
        )
    }

    private companion object {

        /** `qid` is IMDb's own kind, and a person row is never the answer. */
        val filmKinds = setOf("movie", "tvMovie")
        val seriesKinds = setOf("tvSeries", "tvMiniSeries")

        val fourDigits = Regex("\\b(19|20)\\d{2}\\b")

        /** [y] is absent on a title not out yet, where the release line still names a year. */
        fun releaseYear(row: JSONObject): Int? =
            positiveYear(row.optInt("y")) ?: fourDigits.find(row.optString("tl"))?.value?.toIntOrNull()
                ?.let { positiveYear(it) }
    }
}

/**
 * The typeahead address for one name: IMDb buckets its index by the first character of the query, so
 * that character is part of the path rather than a parameter.
 */
internal fun imdbTypeaheadUrl(title: String): String? {
    val trimmed = title.trim()
    val bucket = trimmed.firstOrNull { it.isLetterOrDigit() }?.lowercaseChar() ?: return null
    val query = URLEncoder.encode(trimmed, "UTF-8").replace("+", "%20")
    return "https://v3.sg.media-imdb.com/suggestion/$bucket/$query.json"
}

/**
 * The card-sized rendition of an IMDb poster, or null unless the address is really IMDb's own CDN.
 *
 * The URL the API hands back is the scan — 900 KB for a poster this app draws 130 dp wide, once per
 * card — and Amazon resizes on the address instead of on request. The host is pinned because this
 * string came out of somebody else's reply and is about to be fetched.
 */
internal fun imdbPoster(imageUrl: String?): String? {
    val url = imageUrl.nullIfBlank() ?: return null
    if (!url.startsWith("https://m.media-amazon.com/images/")) return null
    val marker = url.indexOf("@._V1_")
    if (marker < 0) return url
    return url.substring(0, marker + "@._V1_".length) + "SX500_.jpg"
}

