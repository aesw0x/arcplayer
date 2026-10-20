package com.aeswox.arcplayer.metadata

/**
 * Whether one shelf card answers a typed query.
 *
 * The names on a shelf were typed by whoever uploaded the file, so "stranger things" has to be made to
 * agree with "Stranger.Things.S01.E03.The.Body.2160p.10bit.BluRay". Both sides are lower-cased and read
 * for letters and digits only, and the test runs twice: once with the punctuation left in as spaces, so
 * whole words line up with whole words, and once with it removed, so an episode typed the way people
 * type it — "s01e03", no spaces — still finds the file written "S01.E03".
 *
 * The squashed side also drops the padding zero of a number, because a release is named "S01E03" and a
 * person types "s1e3", and only one of those two is the way anyone remembers an episode. A zero inside a
 * number stays put, so "2016" and "10bit" are still themselves.
 *
 * Every word of the query has to land somewhere. A card that matches half of "batman returns 1989" is a
 * different film, and showing it as a near miss costs more than hiding it.
 *
 * Nothing here asks Telegram or the network: it reads the list the shelf is already drawn from, which
 * is what makes it instant.
 */
object ShelfSearch {

    private val BOUNDARY = Regex("[^\\p{L}\\p{N}]+")

    fun matches(query: String, haystack: String): Boolean {
        val wanted = words(query)
        if (wanted.isEmpty()) return true
        val held = words(haystack)
        val squashed = squash(haystack)
        return wanted.all { word ->
            held.any { it.contains(word) } || squashed.contains(squash(word))
        }
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(BOUNDARY).filter { it.isNotEmpty() }

    /** Letters and digits with nothing between them, and a number's leading zero gone. */
    private fun squash(text: String): String = buildString(text.length) {
        var previous: Char? = null
        for (char in text.lowercase()) {
            if (!char.isLetterOrDigit()) continue
            val padsAWholeNumber = char == '0' && (previous == null || !previous.isDigit())
            if (!padsAWholeNumber) append(char)
            previous = char
        }
    }
}
