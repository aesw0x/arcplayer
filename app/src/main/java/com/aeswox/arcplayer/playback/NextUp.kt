package com.aeswox.arcplayer.playback

import com.aeswox.arcplayer.metadata.MediaGuess
import com.aeswox.arcplayer.metadata.MediaNameParser

/**
 * What the player should offer when a file runs out.
 *
 * Two questions live here, and a viewer notices when either one is wrong. The first is whether the
 * player has genuinely reached the end: a cloud file with a hole in it reads to libVLC as the end of
 * the media, so an end report is only a signal to go and look, and [isHonestEnding] is what to look
 * for. The second is what to call the thing coming next — the queue is made of file names, and
 * `Stranger.Things.S01E02.1080p.WEB-DL.x264.mkv` is not a caption.
 *
 * Pure, like the rest of the rules in this app, because both answers depend on numbers and a name
 * rather than on a player that has to be running.
 */
object NextUp {

    /** How long the card sits before it hands the next part over on its own. */
    const val COUNTDOWN_SECONDS = 6

    /**
     * How far from its own end the skip is offered before any end report exists to offer it.
     *
     * The last seconds of a part are the ones a cloud file can lose: a tail the container will not
     * parse reads exactly like a tail that never arrived, and neither the pump nor a re-open can tell
     * those apart. Waiting for the end report there can mean waiting forever, so the skip is put in
     * reach while there is still something to skip from.
     */
    const val EARLY_OFFER_MS = 20_000L

    /**
     * How far back from its own length the last honest frame may sit and still count as an ending.
     *
     * The end report moves the bar to the full length, so the only number that says where the viewer
     * really got to is the last tick the player showed. This is how far behind it the bar may be sent
     * and still be believed — which is a different question from whether the arrival there was real,
     * and [isHonestEnding] is where that one is answered.
     */
    const val HONEST_END_TOLERANCE_MS = 5_000L

    /**
     * How long the play head has to have spent crossing that band before the end report means anything.
     *
     * Position cannot tell a finished part from a hole, because a hole puts the position at the end all
     * by itself: a cloud file is stretched on disk to the length the shelf promised, so the bytes that
     * never arrived read as the last frame of the media and the play head is at the end in the same
     * moment it was last seen. What separates the two is the other axis. Ticks arrive about once a
     * second, so a part watched out has sat inside this band for seconds of real time and one announced
     * by a hole has sat there for none.
     */
    const val HONEST_END_DWELL_MS = 1_500L

    /**
     * How many times the film's own clock the play head is ever legitimately seen moving.
     *
     * The app has no speed control and a decoder working slowly only moves its head slower than real
     * time, so one report per second advancing one second of the film is the ordinary answer, and a
     * seek is separately excused because the viewer is the one who moved the head. Anything past this
     * multiple is the file running out ahead of the play head rather than a film being watched.
     */
    const val MAX_END_TRAVEL_RATE: Long = 3L

    data class Snapshot(
        /** The furthest moment actually shown, not the position the end report moved the bar to. */
        val lastShownMs: Long,
        val durationMs: Long,
        /** Whether bytes are still on their way: then the quiet is a fetch rather than an ending. */
        val waitingForBytes: Boolean,
        /** Whether this queue was opened from a shelf card, so which files are one work is known. */
        val knowsItsParts: Boolean,
        val hasNextPart: Boolean,
        /**
         * Whether the position above is the part the screen is on.
         *
         * A handover changes the caption, the queue and the timings before it changes the player: the new
         * part has to be fetched and opened first, and until that happens the head still describes the
         * part that was just watched. Both rules read a position, and a stale one is always further along
         * than the part now named — so without this the arrival at an episode can put the card for the
         * *next* one on screen in the same moment, and can start the countdown that would carry the
         * viewer past the episode they asked for.
         */
        val headBelongsToThisPart: Boolean,
        /**
         * Whether the watchdog has proved that no more of this part is arriving.
         *
         * The offer normally reads a position, and this is the case where the position is the one number
         * the app has refused to believe: the head is parked at the far end of a hole rather than at the
         * end of the film, and the resume point is deliberately held back at the last frame that played.
         * Saying the part is missing is what qualifies the way out when the numbers cannot.
         */
        val theTailNeverArrived: Boolean = false,
        /**
         * Where this part's credits begin, as far as the app is willing to believe it.
         *
         * The one honest answer to when a Next up card belongs on screen, and it is null more often than
         * not: the databases are crowd-sourced, the app refuses a mark it cannot place on this file, and
         * most parts have never been asked about at all.
         */
        val outroStartMs: Long? = null
    )

