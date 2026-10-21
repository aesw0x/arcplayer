package com.aeswox.arcplayer.metadata

import java.util.Calendar

/**
 * What a file name claims the thing inside it is, before any provider gets a chance to correct it.
 *
 * Names are typed by whoever uploaded the file, so they carry that person's habits rather than the
 * work's real title. This guess exists to turn one into something a catalogue can be searched with,
 * never to be believed.
 */
sealed interface MediaGuess {

    /** Nothing in the name looked like a numbered part of a series, so treat the file as one work. */
    data class Movie(val title: String, val year: Int?) : MediaGuess

    /**
     * A numbered part of a series.
     *
     * [season] is null for the long-running shows that number their episodes straight from one,
     * which is most of what gets forwarded around a chat archive.
     */
    data class Episode(
        val series: String,
        val season: Int?,
        val episode: Int,
        val episodeTitle: String?
    ) : MediaGuess

    /** The name gave up nothing usable; keep it on the card rather than show a blank one. */
    data class Unnamed(val fallback: String) : MediaGuess

    /** What to show while no provider has answered. */
    val fallbackTitle: String
        get() = when (this) {
            is Movie -> if (year == null) title else "$title ($year)"
            is Episode -> {
                val head = "$series · Episode $episode"
                if (episodeTitle.isNullOrBlank()) head else "$head · $episodeTitle"
            }
            is Unnamed -> fallback
        }
}

/**
 * Turns a downloaded file name into a [MediaGuess].
 *
 * Written from the shapes of the names in this library and nothing else: split on the punctuation
 * people type between words, drop bracketed groups, release tags and watermarks, keep the last
 * number that could be a year, and look for a part number. Every rule is a bet about how uploaders
 * type, so the tests carry the real names and the resolver treats the result as a query only.
 */
object MediaNameParser {

    private const val OLDEST_YEAR = 1900

    /** Only a suffix in this set is a container; any other trailing group of characters is part of the name. */
    private val containers = setOf(
        "mkv", "mp4", "avi", "mov", "wmv", "flv", "m4v", "f4v", "m2ts", "mts", "ts",
        "webm", "mpg", "mpeg", "3gp", "vob", "ogv", "divx", "rmvb", "asf"
    )

    /**
     * Punctuation used between words.
     *
     * A hyphen is deliberately absent: "Spider-Man" and "WALL-E" are names, and splitting those would
     * search for something else.
     */
    private val separator = Regex("[\\s_.,;:'\"|/\\\\]+")

    private val bracketed = Regex("[(\\[<{][^()\\[\\]<>{}]*[)\\]>}]")

    private val fourDigitYear = Regex("^(19|20)\\d{2}$")

    /**
     * A hyphen someone typed with spaces around it is a separator, not a word.
     *
     * The ampersand is deliberately absent: "Tom & Jerry" is a title, and the catalogues spell the
     * same film both ways, so the symbol has to reach [TitleScore] to be read as the word.
     */
    private val punctuationOnly = Regex("^[-+~#*]+$")

    private val seasonEpisode = Regex(
        "\\bs(\\d{1,2})[ ._-]*e(?:p(?:isode)?)?[ ._-]*(\\d{1,4})\\b",
        RegexOption.IGNORE_CASE
    )

    /** The "1x05" shorthand. Digit counts are capped at two so "1080x720" has no room to fit. */
    private val crossEpisode = Regex("\\b(\\d{1,2})x(\\d{1,2})\\b")

