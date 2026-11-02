package com.aeswox.arcplayer.cloud

/**
 * The line the keeps are served in, and what each one is called while it stands in it.
 *
 * One link, and one film at a time on it: the repository holds a single permit, so the asks a viewer
 * makes queue up behind each other. What the queue could not say until now is which of them is the one
 * arriving — every pending row looked identical, and a season pressed all at once read as six films
 * landing together when it was one film and five promises. The line is the difference between those two
 * sentences, and it is the shelf's order rather than Telegram's because the viewer chose the order.
 *
 * It is kept as a list of stable ids rather than derived from the ledger, because the ledger says what
 * was asked and not what happens next: a row that is pending is pending whether it is first or ninth,
 * and the only thing that separates them is standing at the front.
 */
object KeepLine {

    /** What one ask is, from the line's point of view. */
    enum class Place {
        /** The front of the line — the film whose bytes are coming. */
        RUNNING,

        /** Still waiting, behind [RUNNING] and whatever else was asked first. */
        QUEUED,

        /** Not in the line: never asked, already landed, or taken back. */
        NONE,
    }

    /** Take the back of the line. An ask already standing is not served twice. */
    fun ask(line: List<Long>, stableId: Long): List<Long> =
        if (stableId in line) line else line + stableId

    /**
     * Step out of the line, whether the film landed, was given up on, or was taken back.
     *
     * The head is whoever is left, which is why leaving is one operation and not two: a row that finishes
     * and a row that is cancelled both hand the link to the same next name.
     */
    fun release(line: List<Long>, stableId: Long): List<Long> =
        if (stableId in line) line - stableId else line

    /** Whose turn the link is, or null when nobody is standing. */
    fun head(line: List<Long>): Long? = line.firstOrNull()

    fun place(line: List<Long>, stableId: Long): Place = when {
        line.firstOrNull() == stableId -> Place.RUNNING
        stableId in line -> Place.QUEUED
        else -> Place.NONE
    }

    /** How many asks stand in front of this one, which is the wait a row can say out loud. */
    fun ahead(line: List<Long>, stableId: Long): Int {
        val at = line.indexOf(stableId)
        return if (at <= 0) 0 else at
    }
}
