package com.aeswox.arcplayer.metadata

/**
 * Decides which of a catalogue's search results is actually this file.
 *
 * A search returns many near-misses, and the wrong one is worse than none: the viewer gets a poster
 * for a different film and no way to tell. So agreement on the name does most of the work, the year
 * only ever nudges, and everything below the floor is refused.
 */
object TitleScore {

    const val EXACT = 8

    /**
     * One name starting with the other, which is what "Demon Slayer" against
     * "Demon Slayer: Kimetsu no Yaiba" looks like — and "Interstellar" against the 2015 anime
     * "Interstellar Wanderer", which is why a prefix on its own is worth less than the floor.
     */
    const val PREFIX = 3
    const val CONTAINS = 1

    /** A result has to share the name, or share a fragment of it and agree about the year. */
    const val ACCEPT = 4

    const val SAME_YEAR = 2
    const val NEAR_YEAR = 1
    const val WRONG_YEAR = -2

    /** One row of a search response, reduced to what can be compared. */
    data class Candidate(val title: String, val year: Int?)

    /**
     * Lower-case, with everything that is not a letter or a digit removed.
     *
     * This is what makes "Spider-Man", "Spider Man" and "spider man" the same name, and leaves
     * punctuation to the catalogues, which is where it belongs.
     */
    fun key(value: String): String {
        val builder = StringBuilder(value.length)
        for (character in value.lowercase()) {
            if (character.isLetterOrDigit()) builder.append(character)
        }
        return builder.toString()
    }

    fun of(candidate: Candidate, wantedTitle: String, wantedYear: Int?): Int {
        val found = key(candidate.title)
        val wanted = key(wantedTitle)
        if (found.isEmpty() || wanted.isEmpty()) return 0

        val shared = when {
            found == wanted -> EXACT
            found.startsWith(wanted) || wanted.startsWith(found) -> PREFIX
            found.contains(wanted) || wanted.contains(found) -> CONTAINS
            else -> 0
        }
        val year = candidate.year
        val nudge = when {
            year == null || wantedYear == null -> 0
            year == wantedYear -> SAME_YEAR
            year in (wantedYear - 1)..(wantedYear + 1) -> NEAR_YEAR
            else -> WRONG_YEAR
        }
        return shared + nudge
    }

    /**
     * The strongest row, or null when none of them is about this file.
     *
     * Ties keep the earlier row on purpose: a catalogue ranks by popularity for a reason, and a
     * tie-break invented here would only be a second opinion with less information.
     */
    fun bestIndex(candidates: List<Candidate>, wantedTitle: String, wantedYear: Int?): Int {
        var chosen = -1
        var score = 0
        candidates.forEachIndexed { index, candidate ->
            val current = of(candidate, wantedTitle, wantedYear)
            if (current > score) {
                score = current
                chosen = index
            }
        }
        return if (score >= ACCEPT) chosen else -1
    }
}
