package com.aeswox.arcplayer.metadata

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln

/**
 * One work an actor appears in, as the catalogue's combined credits list it.
 *
 * Popularity and vote count travel together because neither alone orders a filmography: popularity
 * spikes for a week-old trailer nobody has seen, and vote count alone buries a new release under a
 * decades-old cameo.
 */
data class PersonCredit(
    val id: Int,
    val isSeries: Boolean,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val character: String,
    val popularity: Double,
    val voteCount: Int
)

/** Everything a cast headshot opens onto. */
data class PersonDetail(
    val id: Int,
    val name: String,
    val biography: String,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    /** The tall rendition, because the page shows a headshot far larger than the rail's. */
    val photoUrl: String?,
    val credits: List<PersonCredit>
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("shape", PERSON_SHAPE)
        put("id", id)
        put("name", name)
        put("bio", biography)
        put("birthday", birthday ?: JSONObject.NULL)
        put("deathday", deathday ?: JSONObject.NULL)
        put("place", placeOfBirth ?: JSONObject.NULL)
        put("photo", photoUrl ?: JSONObject.NULL)
        put("credits", JSONArray().apply { credits.forEach { put(it.toJson()) } })
    }

    companion object {
        private const val PERSON_SHAPE = 1

        fun fromJson(obj: JSONObject): PersonDetail? {
            if (obj.optInt("shape") < PERSON_SHAPE) return null
            val id = obj.optInt("id")
            val name = obj.optString("name").nullIfBlank() ?: return null
            if (id <= 0) return null
            val credits = obj.optJSONArray("credits").objects().mapNotNull { row ->
                val creditId = row.optInt("id")
                val title = row.optString("title").nullIfBlank() ?: return@mapNotNull null
                if (creditId <= 0) return@mapNotNull null
                PersonCredit(
                    id = creditId,
                    isSeries = row.optBoolean("series"),
                    title = title,
                    year = row.optInt("year").takeIf { it > 0 },
                    posterUrl = row.optString("poster").nullIfBlank(),
                    character = row.optString("character").orEmpty(),
                    popularity = row.optDouble("popularity", 0.0),
                    voteCount = row.optInt("votes")
                )
            }
            return PersonDetail(
                id = id,
                name = name,
                biography = obj.optString("bio").orEmpty(),
                birthday = obj.optString("birthday").nullIfBlank(),
                deathday = obj.optString("deathday").nullIfBlank(),
                placeOfBirth = obj.optString("place").nullIfBlank(),
                photoUrl = obj.optString("photo").nullIfBlank(),
                credits = credits
            )
        }
    }
}

private fun PersonCredit.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("series", isSeries)
    .put("title", title)
    .put("year", year ?: JSONObject.NULL)
    .put("poster", posterUrl ?: JSONObject.NULL)
    .put("character", character)
    .put("popularity", popularity)
    .put("votes", voteCount)

/**
 * The order a filmography is shown in, and the rows it is shown without.
 *
 * A combined credits list is every appearance the catalogue has ever filed: talk-show slots as
 * "Self", unvoted festival shorts, and the same work twice under two character names. Ranking is
 * popularity weighted by how many people actually voted, so a well-known work outranks a briefly
 * trending one, and the noise is dropped before the grid ever sees it.
 */
object PersonCredits {

    /** Under this many votes a work's popularity is a handful of people, not an audience. */
    const val MIN_VOTES = 10

    private val appearanceOnly = setOf("self", "himself", "herself", "themselves")

    fun score(credit: PersonCredit): Double = credit.popularity * ln(1.0 + credit.voteCount)

    fun rank(credits: List<PersonCredit>): List<PersonCredit> =
        credits.asSequence()
            .filter { it.voteCount >= MIN_VOTES && it.posterUrl != null }
            .filter { it.character.trim().lowercase() !in appearanceOnly }
            .groupBy { it.id to it.isSeries }
            .mapNotNull { (_, rows) -> rows.maxByOrNull { score(it) } }
            .sortedByDescending { score(it) }
            .toList()

