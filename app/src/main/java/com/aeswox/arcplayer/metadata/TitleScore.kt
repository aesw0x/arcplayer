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

    /**
     * Words that only ever count off the parts of a franchise, so a digit immediately after one is
     * doing the naming and the word is not. Deliberately short: "Season" and "Episode" are left out
     * because a file carrying those has already been read as one part of a show, not as a film.
     */
    private val partWords = setOf("chapter", "part", "vol", "volume")

    /** One row of a search response, reduced to what can be compared. */
    data class Candidate(val title: String, val year: Int?, val bonus: Int = 0)

    /**
     * Lower-case, with everything that is not a letter or a digit removed and an ampersand spent on
     * the word it stands for.
     *
     * This is what makes "Spider-Man", "Spider Man" and "spider man" the same name. The ampersand is
     * the exception because catalogues and uploaders disagree about it for the same film: IMDb calls
     * the 2026 biopic "Virginia Woolf's Night & Day" while the file says "Night and Day", and dropping
     * the symbol instead of reading it leaves those two names unrelated.
     *
     * A word that only numbers the part — "Chapter", "Part" — disappears when a digit follows it, for
     * the same reason of disagreement. TMDB names the 2023 film "John Wick: Chapter 4" and the scene
     * names the file "John.Wick.4", and measured against the live catalogue that gap alone is what
     * refused the film: the correct row was the first one back from all three of TMDB, IMDb and
     * Wikipedia, and every one of them scored 2 against a floor of 4. Names that really do differ,
     * like "John Wick" against "John Wick 2", still differ, because only the numbering word is spent
     * and the digit stays.
     */
    fun key(value: String): String {
        val words = ArrayList<String>(4)
        val word = StringBuilder()
        fun flush() {
            if (word.isNotEmpty()) {
                words += word.toString()
                word.setLength(0)
            }
        }
        for (character in value.lowercase()) {
            when {
                character.isLetterOrDigit() -> word.append(character)
                character == '&' -> {
                    flush()
                    word.append("and")
                    flush()
                }
                else -> flush()
            }
        }
        flush()
        val builder = StringBuilder(value.length)
        words.forEachIndexed { index, word ->
            val numbersAPart = word in partWords && words.getOrNull(index + 1)?.all { it.isDigit() } == true
            if (!numbersAPart) builder.append(word)
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
        return shared + nudge + candidate.bonus
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
