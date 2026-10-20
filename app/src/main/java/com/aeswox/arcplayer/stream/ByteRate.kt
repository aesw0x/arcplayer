package com.aeswox.arcplayer.stream

import java.util.Locale

/**
 * How fast the bytes of one file are arriving, read from the counter that keeps climbing.
 *
 * A speed taken straight from two samples lies in both directions. The counter moves backwards when
 * a download is broken off and pointed at another range, and it leaps when a part already in flight
 * lands all at once; printed as "9 MB/s" either one teaches the viewer that the link is many times
 * faster than it is, which is exactly the reading a wait is being judged by. So an untrustworthy
 * history is thrown away and the answer is no number at all until a clean pair of samples arrives.
 */
class ByteRate {

    private val times = ArrayDeque<Long>()
    private val totals = ArrayDeque<Long>()
    private var lastBytes = -1L

    /**
     * The rate over the recent history in bytes per second, or null when there is not yet an honest
     * one to show.
     */
    fun sample(nowMs: Long, bytes: Long): Double? {
        if (lastBytes < 0L || bytes < lastBytes) {
            startAgain(nowMs, bytes)
            return null
        }
        times.addLast(nowMs)
        totals.addLast(bytes)
        lastBytes = bytes
        // Only the recent past says what the link is doing now, but two samples are always kept so a
        // span to measure exists even when the updates are slow.
        while (times.size > 2 && nowMs - times.first() > WINDOW_MS) {
            times.removeFirst()
            totals.removeFirst()
        }
        val spanMs = nowMs - times.first()
        if (spanMs < MIN_SPAN_MS) return null
        val rate = (totals.last() - totals.first()) * 1000.0 / spanMs
        if (rate > MAX_PLAUSIBLE_BYTES_PER_SECOND) {
            // A jump this big is the counter being rebuilt, not bytes arriving.
            startAgain(nowMs, bytes)
            return null
        }
        return rate
    }

    private fun startAgain(nowMs: Long, bytes: Long) {
        times.clear()
        totals.clear()
        times.addLast(nowMs)
        totals.addLast(bytes)
        lastBytes = bytes
    }

    companion object {
        /** History a rate is read from: long enough to be steady, short enough to follow the link. */
        const val WINDOW_MS = 4_000L

        /** Below this the two samples are one TDLib update apart and the number is noise. */
        const val MIN_SPAN_MS = 1_000L

        /**
         * Past this the "speed" is a counter jumping. The link measured on device runs about 350 KB/s,
         * so this ceiling is twenty times the truth and only catches a rebuild, never a real burst.
         */
        const val MAX_PLAUSIBLE_BYTES_PER_SECOND = 8.0 * 1024.0 * 1024.0

        /** The rate the way a viewer reads it, or null when there is nothing honest to say. */
        fun speedLabel(bytesPerSecond: Double?): String? {
            val rate = bytesPerSecond ?: return null
            return if (rate >= 1024.0 * 1024.0) {
                String.format(Locale.US, "%.1f MB/s", rate / (1024.0 * 1024.0))
            } else {
                String.format(Locale.US, "%.0f KB/s", rate / 1024.0)
            }
        }
    }
}
