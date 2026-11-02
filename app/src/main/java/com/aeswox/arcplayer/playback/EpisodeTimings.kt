package com.aeswox.arcplayer.playback

import kotlin.math.abs

/** Which service a set of marks arrived from, because what each one is worth believing differs. */
enum class TimingSource {
    /** TheIntroDB, keyed by the TMDB id and the season and episode the file name says. */
    IntroDb,

    /** AniSkip, keyed by a MyAnimeList id and an episode number that is not always the file's. */
    AniSkip
}

/**
 * What one service said about one part, in milliseconds and before anything here has judged it.
 *
 * This is what gets remembered: a claim about the work rather than a verdict about a file, because
 * whether a mark can be believed depends on the length of the copy actually on screen, and two copies
 * of one episode do not always have the same one. [TimingTrust.believe] is where the length is brought
 * in, and it runs at the moment the marks are used rather than at the moment they were fetched — which
 * is also the only moment a season-wide pass has any length to check against.
 *
 * Every mark is nullable because these databases are crowd-sourced one end at a time: TheIntroDB answers
 * `intro {null → 80000}` for an opening whose beginning nobody has pinned down and `credits {1390100 →
 * null}` for a roll that runs to the end of the file. A missing number is therefore an ordinary answer
 * rather than a failed parse, and treating it as one would throw away the marks that are right. An
 * answer whose marks are *all* null is the other ordinary answer: this part is filed and nobody has
 * marked it, which is worth remembering so the next watch does not ask again.
 *
 * The units are converted at whatever reported them — TheIntroDB in milliseconds, AniSkip in seconds —
 * so that nothing downstream has to know which service was talking.
 */
data class TimingAnswer(
    val source: TimingSource,
    val introEndMs: Long? = null,
    val outroStartMs: Long? = null,
    /** Absent when nobody has pinned where the credits stop, which is the common answer. */
    val outroEndMs: Long? = null,
    /** The length the service believed this part was, where it volunteered one. */
    val claimedLengthMs: Long? = null
)

/**
 * The marks this app is willing to put a control on, for one part of one length.
 *
 * A null means the source said nothing about that moment or said something [TimingTrust] cannot believe,
 * and the two are the same to the screen: what happens next is that the app behaves as though it had
 * never asked.
 */
data class EpisodeTimings(
    val introEndMs: Long? = null,
    val outroStartMs: Long? = null,
    val outroEndMs: Long? = null
)

/**
 * Which of what the timing services report is believed, and why the rest is not.
 *
 * Both databases are crowd-sourced, so an answer is a claim about somebody's copy rather than a fact
 * about the part the viewer is watching: a submission made against a TV edit, a recap, a different
 * numbering or a cut of different length puts the credits at a minute this file does not have. That is
 * worth more caution here than on a website, because the app lays a Next up card over the end of a part
 * on the strength of these numbers and a card that appears minutes early is one nobody asked for.
 *
 * What makes the rules above affordable is that refusing costs nothing. A part whose timings are all
 * disbelieved keeps [NextUp.EARLY_OFFER_MS], which is the behaviour that has been on the phone for a
 * month and is the floor these marks are an improvement on rather than a replacement for.
 */
object TimingTrust {

    /**
     * How short an "opening" may be before it stops being one.
     *
     * A mark inside the first few seconds belongs to a cold open that is part of the story, and offering
     * to skip it would be the app proposing to cut the scene the viewer is meant to see.
     */
    const val MIN_INTRO_MS = 5_000L

    /**
     * How much of a part an opening may run past before the mark stops being an opening, as a percent.
     *
     * Written as a percent of the length rather than a fraction so the rule is integer arithmetic: a
     * quarter of the film is the widest thing a title sequence has ever been, and anything past that is a
     * submission about a different cut or a different episode.
     */
    const val MAX_INTRO_PERCENT = 25L

    /**
     * How early in a part the credits may begin, as a percent of its length.
     *
     * The mirror of [MAX_INTRO_PERCENT], and the two together mean an opening and a credit roll can never
     * be believed at positions that overlap, which is why no separate order check is needed.
     */
    const val MIN_OUTRO_PERCENT = 50L

    /**
     * How much of the part has to be left after the credited start for a card to be worth putting there.
     *
     * A mark within this of the end is the end itself, and the offer the player already makes there beats
     * one made from a number nobody is certain of.
     */
    const val MIN_OUTRO_TAIL_MS = 5_000L

    /**
     * How long a credit roll this app will keep a card on screen for.
     *
     * Only ever a check on a pair, because a window is two submitted numbers and it is the pair that can
     * be tested: a roll this wide is evidence that the submission is about a different cut, and the end it
     * gives is no more trustworthy than the start being thrown out. Where no stop was ever filed there is
     * no window to measure, and refusing the start for the length of a roll nobody described was throwing
     * away the only number the card is made of — see [outroWindow].
     */
    const val MAX_OUTRO_WINDOW_MS = 180_000L

    /**
     * How far from the length being watched an AniSkip answer may claim a part is and still be about it.
     *
     * AniSkip records the length of the submission rather than of the show, which makes it the one check
     * available against a wrong episode number — the numbering of an anime on Telegram is routinely not
     * the numbering of the same series on MyAnimeList. Fifteen percent is room for a recap that carries
     * the opening, a different broadcast length and the rounding of a submission; anything wider is a
     * different episode, and skipping its opening would cut the scene the viewer came for.
     */
    const val MAX_ANISKIP_LENGTH_ERROR_PERCENT = 15L

