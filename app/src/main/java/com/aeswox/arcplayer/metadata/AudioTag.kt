package com.aeswox.arcplayer.metadata

/**
 * What one copy's file name says about its sound, and whether a television can play that.
 *
 * A phone with libVLC decodes almost anything, so nothing in this app has needed to know what a film's
 * audio actually is until a TV asked. A television does not run a player of its own: it hands the stream
 * to hardware that understands a short list of codecs, and a DTS or TrueHD track is a silent film there —
 * the picture moves and nothing comes out of the speakers, which a viewer reads as a broken file rather
 * than as a codec they never had.
 *
 * So this is a reading of the uploader's text, and nothing more. It is wrong about a file whose name says
 * no audio, right about one that does, and the only claim it makes strongly enough to warn on is the one
 * the name states twice over: nothing the panel can play, and no compatible track buried beside it.
 */
object AudioTag {

    /**
     * What a name claims.
     *
     * [codec] is the short name a badge is drawn from, or null when the name says nothing about sound.
     * [unsupported] means every track the name states is one this TV's hardware cannot decode, which is
     * the difference between a chip that informs and a warning that stops a viewer sitting down.
     */
    data class Claim(val codec: String?, val unsupported: Boolean)

    /** Nothing stated, nothing warned. */
    private val Unknown = Claim(null, false)

    private val extension = Regex("\\.[A-Za-z0-9]{1,5}$")

    /** A channel's signature, which describes whoever uploaded the file and not the file. */
    private val handle = Regex("@[A-Za-z0-9_]{2,32}")

    /**
     * The claims worth making, most specific first, and whether television hardware decodes them.
     *
     * Order does the work here: a name reading `DTS-HD.Master.Atmos.AC3` matches both `dts-hd` and `ac3`,
     * and the first of those that is something the panel can play is the one the viewer is actually going
     * to hear — a dual-audio release carries a compatible track precisely so that machines without the
     * fancy one still get sound.
     *
     * Only DTS and TrueHD are marked as unsupported, because those are the two this generation of Samsung
     * set genuinely refuses. Dolby Digital and Digital Plus, AAC, Opus and FLAC all play, and a warning
     * fired on a film that would have watched itself costs more trust than one that misses a film that
     * will not.
     */
    private enum class Tag(val codec: String, val decodable: Boolean, val pattern: Regex) {
        TrueHd("truehd", false, Regex("(?<![0-9a-z])(?:true[ ._-]?hd|dtt?hd|thd)(?![0-9a-z])")),
        DtsHd("dts-hd", false, Regex("(?<![0-9a-z])dts[ ._-]?(?:hd|ma|hr)(?![0-9a-z])")),
        Dts("dts", false, Regex("(?<![0-9a-z])dts(?![0-9a-z])")),
        Eac3("eac3", true, Regex("(?<![0-9a-z])(?:eac[ ._-]?3|ddp\\d*|dd[ ._-]?(?:plus|\\+)|dolby[ ._-]?digital[ ._-]?plus)(?![0-9a-z])")),
        Ac3("ac3", true, Regex("(?<![0-9a-z])(?:ac[ ._-]?3|dd|dd5[ ._-]?1|dolby[ ._-]?digital)(?![0-9a-z])")),
        Flac("flac", true, Regex("(?<![0-9a-z])flac(?![0-9a-z])")),
        Opus("opus", true, Regex("(?<![0-9a-z])opus(?![0-9a-z])")),
        Aac("aac", true, Regex("(?<![0-9a-z])aac(?:[ ._-]?[0-9][ ._-]?[01])?(?![0-9a-z])")),
        Mp3("mp3", true, Regex("(?<![0-9a-z])(?:mp3|mp2)(?![0-9a-z])"))
    }

    /**
     * Read one copy's name.
     *
     * The signature comes out before anything is matched, because `@DTS_HD_group` is a person's handle and
     * a badge that reads "AUDIO UNSUPPORTED" off one is exactly the false alarm a viewer cannot unsee.
     */
    fun of(fileName: String): Claim {
        val named = extension.replace(directoryless(fileName), " ")
        val body = handle.replace(named, " ").lowercase()

        val matched = Tag.entries.filter { it.pattern.containsMatchIn(body) }
        val playable = matched.firstOrNull { it.decodable }
        if (playable != null) return Claim(playable.codec, false)

        val refused = matched.firstOrNull()
        return if (refused == null) Unknown else Claim(refused.codec, true)
    }

    private fun directoryless(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
}