    /**
     * Whether to put the next part up on screen.
     *
     * [Snapshot.knowsItsParts] is what keeps this to series and split films. Any folder queue has a
     * "next file" as well, and that is simply whatever else was in the folder — the neighbour of a
     * holiday clip is not its sequel, and starting it unasked would be the app choosing the viewer's
     * next thing to watch.
     */
    fun shouldOffer(fact: Snapshot): Boolean {
        if (!fact.headBelongsToThisPart) return false
        if (!fact.hasNextPart || !fact.knowsItsParts || fact.waitingForBytes) return false
        // A clip too short to be worth a resume point is too short to be worth queueing after.
        if (fact.durationMs < ResumeRules.MIN_TRACKABLE_DURATION_MS) return false
        return fact.lastShownMs >= fact.durationMs - HONEST_END_TOLERANCE_MS
    }

    /**
     * Whether the play head is inside the band before the end where the skip is offered early.
     *
     * Deliberately indifferent to [Snapshot.waitingForBytes]: a stall inside the band is one of the
     * two reasons the band exists, and disqualifying the offer on it would take the way out off the
     * screen at the exact moment it is needed.
     *
     * [Snapshot.theTailNeverArrived] opens the offer without reading a position at all, and it has to:
     * the honest answer to a hole is to keep the resume point at the last frame that played, so a part
     * that died nine tenths of the way in is one whose own numbers can never reach this band. Without
     * that route the screen would say the rest never arrived and leave the viewer with no way on.
     *
     * Where the band starts is the other half of the rule. [Snapshot.outroStartMs] puts it at the
     * credits, which is the moment the viewer stopped needing this part, and a fixed twenty seconds is
     * what the app reaches for when it does not know them. The two are taken as whichever comes first
     * rather than as a choice between them, because timings are an improvement on the window the app
     * already had and never a reason to arrive later than it used to.
     *
     * Opening the window wider is also what makes [Snapshot.headBelongsToThisPart] load-bearing: a mark
     * one minute before the end is reached by the last second of the episode before it.
     */
    fun withinEarlyWindow(fact: Snapshot): Boolean {
        if (!fact.headBelongsToThisPart) return false
        if (!fact.hasNextPart || !fact.knowsItsParts) return false
        if (fact.durationMs < ResumeRules.MIN_TRACKABLE_DURATION_MS) return false
        if (fact.theTailNeverArrived) return true
        val windowStartMs = minOf(
            fact.outroStartMs ?: fact.durationMs,
            fact.durationMs - EARLY_OFFER_MS
        )
        return fact.lastShownMs >= windowStartMs && fact.lastShownMs < fact.durationMs
    }

    /**
     * Whether a play-head report sits inside the band an honest ending has to be travelled through.
     *
     * A file sent to Telegram as a document carries no length, and the player reports the placeholder
     * 1 until its container opens. Every position is then past this band's edge, so without the floor a
     * film would be called finished the moment it opened.
     */
    fun isInsideEndBand(positionMs: Long, durationMs: Long): Boolean =
        durationMs > HONEST_END_TOLERANCE_MS && positionMs >= durationMs - HONEST_END_TOLERANCE_MS

    /** What the player's end report was standing on when it fired. */
    data class Ending(
        /** Whether a frame ever arrived: an ending from a file that never painted is a refusal, not a finish. */
        val painted: Boolean,
        /** How many play-head reports landed inside the band, counted from when the file was opened. */
        val nearEndTickCount: Int,
        /** Real time since the first of those reports, or 0 until the band has been entered at all. */
        val dwellMs: Long,
        /** How far through the film those reports actually carried the play head. */
        val travelledMs: Long,
        /** Whether every byte is on this device, where there is no hole left to mistake for an ending. */
        val isWholeFileOnDisk: Boolean
    )

    /**
     * Whether the player ran out of file rather than out of file arriving.
     *
     * [Snapshot] is deliberately not enough here, and the difference is what a viewer feels: that one
     * compares two positions, and a hole puts both of them at the end. [Ending.isWholeFileOnDisk] is
     * the exemption — a film the viewer kept has all of its bytes on the disk it was saved on, so its
     * ending took the seconds it always took and demanding the proof again would only risk missing it.
     */
    fun isHonestEnding(fact: Ending): Boolean {
        if (!fact.painted) return false
        if (fact.isWholeFileOnDisk) return true
        if (fact.nearEndTickCount < 2) return false
        if (fact.dwellMs < HONEST_END_DWELL_MS) return false
        // Two axes, because the hole can fake either one alone: it can sit inside the band for a second
        // and a half while the head crawls a stretch of empty space, and a head that races to the end
        // can cover the band in a single report.
        return fact.travelledMs <= fact.dwellMs * MAX_END_TRAVEL_RATE
    }

    /**
     * The caption for the part coming next.
     *
     * An episode is named by its number, which is the only part of a series file name a viewer reads
     * as information; anything else falls back to whatever title can be recovered from the name.
     */
    fun labelFor(fileName: String): String =
        when (val guess = MediaNameParser.parse(fileName)) {
            is MediaGuess.Episode -> buildString {
                guess.season?.let { append("S").append(it).append(' ') }
                append("E").append(guess.episode)
                guess.episodeTitle?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            }
            else -> guess.fallbackTitle
        }
}
