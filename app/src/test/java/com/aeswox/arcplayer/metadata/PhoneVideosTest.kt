package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which of the phone's videos belong on a shelf, decided from a path and a runtime.
 *
 * The paths are the ones MediaStore actually reports — always rooted at the volume and always ending
 * with a slash — and the feature-length figures are the ones a real film carries. Each test moves one
 * thing, because a rule that rejects a film is worse than a rule that keeps a holiday video: the first
 * is a broken library, and the second is only a quiet one.
 */
class PhoneVideosTest {

    @Test
    fun aFeatureFiledUnderMoviesIsACollection() {
        assertEquals(PhoneVideoVerdict.COLLECTION, PhoneVideos.verdictOf("Movies/", TWO_HOURS))
    }

    @Test
    fun aFolderNobodyNamedIsStillReadByItsLength() {
        // A sideloaded film sits in Download/, which is neither a camera's folder nor a collection's.
        // Absence of evidence is not evidence of a clip.
        assertEquals(PhoneVideoVerdict.COLLECTION, PhoneVideos.verdictOf("Download/", TWO_HOURS))
        assertEquals(PhoneVideoVerdict.COLLECTION, PhoneVideos.verdictOf("", TWO_HOURS))
    }

    @Test
    fun theCameraRollIsNotACollection() {
        assertEquals(PhoneVideoVerdict.PERSONAL, PhoneVideos.verdictOf("DCIM/Camera/", TWO_HOURS))
        assertEquals(PhoneVideoVerdict.PERSONAL, PhoneVideos.verdictOf("Camera/", TWO_HOURS))
    }

    @Test
    fun aMessengerSaysSoSeveralFloorsDown() {
        // The leaf here is "WhatsApp Video", but the same chat writes "Media" a level above it and a
        // rule that only read the leaf would let the app rename its way back onto the shelf.
        assertEquals(
            PhoneVideoVerdict.PERSONAL,
            PhoneVideos.verdictOf("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/", TWO_HOURS)
        )
        assertEquals(
            PhoneVideoVerdict.PERSONAL,
            PhoneVideos.verdictOf("Pictures/Instagram/Reels/", TWO_HOURS)
        )
    }

    @Test
    fun aScreenRecordingIsPersonalEvenWhenItRunsLong() {
        assertEquals(
            PhoneVideoVerdict.PERSONAL,
            PhoneVideos.verdictOf("Movies/Screen recordings/", TEN_HOURS)
        )
    }

    @Test
    fun anAppsOwnStorageIsNotThePersonsLibrary() {
        // "Android/data" spans two segments, so it can only ever be read against the whole path —
        // checking it segment by segment is a rule that never fires and never looks wrong.
        assertEquals(
            PhoneVideoVerdict.PERSONAL,
            PhoneVideos.verdictOf("Android/data/com.instagram.android/files/videos/", TWO_HOURS)
        )
        assertEquals(PhoneVideoVerdict.PERSONAL, PhoneVideos.verdictOf("cache/", TWO_HOURS))
    }

    @Test
    fun foldersAreMatchedWithoutCareAboutCase() {
        assertEquals(PhoneVideoVerdict.PERSONAL, PhoneVideos.verdictOf("dcim/CAMERA/", TWO_HOURS))
    }

    @Test
    fun aShortThingIsAClipWhateverFolderHoldsIt() {
        assertEquals(PhoneVideoVerdict.CLIP, PhoneVideos.verdictOf("Movies/", NINETEEN_MINUTES))
        // The boundary itself: twenty minutes is the shortest thing the shelf calls a work.
        assertEquals(PhoneVideoVerdict.COLLECTION, PhoneVideos.verdictOf("Movies/", TWENTY_MINUTES))
    }

    @Test
    fun aFileTheScannerCouldNotReadIsNotWorthOpening() {
        // MediaStore reports 0 for a video whose header it never got through. The player is no likelier
        // to open it than the shelf is to name it.
        assertEquals(PhoneVideoVerdict.CLIP, PhoneVideos.verdictOf("Movies/", 0L))
    }

    @Test
    fun aCameraClipIsRejectedBeforeItsLengthIsEverAsked() {
        // A 12-second video of a cat is personal for two independent reasons, and the folder has to
        // answer first: the count of hidden files is a person's holiday videos, not their runtime.
        assertEquals(PhoneVideoVerdict.PERSONAL, PhoneVideos.verdictOf("DCIM/Camera/", 12_000L))
    }

    @Test
    fun aFilmsOwnFolderIsNotAShelfCopyTwiceOver() {
        // Downloads are filed under `Movies` so the gallery can find them, which is the one rule here
        // that would otherwise let them back in as phone videos. They are already on the shelf, as the
        // Telegram copies they are — a second card for the same film is the failure this declines.
        assertEquals(PhoneVideoVerdict.PERSONAL, PhoneVideos.verdictOf("Movies/Arc Player/", TWO_HOURS))
        // And the rule is about that folder, not about everything under Movies.
        assertEquals(PhoneVideoVerdict.COLLECTION, PhoneVideos.verdictOf("Movies/Downloaded/", TWO_HOURS))
    }

    private companion object {
        const val TWENTY_MINUTES = 20L * 60L * 1000L
        const val NINETEEN_MINUTES = 19L * 60L * 1000L
        const val TWO_HOURS = 120L * 60L * 1000L
        const val TEN_HOURS = 600L * 60L * 1000L
    }
}