    /**
     * The marks worth acting on in an answer, judged against the length of the part on screen.
     *
     * The two segments are judged apart, so one bad submission does not hide a good one; the AniSkip
     * length check is the exception, because a wrong episode number is wrong about both ends at once.
     */
    fun believe(answer: TimingAnswer, durationMs: Long): EpisodeTimings {
        if (durationMs < ResumeRules.MIN_TRACKABLE_DURATION_MS) return EpisodeTimings()
        if (answer.source == TimingSource.AniSkip && !isTheSamePart(answer.claimedLengthMs, durationMs)) {
            return EpisodeTimings()
        }
        val outro = outroWindow(answer, durationMs)
        return EpisodeTimings(
            introEndMs = introEnd(answer, durationMs),
            outroStartMs = outro?.first,
            outroEndMs = outro?.last
        )
    }

    private fun introEnd(answer: TimingAnswer, durationMs: Long): Long? {
        val end = answer.introEndMs ?: return null
        if (end < MIN_INTRO_MS) return null
        if (end * 100L > durationMs * MAX_INTRO_PERCENT) return null
        return end
    }

    /** Where the credits run from and to, or nothing at all about them. */
    private fun outroWindow(answer: TimingAnswer, durationMs: Long): LongRange? {
        val start = answer.outroStartMs ?: return null
        // A roll whose end nobody pinned is a roll that runs to the end of the file, and taking that on
        // faith is the smaller risk: the alternative is to say nothing about credits at all.
        val end = minOf(answer.outroEndMs ?: durationMs, durationMs)
        if (start * 100L < durationMs * MIN_OUTRO_PERCENT) return null
        if (start > durationMs - MIN_OUTRO_TAIL_MS) return null
        // Also catches a submission whose end sits before its start, which is two numbers that cannot
        // both be about this file.
        if (end <= start) return null
        // The width of a roll is only a piece of evidence when both of its ends were filed, and the
        // common answer is a start alone: One Piece S4E126 says `1364000 → null` and a show with a
        // four-minute ending theme says nothing more. Measuring that silence against the whole of the
        // part is what refused the mark, so the card kept waiting for the last twenty seconds of a part
        // whose credits the app had been told the position of. A start that stands alone is left to the
        // two position rules above, and the worst a wrong one can do is put a tap-only card on screen
        // over a stretch of story the viewer was never going to watch past.
        if (answer.outroEndMs != null && end - start > MAX_OUTRO_WINDOW_MS) return null
        return start..end
    }

    private fun isTheSamePart(claimedLengthMs: Long?, durationMs: Long): Boolean {
        // An answer that does not say what it thought the part was has nothing to be checked against,
        // and this source's whole claim to be trusted is that it does say. A missing length is therefore
        // refused rather than waved through, because the app already has an offer that needs no help.
        val claimed = claimedLengthMs ?: return false
        if (claimed <= 0L) return false
        return abs(claimed - durationMs) * 100L <= durationMs * MAX_ANISKIP_LENGTH_ERROR_PERCENT
    }
}

/**
 * Whether this part still has an opening ahead of the play head to jump over.
 *
 * The rule is one-directional because a tap means "take me forward": a mark the head has already reached
 * is not an offer to rewind to it, and a mark barely ahead is not worth a thumb, so the button stands
 * down a moment before it would start moving the story backwards.
 *
 * Nothing here jumps by itself. [TimingTrust] decides whether a mark is real; whether the viewer wants to
 * be on the other side of it is theirs, and a cold open that turns out to be part of the story is exactly
 * the case an automatic skip would ruin.
 */
object SkipIntro {

    /** How much of the opening has to remain for the jump to still be a jump forward. */
    const val LEAD_MS = 1_500L

    fun isOffered(timings: EpisodeTimings, positionMs: Long): Boolean {
        val end = timings.introEndMs ?: return false
        return positionMs <= end - LEAD_MS
    }
}

/**
 * Whether the play head has left the story behind and is inside the credits.
 *
 * This is the second at which a part has given the viewer everything it was made to give them, and the
 * app is willing to call the part finished from here without waiting for the file to run out: somebody
 * who walks away during a credit roll has watched the episode, and making them sit through the roll to
 * earn the tick is a rule that only ever punishes the honest.
 *
 * [TimingTrust] is what makes this affordable to believe, and a part whose marks it refuses answers null
 * and keeps the older rule — the player's own end report, accepted only by [NextUp.isHonestEnding]. So
 * the app never marks a part done on a guess it had to make up: the worst a wrong submission can do here
 * is call a part finished a minute early, and [TimingTrust.MIN_OUTRO_PERCENT] is what keeps "a minute
 * early" from becoming "halfway through".
 *
 * Only the start of the roll is read. A head inside the window or past its filed end is past the story
 * either way, and where no stop was ever filed the roll runs to the end of the file.
 */
object StoryIsOver {

    fun isOver(timings: EpisodeTimings, positionMs: Long): Boolean {
        val outroStartMs = timings.outroStartMs ?: return false
        return positionMs >= outroStartMs
    }
}
