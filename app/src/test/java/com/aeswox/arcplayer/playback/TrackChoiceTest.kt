package com.aeswox.arcplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackChoiceTest {

    private val hindiThenEnglish = listOf(41, 42)

    @Test
    fun noChoiceLetsThePlayersOwnDefaultStand() {
        // The first open of a film nobody has touched: VLC's default language is the only opinion there is.
        assertNull(TrackChoice.resolve(null, reportedId = 41, availableIds = hindiThenEnglish))
    }

    @Test
    fun aChoiceAlreadyInPlaceIsLeftAlone() {
        // The steady state, hit on every Playing and Vout event. Pushing the track again here would
        // make a plain refresh of the list stutter the audio.
        assertNull(TrackChoice.resolve(42, reportedId = 42, availableIds = hindiThenEnglish))
    }

    @Test
    fun aTrackThePlayerChangedIsPutBack() {
        // The bug this exists for: English was picked, the film stalled, the re-open built a fresh
        // media and VLC answered with Hindi.
        assertEquals(42, TrackChoice.resolve(42, reportedId = 41, availableIds = hindiThenEnglish))
    }

    @Test
    fun aTrackThatIsNoLongerThereIsNotPushed() {
        // A re-read that dropped the second audio track. An id the player has never heard of is a
        // request it silently refuses, and the choice is gone for good.
        assertNull(TrackChoice.resolve(42, reportedId = 41, availableIds = listOf(41)))
    }

    @Test
    fun anUnparsedTrackListWaitsForTheNextRefresh() {
        // The list arrives empty for a beat after every media swap, so there is nothing to select yet
        // and the next event gets another turn.
        assertNull(TrackChoice.resolve(42, reportedId = -1, availableIds = emptyList()))
    }

    @Test
    fun disablingIsHonouredEvenThoughItIsNotATrack() {
        // "Disable Subtitles" is a real choice the viewer made, and OFF never appears in any list.
        assertEquals(TrackChoice.OFF, TrackChoice.resolve(TrackChoice.OFF, reportedId = 57, availableIds = listOf(57, 58)))
    }

    @Test
    fun anAutoSelectedSubtitleIsTurnedOffAgainAfterAStall() {
        // VLC defaults a subtitle track back on over the viewer's disable, on exactly the re-open that
        // resets the audio language.
        assertEquals(TrackChoice.OFF, TrackChoice.resolve(TrackChoice.OFF, reportedId = 57, availableIds = listOf(57)))
    }
}
