package com.aeswox.arcplayer.metadata

/**
 * The franchise-font rendering of a title, on a transparent background.
 *
 * Catalogues hold several renditions of the same wordmark — different languages, different uploads,
 * anything from a 200 px thumbnail to a 14,000 px scan. Which one gets shown is a ranking question,
 * so it lives here as a pure function rather than inside the HTTP code that can only be answered by a
 * test that talks to themoviedb.org.
 */
object TitleLogo {

    data class Rendition(
        val filePath: String,
        val language: String?,
        val widthPx: Int,
        val voteAverage: Double,
        val voteCount: Int
    )

    /**
     * Below this the wordmark is a scan of a scan, and it is shown at four times its width on a
     * landscape phone. A title with nothing larger is better off with its plain name.
     */
    const val MIN_WIDTH_PX = 400

    /**
     * The one rendition worth downloading, or null when the title has none.
     *
     * PNG only: TMDB serves a minority of wordmarks as SVG and this app carries no vector decoder, so
     * an SVG-only title falls back to the plain name rather than pulling in a dependency for it.
     *
     * Language is the first cut because a German release's own logo is what a German viewer expects to
     * read; vote score is the cut within a language, since the community removes the misfiled and the
     * low-res uploads, and width breaks the ties left over when nothing has been voted on at all.
     */
    fun best(renditions: List<Rendition>, preferredLanguage: String? = null): Rendition? {
        val usable = renditions.filter {
            it.filePath.endsWith(".png", ignoreCase = true) && it.widthPx >= MIN_WIDTH_PX
        }
        return band(usable, preferredLanguage)
            ?: band(usable, "en")
            ?: usable.maxWithOrNull(ranking)
    }

    private fun band(renditions: List<Rendition>, language: String?): Rendition? =
        language?.let { wanted -> renditions.filter { it.language.equals(wanted, ignoreCase = true) } }
            ?.maxWithOrNull(ranking)

    private val ranking: Comparator<Rendition> =
        compareBy<Rendition> { it.voteAverage }.thenBy { it.voteCount }.thenBy { it.widthPx }
}
