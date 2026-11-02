package com.aeswox.arcplayer.metadata

import com.aeswox.arcplayer.playback.TimingAnswer
import com.aeswox.arcplayer.playback.TimingSource
import org.json.JSONObject

/**
 * A mark the source did not pin is a null here, never a zero: `optLong` answers 0 for both an absent key
 * and a JSON null, and zero is a minute this app would happily place a control on.
 */
internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key, -1L).takeIf { it >= 0L }

/**
 * TheIntroDB.
 *
 * Asked by the TMDB id the shelf already stores, which is why it is the first of the two timing services
 * and the only one in this build: the app has no MyAnimeList id for anything, so AniSkip could not be
 * asked at all without a lookup of its own first. Reads need no key, and a part nobody has marked is a
 * 404 rather than a bill.
 *
 * Its shape is what makes the nullable marks in [TimingAnswer] normal rather than defensive. Breaking
 * Bad S1E1 answers `intro [{228583 → 246569}]` and `credits [{3431000 → null}]` — a roll whose start
 * somebody watched and whose stop nobody pinned — and Inception answers `intro [{null → 38000}]` with no
 * credits at all. So one end of a segment is routinely missing, and the only reading that survives is to
 * treat every number as its own question.
 *
 * What comes back is the claim rather than a verdict. Whether a mark can be believed depends on the
 * length of the copy on screen, which a season-wide pass has no way of knowing and two copies of one
 * episode do not always agree about, so [com.aeswox.arcplayer.playback.TimingTrust] runs where the mark
 * is used instead.
 */
internal object IntroDbSource {

    private val base = "https://api.theintrodb.org/v4"

    /**
     * What this service has filed about one part, or null when the question did not reach an answer.
     *
     * Null covers the network and every error body; a part that is filed but unmarked comes back as an
     * answer with nothing in it, which the store remembers so the next watch does not ask again.
     *
     * A film and an episode are the same endpoint asked with different numbers, and half a pair is not a
     * smaller question but one the service refuses: `season=1` with no episode answers "season and
     * episode must be provided together". An absolute episode number with no season belongs to the caller
     * to resolve before asking, because answering it here would be this layer guessing.
     */
    fun answer(tmdbId: Int, season: Int?, episode: Int?): TimingAnswer? {
        val id = "tmdb_id" to tmdbId.toString()
        val params = when {
            season != null && episode != null -> arrayOf(
                id, "season" to season.toString(), "episode" to episode.toString()
            )
            // A film: the same endpoint with no numbers, which is how its one opening is filed.
            season == null && episode == null -> arrayOf(id)
            else -> return null
        }
        val response = HttpJson.get(HttpJson.url("$base/media", *params)) ?: return null
        // Neither array is the shape of every error this endpoint gives, from a show it has never heard
        // of to a captive portal answering instead, and none of them is an answer about this part.
        if (!response.has("intro") && !response.has("credits")) return null
        val roll = earliestCreditStart(response.optJSONArray("credits").objects())
        return TimingAnswer(
            source = TimingSource.IntroDb,
            introEndMs = filedIntroEnd(response.optJSONArray("intro").objects()),
            outroStartMs = roll?.first,
            outroEndMs = roll?.second?.optLongOrNull("end_ms")
        )
    }

    /**
     * The stop of the opening, out of however many were filed.
     *
     * More than one row means more than one person has marked this part rather than an opening that ends
     * twice, and the viewer wants to be past all of them: the latest claim is the one that can be relied
     * on to have left the story behind. A mark that turns out to belong to a different cut is refused by
     * the whole answer, which costs only the pill.
     */
    private fun filedIntroEnd(intros: List<JSONObject>): Long? =
        intros.mapNotNull { it.optLongOrNull("end_ms") }.maxOrNull()

    /**
     * The credits to act on, out of however many were filed, with its own stop still attached.
     *
     * The earliest start is what a viewer wants: the card is an offer, and one made the moment the story
     * ends is worth more than one made a minute later. A roll filed without saying where it began says
     * nothing this app can put a card on.
     *
     * A row beginning at the very first millisecond is filed for where the credits end and not for where
     * they start — One Piece S4E126 carries `0 → 210794` beside the real `1364000 → null` — and reading it
     * as the earliest claim would both hide the mark that means something and be refused outright by the
     * trust rules, leaving the part with no outro at all.
     */
    private fun earliestCreditStart(credits: List<JSONObject>): Pair<Long, JSONObject>? =
        credits.mapNotNull { row -> row.optLongOrNull("start_ms")?.takeIf { it > 0L }?.let { it to row } }
            .minByOrNull { it.first }
}
