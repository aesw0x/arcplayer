package com.aeswox.arcplayer.metadata

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** The image host and a size preset, which is the only way TMDB will resize anything. */
private fun tmdbImage(path: String?, size: String): String? =
    path.nullIfBlank()?.let { "https://image.tmdb.org/t/p/$size$it" }

/** The catalogue's own shape: an array of `{ "name": … }` rows, which is how genres and crew arrive. */
private fun JSONArray?.namedValues(): List<String> =
    objects().mapNotNull { it.optString("name").nullIfBlank() }

/** The shape this file writes into the cache: an array of bare strings. The two are not readable by each other. */
private fun JSONArray?.plainValues(): List<String> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optString(it).nullIfBlank() }
}

/** Headshots come back for most actors and none of the ones a series cast list forgot. */
private fun JSONArray?.castMembers(): List<CastMember> =
    objects().mapNotNull { row ->
        val name = row.optString("name").nullIfBlank() ?: return@mapNotNull null
        CastMember(
            name = name,
            role = row.optString("character").orEmpty(),
            photoUrl = tmdbImage(row.optString("profile_path"), "w185")
        )
    }.take(20)

/**
 * The cast, seasons and episodes of one title.
 *
 * A card answers "what is this file called", which is all a grid needs. An info page needs the parts
 * a search result never carries, and those only come from one request made with the catalogue's own
 * id — which is the reason [MediaCard.providerId] exists at all.
 */
data class CastMember(
    val name: String,
    val role: String,
    val photoUrl: String?
)

/** One episode, as the catalogue numbers it. */
data class EpisodeDetail(
    val season: Int,
    val number: Int,
    val title: String,
    val overview: String,
    val stillUrl: String?,
    val runtimeMinutes: Int
)

/** A season's row in the season selector. */
data class SeasonCard(
    val number: Int,
    val title: String,
    val posterUrl: String?,
    /** How many episodes the catalogue files here, which is what makes a number in the whole run findable. */
    val episodeCount: Int
)

/**
 * Everything the info page shows that a file name does not say.
 *
 * Nullable rather than absent, because a title genuinely has no overview, no genres and sometimes no
 * artwork, and the page has to keep its shape when it lands on one of those.
 */
data class TitleDetail(
    val isSeries: Boolean,
    val overview: String,
    val genres: List<String>,
    val startYear: Int?,
    val endYear: Int?,
    /** A film's own length, or one episode's for a show. Zero when the catalogue says nothing. */
    val runtimeMinutes: Int,
    val directors: List<String>,
    val writers: List<String>,
    val cast: List<CastMember>,
    val seasons: List<SeasonCard>,
    val backdropUrl: String?,
    val maturity: String?,
    /** The catalogue's own score, which is not IMDb's and is labelled as such wherever it is shown. */
    val voteScore: Double?,
    val seasonsFetched: Boolean = false
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("shape", DETAIL_SHAPE)
        put("series", isSeries)
        put("overview", overview)
        put("genres", JSONArray(genres))
        put("startYear", startYear ?: JSONObject.NULL)
        put("endYear", endYear ?: JSONObject.NULL)
        put("runtime", runtimeMinutes)
        put("directors", JSONArray(directors))
        put("writers", JSONArray(writers))
        put("cast", JSONArray().apply { cast.forEach { put(it.toJson()) } })
        put("seasons", JSONArray().apply { seasons.forEach { put(it.toJson()) } })
        put("backdrop", backdropUrl ?: JSONObject.NULL)
        put("maturity", maturity ?: JSONObject.NULL)
        put("vote", voteScore ?: JSONObject.NULL)
        put("seasonsFetched", seasonsFetched)
    }

    companion object {
        /**
         * Bumped whenever a stored detail stops being readable by the next build. An older row is
         * dropped rather than patched, so the title is simply asked of the catalogue once more.
         */
        private const val DETAIL_SHAPE = 2

        fun fromJson(obj: JSONObject): TitleDetail? {
            if (obj.optInt("shape") < DETAIL_SHAPE) return null
            val genres = obj.optJSONArray("genres").plainValues()
            val cast = obj.optJSONArray("cast").objects().mapNotNull { row ->
                val name = row.optString("name").nullIfBlank() ?: return@mapNotNull null
                CastMember(name, row.optString("role").orEmpty(), row.optString("photo").nullIfBlank())
            }
            val seasons = obj.optJSONArray("seasons").objects().mapNotNull { row ->
                val number = row.optInt("number", -1)
                if (number < 0) return@mapNotNull null
                SeasonCard(
                    number = number,
                    title = row.optString("title").orEmpty(),
                    posterUrl = row.optString("poster").nullIfBlank(),
                    episodeCount = row.optInt("count")
                )
            }
            return TitleDetail(
                isSeries = obj.optBoolean("series"),
                overview = obj.optString("overview"),
                genres = genres,
                startYear = obj.optInt("startYear").takeIf { it > 0 },
                endYear = obj.optInt("endYear").takeIf { it > 0 },
                runtimeMinutes = obj.optInt("runtime"),
                directors = obj.optJSONArray("directors").plainValues(),
                writers = obj.optJSONArray("writers").plainValues(),
                cast = cast,
                seasons = seasons,
                backdropUrl = obj.optString("backdrop").nullIfBlank(),
                maturity = obj.optString("maturity").nullIfBlank(),
                voteScore = obj.optDouble("vote").takeIf { !it.isNaN() && it > 0.0 },
                seasonsFetched = obj.optBoolean("seasonsFetched")
            )
        }
    }
}

