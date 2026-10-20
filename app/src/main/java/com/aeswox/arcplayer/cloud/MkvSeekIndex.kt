package com.aeswox.arcplayer.cloud

import java.io.ByteArrayOutputStream

/**
 * Reads the seek index a Matroska file carries for itself.
 *
 * A cloud video is watched while it arrives, so the player is pointed at a file that is mostly
 * holes, and telling Telegram which bytes to carry next needs a moment of the film turned into a
 * byte offset. A constant-bitrate guess drifts by well over a window's worth of bytes on a
 * variable-bitrate encode, and a seek that lands outside what has arrived does not recover: the
 * player reads a hole as the end of the movie. The cue table is the answer the player itself uses,
 * and it is a few tens of kilobytes at the tail of a two and a half hour film.
 *
 * Two reads. The header's seek head names where the cue table starts; the table is then fetched and
 * turned into a time to byte lookup.
 *
 * Pure byte arithmetic: no Android, no Telegram, so it runs on the JVM test box.
 */
object MkvSeekIndex {

    /** Where the pieces of the index live, as read out of the file's first few kilobytes. */
    data class Layout(val segmentDataStart: Long, val cuesOffset: Long, val timecodeScaleNs: Long)

    /**
     * Timecodes and the byte each one starts a cluster at, both ascending.
     *
     * [byteFor] answers between the points by assuming the bitrate holds inside one cluster, which it
     * does to within a cluster's size — accuracy a download window does not need to beat.
     */
    class CueTable internal constructor(val timesMs: LongArray, val clusterBytes: LongArray) {

        val isEmpty: Boolean get() = timesMs.size < 2

        /**
         * The byte to start reading from to show [positionMs], or null when this table cannot answer
         * for that moment. A table built for a whole film answers everywhere inside it.
         */
        fun byteFor(positionMs: Long, sizeBytes: Long): Long? {
            if (isEmpty || sizeBytes <= 0L) return null
            if (positionMs <= timesMs[0]) return clusterBytes[0]
            val last = timesMs.size - 1
            if (positionMs >= timesMs[last]) return null
            var low = 0
            var high = last
            while (low + 1 < high) {
                val mid = (low + high) / 2
                if (timesMs[mid] <= positionMs) low = mid else high = mid
            }
            val startByte = clusterBytes[low]
            val endByte = clusterBytes[low + 1]
            val spanTime = timesMs[low + 1] - timesMs[low]
            if (spanTime <= 0L || endByte <= startByte) return startByte
            return startByte + (positionMs - timesMs[low]) * (endByte - startByte) / spanTime
        }
    }

    /**
     * The header of a Matroska file: enough of it to find the cue table.
     *
     * Null for anything that is not Matroska. A file whose header has not landed yet reads as zeroes
     * and also answers null, so a caller that cares should retry rather than give up on the film.
     */
    fun readLayout(head: ByteArray, headLength: Int = head.size): Layout? {
        val ebml = readElement(head, 0, headLength) ?: return null
        if (ebml.id != EBML_HEADER) return null
        val segment = readElement(head, nextOf(ebml, headLength), headLength) ?: return null
        if (segment.id != SEGMENT) return null
        val segmentDataStart = segment.dataStart

        var timecodeScaleNs = DEFAULT_TIMECODE_SCALE_NS
        var cuesRelative = -1L
        var child = readElement(head, segmentDataStart, headLength)
        while (child != null) {
            when {
                child.id == SEEK_HEAD ->
                    cuesRelative = readCuesOffset(head, child, headLength) ?: cuesRelative

                child.id == INFO ->
                    timecodeScaleNs = readTimecodeScale(head, child, headLength) ?: timecodeScaleNs

                // The first cluster ends the metadata; anything still missing after it is missing.
                child.id == CLUSTER -> return layoutOf(segmentDataStart.toLong(), cuesRelative, timecodeScaleNs)
            }
            val next = nextOf(child, headLength)
            if (next <= child.dataStart) break
            child = readElement(head, next, headLength)
        }
        return layoutOf(segmentDataStart.toLong(), cuesRelative, timecodeScaleNs)
    }

    private fun layoutOf(segmentDataStart: Long, cuesRelative: Long, timecodeScaleNs: Long): Layout? =
        if (cuesRelative < 0L) null else Layout(segmentDataStart, segmentDataStart + cuesRelative, timecodeScaleNs)

    private fun readCuesOffset(head: ByteArray, seekHead: Element, headLength: Int): Long? {
        val end = endOf(seekHead, headLength)
        var entry = readElement(head, seekHead.dataStart, end)
        while (entry != null) {
            if (entry.id == SEEK) {
                readSeekPosition(head, entry, end)?.let { return it }
            }
            val next = nextOf(entry, end)
            if (next <= entry.dataStart) break
            entry = readElement(head, next, end)
        }
        return null
    }

