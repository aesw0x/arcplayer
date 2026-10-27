package com.aeswox.arcplayer.metadata

/**
 * Which of several copies of one thing is worth opening.
 *
 * Size answered this on its own, and it was the only figure a name could not fake — but it says nothing
 * about a 4K rip versus a 1080p one that happens to be encoded less efficiently, or a clean master
 * versus the same picture with subtitles burnt into it. So the claims a name makes are now read in a
 * fixed order and the FIRST claim that separates two copies decides. Size stays where it was, at the
 * end, breaking the ties the names leave.
 *
 * Deliberately not read: language, "Dual audio" and soft-subtitle tags. Those change what is inside the
 * file rather than how well it was made, and a Hindi-dubbed copy is not a worse copy.
 *
 * Every tier is a reading of [ReleaseInfo], so an absent claim is normal and is ranked as what it
 * is — not knowing, rather than being last.
 */
data class CopyRank(
    /** 8K down to 480p; a name that says nothing sits below those, and a stated 360p below that. */
    val resolution: Int,
    /** Dolby Vision above HDR10+ above HDR10 above an unstated picture, with a stated SDR below it. */
    val range: Int,
    /** Where the picture came from: disc above store download above a silent name above a web rip. */
    val source: Int,
    /** Atmos, then TrueHD, then a Dolby or DTS bed, then anything else — a silent name included. */
    val audio: Int,
    val bitDepth: Int,
    val codec: Int,
    /** 1 while the picture is clean, 0 once a name admits burnt-in subtitles. */
    val cleanPicture: Int,
    /** 1 for a copy already on this phone, 0 for one that has to be fetched. */
    val phone: Int,
    val sizeBytes: Long
) : Comparable<CopyRank> {

    override fun compareTo(other: CopyRank): Int = comparator.compare(this, other)

    companion object {

        private val comparator = compareBy<CopyRank>({ it.resolution })
            .thenBy { it.range }
            .thenBy { it.source }
            .thenBy { it.audio }
            .thenBy { it.bitDepth }
            .thenBy { it.codec }
            .thenBy { it.cleanPicture }
            // Ahead of size, because two names that claim the same picture describe the same picture and
            // the one already on the phone needs nothing. Behind everything the names claim, because a
            // 4K copy is better than a 1080p one even when the 1080p is already here and the 4K is not.
            .thenBy { it.phone }
            .thenBy { it.sizeBytes }

        /** [frameHeight] is Telegram's own figure and fills in only where the name states no resolution. */
        fun of(
            fileName: String,
            sizeBytes: Long,
            frameHeight: Int = 0,
            onPhone: Boolean = false
        ): CopyRank {
            val read = ReleaseInfo.parse(fileName, frameHeight)
            val tags = read.tags.toSet()
            return CopyRank(
                resolution = resolutionOf(read.quality),
                range = rangeOf(tags),
                source = sourceOf(tags),
                audio = audioOf(tags),
                bitDepth = if ("10-bit" in tags) 1 else 0,
                codec = if ("HEVC" in tags || "AV1" in tags) 1 else 0,
                cleanPicture = if ("Burned-in subs" in tags) 0 else 1,
                phone = if (onPhone) 1 else 0,
                sizeBytes = sizeBytes
            )
        }

        private fun resolutionOf(quality: String?): Int = when (quality) {
            "8K" -> 7
            "4K" -> 6
            "1440p" -> 5
            "1080p" -> 4
            "720p" -> 3
            "480p" -> 2
            null -> 1
            // 576p, 360p, 240p: stated, and therefore worth less than a name that said nothing.
            else -> 0
        }

        private fun rangeOf(tags: Set<String>): Int = when {
            "Dolby Vision" in tags -> 4
            "HDR10+" in tags -> 3
            "HDR10" in tags || "HDR" in tags -> 2
            // Anything else is not knowing, which ranks above a name that admits an SDR grade.
            "SDR" in tags -> 0
            else -> 1
        }

        private fun sourceOf(tags: Set<String>): Int = when {
            "Blu-ray" in tags -> 5
            "WEB-DL" in tags -> 4
            "WEBRip" in tags -> 2
            "HDTV" in tags -> 2
            "DVD" in tags -> 1
            "Screener" in tags -> 1
            "Telesync" in tags -> 0
            "VHS" in tags -> 0
            else -> 3
        }

        private fun audioOf(tags: Set<String>): Int = when {
            "Dolby Atmos" in tags -> 5
            "TrueHD" in tags -> 4
            "Dolby" in tags -> 3
            "DTS" in tags -> 3
            "FLAC" in tags -> 2
            "5.1" in tags || "7.1" in tags -> 2
            // AAC, Opus, or nothing said at all: a name that mentions no audio may be holding anything,
            // so silence is weighed the same as the lossy stereo, never below it.
            else -> 1
        }
    }
}
