package com.aeswox.arcplayer.tvserver

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Where a stream's bytes come from.
 *
 * Deliberately tiny: the only thing the HTTP layer is allowed to assume is that [sizeBytes] is the size
 * the viewer was *told* and that [readAt] either returns bytes or says there are no more. Everything
 * about Telegram, the media catalog and how a film got onto this device lives behind this line.
 */
interface ByteSource : AutoCloseable {
    val sizeBytes: Long
    val contentType: String

    /** Reads up to [len] bytes at [offset], returning -1 once [offset] reaches the end. */
    fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int
}

/**
 * A film whose every byte is already on this device, named by a path.
 *
 * On this server that is only ever a file pushed in to test with; a kept film comes from
 * [CatalogHandleSource] instead. Naming the distinction in the types rather than in a flag is the point:
 * with nothing left to arrive, a read past the end is the honest end of the file, and a byte that does not
 * exist costs a closed connection rather than a zero. Fabricated bytes are the one failure a TV player
 * cannot recover from, and neither of these two sources can make any.
 */
class CompleteFileSource private constructor(
    private val handle: RandomAccessFile,
    override val contentType: String,
) : ByteSource {

    override val sizeBytes: Long = handle.length()

    /**
     * Synchronized against [close] rather than against itself.
     *
     * Two readers never share a source, but a newer request for the same film closes this one from the
     * thread that accepted it while this thread is still inside a read.
     */
    @Synchronized
    override fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int {
        if (offset >= sizeBytes) return -1
        handle.seek(offset)
        return handle.read(buffer, off, minOf(len.toLong(), sizeBytes - offset).toInt())
    }

    @Synchronized
    override fun close() {
        runCatching { handle.close() }
    }

    companion object {
        /** A file named by path; one that reports no bytes behind it is not a film, so hand it back. */
        fun path(file: File, contentType: String): CompleteFileSource? {
            if (!file.isFile || !file.canRead()) return null
            val source = runCatching { CompleteFileSource(RandomAccessFile(file, "r"), contentType) }.getOrNull()
            if (source != null && source.sizeBytes > 0L) return source
            source?.close()
            return null
        }
    }
}

/**
 * The bytes behind a row of the device's media catalog, which is where every kept film lives.
 *
 * A handle rather than a path on purpose: the row outlives any name the viewer gives the file in a gallery
 * app, which is the reason [com.aeswox.arcplayer.cloud.KeptMedia] hands out handles at all.
 *
 * There is no `RandomAccessFile` for a handle — its constructors want a path, and no path is promised here.
 * So the seeking drops one level to the descriptor's own file position, which means a provider that answers
 * with a pipe rather than a file fails the seek, and the viewer gets a closed stream instead of a film
 * quietly assembled out of nothing.
 */
class CatalogHandleSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val baseOffset: Long,
    override val sizeBytes: Long,
    override val contentType: String,
) : ByteSource {

    private val file = descriptor.fileDescriptor

    @Synchronized
    override fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int {
        if (offset >= sizeBytes) return -1
        val want = minOf(len.toLong(), sizeBytes - offset).toInt()
        if (want <= 0) return -1

        val absolute = baseOffset + offset
        val seeked = runCatching { Os.lseek(file, absolute, OsConstants.SEEK_SET) }
        seeked.exceptionOrNull()?.let { Log.w(TAG, "the catalog handle refuses to seek", it) }
        if (seeked.getOrNull() != absolute) return -1

        val read = runCatching { Os.read(file, buffer, off, want) }
        read.exceptionOrNull()?.let { Log.w(TAG, "a read from the catalog handle failed", it) }
        return read.getOrDefault(-1)
    }

    @Synchronized
    override fun close() {
        runCatching { descriptor.close() }
    }

    /**
     * The row's bytes, bounded to exactly the region the catalog says this row owns.
     *
     * A handle can point into the middle of a larger container, so the offset is added to every seek and
     * the length is the size both the library and the HTTP headers quote — never the size of whatever file
     * happens to sit underneath. `openAssetFileDescriptor` is asked rather than `openFileDescriptor`
     * because it is the one call that reports that region; where it reports no length, `stat` answers
     * instead. Nothing comes back as a film when neither can give a positive number to serve.
     */
    companion object {
        private const val TAG = "ArcTV-Stream"

        fun open(resolver: ContentResolver, handle: String, contentType: String): CatalogHandleSource? {
            val uri = runCatching { Uri.parse(handle) }.getOrNull() ?: return null
            val region = runCatching { resolver.openAssetFileDescriptor(uri, "r") }.getOrNull() ?: return null
            val descriptor = region.parcelFileDescriptor ?: return null

            val start = region.startOffset
            val length = if (region.length >= 0) region.length else descriptor.statSize - start
            if (length > 0) return CatalogHandleSource(descriptor, start, length, contentType)

            runCatching { descriptor.close() }
            return null
        }
    }
}

/** A body limited to `[start, endExclusive)`, pulled lazily from its source. */
class RangeInputStream(
    private val source: ByteSource,
    private val start: Long,
    private val endExclusive: Long,
) : InputStream() {

    private var position = start

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) <= 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        if (position >= endExclusive) return -1
        val want = minOf(len.toLong(), endExclusive - position).toInt()
        if (want <= 0) return -1
        val read = source.readAt(position, buffer, off, want)
        if (read <= 0) return -1
        position += read
        return read
    }

    override fun close() {
        source.close()
    }
}

sealed interface RangeResult {
    data class Satisfiable(val start: Long, val endExclusive: Long) : RangeResult
    data object Unsatisfiable : RangeResult
}

/**
 * Parses a single-range `Range` header.
 *
 * Multi-range requests are rejected rather than half-implemented: no player sends them, and a
 * `multipart/byteranges` body is far more code than it is worth. Loose parsing is the worse option —
 * serving one range where the demuxer expects two reads as a corrupt film rather than a server bug.
 */
object RangeHeader {

    fun parse(header: String?, size: Long): RangeResult {
        if (header.isNullOrBlank()) return RangeResult.Satisfiable(0, size)
        val text = header.trim()
        if (!text.startsWith("bytes=", ignoreCase = true)) return RangeResult.Unsatisfiable

        val spec = text.substring(6).trim()
        if (spec.isEmpty() || spec.contains(',')) return RangeResult.Unsatisfiable

        val dash = spec.indexOf('-')
        if (dash < 0) return RangeResult.Unsatisfiable

        val startText = spec.substring(0, dash).trim()
        val endText = spec.substring(dash + 1).trim()

        if (size <= 0) return RangeResult.Unsatisfiable

        if (startText.isEmpty()) {
            // Suffix form: the last N bytes.
            val count = endText.toLongOrNull() ?: return RangeResult.Unsatisfiable
            if (count <= 0) return RangeResult.Unsatisfiable
            return RangeResult.Satisfiable(maxOf(0, size - count), size)
        }

        val start = startText.toLongOrNull() ?: return RangeResult.Unsatisfiable
        if (start < 0 || start >= size) return RangeResult.Unsatisfiable

        val endInclusive = if (endText.isEmpty()) {
            size - 1
        } else {
            endText.toLongOrNull() ?: return RangeResult.Unsatisfiable
        }
        if (endInclusive < start) return RangeResult.Unsatisfiable

        return RangeResult.Satisfiable(start, minOf(endInclusive, size - 1) + 1)
    }
}
