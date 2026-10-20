package com.aeswox.arcplayer.metadata

import org.json.JSONArray
import org.json.JSONObject

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

