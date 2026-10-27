package com.aeswox.arcplayer.cloud

import kotlin.math.roundToInt

/**
 * The one line of arithmetic behind every download bar — the notification's and the Downloads page's.
 *
 * Pure because a bar is a promise. The player's own ring has the same problem and the same answer: what
 * a person reads off the shade of the phone cannot be checked against a log line afterwards, so the only
 * honest place to decide it is a function a test can hold.
 *
 * Two decisions in here are worth their reasons:
 * - The fraction is taken across every film still arriving rather than per film, because one bar answers
 *   the only question a lock screen asks — when is this finished. Summing the *promised* lengths of all
 *   of them, including the one Telegram has not started answering for, is what keeps that number
 *   truthful; counting only the files that report back would open at 100 %.
 * - A film with no length promised contributes nothing to either side of the sum. Counting it as zero
 *   landed would freeze the bar under a download that is moving; counting it as arrived would lie the
 *   other way.
 */
object DownloadNotices {

    /** What the ongoing notification says, summed over the films this app is still fetching. */
    data class Running(
        val films: Int,
        /** The oldest request, which is the one the bar is mostly made of. */
        val leadingTitle: String,
        val doneBytes: Long,
        val totalBytes: Long,
    ) {
        val fraction: Float
            get() = progressOf(totalBytes, doneBytes)

        val percent: Int
            get() = (fraction * 100f).roundToInt()
    }

    /**
     * The state worth showing, or null when nothing is being fetched.
     *
     * [titleOf] resolves a shelf stable id to the name the viewer posted it under, because a promise
     * records where a film lives rather than what it is called — and "Keeping 3 films" is a better
     * sentence than "Keeping -4718099455423".
     */
    fun running(
        entries: Map<Long, DownloadEntry>,
        progress: Map<Int, CloudProgress>,
        titleOf: (Long) -> String,
    ): Running? {
        val live = entries.values.filterNot { it.isComplete }
        if (live.isEmpty()) return null
        var done = 0L
        var total = 0L
        for (entry in live) {
            val promised = entry.sizeBytes
            if (promised <= 0L) continue
            done += landedBytes(promised, progress[entry.fileId])
            total += promised
        }
        val leading = live.first()
        return Running(
            films = live.size,
            leadingTitle = titleOf(leading.stableId),
            doneBytes = done,
            totalBytes = total,
        )
    }

    /**
     * How much of one film is on the device, of the length the shelf promised.
     *
     * The pair the player's own ring reads: every byte TDLib has taken for the file, or the window's
     * prefix if that somehow stands higher, and never more than was promised. The clamp matters — a
     * record can report a length it inherited from a different copy of the same bytes, and a bar over
     * full is a bar that stops moving at the wrong end.
     *
     * Public because the row a film wears on the Downloads page has to answer the same question as the
     * notification does, and two answers that disagree is a bug report nobody can settle.
     */
    fun landedBytes(promised: Long, facts: CloudProgress?): Long =
        if (promised <= 0L || facts == null) 0L
        else facts.downloadedBytes.coerceAtLeast(facts.bytesOnDisk).coerceAtMost(promised)

    /**
     * The bar fraction for one film rather than the whole shade.
     *
     * A film the shelf has no length for reads as 0 rather than 1, because a row that opens full is the
     * exact lie the notification was first written to stop telling.
     */
    fun progressOf(promised: Long, landed: Long): Float =
        if (promised <= 0L) 0f else (landed.toFloat() / promised.toFloat()).coerceIn(0f, 1f)

    /**
     * Whether the phone would show a different number.
     *
     * TDLib pushes an update for every packet group, which on a fast link is several a second, and each
     * one would otherwise rebuild and repost a notification the viewer cannot tell apart from the last.
     * The threshold is the size of the smallest digit in the readout.
     */
    fun needsRepaint(previous: Running?, next: Running): Boolean {
        if (previous == null) return true
        if (previous.films != next.films) return true
        if (previous.leadingTitle != next.leadingTitle) return true
        if (previous.percent != next.percent) return true
        return (next.doneBytes - previous.doneBytes).let { it < 0 || it >= REPAINT_BYTES }
    }

    /** Ten megabytes: the least [formatFileSize] can show changed in its gigabyte form. */
    private const val REPAINT_BYTES = 10L * 1024L * 1024L
}
