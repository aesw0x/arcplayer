package com.aeswox.arcplayer.metadata

import com.aeswox.arcplayer.playback.TimingAnswer
import com.aeswox.arcplayer.playback.TimingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Which part of one title a set of marks belongs to.
 *
 * Named by the catalogue's id rather than by a cloud file id, the same reason [TitleDetail] is: a shelf
 * can hold one episode three times over, and all three copies watch the same opening. A film carries no
 * season and no episode, and its row is a different question to a service than a series part is, so both
 * halves of the pair being absent is meaningful rather than a missing value.
 */
data class TimingRequest(val providerId: Int, val season: Int?, val episode: Int?) {

    /** The row this part is filed under, and the only place the two shapes of part are told apart. */
    val key: String get() = "$providerId|${season ?: "-"}|${episode ?: "-"}"
}

/**
 * The part to ask about: a season, and the number that season carries in the catalogue.
 *
 * A name that writes its season needs nothing from outside itself. A name numbered by its place in the
 * whole run needs the season that run number falls in, and keeps its own number, because a long show is
 * filed by the catalogue the same absolute way it is uploaded — One Piece 126 is season four's thirty-fifth
 * episode to a person counting arcs and episode 126 to every service that has marked it, and the second is
 * the one that answers. With no season lengths to work from the part is left unasked rather than asked with
 * a null season, which the service refuses outright and the pass would read as a part nobody answered.
 */
internal fun timingRequest(
    providerId: Int,
    season: Int?,
    episode: Int,
    seasons: List<SeasonCard> = emptyList()
): TimingRequest? {
    val placed = season ?: AbsoluteSeasons.seasonFor(episode, seasons) ?: return null
    return TimingRequest(providerId, placed, episode)
}

/**
 * The part to ask about for one file name, or null when the name does not say what part this is.
 *
 * A film is asked with neither half of the pair, which is the shape a movie is filed under; a series part
 * needs both.
 */
internal fun timingRequestFor(
    providerId: Int?,
    guess: MediaGuess,
    seasons: List<SeasonCard> = emptyList()
): TimingRequest? = when (guess) {
    is MediaGuess.Movie -> providerId?.let { TimingRequest(it, null, null) }
    is MediaGuess.Episode -> providerId?.let { timingRequest(it, guess.season, guess.episode, seasons) }
    is MediaGuess.Unnamed -> null
}

/**
 * What one part's marks look like on the device.
 *
 * Carries a shape stamp for the same reason a title detail does: these rows outlive the build that wrote
 * them, and a row the next build cannot read is dropped rather than patched, so the part is simply asked
 * about once more.
 */
private const val ANSWER_SHAPE = 1

internal fun TimingAnswer.toJson(): JSONObject = JSONObject().apply {
    put("shape", ANSWER_SHAPE)
    put("source", wireName)
    put("introEnd", introEndMs ?: JSONObject.NULL)
    put("outroStart", outroStartMs ?: JSONObject.NULL)
    put("outroEnd", outroEndMs ?: JSONObject.NULL)
    put("claimed", claimedLengthMs ?: JSONObject.NULL)
}

internal fun JSONObject.toTimingAnswer(): TimingAnswer? {
    if (optInt("shape") < ANSWER_SHAPE) return null
    val source = when (optString("source")) {
        "introdb" -> TimingSource.IntroDb
        "aniskip" -> TimingSource.AniSkip
        // A row written by a build that knew a service this one has never heard of is not this build's
        // to interpret, and guessing which of the two it meant would be acting on marks nobody claimed.
        else -> return null
    }
    return TimingAnswer(
        source = source,
        introEndMs = optLongOrNull("introEnd"),
        outroStartMs = optLongOrNull("outroStart"),
        outroEndMs = optLongOrNull("outroEnd"),
        claimedLengthMs = optLongOrNull("claimed")
    )
}

private val TimingAnswer.wireName: String get() = when (source) {
    TimingSource.IntroDb -> "introdb"
    TimingSource.AniSkip -> "aniskip"
}

/**
 * Asks the timing services where each part's opening and credits are, in the background and once per part.
 *
 * Process level the way [MediaLibrary] is, because a question that died when the viewer rotated the phone
 * would be asked again from the top, and sequential for the same reason the catalogue pass is: the radio
 * is already busy delivering the film being watched, and marks that arrive a second late cost nothing
 * while a stall in the picture costs the scene the viewer is in.
 *
 * Demand-driven, and deliberately narrow. Neither service has a batch route, so a shelf-wide warm-up is
 * one request per part of every title the viewer has ever stored — which is how a free API learns a
 * stranger's viewing habits at the speed of its rate limit. What gets asked instead is the part on screen
 * and the one the next-up card is about to name, plus a season when its page is opened.
 *
 * Never call this from a composable body: it is a queue of blocking requests, and the answer arrives on
 * [Dispatchers.IO]. The store's map is snapshot state, so writing it from here is what puts a mark on
 * screen while the film is already playing.
 */
object SkipTimingLibrary {

    /** How many parts one pass asks about. The rest wait until they are watched. */
    const val PASS_LIMIT = 40

    /** Keyless, crowd-sourced and free is still not a licence to ask twice in the same tenth of a second. */
    private const val SPACING_MS = 900L

    /** Waited after a part nobody answered for. */
    private const val COOLDOWN_MS = 6_000L

    /** Three unanswered parts in a row means the network is gone, not that the shelf is unmarked. */
    private const val GIVE_UP_AFTER = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Parts a request is currently out for, keyed by [TimingRequest.key] rather than by file.
     *
     * Two copies of one episode opened one after the other are the same question, and a season page
     * recomposed while its pass is still running must not start a second pass behind the first.
     */
    private val inFlight = ConcurrentHashMap<String, Unit>()

    /**
     * Ask about these parts and hand each answer back as it arrives.
     *
     * A part nobody has marked comes back as an answer with nothing in it, which the caller is expected to
     * store: that is the difference between "asked" and "nobody knows", and re-asking it on the next watch
     * is the one cost this feature can stand to pay only once. A part whose request did not reach an answer
     * is left askable, because treating a bad minute of connectivity as a refusal is how a whole show ends
     * up with no skip buttons.
     */
    fun ask(requests: List<TimingRequest>, onAnswered: (TimingRequest, TimingAnswer) -> Unit) {
        val queue = requests.filter { inFlight.putIfAbsent(it.key, Unit) == null }
        if (queue.isEmpty()) return
        scope.launch {
            val settled = HashSet<String>()
            var unanswered = 0
            for ((index, request) in queue.take(PASS_LIMIT).withIndex()) {
                if (index > 0) delay(if (unanswered == 0) SPACING_MS else COOLDOWN_MS)
                val answer = runCatching {
                    IntroDbSource.answer(request.providerId, request.season, request.episode)
                }.getOrNull()
                if (answer == null) {
                    if (++unanswered >= GIVE_UP_AFTER) break
                    continue
                }
                unanswered = 0
                settled += request.key
                onAnswered(request, answer)
            }
            queue.forEach { if (it.key !in settled) inFlight.remove(it.key) }
        }
    }
}
