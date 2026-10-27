package com.aeswox.arcplayer.cloud

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What one offer of a finished file to the catalog turned out to be.
 *
 * There were five ways out of the old copy and every one of them was the same `false`, which meant a
 * viewer seeing "no room to save it" on a device with 300 GB free had nothing to check and neither did
 * anybody reading the logs. Each name here is one clause of the copy, and the numbers behind it go into
 * the log line beside it.
 */
enum class PublishOutcome {
    /** Every promised byte is in the row and the row is published. The film is kept. */
    Held,

    /** The handle the ledger wrote down is not a row this app can open. */
    NoHandle,

    /** The copy wrote nothing, which is the source being gone or the row refusing the stream. */
    NothingReadable,

    /** Fewer bytes came out of the row than the source held when the copy started. */
    LostInCopy,

    /** A faithful copy of a source that is not the whole film. */
    ShortOfPromise,

    /** The bytes are all in the row, and the catalog would not take the pending flag off it. */
    CatalogRefused,
}

/**
 * The films this app is keeping, filed where the rest of the phone can see them.
 *
 * Two things had to be true at once, and the first build of the download feature only managed one. A
 * kept film has to sit outside Telegram's tree, because that is the tree Telegram is entitled to empty
 * — and it has to sit somewhere the Gallery, the other players and the computer the phone gets plugged
 * into can all find it. The app's own corner of external storage answers the first and fails the second:
 * Android hides `Android/data/<package>` from the media catalog on purpose, so a film written there is
 * real, durable and invisible to everything else on the device.
 *
 * The media catalog is the only place that satisfies both, and on this API level it is also the only
 * place a plain `File` write cannot reach. So every byte goes in through a row: reserved under a
 * pending flag, filled, checked, and only then published. A pending row is the `.part` file of this
 * arrangement — the Gallery cannot list a film that is still being written, so an interrupted copy
 * leaves nothing that looks like a movie and is not one.
 *
 * One free benefit: the handle this returns is a row, not a name. Two uploads of one release used to
 * need their file names argued over before either was written; the catalog settles that by itself, and
 * a film keeps its address even after somebody renames it in a file manager.
 */
class KeptMedia(private val context: Context) {

