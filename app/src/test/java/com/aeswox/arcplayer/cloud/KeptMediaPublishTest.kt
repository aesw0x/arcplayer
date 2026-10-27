package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind "did that copy bring the whole film".
 *
 * Only the counting is testable here — the copy itself needs a content resolver — and the counting is
 * exactly where a real download was lost. A film that had arrived in full was thrown away because the
 * check asked whether the byte counts *agreed* rather than whether bytes were *lost*: the source was
 * still being written while it was copied, so the copy came out longer than the length read a moment
 * earlier, and a growing file was read as a shrinking one. Every case below is one clause of that
 * decision, with the first being the film itself.
 *
 * The sizes are the real ones from the device: 3,084,048,495 bytes, the length the shelf promised for
 * the film that survived only after the check stopped demanding equality.
 */
class KeptMediaPublishTest {

    private val film = 3_084_048_495L
    private val mb = 1024 * 1024L

    private fun outcome(source: Long, copied: Long, promised: Long = film) =
        KeptMedia.outcomeFor(true, source, copied, promised)

    @Test
    fun aSourceThatGrewWhileItWasCopiedIsStillTheWholeFilm() {
        // Telegram was still writing the tail when the copy began, so the row ends up with more than
        // the source claimed at the start. This is the case the old `copied != from` called a failure.
        assertEquals(PublishOutcome.Held, outcome(source = film - 28 * mb, copied = film))
    }

    @Test
    fun fewerBytesOutThanTheSourceHeldIsALostCopy() {
        // The only direction that really means something went wrong mid-copy.
        assertEquals(PublishOutcome.LostInCopy, outcome(source = film, copied = film - mb))
    }

    @Test
    fun aFaithfulCopyOfAFileThatStoppedShortIsNotTheFilm() {
        // A stretched shell: a few megabytes of bytes behind a length that claims the whole feature.
        // Copying it loses nothing, and it is still not a film, so the promise is what refuses it.
        assertEquals(PublishOutcome.ShortOfPromise, outcome(source = 4 * mb, copied = 4 * mb))
    }

    @Test
    fun aCopyThatLostNothingButArrivedAWholeShortIsRefusedByThePromise() {
        // The other side of the same rule: a faithful copy of a file that never finished is not a film,
        // and the slack below has to be small enough that "still fetching" never fits inside it.
        assertEquals(PublishOutcome.ShortOfPromise, outcome(source = film / 2, copied = film / 2))
    }

    @Test
    fun aCopyInsideTheRoundingSlackCountsAsArrived() {
        // The slack [DownloadLedger.hasEveryByte] allows is what lets a promote start while the last
        // percent is still landing. Tightening it to exact equality would bring this bug back.
        assertEquals(PublishOutcome.Held, outcome(source = film - 2 * mb, copied = film - mb))
    }

    @Test
    fun nothingComingOutIsNotReadAsAShortedFile() {
        // Both an empty source and a refused stream end here, and the distinction belongs in the log
        // line beside the numbers rather than in a rule that has to hold either way.
        assertEquals(PublishOutcome.NothingReadable, outcome(source = 0L, copied = 0L))
        assertEquals(PublishOutcome.NothingReadable, outcome(source = film, copied = -1L))
    }

    @Test
    fun aHandleThatDoesNotExistOutranksEveryByteCount() {
        // The caller has no source to measure against yet, so its counts are placeholders and the
        // handle is the only true thing about the call.
        assertEquals(PublishOutcome.NoHandle, KeptMedia.outcomeFor(false, 0L, -1L, film))
    }
}