    /** The three works a name is recognised by, which is the line under it on the page. */
    fun knownFor(credits: List<PersonCredit>): List<String> =
        rank(credits).take(3).map { it.title }
}

/**
 * The catalogue's person endpoints.
 *
 * One request carries the biography and the whole filmography together, because a page that asked
 * for them separately would show a name with nothing under it twice. Blocking by design, like the
 * rest of this package: callers are on an IO dispatcher.
 */
object TmdbPeople {

    private const val BASE = "https://api.themoviedb.org/3"

    fun person(apiKey: String, personId: Int): PersonDetail? {
        if (apiKey.isBlank() || personId <= 0) return null
        val response = HttpJson.get(
            HttpJson.url(
                "$BASE/person/$personId",
                "api_key" to apiKey,
                "language" to "en-US",
                "append_to_response" to "combined_credits"
            )
        ) ?: return null
        val name = response.optString("name").nullIfBlank() ?: return null
        val credits = response.optJSONObject("combined_credits")?.optJSONArray("cast").objects()
            .mapNotNull { row ->
                val mediaType = row.optString("media_type")
                if (mediaType != "movie" && mediaType != "tv") return@mapNotNull null
                val id = row.optInt("id")
                if (id <= 0) return@mapNotNull null
                val isSeries = mediaType == "tv"
                val title = if (isSeries) {
                    row.optString("name").ifBlank { row.optString("original_name") }
                } else {
                    row.optString("title").ifBlank { row.optString("original_title") }
                }.nullIfBlank() ?: return@mapNotNull null
                PersonCredit(
                    id = id,
                    isSeries = isSeries,
                    title = title,
                    year = yearOf(if (isSeries) row.optString("first_air_date") else row.optString("release_date")),
                    posterUrl = row.optString("poster_path").nullIfBlank()?.let { "https://image.tmdb.org/t/p/w342$it" },
                    character = row.optString("character").orEmpty(),
                    popularity = row.optDouble("popularity", 0.0),
                    voteCount = row.optInt("vote_count")
                )
            }
        return PersonDetail(
            id = personId,
            name = name,
            biography = response.optString("biography").orEmpty(),
            birthday = response.optString("birthday").nullIfBlank(),
            deathday = response.optString("deathday").nullIfBlank(),
            placeOfBirth = response.optString("place_of_birth").nullIfBlank(),
            // Profiles are served at w45/w185/h632/original only; a width the rail uses is too small
            // for the page's headshot and a made-up size is a silent grey box.
            photoUrl = response.optString("profile_path").nullIfBlank()?.let { "https://image.tmdb.org/t/p/h632$it" },
            // Ranked and capped before storage: a voice actor's combined credits run to hundreds of
            // rows, and the page shows the head of that list, so the cache keeps the head too.
            credits = PersonCredits.rank(credits).take(120)
        )
    }

    /**
     * The id behind a name, for a cast row cached before person ids were stored.
     *
     * The first result is taken rather than scored: the name came from the catalogue's own cast list
     * for the title being read, so it is already the catalogue's spelling of that person.
     */
    fun idByName(apiKey: String, name: String): Int? {
        val trimmed = name.trim()
        if (apiKey.isBlank() || trimmed.isBlank()) return null
        val response = HttpJson.get(
            HttpJson.url(
                "$BASE/search/person",
                "api_key" to apiKey,
                "language" to "en-US",
                "query" to trimmed,
                "include_adult" to "false"
            )
        ) ?: return null
        return response.optJSONArray("results").objects()
            .firstOrNull { it.optString("name").equals(trimmed, ignoreCase = true) }
            ?.optInt("id")?.takeIf { it > 0 }
            ?: response.optJSONArray("results").objects().firstOrNull()?.optInt("id")?.takeIf { it > 0 }
    }
}