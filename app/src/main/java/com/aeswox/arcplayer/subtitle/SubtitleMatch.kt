package com.aeswox.arcplayer.subtitle

import com.aeswox.arcplayer.metadata.MediaGuess
import com.aeswox.arcplayer.metadata.MediaNameParser

/**
 * Pairs a subtitle forwarded into the channel with the picture it belongs to.
 *
 * Whoever forwards a caption types its name to match the file they already uploaded, so the two names
 * are one work written twice by one hand. That is why this compares what the parser makes of each name
 * rather than the strings: the ordinary case is a video called
 * `Inception.2010.1080p.BluRay.x264.mkv` and a subtitle called `Inception.2010.srt`, and no amount of
 * trimming one into the other is going to make those equal.
 *
 * Deliberately strict, because a caption attached to the wrong thing is worse than no caption. It
 * arrives looking authoritative, it lands in the same track list as the real ones, and it says things
 * nobody on screen is saying.
 */
object SubtitleMatch {

    /** Suffixes that mean this document is words rather than pictures. */
    private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub")

    /**
     * Whether a channel document is a caption.
     *
     * The shelf walk already refuses anything that is not a video, so this only has to say which of the
     * refusals are worth keeping for later — which is why an unknown suffix is not a subtitle rather
     * than a guess at one.
     */
    fun isSubtitle(fileName: String): Boolean =
        extensionOf(fileName)?.let { it in SUBTITLE_EXTENSIONS } == true

    /**
     * Whether [subtitle] captions [video].
     *
     * Both names go through the parser with their extension off, because the parser's rules were
     * written for container suffixes and a trailing `srt` is not one of them.
     */
    fun matches(video: String, subtitle: String): Boolean {
        val left = identityOf(stripExtension(video)) ?: return false
        val right = identityOf(stripExtension(subtitle)) ?: return false
        return agree(left, right)
    }

    /**
     * Which of several captions to put on the picture, or null when none of them belong.
     *
     * A channel holding one episode's subtitles in three languages is normal, and picking the viewer's
     * own is the difference between a film that just works and one that needs the track sheet opened
     * every time. Without a language to aim at, the newest forward wins — the one they just sent is the
     * one they meant.
     */
    fun pick(video: String, subtitles: List<String>, language: String?): String? {
        val candidates = subtitles.filter { matches(video, it) }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1 || language.isNullOrBlank()) return candidates.first()
        val wanted = language.lowercase().take(2)
        return candidates.firstOrNull { namesLanguage(it, wanted) } ?: candidates.first()
    }

    /**
     * Whether the caption's own name says which language it is, ignoring the extension.
     *
     * A two-letter language has to answer to the three-letter tag a subtitle actually carries — the
     * phone says `es` and the file says `esp` — but only at exactly that length. Letting the prefix
     * stretch further would read a four-letter word in the title as a language and hand the viewer a
     * caption for the wrong thing on a coin flip.
     */
    private fun namesLanguage(subtitle: String, language: String): Boolean =
        stripExtension(subtitle).lowercase().split(DELIMITERS).any { token ->
            token == language || (token.length == 3 && token.startsWith(language))
        }
    /**
     * What a name says the file is, reduced to the part two names must share to be the same work.
     *
     * A release group's tag, a resolution and a source all belong to one file and not the other, so
     * none of them may take part in the comparison. The year may: two films called by one name is a
     * real thing that happens, and where both names bother to say, they have to agree.
     */
    private fun identityOf(fileName: String): Identity? =
        when (val guess = MediaNameParser.parse(fileName)) {
            is MediaGuess.Episode -> Identity.Part(
                series = normalise(guess.series),
                season = guess.season,
                episode = guess.episode
            )

            is MediaGuess.Movie -> Identity.Work(
                title = normalise(guess.title),
                year = guess.year
            )

            is MediaGuess.Unnamed -> guess.fallback
                .takeIf { it.isNotBlank() }
                ?.let { Identity.WholeFile(normalise(it)) }
        }

    private fun agree(left: Identity, right: Identity): Boolean = when {
        left is Identity.Part && right is Identity.Part -> left == right
        left is Identity.Work && right is Identity.Work ->
            left.title == right.title &&
                (left.year == null || right.year == null || left.year == right.year)

        left is Identity.WholeFile && right is Identity.WholeFile -> left == right
        else -> false
    }

    private fun normalise(text: String): String =
        text.lowercase().trim().replace(DELIMITERS, " ").replace(BLANK_RUNS, " ")

    private fun extensionOf(fileName: String): String? =
        fileName.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it != fileName.lowercase() }

    private fun stripExtension(fileName: String): String {
        val extension = extensionOf(fileName) ?: return fileName
        return fileName.dropLast(extension.length + 1)
    }

    /** Where one name ends and the next word begins, across the punctuation uploaders choose. */
    private val DELIMITERS = Regex("[._\\-\\[\\](){} ]+")

    private val BLANK_RUNS = Regex(" +")

    private sealed interface Identity {
        data class Part(val series: String, val season: Int?, val episode: Int) : Identity
        data class Work(val title: String, val year: Int?) : Identity
        data class WholeFile(val name: String) : Identity
    }
}