private fun CastMember.toJson(): JSONObject = JSONObject()
    .put("name", name)
    .put("role", role)
    .put("photo", photoUrl ?: JSONObject.NULL)

private fun SeasonCard.toJson(): JSONObject = JSONObject()
    .put("number", number)
    .put("title", title)
    .put("poster", posterUrl ?: JSONObject.NULL)
    .put("count", episodeCount)

/**
 * The catalogue's detail endpoints, read into [TitleDetail].
 *
 * One request when a page opens and one per season the viewer actually expands, because a twenty
 * season show would otherwise pay twenty requests to render the first screen. Blocking by design,
 * like the rest of this package: callers are on an IO dispatcher.
 */
object TmdbDetails {

    private const val BASE = "https://api.themoviedb.org/3"

    /**
     * One title, or null when the request failed.
     *
     * Null is not "this title has no details" — the caller keeps the card's own name and poster and
     * simply does not store anything, so the next open asks again.
     */
    fun title(apiKey: String, providerId: Int, isSeries: Boolean): TitleDetail? {
        if (apiKey.isBlank() || providerId <= 0) return null
        val kind = if (isSeries) "tv" else "movie"
        val appended = if (isSeries) "credits,content_ratings,videos" else "credits,release_dates,videos"
        val response = HttpJson.get(
            HttpJson.url(
                "$BASE/$kind/$providerId",
                "api_key" to apiKey,
                "language" to "en-US",
                "append_to_response" to appended
            )
        ) ?: return null
        return if (isSeries) tv(response) else movie(response)
    }

    /** One season's episodes, which is the only place TMDB puts an episode's still and synopsis. */
    fun episodes(apiKey: String, showId: Int, season: Int): List<EpisodeDetail> {
        if (apiKey.isBlank() || showId <= 0) return emptyList()
        val response = HttpJson.get(
            HttpJson.url("$BASE/tv/$showId/season/$season", "api_key" to apiKey, "language" to "en-US")
        ) ?: return emptyList()
        return response.optJSONArray("episodes").objects().mapNotNull { row ->
            val number = row.optInt("episode_number", -1)
            if (number < 0) return@mapNotNull null
            EpisodeDetail(
                season = row.optInt("season_number", season),
                number = number,
                title = row.optString("name").orEmpty(),
                overview = row.optString("overview").orEmpty(),
                // TMDB only serves stills at w92/w185/w300; asking for anything wider 404s.
                stillUrl = tmdbImage(row.optString("still_path"), "w300"),
                runtimeMinutes = row.optInt("runtime")
            )
        }.sortedBy { it.number }
    }