    /** One seek entry: which element it points at, and where. */
    private fun readSeekPosition(head: ByteArray, seek: Element, end: Int): Long? {
        var child = readElement(head, seek.dataStart, end)
        var pointsAt = -1L
        while (child != null) {
            when {
                child.id == SEEK_ID ->
                    pointsAt = if (child.size == 4L) readUInt(head, child.dataStart, 4) else -1L

                child.id == SEEK_POSITION && pointsAt == CUES ->
                    return readUInt(head, child.dataStart, child.size.toInt())
            }
            val next = nextOf(child, end)
            if (next <= child.dataStart) break
            child = readElement(head, next, end)
        }
        return null
    }

    private fun readTimecodeScale(head: ByteArray, info: Element, headLength: Int): Long? {
        val end = endOf(info, headLength)
        var child = readElement(head, info.dataStart, end)
        while (child != null) {
            if (child.id == TIMECODE_SCALE) {
                return readUInt(head, child.dataStart, child.size.toInt()).takeIf { it > 0L }
            }
            val next = nextOf(child, end)
            if (next <= child.dataStart) break
            child = readElement(head, next, end)
        }
        return null
    }

    /**
     * Turn a fetched cue table into a lookup.
     *
     * Null is what keeps a file with an unreadable index on the bitrate guess: a table is only
     * returned when it holds at least two ascending points, since one cannot bracket a moment.
     */
    fun readCueTable(cues: ByteArray, layout: Layout): CueTable? {
        val cuesElement = readElement(cues, 0, cues.size) ?: return null
        if (cuesElement.id != CUES || cuesElement.size <= 0L) return null
        val end = endOf(cuesElement, cues.size)
        val times = ArrayList<Long>()
        val positions = ArrayList<Long>()
        var point = readElement(cues, cuesElement.dataStart, end)
        while (point != null) {
            if (point.id == CUE_POINT) {
                // Bounded by the point, not by the table. Handing a child scan the whole table's end
                // makes it walk every later cue as well, which is quadratic in the number of cues and
                // was measured on the phone: five ANRs, every one with the main thread inside this
                // loop, on a film with thousands of cue points.
                readCuePoint(cues, point, endOf(point, end))?.let { (timecode, cluster) ->
                    // Cue times count in TimecodeScale units, not milliseconds.
                    val timeMs = timecode * layout.timecodeScaleNs / 1_000_000L
                    // Cues are written in time order. One that is not later than its neighbour is a
                    // timecode scale finer than a millisecond, and a lookup that is not strictly
                    // increasing cannot be binary searched.
                    if (times.isEmpty() || timeMs > times.last()) {
                        times += timeMs
                        positions += layout.segmentDataStart + cluster
                    }
                }
            }
            val next = nextOf(point, end)
            if (next <= point.dataStart) break
            point = readElement(cues, next, end)
        }
        if (times.size < 2) return null
        // Bytes have to climb with time as well; two that do not means these were not cue points at
        // all, and a lookup built on them would move the download window somewhere invented.
        for (i in 1 until positions.size) {
            if (positions[i] < positions[i - 1]) return null
        }
        return CueTable(times.toLongArray(), positions.toLongArray())
    }

    private fun readCuePoint(cues: ByteArray, point: Element, end: Int): Pair<Long, Long>? {
        var time = -1L
        var cluster = -1L
        var child = readElement(cues, point.dataStart, end)
        while (child != null) {
            when (child.id) {
                CUE_TIME -> time = readUInt(cues, child.dataStart, child.size.toInt())
                CUE_TRACK_POSITIONS ->
                    cluster = readClusterPosition(cues, child, endOf(child, end)) ?: cluster
            }
            val next = nextOf(child, end)
            if (next <= child.dataStart) break
            child = readElement(cues, next, end)
        }
        return if (time >= 0L && cluster >= 0L) time to cluster else null
    }

    private fun readClusterPosition(cues: ByteArray, positions: Element, end: Int): Long? {
        var child = readElement(cues, positions.dataStart, end)
        while (child != null) {
            if (child.id == CUE_CLUSTER_POSITION) return readUInt(cues, child.dataStart, child.size.toInt())
            val next = nextOf(child, end)
            if (next <= child.dataStart) break
            child = readElement(cues, next, end)
        }
        return null
    }

    // --- EBML primitives ---------------------------------------------------------------

    /** An element's id, its payload size, and where that payload starts. */
    private class Element(val id: Long, val size: Long, val dataStart: Int)

    /** Where this element's payload ends, clamped to the buffer that holds it. */
    private fun endOf(element: Element, limit: Int): Int =
        minOf(element.dataStart.toLong() + element.size, limit.toLong()).toInt()

    /** The element after this one, or one past the bound when its payload was truncated. */
    private fun nextOf(element: Element, limit: Int): Int = endOf(element, limit)

