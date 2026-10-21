package com.aeswox.arcplayer.metadata

/**
 * What a catalogue says this file is, in the shape the grid needs.
 *
 * [posterUrl] is a remote address, never a local path: the artwork is fetched and cached by
 * [PosterCache], and a card can be shown before that finishes.
 */
data class MediaMatch(
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val source: String,
    /** The franchise-font rendering of [title] on a transparent background, where a catalogue has one. */
    val logoUrl: String? = null,
    /**
     * The catalogue's own id for this title, which is the only thing a detail request can be made
     * with. Search results carry it and cards used to throw it away, which meant a page that wanted
     * cast and seasons had to search again and could land on a different film than the card shows.
     */
    val providerId: Int? = null,
    /** Whether [providerId] names a show rather than a film, because the two endpoints differ. */
    val isSeries: Boolean = false
)

/**
 * One catalogue answer kept on the device for one cloud file.
 *
 * A card with [source] set to [NONE] means the lookup ran and nothing was good enough, which is
 * stored deliberately: without it the app would ask again on every launch and never show anything.
 */
data class MediaCard(
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val source: String,
    val logoUrl: String? = null,
    /**
     * Whether the wordmark step of the lookup actually ran.
     *
     * A card saved before wordmarks existed and a TMDB title that has none both end up with
     * [logoUrl] null, and the two need different treatment: one is worth asking about again, the
     * other is an answer. Without this the app would re-query every logo-less film on every launch.
     */
    val logoKnown: Boolean = false,
    /** Carried from [MediaMatch] so the info page can ask for one title's details directly. */
    val providerId: Int? = null,
    val isSeries: Boolean = false
) {
    companion object {
        const val NONE = "none"

        /** The parsed name on its own, for a file whose lookup has not landed or never will. */
        fun unresolved(guess: MediaGuess): MediaCard =
            MediaCard(guess.fallbackTitle, null, null, NONE)
    }
}

/**
 * What one catalogue says about one [MediaGuess].
 *
 * [NoMatch] and [Unreachable] are deliberately different answers. A catalogue that replied and drew a
 * blank is evidence, and worth remembering so the next launch does not ask again; one that answered
 * 429 or not at all is not evidence, and remembering *that* would freeze a whole chat as unnamed
 * because the phone happened to be on a slow connection.
 */
sealed interface MediaOutcome {

    data class Found(val match: MediaMatch) : MediaOutcome

    object NoMatch : MediaOutcome

    object Unreachable : MediaOutcome
}

/**
 * One catalogue that can answer a [MediaGuess].
 *
 * Blocking by design: callers are expected to be on an IO dispatcher, one file at a time, with room
 * between requests.
 */
interface MediaSource {
    val name: String
    fun lookup(guess: MediaGuess): MediaOutcome
}

/**
 * The order the catalogues are asked.
 *
 * TMDB first because it is the widest and the key is free, but it is not even in the list until a key
 * is compiled in. AniList carries the anime that fills this library without needing any registration.
 * IMDb is asked third because it indexes the regional films TMDB's name search misses, and spends the
 * IMDb id on TMDB when the two turn out to hold the same film under different spellings. Wikipedia is
 * last because it knows a film by its article rather than by a poster.
 */
object MediaSources {

    fun chain(apiKey: String): List<MediaSource> {
        val tmdb = if (apiKey.isBlank()) null else TmdbSource(apiKey)
        return listOfNotNull(tmdb, AniListSource(), ImdbSource(tmdb), WikipediaSource())
    }

    /**
     * The first catalogue that recognises this file, or why none of them did.
     *
     * Every source is consulted before giving up, because the one that knows an uploaded film is not
     * the one that answers fastest. A single unreachable source is enough to make the whole answer
     * [MediaOutcome.Unreachable]: refusing on partial evidence is how a bad minute becomes permanent.
     */
    fun lookup(guess: MediaGuess, apiKey: String): MediaOutcome {
        if (guess is MediaGuess.Unnamed) return MediaOutcome.NoMatch
        var unreachable = false
        for (source in chain(apiKey)) {
            val outcome = runCatching { source.lookup(guess) }.getOrDefault(MediaOutcome.Unreachable)
            when (outcome) {
                is MediaOutcome.Found -> return outcome
                MediaOutcome.NoMatch -> Unit
                MediaOutcome.Unreachable -> unreachable = true
            }
        }
        return if (unreachable) MediaOutcome.Unreachable else MediaOutcome.NoMatch
    }
}

/**
 * JSON reads a missing value as the string "null" as often as not.
 *
 * Every catalogue field goes through here so an absent poster never becomes a broken URL.
 */
internal fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() && it != "null" }