    private fun movie(response: JSONObject): TitleDetail {
        val crew = response.optJSONObject("credits")?.optJSONArray("crew").objects()
        return TitleDetail(
            isSeries = false,
            overview = response.optString("overview").orEmpty(),
            genres = response.optJSONArray("genres").namedValues(),
            startYear = yearOf(response.optString("release_date")),
            endYear = null,
            runtimeMinutes = response.optInt("runtime"),
            directors = crew.credits("Directing"),
            writers = crew.credits("Writing"),
            cast = response.optJSONObject("credits")?.optJSONArray("cast").castMembers(),
            seasons = emptyList(),
            backdropUrl = tmdbImage(response.optString("backdrop_path"), "w1280"),
            maturity = response.optJSONObject("release_dates")?.optJSONArray("results").objects()
                .firstOrNull { it.optString("iso_3166_1") == "US" }
                ?.optJSONArray("release_dates").objects()
                .firstOrNull { it.optString("certification").isNotBlank() }
                ?.optString("certification")?.nullIfBlank(),
            voteScore = response.optDouble("vote_average").takeIf { !it.isNaN() && it > 0.0 },
            seasonsFetched = true
        )
    }

    private fun tv(response: JSONObject): TitleDetail {
        val seasons = response.optJSONArray("seasons").objects().mapNotNull { row ->
            val number = row.optInt("season_number", -1)
            if (number < 0) return@mapNotNull null
            SeasonCard(
                number = number,
                title = row.optString("name").orEmpty(),
                posterUrl = tmdbImage(row.optString("poster_path"), "w342"),
                episodeCount = row.optInt("episode_count")
            )
        }.sortedBy { it.number }
        return TitleDetail(
            isSeries = true,
            overview = response.optString("overview").orEmpty(),
            genres = response.optJSONArray("genres").namedValues(),
            startYear = yearOf(response.optString("first_air_date")),
            endYear = yearOf(response.optString("last_air_date")),
            runtimeMinutes = response.optJSONArray("episode_run_time")?.optInt(0) ?: 0,
            directors = response.optJSONArray("created_by").objects().mapNotNull {
                it.optString("name").nullIfBlank()
            },
            writers = response.optJSONObject("credits")?.optJSONArray("crew").objects().credits("Writing"),
            cast = response.optJSONObject("credits")?.optJSONArray("cast").castMembers(),
            seasons = seasons,
            backdropUrl = tmdbImage(response.optString("backdrop_path"), "w1280"),
            maturity = response.optJSONObject("content_ratings")?.optJSONArray("results").objects()
                .firstOrNull { it.optString("iso_3166_1") == "US" }
                ?.optString("rating")?.nullIfBlank(),
            voteScore = response.optDouble("vote_average").takeIf { !it.isNaN() && it > 0.0 },
            seasonsFetched = false
        )
    }

    private fun List<JSONObject>.credits(department: String): List<String> =
        filter { it.optString("department") == department }
            .mapNotNull { it.optString("name").nullIfBlank() }
            .distinct()
            .take(3)
}

/**
 * Which copy of a work the viewer should be offered.
 *
 * A *series* is different from a film: the point of opening one is to carry on where it was left, so
 * the part to play is the first episode with a resume position behind it and no position ahead of it.
 * Each part can be held by several copies, and the copy offered is always the best of them — someone
 * twelve minutes into the 720p rip deserves those twelve minutes in the 4K file. Pure, because the
 * answer only depends on three lists and it is the kind of rule that reads correct and indexes wrong.
 */
object PlayOrder {

    /** The file to hand the player, and the minute inside it the viewer stopped at. */
    data class Play(val partId: Long, val resumeMs: Long)

