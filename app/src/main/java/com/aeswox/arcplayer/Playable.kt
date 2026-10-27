package com.aeswox.arcplayer

/**
 * One file the player can be handed, whether it came off Telegram or already sits on this phone.
 *
 * Everything above the player — grouping names into works, ranking one copy against another, filing the
 * catalogue's answer — asks only about a file's name, its size, its length and where it came from, and
 * a device file can answer all of those. Until now the shelf could not ask: every type under the card
 * was the Telegram one, so a film the viewer also owns in `Movies/` had nowhere to be filed beside its
 * own copy on the channel and was dropped where the card was built.
 *
 * Anything that has to reach the file itself casts back to the concrete type. That is deliberate, and
 * it is a safety line as much as a typing one: the way a Telegram copy is taken off a shelf and the way
 * a person's own file is deleted have nothing in common, and only the first of those is this app's to
 * do.
 */
interface Playable {

    /**
     * What every card, resume position and watched minute in the app is filed under.
     *
     * MediaStore's own number for a device file, which is always positive, and the derived key from
     * [com.aeswox.arcplayer.cloud.CloudIds] for a Telegram one, which is always negative — so the two
     * can share the same maps and no lookup can mistake one for the other.
     */
    val stableId: Long

    /** The name the file carries, which is the question asked of the catalogues. */
    val title: String

    val sizeBytes: Long

    /** How long it runs, or 0 until something has read it. */
    val durationMs: Long

    /** The picture's height as the container itself reported it, which fills a gap in a name. */
    val frameHeight: Int

    val sizeFormatted: String

    /** When this file joined the library, in epoch seconds, which is how one shelf is ordered. */
    val arrivedAtSec: Long

    /**
     * Whether this copy is already on the phone.
     *
     * The reason Play reaches for it: nothing has to be fetched, so nothing can stall, and the film is
     * there even on a flight. It is a tie-breaker and never a claim about the picture.
     */
    val isOnPhone: Boolean

    /**
     * The same file with a length learned after it was listed, which never shortens it.
     *
     * A film forwarded into a chat as a document carries no duration in Telegram's schema — which is how
     * nearly every release arrives — so the only place the shelf can get one is the player having read it
     * out of the container. A device file already has MediaStore's figure, and a smaller number reaching
     * here means a stale entry under a reused id rather than a shorter film.
     */
    fun withLength(learnedMs: Long): Playable
}
