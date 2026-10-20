package com.aeswox.arcplayer.metadata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** One file offered to [MediaLibrary]: the id to report back against, and the name to read. */
data class LookupRequest(val videoId: Long, val fileName: String)

/**
 * Asks the catalogues what each cloud file actually is, once per file, in the background.
 *
 * Lives at process level the way the Telegram session does, because a lookup that died when the
 * viewer switched tabs would leave a chat permanently half-named. Sequential rather than parallel on
 * purpose: these are free third-party APIs, and the same radio is busy downloading the film being
 * watched.
 */
object MediaLibrary {

    /** How many files one pass asks about. The rest wait for the next time the chat is opened. */
    const val PASS_LIMIT = 60

    /** AniList and Wikipedia both start answering 429 well below one file a second. */
    private const val SPACING_MS = 1_200L

    /** Waited after a file nobody answered for. */
    private const val COOLDOWN_MS = 8_000L

    /** Three unanswered files in a row means the network or the quota is gone, not the catalogue. */
    private const val GIVE_UP_AFTER = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Files a request is currently out for.
     *
     * Kept here rather than in the composable so the same chat opened twice does not pay twice, and
     * so a pass that is still running cannot be started again by a recomposition.
     */
    private val inFlight = ConcurrentHashMap<Long, Unit>()

    /**
     * Queue the files nobody has asked about yet.
     *
     * [requests] is expected to hold only the cards the store does not already have — a refusal is a
     * stored answer too, and asking again on every launch would only be slower.
     */
    fun search(requests: List<LookupRequest>, apiKey: String, onFound: (Long, MediaCard) -> Unit) {
        val queue = requests.filter { inFlight.putIfAbsent(it.videoId, Unit) == null }
        if (queue.isEmpty()) return
        scope.launch {
            val answered = HashSet<Long>()
            var unanswered = 0
            for ((index, request) in queue.take(PASS_LIMIT).withIndex()) {
                if (index > 0) delay(if (unanswered == 0) SPACING_MS else COOLDOWN_MS)
                val card = runCatching { cardFor(request.fileName, apiKey) }.getOrNull()
                if (card == null) {
                    if (++unanswered >= GIVE_UP_AFTER) break
                    continue
                }
                unanswered = 0
                answered += request.videoId
                onFound(request.videoId, card)
            }
            // Anything this pass did not settle stays askable. A dropped marker is not a refusal, and
            // treating it as one is how a minute of bad connectivity names a whole chat wrong forever.
            queue.forEach { if (it.videoId !in answered) inFlight.remove(it.videoId) }
        }
    }

    /**
     * The whole answer for one file name: parse, ask, refuse what is not close enough.
     *
     * Blocking, and never called from a composable. Null means nobody got back to us, which is not the
     * same as nobody knowing — see [MediaOutcome.Unreachable].
     */
    fun cardFor(fileName: String, apiKey: String): MediaCard? {
        val guess = MediaNameParser.parse(fileName)
        if (guess is MediaGuess.Unnamed) return MediaCard.unresolved(guess)
        return when (val outcome = MediaSources.lookup(guess, apiKey)) {
            // TMDB is the only catalogue asked for a wordmark, so a card from it has been through
            // that step whether or not anything came back; a card from anywhere else has not been
            // asked at all and must not be re-asked on the strength of a missing logo.
            is MediaOutcome.Found -> with(outcome.match) {
                MediaCard(
                    title, subtitle, posterUrl, source, logoUrl,
                    logoKnown = source == "tmdb", providerId = providerId, isSeries = isSeries
                )
            }
            MediaOutcome.NoMatch -> MediaCard.unresolved(guess)
            MediaOutcome.Unreachable -> null
        }
    }

    fun isSearching(videoId: Long): Boolean = inFlight.containsKey(videoId)
}