    /**
     * Ask for a place in the folder, and get the handle to write into.
     *
     * The row comes back unpublished, so nothing downstream has to know the difference between a film
     * that arrived and one that is still being copied.
     */
    suspend fun reserve(displayName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeTypeFor(displayName))
                put(MediaStore.Video.Media.RELATIVE_PATH, FOLDER)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            context.contentResolver.insert(collection(), values)?.toString()
        }.getOrNull()
    }

    /**
     * Copy a finished fetch into a reserved row and publish it.
     *
     * A refusal leaves the row pending rather than deleting it, because the caller may want to offer the
     * same file again and a row that is gone cannot be filled twice — the pump retries a copy that lost
     * bytes, and taking the row back the moment it failed would turn every retry into a copy against
     * nothing. Releasing it is the caller's decision, made with [release] when the film is finally given
     * up on. A pending row is invisible to every other app meanwhile, so an abandoned offer litters
     * nothing; [discardUnfinished] clears whatever is left at the next launch.
     *
     * The byte count the copy reports is the honest number — unlike a length, which is what made
     * Telegram's stretched shells so convincing — so it is asked twice: once against the file it came
     * from, so nothing was lost in the copy, and once against the length the shelf promised, so a fetch
     * that stopped short never gets published.
     *
     * The first of those two asks only fails a copy that *lost* bytes. A source that grew while the copy
     * ran is a source still being written, and the row holds everything it held at the start and more:
     * calling that a failure is how a film that had arrived in full was thrown away.
     */
    suspend fun publish(handle: String, source: String, sizeBytes: Long): PublishOutcome =
        withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(handle) }.getOrNull()
            val from = if (uri == null) 0L else File(source).length()
            val copied = if (uri == null) -1L else runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    File(source).inputStream().use { input -> input.copyTo(output, COPY_BUFFER_BYTES) }
                } ?: -1L
            }.getOrDefault(-1L)
            var outcome = outcomeFor(uri != null, from, copied, sizeBytes)
            if (uri != null && outcome == PublishOutcome.Held) {
                val published = runCatching {
                    val ready = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                    context.contentResolver.update(uri, ready, null, null) > 0
                }.getOrDefault(false)
                if (!published) outcome = PublishOutcome.CatalogRefused
            }
            if (outcome != PublishOutcome.Held) {
                Log.w(
                    TAG,
                    "publish $outcome from=$from copied=$copied promised=$sizeBytes source=$source into=$handle"
                )
            }
            outcome
        }

    /** Whether a film this app promised is still in the catalog. */
    suspend fun isHeld(handle: String): Boolean = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(handle) }.getOrNull() ?: return@withContext false
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media._ID), null, null, null)
                ?.use { it.moveToFirst() }
        }.getOrDefault(false) == true
    }

    /** Where the row's bytes actually lie, for a log line a person can follow into a file manager. */
    suspend fun pathOf(handle: String): String = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(handle) }.getOrNull() ?: return@withContext ""
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use ""
                val column = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                if (column < 0 || cursor.isNull(column)) "" else cursor.getString(column)
            }
        }.getOrNull().orEmpty()
    }

    /** Take a kept film off the device. The catalog deletes the bytes with the row. */
    suspend fun release(handle: String) {
        if (handle.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching { Uri.parse(handle) }.getOrNull()?.let { abandon(it) }
        }
    }

    /**
     * Clear the rows this install left half-written.
     *
     * A copy cut short by the app dying keeps its pending flag, and a pending row is invisible to
     * everything except the app that owns it — which is this one, forever, until this runs. It is the
     * same sweep the first version of this file did over a folder of `.part` files, and it has the same
     * license to delete: nothing but this app can see these rows, and none of them is a film.
     */
    suspend fun discardUnfinished() = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                collection(),
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.IS_PENDING} = 1 AND " +
                    "${MediaStore.Video.Media.OWNER_PACKAGE_NAME} = ?",
                arrayOf(context.packageName),
                null,
            )?.use { cursor ->
                val column = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                if (column < 0) return@use
                while (cursor.moveToNext()) {
                    abandon(ContentUris.withAppendedId(collection(), cursor.getLong(column)))
                }
            }
        }
        Unit
    }

    private fun abandon(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private fun collection(): Uri =
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun mimeTypeFor(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "video/*"
    }

    companion object {
        /** Where kept films appear on the device, in the volume's own terms. */
        const val FOLDER = "Movies/Arc Player/"

        /**
         * Which clause of the copy a set of byte counts falls to.
         *
         * Pulled out of [publish] because the comparison that used to throw a finished film away is
         * arithmetic rather than Android, and arithmetic is the part a test can hold. The copy asks
         * whether bytes were *lost*, not whether the counts agree: a source Telegram was still writing
         * reports more bytes out than it claimed when the copy started, and reading that as a failure
         * is how a 2.87 GB download that had arrived in full got deleted.
         */
        fun outcomeFor(
            hasHandle: Boolean,
            sourceBytes: Long,
            copiedBytes: Long,
            promisedBytes: Long,
        ): PublishOutcome = when {
            !hasHandle -> PublishOutcome.NoHandle
            copiedBytes <= 0L -> PublishOutcome.NothingReadable
            copiedBytes < sourceBytes -> PublishOutcome.LostInCopy
            !DownloadLedger.hasEveryByte(promisedBytes, copiedBytes) -> PublishOutcome.ShortOfPromise
            else -> PublishOutcome.Held
        }

        /** The cloud's one log tag, so a publish refusal reads beside the download that hit it. */
        private const val TAG = "ArcCloud"

        private const val COPY_BUFFER_BYTES = 128 * 1024
    }
}
