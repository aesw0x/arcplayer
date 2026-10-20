package com.aeswox.arcplayer.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Poster artwork, fetched once and kept.
 *
 * Written by hand because the app carries no image library, and because a poster has to be small
 * before it reaches the grid: a catalogue serves 2000 px art, the widest card here is under 500 px,
 * and decoding the full size is exactly the kind of work that turns this grid's known scroll jank
 * into dropped frames. Bytes go to disk as they arrived and are decoded down on the way out, so a
 * re-launch never asks the network for a cover it already has.
 */
object PosterCache {

    /** Wide enough for the largest card the grid can ask for. */
    const val TARGET_WIDTH_PX = 560

    /** Wide enough for a wordmark drawn across a landscape phone, which is the widest thing here. */
    const val WORDMARK_WIDTH_PX = 1_100

    private val maxKilobytes = (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt().coerceAtLeast(12 * 1024)

    private val decodeSemaphore = Semaphore(permits = 4)

    private val memory = object : LruCache<String, Bitmap>(maxKilobytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /**
     * The poster for one url, or null when it is not available yet.
     *
     * Null rather than a failed load: the card keeps the small inline frame Telegram already sent and
     * the next pass over the same chat gets another try.
     */
    suspend fun bitmap(context: Context, url: String?, targetWidthPx: Int = TARGET_WIDTH_PX): Bitmap? {
        if (url.isNullOrBlank()) return null
        memory.get(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            memory.get(url)?.let { return@withContext it }
            decodeSemaphore.withPermit {
                coroutineContext.ensureActive()
                val file = diskFile(context, url)
                if (!file.exists()) runCatching { download(url, file) }

                val poster = runCatching { decode(file, targetWidthPx) }.getOrNull() ?: return@withPermit null
                coroutineContext.ensureActive()
                poster.prepareToDraw()
                memory.put(url, poster)
                poster
            }
        }
    }

    /** Bytes first, downsampled second: re-encoding a JPEG to store a poster only costs quality. */
    private fun download(url: String, into: File) {
        val bytes = HttpJson.getBytes(url) ?: return
        if (bytes.size < MIN_REAL_IMAGE_BYTES) return
        into.parentFile?.mkdirs()
        into.writeBytes(bytes)
    }

    /** A redirect page or an error body is small; anything under this is not the artwork. */
    private const val MIN_REAL_IMAGE_BYTES = 2048

    private fun decode(file: File, targetWidthPx: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, targetWidthPx) }
        )
    }

    /** Powers of two, because that is what the decoder does cheaply. */
    fun sampleSizeFor(widthPx: Int, targetPx: Int): Int {
        var sample = 1
        while (widthPx / (sample * 2) >= targetPx) sample *= 2
        return sample
    }

    /** A file name that survives any url, including the query strings Apple uses. */
    private fun diskFile(context: Context, url: String): File =
        File(File(context.applicationContext.cacheDir, "posters"), hash(url) + ".img")

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
