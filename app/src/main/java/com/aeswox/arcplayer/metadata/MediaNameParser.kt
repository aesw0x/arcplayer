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
        val episodeTitle: String?,
        val year: Int? = null
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

    /** A balanced bracket group, which is where an uploader puts a season the name itself has no room for. */
    private val bracketed = Regex("[(\\[<{][^()\\[\\]<>{}]*[)\\]>}]")

    /**
     * Every bracket character, which is a separator and never a title character.
     *
     * Telegram clips long captions, so a carefully uploaded name can lose its closing square bracket
     * in transit: "Ballerina (2025) [English - 1080p HQ HDRip - x264 - [DDP 5." is the whole release
     * list of one film, with only the first group closed. An opener left glued to the word after it
     * makes "[English" something other than the tag "english", and the tail then never gets dropped.
     * Each one becomes exactly one space, because the year boundary is read out of the offsets in the
     * result and a deletion would move every number after it.
     */
    private val strayBracket = Regex("[()\\[\\]<>{}]")

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

    /**
     * A year, with the digits either side ruled out so "20260805" in a phone recording is not read as
     * one, and "1080p" never can be.
     */
    private val yearNumber = Regex("(?<!\\d)(19|20)\\d{2}(?!\\d)")

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
        "dsnp", "dsn", "dsny", "atvp", "amzn", "hmax", "pmtp", "npvp", "cwa", "dmu",
        "audio", "dual", "multi", "multiaudio", "subs", "subbed", "hardcoded", "softsubs",
        "multisubs",
        "uncut", "extended", "remastered", "remaster", "repack", "proper", "internal", "limited",
        "unrated", "censored", "uncensored", "theatrical", "imax", "final", "cut",
        "mkv", "mp4", "avi", "mov", "webm", "10bit", "8bit",
        "size", "gb", "mb", "nsb", "nzb", "torrent", "rarbg", "yts", "yify", "xoj", "evo", "cmrg",
        "telly", "htwr", "ecsv", "asap", "frss", "pspa", "rtf", "safu", "w4b", "sima", "flaws"
    )

    /**
     * Codes that name the language a copy speaks rather than how it was made.
     *
     * They count only when they stand on their own. Half of a hyphenated word is somebody's name:
     * "chi" is Mandarin and also the end of "Shang-Chi", and a word read entirely as a tag is dropped
     * from the query — that hero's own title went with it, and a file left with no name in front of
     * its year is never asked about at all. "Ben-Hur" and "Kan" sit in the same trap.
     */
    private val languageCodes = setOf(
        "eng", "english", "hin", "hindi", "tam", "tamil", "tel", "telugu", "mal", "malayalam",
        "kan", "kannada", "ben", "bengali", "mar", "marathi", "jav", "japanese", "chi", "chinese",
        "kor", "korean", "esp", "spanish", "fre", "french", "ger", "german", "ita", "italian",
        "rus", "russian", "pun", "punjabi"
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
        // The bracket characters and any watermark go, but as spaces rather than by deletion, so every
        // offset still points at the same character of the name: the year is found by where it sits,
        // and that only works if where it sits survives the cleanup. Underscores go the same way,
        // because \b does not read one as a boundary — in "Stranger_Things_S03E01" there is none
        // before the S, and the episode escapes its series card.
        val spaced = strayBracket.replace(
            handle.replace(stem) { " ".repeat(it.value.length) },
            " "
        ).replace('_', ' ')

        // Brackets are where a careful uploader puts the facts the release tail would otherwise hide.
        val bracketSeason = seasonAlone.findAll(brackets).mapNotNull { it.groupValues[1].toIntOrNull() }.lastOrNull()
        val bracketYear = yearNumber.findAll(brackets).mapNotNull { it.value.toIntOrNull() }.lastOrNull { it in OLDEST_YEAR..(thisYear + 1) }

        episodeMarker(spaced)?.let { marker ->
            val head = spaced.substring(0, marker.start)
            val seasonInHead = if (marker.season == null) seasonAlone.find(head) else null
            val seriesText = if (seasonInHead != null) head.substring(0, seasonInHead.range.first) else head
            val headCut = cutAtFirstTag(tokenize(seriesText))
            val trailingYear = headCut.lastOrNull()?.takeIf { it.matches(fourDigitYear) }?.toIntOrNull()
                ?.takeIf { it in OLDEST_YEAR..(thisYear + 1) }
            val series = phraseUpTo(seriesText, dropTrailingYear = true)
            if (series.isBlank()) return MediaGuess.Unnamed(fallback(rawName, spaced))
            // A season written after the part number — "Episode 3 (Season 1)" — is the same fact the
            // head carries, not the name of the episode, so it stops the tail the same way it stops
            // the head.
            val tail = spaced.substring(marker.end)
            val seasonAfter = seasonAlone.find(tail)
            val episodeTitle = phraseUpTo(
                if (seasonAfter != null) tail.substring(0, seasonAfter.range.first) else tail,
                dropTrailingYear = false
            )
            return MediaGuess.Episode(
                series = series,
                season = marker.season ?: seasonInHead?.groupValues?.get(1)?.toIntOrNull() ?: bracketSeason,
                episode = marker.episode,
                episodeTitle = episodeTitle.takeIf { it.isNotBlank() },
                year = trailingYear ?: bracketYear
            )
        }

        val words = tokenize(spaced)
        if (words.isEmpty()) return MediaGuess.Unnamed(fallback(rawName, spaced))
        if (words.isUploadBoilerplate()) return MediaGuess.Unnamed(fallback(rawName, spaced))

        // A year is the one edge in these names that does not depend on guessing which words an
        // uploader used for quality: everything in front of it is the title and everything behind it
        // is how this copy was made. Right to left, so a title that ends in a number keeps it —
        // Blade Runner 2049, not Blade Runner.
        val year = titleBlock(spaced, thisYear)
        return if (year == null) {
            // With no year to stop at, the first tag is the only edge available — unless taking it
            // would leave nothing, which means that word is the name and not a tag.
            val cut = cutAtFirstTag(words)
            val title = joinUp(cut.ifEmpty { words })
            if (title.isBlank()) MediaGuess.Unnamed(fallback(rawName, spaced))
            else MediaGuess.Movie(title, null)
        } else {
            if (year.title.isBlank()) MediaGuess.Unnamed(fallback(rawName, spaced))
            else MediaGuess.Movie(year.title, year.value)
        }
    }

    /** A release year and the name that stands in front of it. */
    private data class YearText(val value: Int, val title: String)

    /**
     * The name and the year it was released, read out of where the year sits.
     *
     * Scanned from the right, so a title ending in a number keeps it — Blade Runner 2049, not Blade
     * Runner — and a year only counts as a release date when something stands in front of it. Null
     * when the name carries no plausible year at all, which leaves the release tags as the only edge.
     */
    private fun titleBlock(text: String, thisYear: Int): YearText? =
        yearNumber.findAll(text)
            .filter { it.range.first > 0 }
            .mapNotNull { match ->
                val value = match.value.toIntOrNull()
                if (value == null || value !in OLDEST_YEAR..(thisYear + 1)) null else match to value
            }
            .lastOrNull { (match, _) -> tokenize(text.substring(0, match.range.first)).isNotEmpty() }
            ?.let { (match, value) ->
                YearText(
                    value = value,
                    title = joinUp(nameBeforeYear(tokenize(text.substring(0, match.range.first)), value))
                )
            }

    /**
     * The words in front of a release year that name the work.
     *
     * Tags spoken there describe this copy and go, but the first word stays whatever the list says
     * about it, because a file name opens with its title — "Final Destination" is a name, and a word
     * the list calls noise can still be the word a film is known by. A year repeated in front of the
     * one just found is the uploader stating the release date twice, once bare and once in brackets,
     * and a name never says the same year twice.
     */
    private fun nameBeforeYear(headWords: List<String>, year: Int): List<String> {
        val kept = headWords.filterIndexed { index, word -> index == 0 || !isReleaseTag(word) }
        return kept.dropLastWhile { it.matches(fourDigitYear) && it.toIntOrNull() == year }
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

    private fun isReleaseTag(word: String, wholeWord: Boolean = true): Boolean {
        val lower = word.lowercase()
        if (wholeWord && lower in languageCodes) return true
        if (lower in releaseWords) return true
        if (releasePatterns.any { it.matches(lower) }) return true
        // "x264-group" and "web-dl" are one tag; "Spider-Man" has no tagged half.
        if (word.contains('-')) {
            val parts = word.split('-').filter { it.isNotBlank() }
            if (parts.size > 1) return parts.any { isReleaseTag(it, wholeWord = false) }
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