    /**
     * The copy to play, or null when the group holds nothing.
     *
     * [slots] is one entry per episode — or one for a whole film — in watch order, each holding that
     * part's files best copy first. An episode that was started but not finished wins; a finished one,
     * whose position is at or past the end, is passed over for the next part nobody has opened. The
     * position carried along is the deepest any copy of that part reached, because watching the
     * smaller file is still watching the episode.
     *
     * [lastPlayedMs] is when each file was last handed to the player. A part nobody has opened is
     * offered at its best copy; a part that has been is opened on the copy it was last watched on.
     *
     * [savedIds] names the copies this device is holding. A saved file outranks both of those rules —
     * see [pickCopy] — which is why the same set has to reach [progress] too, or the Continue Watching
     * row would resume a position on a copy the Play button then refuses to open.
     */
    fun nextCopy(
        slots: List<List<Long>>,
        resumeMs: Map<Long, Long>,
        lengthsMs: Map<Long, Long> = emptyMap(),
        lastPlayedMs: Map<Long, Long> = emptyMap(),
        savedIds: Set<Long> = emptySet()
    ): Play? {
        val opening = slots.firstOrNull { it.isNotEmpty() } ?: return null
        val openingCopy = pickCopy(opening, lastPlayedMs, savedIds) ?: return null
        for (slot in slots) {
            val chosen = pickCopy(slot, lastPlayedMs, savedIds) ?: continue
            val position = slot.maxOfOrNull { resumeMs[it] ?: 0L } ?: 0L
            val length = slot.maxOfOrNull { lengthsMs[it] ?: 0L } ?: 0L
            if (position <= 0L) return Play(chosen, 0L)
            // A file too little known to have a length is trusted instead: progress is erased the
            // moment a viewer reaches the tail, so a position on its own already means "not finished".
            if (length <= 0L || position < length - FINISHED_SLACK_MS) return Play(chosen, position)
        }
        // Everything watched through: start it again on the copy that was last watched.
        return Play(openingCopy, 0L)
    }

    /**
     * Which of a part's copies to open.
     *
     * Best first is the right answer only until the viewer has made a choice themselves. Moving to a
     * smaller upload is how you watch a film on a link that cannot carry the big one, and a Resume
     * that ignores that puts them back on the frame rate they just left — for a film whose picture
     * they have already been watching for an hour on the copy they picked.
     *
     * A copy this device is holding beats both of those, and not because it is better: it is the one
     * that opens with the link dead. The reason the last-watched rule exists — the big file would not
     * stream — is precisely the reason it stops applying once a file has been saved, because a saved
     * file streams itself. Where several copies of one part are saved, the best ranked of them wins,
     * since [slots] arrives in the order [CopyRank] gave it.
     */
    private fun pickCopy(slot: List<Long>, lastPlayedMs: Map<Long, Long>, savedIds: Set<Long>): Long? {
        slot.firstOrNull { it in savedIds }?.let { return it }
        val opened = slot.filter { (lastPlayedMs[it] ?: 0L) > 0L }
        return opened.maxByOrNull { lastPlayedMs.getValue(it) } ?: slot.firstOrNull()
    }

    /**
     * Another copy of the same part to hand the player after one refused to open, or null when the part
     * has run out of files.
     *
     * Only the slot the failed file belongs to can answer, because a second try means the same film in a
     * different container — stepping on to the next episode because this one would not open is a different
     * choice than the viewer made. [alreadyTried] is what stops a shelf of three bad uploads from
     * cycling through them forever: once a part has offered every file it holds, the player has to say
     * why rather than try again.
     */
    fun otherCopyOfPart(
        slots: List<List<Long>>,
        presentIds: List<Long>,
        failedId: Long,
        alreadyTried: Set<Long> = emptySet()
    ): Long? {
        val slot = slots.firstOrNull { failedId in it } ?: return null
        val present = presentIds.toSet()
        return slot.firstOrNull { it != failedId && it in present && it !in alreadyTried }
    }

    /**
     * Where each copy should open, keyed by copy id.
     *
     * A watched minute belongs to the part, not to the file that happened to earn it: twenty minutes
     * spent on the 1080p are twenty minutes into the episode, so every copy of it is shown and opened
     * at the deepest place any of them reached. The player looks for a position only in the file it is
     * handed, which is why the answer is spread over the copies rather than kept as one figure.
     */
    fun positionByCopy(slots: List<List<Long>>, resumeMs: Map<Long, Long>): Map<Long, Long> =
        slots.flatMap { slot ->
            val deepest = slot.maxOfOrNull { resumeMs[it] ?: 0L } ?: 0L
            slot.map { it to deepest }
        }.toMap()

    /** Where a viewer stopped inside a part, how long that part runs, and what is left of it. */
    data class Progress(val partId: Long, val resumeMs: Long, val lengthMs: Long, val remainingMs: Long)