    /**
     * The element that starts at [at], or null when those bytes are not one.
     *
     * A payload is allowed to run past [limit]: that is what a Segment carrying a film looks like in
     * a buffer that holds only its header. Callers bound the traversal they care about by passing an
     * enclosing element's own end.
     */
    private fun readElement(bytes: ByteArray, at: Int, limit: Int): Element? {
        if (at < 0 || at >= limit || at >= bytes.size) return null
        val idLength = vintLength(bytes[at].toInt() and 0xFF)
        if (idLength == 0 || idLength > 4) return null
        val sizeAt = at + idLength
        if (sizeAt >= limit || sizeAt >= bytes.size) return null
        val sizeLength = vintLength(bytes[sizeAt].toInt() and 0xFF)
        if (sizeLength == 0 || sizeAt + sizeLength > limit || sizeAt + sizeLength > bytes.size) return null
        var id = 0L
        for (i in 0 until idLength) id = (id shl 8) or (bytes[at + i].toLong() and 0xFF)
        var size = (bytes[sizeAt].toLong() and 0xFF) and (0xFFL shr sizeLength)
        for (i in 1 until sizeLength) size = (size shl 8) or (bytes[sizeAt + i].toLong() and 0xFF)
        // All ones is EBML's "length unknown", which cannot be walked to a next element. Anything
        // above a terabyte is a misread, and would send the download window nowhere near the film.
        if (size < 0L || size > MAX_ELEMENT_SIZE) return null
        return Element(id, size, sizeAt + sizeLength)
    }

    private fun readUInt(bytes: ByteArray, at: Int, length: Int): Long {
        if (length <= 0 || length > 8 || at < 0 || at + length > bytes.size) return -1L
        var value = 0L
        for (i in 0 until length) value = (value shl 8) or (bytes[at + i].toLong() and 0xFF)
        return value
    }

    /** How many bytes a variable size integer takes: the position of its leading one bit. */
    private fun vintLength(first: Int): Int = when {
        first and 0x80 != 0 -> 1
        first and 0x40 != 0 -> 2
        first and 0x20 != 0 -> 3
        first and 0x10 != 0 -> 4
        first and 0x08 != 0 -> 5
        first and 0x04 != 0 -> 6
        first and 0x02 != 0 -> 7
        first and 0x01 != 0 -> 8
        else -> 0
    }

    private const val EBML_HEADER = 0x1A45DFA3L
    private const val SEGMENT = 0x18538067L
    private const val SEEK_HEAD = 0x114D9B74L
    private const val INFO = 0x1549A966L
    private const val TIMECODE_SCALE = 0x2AD7B1L
    private const val CLUSTER = 0x1F43B675L
    private const val CUES = 0x1C53BB6BL
    private const val CUE_POINT = 0xBBL
    private const val CUE_TIME = 0xB3L
    private const val CUE_TRACK_POSITIONS = 0xB7L
    private const val CUE_CLUSTER_POSITION = 0xF1L

    private const val SEEK = 0x4DBBL
    private const val SEEK_ID = 0x53ABL
    private const val SEEK_POSITION = 0x53ACL

    private const val MAX_ELEMENT_SIZE = 1L shl 40
    private const val DEFAULT_TIMECODE_SCALE_NS = 1_000_000L

    /**
     * Writes elements the way the reader expects to find them, so the tests describe a real file
     * layout instead of a hand-tuned byte array.
     */
    class ElementBuilder {
        private val out = ByteArrayOutputStream()

        fun raw(id: Long, payload: ByteArray) {
            writeId(id)
            writeSize(payload.size.toLong())
            out.write(payload)
        }

        fun uint(id: Long, value: Long, length: Int = smallestUIntLength(value)) {
            val payload = ByteArray(length)
            for (i in 0 until length) payload[i] = ((value shr (8 * (length - 1 - i))) and 0xFF).toByte()
            raw(id, payload)
        }

        fun container(id: Long, nested: ElementBuilder.() -> Unit) {
            raw(id, ElementBuilder().apply(nested).toByteArray())
        }

        fun toByteArray(): ByteArray = out.toByteArray()

        private fun writeId(id: Long) {
            val length = when {
                id and 0xFF000000L != 0L -> 4
                id and 0xFF0000L != 0L -> 3
                id and 0xFF00L != 0L -> 2
                else -> 1
            }
            for (i in 0 until length) out.write(((id shr (8 * (length - 1 - i))) and 0xFF).toInt())
        }

        private fun writeSize(size: Long) {
            val length = when {
                size < 0x7FL -> 1
                size < 0x3FFFL -> 2
                size < 0x1FFFFFL -> 3
                size < 0x0FFFFFFFL -> 4
                else -> 5
            }
            out.write(((size shr (8 * (length - 1))) or (0x80L shr (length - 1))).toInt() and 0xFF)
            for (i in length - 2 downTo 0) out.write(((size shr (8 * i)) and 0xFF).toInt())
        }

        private fun smallestUIntLength(value: Long): Int = when {
            value <= 0xFFL -> 1
            value <= 0xFFFFL -> 2
            value <= 0xFFFFFFL -> 3
            else -> 4
        }
    }
}