    /**
     * A part number with no season in front of it, which is how anime archives are named.
     *
     * The lookbehind is what keeps the capital E at the end of "WALL-E" from turning the film into
     * episode 2008 of a series called WALL: a hyphen before the letter means it belongs to the word.
     * A lone "e" has to be glued to its number for the same reason, because "WALL_E 2008" separated
     * by a space reads exactly like a title and a year.
     */
    private val plainEpisode = Regex(
        "(?<![-\\w])(?:episode|ep|cap|e(?![ ._-]))[ ._-]*(\\d{1,4})\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * A season on its own.
     *
     * The leading boundary is what keeps "Cars 2" and the closing s of "Friends" out of it; only an
     * s that starts a word, then optional "eason", then digits counts.
     */
    private val seasonAlone = Regex(
        "\\bs(?:eason)?[ ._-]*(\\d{1,2})\\b",
        RegexOption.IGNORE_CASE
    )

    private val yearInsideBrackets = Regex("(19|20)\\d{2}")

    /** A channel handle or group watermark glued onto the name. */
    private val handle = Regex("@[A-Za-z0-9_]+")

    /**
     * Words that only ever describe how this particular copy was made.
     *
     * Deliberately missing the short ambiguous ones a film could genuinely be called, like "Cam" or
     * "Dub": losing a title costs more than leaving a tag for the year cut to remove.
     */
    private val releaseWords = setOf(
        "x264", "x265", "h264", "h265", "hevc", "avc", "vc1", "xvid", "divx", "mpeg2", "h263",
        "bluray", "blu", "bdrip", "brrip", "dvd", "dvdrip", "dvdscr", "screener", "scr",
        "webrip", "webdl", "web", "hdtv", "hdrip", "telesync", "telescreen", "r5", "r6", "vhs",
        "laserdisc", "ptv", "dsr", "sat", "dv", "uhd", "fhd", "sdr", "hdr", "hdr10", "hdr10plus",
        "dolby", "atmos", "dts", "dtshr", "ac3", "eac3", "flac", "opus", "vorbis",
        "audio", "dual", "multi", "multiaudio", "subs", "subbed", "hardcoded", "softsubs",
        "multisubs", "eng", "english", "hin", "hindi", "tam", "tamil", "tel", "telugu", "mal",
        "malayalam", "kan", "kannada", "ben", "bengali", "mar", "marathi", "jav", "japanese",
        "chi", "chinese", "kor", "korean", "esp", "spanish", "fre", "french", "ger", "german",
        "ita", "italian", "rus", "russian", "pun", "punjabi",
        "uncut", "extended", "remastered", "remaster", "repack", "proper", "internal", "limited",
        "unrated", "censored", "uncensored", "theatrical", "imax", "final", "cut",
        "mkv", "mp4", "avi", "mov", "webm", "10bit", "8bit",
        "size", "gb", "mb", "nsb", "nzb", "torrent", "rarbg", "yts", "yify", "xoj", "evo", "cmrg",
        "telly", "htwr", "ecsv", "asap", "frss", "pspa", "rtf", "safu", "w4b", "sima", "flaws"
    )

    private val releasePatterns = listOf(
        Regex("^\\d{3,4}[pbi]$"),
        Regex("^\\d{3,4}[x✕]\\d{3,4}$"),
        Regex("^\\d{1,2}bit$"),
        Regex("^\\d\\.\\d$"),
        Regex("^\\d{1,4}[km]b$"),
        Regex("^\\d{1,2}ch$"),
        Regex("^(dd|e?ac3|dts|aac|mp[123])\\d?(\\.\\d)?$"),
        Regex("^\\d{2,4}kbps$"),
        Regex("^[45678]k$")
    )

    fun parse(rawName: String, thisYear: Int = Calendar.getInstance().get(Calendar.YEAR)): MediaGuess {
        val stem = stripExtension(directoryless(rawName))
        val brackets = bracketed.findAll(stem).joinToString(" ") { it.value }
        val stripped = bracketed.replace(handle.replace(stem, " "), " ")

        // An underscore separates words for every rule below, but \b does not know that: in
        // "Stranger_Things_S03E01" there is no boundary before the S, so all three marker patterns
        // read that name as a film and the episode escapes its series card. Swapping the character
        // for a space costs nothing and leaves every offset exactly where it was.
        val spaced = stripped.replace('_', ' ')

        // Brackets are where a careful uploader puts the facts the release tail would otherwise hide.
        val bracketYear = yearInsideBrackets.findAll(brackets).mapNotNull { it.value.toIntOrNull() }.lastOrNull()
        val bracketSeason = seasonAlone.findAll(brackets).mapNotNull { it.groupValues[1].toIntOrNull() }.lastOrNull()

        episodeMarker(spaced)?.let { marker ->
            val head = spaced.substring(0, marker.start)
            val seasonInHead = if (marker.season == null) seasonAlone.find(head) else null
            val seriesText = if (seasonInHead != null) head.substring(0, seasonInHead.range.first) else head
            val series = phraseUpTo(seriesText, dropTrailingYear = true)
            if (series.isBlank()) return MediaGuess.Unnamed(fallback(rawName, stripped))
            val episodeTitle = phraseUpTo(spaced.substring(marker.end), dropTrailingYear = false)
            return MediaGuess.Episode(
                series = series,
                season = marker.season ?: seasonInHead?.groupValues?.get(1)?.toIntOrNull() ?: bracketSeason,
                episode = marker.episode,
                episodeTitle = episodeTitle.takeIf { it.isNotBlank() }
            )
        }

        val words = tokenize(stripped)
        if (words.isEmpty()) return MediaGuess.Unnamed(fallback(rawName, stripped))
        if (words.isUploadBoilerplate()) return MediaGuess.Unnamed(fallback(rawName, stripped))

        // Right to left, so a title that ends in a number keeps it: Blade Runner 2049, not Blade Runner.
        val yearIndex = words.indices
            .reversed()
            .filter { words[it].isPlausibleYear(thisYear) }
            .firstOrNull { before -> words.subList(0, before).any { !isReleaseTag(it) } }

        val title = if (yearIndex == null) {
            joinUp(cutAtFirstTag(words))
        } else {
            joinUp(words.subList(0, yearIndex).filterNot { isReleaseTag(it) })
        }
        val year = yearIndex?.let { words[it].toIntOrNull() } ?: bracketYear

        return if (title.isBlank()) MediaGuess.Unnamed(fallback(rawName, stripped)) else MediaGuess.Movie(title, year)
    }

    private fun String.isPlausibleYear(thisYear: Int): Boolean {
        if (length != 4 || !fourDigitYear.matches(this)) return false
        val value = toIntOrNull() ?: return false
        return value in OLDEST_YEAR..(thisYear + 1)
    }

    /**
     * What to put on the card when nothing could be read out of the name.
     *
     * The stripped form first, because a watermark or a bracket is not a title, and the untouched
     * name only when even that left nothing.
     */
    private fun fallback(rawName: String, stripped: String): String {
        val words = joinUp(cutAtFirstTag(tokenize(stripped)))
        return words.ifBlank { stripExtension(directoryless(rawName)) }
    }

    /** The words before a marker: the name of the show, with no part number or tag left in it. */
    private fun phraseUpTo(text: String, dropTrailingYear: Boolean): String {
        val kept = cutAtFirstTag(tokenize(text)).toMutableList()
        if (dropTrailingYear) {
            while (kept.isNotEmpty() && kept.last().matches(fourDigitYear)) kept.removeAt(kept.lastIndex)
        }
        return joinUp(kept)
    }

    /** Without a year to anchor on, the first tag is the only edge available for where the name ends. */
    private fun cutAtFirstTag(words: List<String>): List<String> {
        val cut = words.indexOfFirst { isReleaseTag(it) }
        return if (cut < 0) words else words.subList(0, cut)
    }

    private data class Marker(val start: Int, val end: Int, val season: Int?, val episode: Int)

    private fun episodeMarker(text: String): Marker? {
        seasonEpisode.find(text)?.let {
            return Marker(
                it.range.first, it.range.last + 1,
                it.groupValues[1].toIntOrNull(), it.groupValues[2].toIntOrNull() ?: return null
            )
        }
        crossEpisode.find(text)?.let {
            return Marker(
                it.range.first, it.range.last + 1,
                it.groupValues[1].toIntOrNull(), it.groupValues[2].toIntOrNull() ?: return null
            )
        }
        plainEpisode.find(text)?.let {
            val episode = it.groupValues[1].toIntOrNull() ?: return null
            return Marker(it.range.first, it.range.last + 1, null, episode)
        }
        return null
    }

    private fun directoryless(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')

    private fun stripExtension(name: String): String {
        val suffix = name.substringAfterLast('.', "").lowercase()
        return if (suffix in containers) name.substringBeforeLast('.') else name
    }

    private fun tokenize(text: String): List<String> =
        text.split(separator)
            .map { it.trim { edge -> edge in edgePunctuation } }
            .filter { it.isNotBlank() && !punctuationOnly.matches(it) }

    /** Clinging punctuation that only ever appears at the ends of a word, unlike the hyphen inside "Spider-Man". */
    private val edgePunctuation = " -+~#*"

    private fun joinUp(words: List<String>): String = words.joinToString(" ").trim()

    private fun isReleaseTag(word: String): Boolean {
        val lower = word.lowercase()
        if (lower in releaseWords) return true
        if (releasePatterns.any { it.matches(lower) }) return true
        // "x264-group" and "web-dl" are one tag; "Spider-Man" has no tagged half.
        if (word.contains('-')) {
            val parts = word.split('-').filter { it.isNotBlank() }
            if (parts.size > 1) return parts.any { isReleaseTag(it) }
        }
        return false
    }

    /**
     * Whether the whole name is just a device or an app counting its own exports.
     *
     * These are worth recognising because a lookup costs a request to a free catalogue, and a chat of
     * phone recordings would otherwise spend a hundred of them on names that cannot possibly match.
     */
    private fun List<String>.isUploadBoilerplate(): Boolean {
        if (isEmpty() || first().lowercase() !in boilerplateHeads) return false
        return drop(1).all { word -> word.none { it.isLetter() } || word.lowercase() in boilerplateNoise }
    }

    private val boilerplateHeads = setOf(
        "video", "vid", "clip", "movie", "img", "image", "photo", "pic", "camera",
        "dsc", "dscn", "mvimg", "pxl", "screenshot", "screen", "recording",
        "whatsapp", "telegram", "miui", "dashcam", "gopro", "insta"
    )

    /** The words that join a boilerplate head to the digits after it without saying anything. */
    private val boilerplateNoise = setOf(
        "at", "am", "pm", "utc", "gmt", "st", "nd", "rd", "th",
        "record", "recording", "video", "audio", "photo", "image", "pic", "file", "clip", "movie"
    )
}
