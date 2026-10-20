package com.aeswox.arcplayer.playback

/**
 * Deciding which elementary stream the player should be told to use.
 *
 * VLC picks a track of its own every time it is handed a fresh media — its own language preference,
 * which is not the one the viewer pressed. This is the rule for when that default may stand and when
 * it has to be overruled, with no player in sight so it can be tested on its own.
 */
object TrackChoice {

    /** libVLC's "no track": the id that turns subtitles off, and never appears in a track list. */
    const val OFF = -1

    /**
     * The id to push into the player, or null to leave it alone.
     *
     * Nothing is pushed while no preference exists, because then VLC's default is the only opinion
     * there is. Nothing is pushed when the player already sits on the wanted track, which is the
     * common case and the reason a refresh of the list never disturbs playback.
     *
     * A real id is only pushed when the freshly enumerated list still carries it. The list arrives
     * empty for a beat at the start of every media, and a track can vanish if the file is re-read
     * differently; pushing an id the player does not know is how a choice silently becomes nothing.
     * [OFF] skips that check because a disabled track is never in the list to begin with.
     */
    fun resolve(preferredId: Int?, reportedId: Int, availableIds: Collection<Int>): Int? {
        val wanted = preferredId ?: return null
        if (wanted == reportedId) return null
        if (wanted == OFF || availableIds.any { it == wanted }) return wanted
        return null
    }
}