    /**
     * The first part that was started and never finished, or null when nothing is half-watched.
     *
     * [nextCopy] answers "what should the Play button open", and it always answers — a shelf watched
     * end to end still has a first episode to reopen. A Continue Watching row needs the narrower
     * question, because a title only belongs there while it has minutes left in it. The copy named is
     * the one that part was last watched on rather than the biggest one, so the row resumes where the
     * viewer actually was.
     */
    fun progress(
        slots: List<List<Long>>,
        resumeMs: Map<Long, Long>,
        lengthsMs: Map<Long, Long>,
        lastPlayedMs: Map<Long, Long> = emptyMap(),
        savedIds: Set<Long> = emptySet()
    ): Progress? {
        for (slot in slots) {
            val partId = pickCopy(slot, lastPlayedMs, savedIds) ?: continue
            val position = slot.maxOfOrNull { resumeMs[it] ?: 0L } ?: 0L
            if (position <= 0L) continue
            val length = slot.maxOfOrNull { lengthsMs[it] ?: 0L } ?: 0L
            if (length > 0L && position >= length - FINISHED_SLACK_MS) continue
            // A part too little known to have a length still counts as unfinished — progress is erased
            // at the tail — but there is no honest way to say how far through it the viewer is.
            val remaining = if (length > position) length - position else 0L
            return Progress(partId, position, length, remaining)
        }
        return null
    }

    /** The run of parts ◀ ▶ walks, and where the file being watched sits inside it. */
    data class Stepping(val order: List<Long>, val index: Int)

    /**
     * The queue the way the skip buttons should read it.
     *
     * Pressing next means the next episode, not the other upload of the one already playing, so each
     * slot contributes one entry: the best copy of that part which is actually in [playlistIds]. A
     * file no slot claims keeps an entry of its own, so a queue built without the shelf's grouping
     * steps exactly as it always did. The index is valid even while the player holds a lesser copy,
     * because that copy stands at its part's place in the run rather than at its own.
     */
    fun stepping(slots: List<List<Long>>, playlistIds: List<Long>, currentId: Long): Stepping {
        if (slots.isEmpty()) return Stepping(playlistIds, playlistIds.indexOf(currentId))
        val present = playlistIds.toSet()
        val entryOf = HashMap<Long, Long>()
        slots.forEach { slot ->
            val entry = slot.firstOrNull { it in present } ?: return@forEach
            slot.forEach { id -> entryOf[id] = entry }
        }
        val order = playlistIds.filter { id -> entryOf[id] == id || id !in entryOf }
        val part = entryOf[currentId] ?: currentId
        return Stepping(order, order.indexOf(part))
    }

    /** Watching to the last second of a file is what "finished" looks like from a resume position. */
    private const val FINISHED_SLACK_MS = 90_000L
}

/**
 * Which season holds a given number in the whole run of a show.
 *
 * Long shows are uploaded by their position in the series rather than by arc — "One Piece E126" says
 * no season at all — and the catalogue files that number absolutely inside whichever season it sits
 * in, so episode 126 arrives as a row of season 4 carrying the number 126. Summing the season lengths
 * in order recovers the season, and specials are left out because they count from their own start.
 */
object AbsoluteSeasons {

    /** The season number, or null when the run does not reach that far or the lengths are unknown. */
    fun seasonFor(number: Int, seasons: List<SeasonCard>): Int? {
        if (number <= 0) return null
        var first = 1
        for (season in seasons.filter { it.number > 0 && it.episodeCount > 0 }.sortedBy { it.number }) {
            if (number < first + season.episodeCount) return season.number
            first += season.episodeCount
        }
        return null
    }
}

/**
 * Where one title's detail is filed in the cache: the catalogue's own id and its kind, because a film
 * and a show can carry the same number and are different works.
 */
fun titleDetailKey(providerId: Int, isSeries: Boolean): String =
    "$providerId/${if (isSeries) "tv" else "movie"}"

/** Two and a half hours of a title's own runtime, in the shape the meta row shows it. */
fun formatRuntime(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> "${minutes}m"
    else -> {
        val hours = minutes / 60
        val rest = minutes % 60
        if (rest == 0) "${hours}h" else String.format(Locale.US, "%dh %dm", hours, rest)
    }
}
