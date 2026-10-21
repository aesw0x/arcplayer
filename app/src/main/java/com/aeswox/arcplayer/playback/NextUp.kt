package com.aeswox.arcplayer.playback

import com.aeswox.arcplayer.metadata.MediaGuess
import com.aeswox.arcplayer.metadata.MediaNameParser

/**
 * What the player should offer when a file runs out.
 *
 * Two questions live here, and a viewer notices when either one is wrong. The first is whether the
 * player has genuinely reached the end: a cloud file with a hole in it reads to libVLC as the end of
 * the media, so an auto-advance built on the end report alone would jump to the next episode every
 * time Telegram is slow near a boundary. The second is what to call the thing coming next — the queue
 * is made of file names, and `Stranger.Things.S01E02.1080p.WEB-DL.x264.mkv` is not a caption.
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
     * really got to is the last tick the player showed. Those arrive about once a second; a gap wider
     * than this was a hole mistaken for an ending, and the stall watchdog is already working on it.
     */
    const val HONEST_END_TOLERANCE_MS = 5_000L

    data class Snapshot(
        /** The furthest moment actually shown, not the position the end report moved the bar to. */
        val lastShownMs: Long,
        val durationMs: Long,
        /** Whether bytes are still on their way: then the quiet is a fetch rather than an ending. */
        val waitingForBytes: Boolean,
        /** Whether this queue was opened from a shelf card, so which files are one work is known. */
        val knowsItsParts: Boolean,
        val hasNextPart: Boolean
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
     */
    fun withinEarlyWindow(fact: Snapshot): Boolean {
        if (!fact.hasNextPart || !fact.knowsItsParts) return false
        if (fact.durationMs < ResumeRules.MIN_TRACKABLE_DURATION_MS) return false
        return fact.lastShownMs >= fact.durationMs - EARLY_OFFER_MS && fact.lastShownMs < fact.durationMs
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
