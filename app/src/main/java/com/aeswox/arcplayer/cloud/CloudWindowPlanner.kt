package com.aeswox.arcplayer.cloud

import com.aeswox.arcplayer.stream.DeliveredRun
import com.aeswox.arcplayer.stream.StreamWindow

/**
 * What TDLib currently tells us about one file's bytes on disk.
 *
 * A direct reading of `TdApi.LocalFile`, kept as plain numbers so the decision below can be
 * tested without a Telegram connection.
 */
data class WindowFacts(
    val fileSizeBytes: Long,
    val downloadOffset: Long,
    val downloadedPrefixSize: Long,
    val isDownloadingActive: Boolean,
    val isDownloadingCompleted: Boolean,
    /**
     * Every byte this file has had, including ranges outside the window described by
     * [downloadOffset] and [downloadedPrefixSize]. The only honest way to tell whether a running
     * fetch is moving at all.
     */
    val downloadedSizeBytes: Long = 0L,
)

sealed class WindowCommand {
    /** Nothing to do: the read position is inside a live window, or the file is whole. */
    data object None : WindowCommand()

    /** Ask TDLib for [limit] bytes starting at [offset]. */
    data class Fetch(val offset: Long, val limit: Long) : WindowCommand()
}

/**
 * Decides when to move TDLib's download window for a video that is being watched as it arrives.
 *
 * The rule is deliberately boring: leave a running download alone, and only re-point it when the
 * player has eaten most of what is ahead of it, or has jumped into a part of the film that has never
 * arrived. Every re-point costs a round trip, so a wrong "just refresh it" here shows up on screen as
 * a stall — and the bytes already on disk are what tells a rewind that needs one from a rewind that
 * does not.
 *
 * All the arithmetic that does not depend on Telegram lives in [StreamWindow]; this is the thin
 * layer that turns those answers into a request.
 */
object CloudWindowPlanner {

    /** Re-point the window once less than this many bytes remain ahead of the read position. */
    const val LOW_WATER_BYTES = 6L * 1024 * 1024

    /** How much to ask for in one go. Big enough to survive a burst of slow network. */
    const val DEFAULT_WINDOW_BYTES = 64L * 1024 * 1024

    /**
     * Where the play head sits inside the file, guessed from time.
     *
     * Telegram hands over bytes and the player reports milliseconds, with no cheap map between the
     * two for an arbitrary MP4, so this assumes a roughly constant bitrate. It only has to be
     * close: [LOW_WATER_BYTES] of slack sits ahead of the read position, and a seek is answered by
     * a fresh request either way.
     */
    fun readPositionBytes(positionMs: Long, durationMs: Long, sizeBytes: Long): Long {
        if (positionMs <= 0L || durationMs <= 0L || sizeBytes <= 0L) return 0L
        return if (positionMs >= durationMs) sizeBytes else sizeBytes * positionMs / durationMs
    }

    fun plan(
        readPositionBytes: Long,
        facts: WindowFacts,
        alreadyDelivered: DeliveredRun = DeliveredRun.NONE,
        windowBytes: Long = DEFAULT_WINDOW_BYTES
    ): WindowCommand {
        if (facts.isDownloadingCompleted) return WindowCommand.None
        if (readPositionBytes < 0L) return WindowCommand.None

        val size = facts.fileSizeBytes
        val windowEnd = facts.downloadOffset + facts.downloadedPrefixSize

        // A ranged fetch reports where it started in `download_offset` but keeps
        // `downloaded_prefix_size` at zero until bytes land, so an in-flight window has to be judged
        // by what it asked for rather than by what has arrived. Measured against the empty prefix,
        // every player tick cancels the transfer it started a second ago.
        val liveWindowLength =
            if (facts.isDownloadingActive) maxOf(facts.downloadedPrefixSize, windowBytes)
            else facts.downloadedPrefixSize

        if (StreamWindow.shouldReaim(
                position = readPositionBytes,
                windowStart = facts.downloadOffset,
                windowLength = liveWindowLength,
                active = facts.isDownloadingActive,
                completed = false,
                alreadyDelivered = alreadyDelivered
            )
        ) {
            return fetchAt(StreamWindow.reaimStartFor(readPositionBytes), windowBytes, size)
        }

        // Inside a live window with room ahead: let it finish rather than interrupting a transfer
        // that is already carrying us forward.
        if (facts.isDownloadingActive) return WindowCommand.None

        // Nothing is transferring, so the only room ahead worth trusting is bytes that have landed.
        // A cancelled ranged fetch leaves its span parked wherever it pointed with an empty prefix,
        // and that end is then a promise about a place the player is not. Measured on device: a head
        // at byte 2606 with 2 MB on disk was told 2.3 GB lay ahead of it, on a window that had been
        // cancelled and was fetching nothing.
        val groundEnd = if (facts.downloadedPrefixSize > 0L) windowEnd else alreadyDelivered.to

        if (groundEnd - readPositionBytes >= LOW_WATER_BYTES) return WindowCommand.None

        // Exhausted and idle. Push the window on, or if the file really is smaller than the
        // window, ask for the whole of what is left.
        if (size in 1 until groundEnd) return WindowCommand.None
        return fetchAt(groundEnd, windowBytes, size)
    }

    private fun fetchAt(offset: Long, windowBytes: Long, size: Long): WindowCommand {
        val roomLeft = if (size > 0L) size - offset else windowBytes
        if (roomLeft <= 0L) return WindowCommand.None
        val limit = minOf(windowBytes, roomLeft)
        // TDLib reads a limit of 0 as "until the end", which on a long file means "the whole rest"
        // and turns a seek near the tail into a full re-download. Never send it.
        if (limit <= 0L) return WindowCommand.None
        return WindowCommand.Fetch(offset, limit)
    }
}
