package com.aeswox.arcplayer.metadata

/**
 * What one copy's own file name says about how it was made.
 *
 * Telegram gives an exact size and, for a video sent as a video, an exact frame size. Everything else a
 * viewer uses to choose between three copies of one film — the codec, where it came from, whether it has
 * a Dolby track or burn-in subtitles, and who uploaded it — exists only in the text the uploader typed.
 * Every claim here is a reading of that text and can be absent, so the row that shows it has to look
 * right when nothing was found.
 *
 * Names are matched with boundary-anchored patterns rather than by splitting on punctuation, because the
 * release tail is written every way there is: "x264", "H.264", "WEB-DL", "Web-Dl", "[Dual]". Splitting on
 * the dot would turn "H.264" into two halves that each mean nothing.
 */
data class ReleaseInfo(
    /** "4K", "1080p" …, or null when neither the name nor Telegram's frame size states one. */
    val quality: String?,
    /** How the copy was made, in the order a viewer scans: codec, source, range, audio, language, cut. */
    val tags: List<String>,
    /** Whoever uploaded it, without the leading @, or null when the name carries no signature. */
    val group: String?
) {

    companion object {

        /** Past this many badges a row turns to wallpaper, and the earliest ones are the deciding ones. */
        private const val MAX_TAGS = 6

        private val extensionless = Regex("\\.[A-Za-z0-9]{1,5}$")

        /** A channel handle glued into the name, which is how most Telegram groups sign their uploads. */
        private val handle = Regex("@([A-Za-z0-9_]{2,32})")

        /** The resolution claim in whatever shape it was typed; the leftmost one a name states. */
        private val qualityClaim = Regex("(?<![0-9a-z])(8k|4k|uhd|\\d{3,4}p)(?![0-9a-z])")

        /** The scene-style signature: a hyphen and then whoever made the rip. */
        private val signedBy = Regex("[-#]\\s*([A-Za-z][A-Za-z0-9]{2,14})$")

        /** Frame heights worth naming, biggest first, so a 1080-tall file reads as 1080p. */
        private val rungs = listOf(2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p")

        /**
         * Read one name. [frameHeight] is Telegram's own figure and is used only where the name states no
         * resolution, because an uploader who wrote 720p beside a 1080-tall file was naming its source.
         */
        fun parse(fileName: String, frameHeight: Int = 0): ReleaseInfo {
            val named = extensionless.replace(directoryless(fileName), " ")
            val mark = handle.find(named)?.groupValues?.get(1)?.trim('_')?.takeIf { it.isNotBlank() }
            // A channel name describes the uploader, not the file: "@Anime_1080p" is no proof of a 1080 copy.
            // The letters typed are kept alongside the lowercase reading, because a group signs its name
            // however it likes and "framessor" is not how that group writes itself.
            val clean = handle.replace(named, " ")
            val body = clean.lowercase()

            val quality = qualityClaim.findAll(body)
                .mapNotNull { resolutionOf(it.groupValues[1]) }
                .firstOrNull()
                ?: rungs.firstOrNull { frameHeight > 0 && frameHeight * 20 >= it.first * 19 }?.second

            val tags = rules
                .filter { (_, pattern) -> pattern.containsMatchIn(body) }
                .map { it.first }
                .distinct()
                .take(MAX_TAGS)

            // A hyphenated tail reads as a group's signature only when the rest of the name is release
            // shaped. "Ant-Man" ends in exactly the same shape and means the film.
            val signed = if (quality == null && tags.isEmpty()) null
                else signedBy.find(clean.trim())?.groupValues?.get(1)?.takeUnless { it.lowercase() in reserved }

            return ReleaseInfo(quality, tags, mark ?: signed)
        }

        /** The claim, only when it names a rung anybody would recognise; anything else is noise. */
        private fun resolutionOf(claim: String): String? = when (claim) {
            "4k", "uhd", "2160p" -> "4K"
            "8k", "4320p" -> "8K"
            "1440p", "1080p", "720p", "480p", "576p", "360p", "240p" -> claim
            else -> null
        }

        /** Words that sit where a group name would but describe the rip instead of who made it. */
        private val reserved = setOf(
            "x264", "x265", "h264", "h265", "hevc", "av1", "aac", "ac3", "eac3", "dts", "dtshr", "atmos",
            "webrip", "webdl", "bluray", "bdrip", "brrip", "hdtv", "dvdrip", "dvdscr", "hindi", "tamil",
            "telugu", "malayalam", "english", "subbed", "uncut", "extended", "remux", "mux", "proper",
            "repack", "internal", "xvid", "divx", "flac", "opus", "truehd", "thd", "sdr", "dovi"
        )

        /**
         * The claims worth making, in the order a viewer weighs them.
         *
         * Deliberately missing the short ambiguous ones a film could genuinely be called, like "Cam", and
         * bare "HD" or "TS", which appear inside real titles. A wrong badge costs more trust than a
         * missing one.
         */
        private val rules: List<Pair<String, Regex>> = listOf(
            "HEVC" to Regex("(?<![0-9a-z])(?:x[ ._-]?265|h[ ._-]?265|hevc)(?![0-9a-z])"),
            "H.264" to Regex("(?<![0-9a-z])(?:x[ ._-]?264|h[ ._-]?264|avc)(?![0-9a-z])"),
            "AV1" to Regex("(?<![0-9a-z])av1(?![0-9a-z])"),
            "Xvid" to Regex("(?<![0-9a-z])xvid(?![0-9a-z])"),
            "Blu-ray" to Regex("(?<![0-9a-z])(?:bluray|bdrip|brrip|bdmux|uhd[ ._-]?bd)(?![0-9a-z])"),
            "WEB-DL" to Regex("(?<![0-9a-z])web[ ._-]?dl(?![0-9a-z])"),
            "WEBRip" to Regex("(?<![0-9a-z])web[ ._-]?rip(?![0-9a-z])"),
            "HDTV" to Regex("(?<![0-9a-z])(?:hdtv|hdrip|tvrip|dvb)(?![0-9a-z])"),
            "DVD" to Regex("(?<![0-9a-z])(?:dvdrip|dvdmux|dvd5|dvd9|dvd)(?![0-9a-z])"),
            "Screener" to Regex("(?<![0-9a-z])(?:dvdscr|screener|web[ ._-]?scr|scr)(?![0-9a-z])"),
            "Telesync" to Regex("(?<![0-9a-z])(?:telesync|telescreen)(?![0-9a-z])"),
            "VHS" to Regex("(?<![0-9a-z])(?:vhsrip|vhs|workprint)(?![0-9a-z])"),
            "Dolby Vision" to Regex("(?<![0-9a-z])(?:dolby[ ._-]?vision|dovi)(?![0-9a-z])"),
            "HDR10+" to Regex("(?<![0-9a-z])hdr10(?:[ ._-]?(?:plus|x)|\\+)(?![0-9a-z])"),
            "HDR10" to Regex("(?<![0-9a-z])hdr10(?![0-9a-z])"),
            "HDR" to Regex("(?<![0-9a-z])hdr(?![0-9a-z])"),
            "SDR" to Regex("(?<![0-9a-z])sdr(?![0-9a-z])"),
            "10-bit" to Regex("(?<![0-9a-z])10[ ._-]?bit(?![0-9a-z])"),
            "Dolby Atmos" to Regex("(?<![0-9a-z])atmos(?![0-9a-z])"),
            "TrueHD" to Regex("(?<![0-9a-z])(?:truehd|thd|dtt?hd)(?![0-9a-z])"),
            "Dolby" to Regex("(?<![0-9a-z])(?:eac3|ac3|dolby(?:[ ._-]?digital)?|dd5[ ._-]?1|dd)(?![0-9a-z])"),
            // DDP is always written with the bed count glued on behind it, so the run of digits after it is
            // part of the claim rather than the thing that disqualifies it.
            "Dolby" to Regex("(?<![0-9a-z])ddp\\d*"),
            "DTS" to Regex("(?<![0-9a-z])(?:dts[ ._-]?hd|dts[ ._-]?ma|dts|dtsi)(?![0-9a-z])"),
            "FLAC" to Regex("(?<![0-9a-z])flac(?![0-9a-z])"),
            "Opus" to Regex("(?<![0-9a-z])opus(?![0-9a-z])"),
            "AAC" to Regex("(?<![0-9a-z])aac(?![0-9a-z])"),
            "7.1" to Regex("(?<![0-9])7[ .]1(?![0-9])"),
            "5.1" to Regex("(?<![0-9])5[ .]1(?![0-9])"),
            "Dual audio" to Regex("(?<![0-9a-z])(?:dual[ ._-]?(?:audio|sound)?|multi[ ._-]?audio)(?![0-9a-z])"),
            "Hindi" to Regex("(?<![0-9a-z])(?:hindi|hin)(?![0-9a-z])"),
            "Tamil" to Regex("(?<![0-9a-z])(?:tamil|tam)(?![0-9a-z])"),
            "Telugu" to Regex("(?<![0-9a-z])(?:telugu|tel)(?![0-9a-z])"),
            "Malayalam" to Regex("(?<![0-9a-z])(?:malayalam|mal)(?![0-9a-z])"),
            "Kannada" to Regex("(?<![0-9a-z])(?:kannada|kan)(?![0-9a-z])"),
            "Bengali" to Regex("(?<![0-9a-z])(?:bengali|ben)(?![0-9a-z])"),
            "Marathi" to Regex("(?<![0-9a-z])(?:marathi|mar)(?![0-9a-z])"),
            "Punjabi" to Regex("(?<![0-9a-z])(?:punjabi|pun)(?![0-9a-z])"),
            "English" to Regex("(?<![0-9a-z])(?:english|eng)(?![0-9a-z])"),
            "Japanese" to Regex("(?<![0-9a-z])(?:japanese|jpn)(?![0-9a-z])"),
            "Korean" to Regex("(?<![0-9a-z])(?:korean|kor)(?![0-9a-z])"),
            "Chinese" to Regex("(?<![0-9a-z])(?:chinese|chi)(?![0-9a-z])"),
            "Spanish" to Regex("(?<![0-9a-z])(?:spanish|esp|lat)(?![0-9a-z])"),
            "French" to Regex("(?<![0-9a-z])(?:french|fre)(?![0-9a-z])"),
            "German" to Regex("(?<![0-9a-z])(?:german|ger)(?![0-9a-z])"),
            "Extended" to Regex("(?<![0-9a-z])extended(?![0-9a-z])"),
            "Uncut" to Regex("(?<![0-9a-z])uncut(?![0-9a-z])"),
            "Unrated" to Regex("(?<![0-9a-z])unrated(?![0-9a-z])"),
            "Remastered" to Regex("(?<![0-9a-z])(?:remastered|remaster)(?![0-9a-z])"),
            "IMAX" to Regex("(?<![0-9a-z])imax(?![0-9a-z])"),
            "Theatrical" to Regex("(?<![0-9a-z])theatrical(?![0-9a-z])"),
            "Burned-in subs" to Regex("(?<![0-9a-z])(?:hardcoded|hard[ ._-]?subs?|burned[ ._-]?in|burnin)(?![0-9a-z])"),
            "Subtitles" to Regex("(?<![0-9a-z])(?:multi[ ._-]?subs?|soft[ ._-]?subs?|embed[ ._-]?subs?|subs?|subbed)(?![0-9a-z])")
        )

        private fun directoryless(name: String): String =
            name.substringAfterLast('/').substringAfterLast('\\')
    }
}
