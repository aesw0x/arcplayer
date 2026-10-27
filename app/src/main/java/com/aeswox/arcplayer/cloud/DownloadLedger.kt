package com.aeswox.arcplayer.cloud

/**
 * One channel copy the viewer asked to keep on the device.
 *
 * The app's whole relationship with Telegram's bytes used to be temporary: the player was handed a
 * file, put it down, and the bytes were released through `DeleteFile`. A download is the first promise
 * this app makes to hold bytes *after* nobody is reading them, and a promise needs writing down, so
 * this is what an install remembers about a film it is keeping.
 */
data class DownloadEntry(
    /** The negative [CloudIds] key the file is played under, and the only stable name it has here. */
    val stableId: Long,
    /** Where the file lives in the channel, kept so [fileId] can be re-asked for after a rebuild. */
    val chatId: Long,
    val messageId: Long,
    /**
     * TDLib's number for the file, as the session that wrote this line knew it.
     *
     * A claim rather than a fact on the next launch: the numbers are handed out by whichever database
     * is open, which is the same bargain [CloudVideoItem.isFromManifest] makes, and for the same reason
     * — a download started from a manifest card asks for the live number before it asks for bytes.
     */
    val fileId: Int,
    /** The length the shelf was told the file has, which is what "it all arrived" is measured against. */
    val sizeBytes: Long,
    /**
     * Where this app keeps the file, as a handle into the device's media catalog.
     *
     * Reserved the moment the viewer asks, so a fetch in progress already knows where it is going, and
     * read back only when [isComplete] — and checked again before use. It is a row rather than a path on
     * purpose twice over: the folder behind it is one the Gallery indexes and Telegram does not own, and
     * the handle still names the film after anybody renames it in a file manager.
     */
    val path: String,
    /** Whether the whole of the file was on the device the last time it was counted. */
    val isComplete: Boolean,
)

/**
 * The list of files this install is keeping, and the rule that says whether one really is.
 *
 * Tab-separated rather than JSON, and with no Android types in it, so the round trip is a pure function
 * a test can hold — the same shape [ShelfManifest] uses and for the same reason.
 *
 * The rule that earns its keep is [hasEveryByte]. Neither of the two answers that look right is:
 * - `isDownloadingCompleted` is Telegram's own belief about a file, and this app has already been
 *   burnt by it outliving the bytes — the whole reason [DownloadSweep] exists is that a record can
 *   call a film complete while its path holds nothing.
 * - `File.length()` reports the whole film about two seconds into a streamed one, because
 *   `CloudRepository.localPathFor` deliberately stretches the shell to the full length so a seek is
 *   never "past the end". A stretched file with 4 KB behind it reads as 3 GB to anything that asks
 *   the filesystem how long it is.
 *
 * Blocks actually allocated answer it, which is what a partial file cannot fake.
 */
object DownloadLedger {

    private const val MAGIC = "arc-download-ledger"
    private const val VERSION = "1"
    private const val SEPARATOR = "\t"

    private val FIELDS = listOf("stableId", "chatId", "messageId", "fileId", "sizeBytes", "path", "isComplete")

    fun encode(entries: Collection<DownloadEntry>): String = buildString {
        append(MAGIC).append(SEPARATOR).append(VERSION).append('\n')
        entries.forEach { entry ->
            val columns = listOf(
                entry.stableId.toString(),
                entry.chatId.toString(),
                entry.messageId.toString(),
                entry.fileId.toString(),
                entry.sizeBytes.toString(),
                entry.path.clean(),
                entry.isComplete.toString(),
            )
            append(columns.joinToString(SEPARATOR)).append('\n')
        }
    }

    /**
     * The kept files, keyed by the id they are played under.
     *
     * A line that does not parse is passed over rather than failing the file: losing one promise to
     * keep one film is worth less than losing every promise the install has made.
     */
    fun decode(text: String): Map<Long, DownloadEntry> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()?.split(SEPARATOR).orEmpty()
        if (header.size < 2 || header[0] != MAGIC || header[1] != VERSION) return emptyMap()
        return lines.drop(1).mapNotNull { line ->
            val parts = line.split(SEPARATOR)
            if (parts.size < FIELDS.size) return@mapNotNull null
            runCatching {
                DownloadEntry(
                    stableId = parts[0].toLong(),
                    chatId = parts[1].toLong(),
                    messageId = parts[2].toLong(),
                    fileId = parts[3].toInt(),
                    sizeBytes = parts[4].toLong(),
                    path = parts[5],
                    isComplete = parts[6].toBoolean(),
                )
            }.getOrNull()?.let { it.stableId to it }
        }.toMap()
    }

    /**
     * Whether a file's worth of blocks is actually on the device.
     *
     * The slack is for the two ways a truthful count comes up a little short — the filesystem's own
     * block rounding, and an allocation the kernel has not committed yet — and nothing else. It is
     * deliberately far too small to let a streamed shell through: those are short by hundreds of
     * megabytes, not by one percent.
     */
    fun hasEveryByte(sizeBytes: Long, allocatedBytes: Long): Boolean {
        if (sizeBytes <= 0L || allocatedBytes <= 0L) return false
        return allocatedBytes >= sizeBytes - (BLOCK_BYTES + sizeBytes / PERCENT_DENOMINATOR)
    }

    /**
     * Whether a line names a catalog row rather than a bare path.
     *
     * Aimed at two specific pasts. The first build of this feature wrote down wherever Telegram happened
     * to keep the file, which for a whole-document fetch was Telegram's temporary directory — the one
     * directory Telegram is entitled to empty, and did. The second wrote down a path inside this app's
     * own corner of external storage, which survives but which Android hides from every other app on the
     * phone. Neither is a film a person can find, so neither is a promise worth keeping.
     */
    fun isCatalogHandle(path: String): Boolean = path.startsWith("content://")

    /** The stable ids of the copies that are entirely on the device right now. */
    fun completeIds(entries: Map<Long, DownloadEntry>): Set<Long> =
        entries.filterValues { it.isComplete }.keys

    /**
     * Which catalog row holds which kept copy, in the device's own numbers.
     *
     * One film is known by two ids the moment it is kept: the shelf calls it by the negative [CloudIds]
     * key it is played under, and the device calls it by the media-catalog row its bytes were published
     * into. Nothing else in the app holds both, and a screen that shows a film rather than a file has to
     * — without the link, the copy in this app's own folder is a stranger wearing a file name.
     *
     * A bare path is passed over rather than matched by name. It is either a pre-catalog keep, which the
     * launch sweep has already re-filed or will, or a pointer into a directory Telegram is entitled to
     * empty; guessing a row from a file name is how two uploads of one release end up called the same
     * film when they are not.
     */
    fun keptRows(entries: Map<Long, DownloadEntry>): Map<Long, Long> =
        entries.values.asSequence()
            .filter { it.isComplete && isCatalogHandle(it.path) }
            .mapNotNull { entry -> entry.path.rowId()?.let { it to entry.stableId } }
            .toMap()

    /** The media-catalog row a handle points at, or nothing when it does not point at one. */
    private fun String.rowId(): Long? =
        removePrefix("content://").trimEnd('/').substringAfterLast('/').toLongOrNull()

    /**
     * The name a kept film is filed under.
     *
     * The channel's own file name is the one worth keeping — it is what a film looks like in a gallery
     * app and on the computer the phone gets plugged into — so it is used as it stands once everything
     * a filesystem would object to has become an underscore. A leading dot goes with them, because a
     * hidden download is one nobody finds again.
     *
     * Uniqueness is deliberately not this function's job. The catalog it is filed in renames a second
     * "Film.mkv" to "Film (1).mkv" by itself and hands back a handle to *that* row, so two uploads of
     * one release cannot end up naming the same film even when they arrive with the same name — which
     * is the failure the first version of this rule had to predict and prevent by hand.
     */
    fun fileNameFor(title: String): String {
        val cleaned = title.map { if (it in ALLOWED_FILE_NAME_CHARS) it else '_' }
            .joinToString("")
            .trim()
            .trimStart('.')
        return if (cleaned.isEmpty()) "download" else shortened(cleaned)
    }

    /** Cut a name to what one path component can hold, keeping the extension that says what it is. */
    private fun shortened(name: String): String {
        if (name.length <= MAX_FILE_NAME_CHARS) return name
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0 && name.length - dot <= MAX_EXTENSION_CHARS) name.substring(dot) else ""
        return name.take(MAX_FILE_NAME_CHARS - extension.length) + extension
    }

    private fun String.clean(): String = replace(SEPARATOR, " ").replace("\n", " ")

    private const val BLOCK_BYTES = 4096L

    /** One percent, expressed without floating point so the rule is exact arithmetic. */
    private const val PERCENT_DENOMINATOR = 100L

    /** Everything a file name may hold on the storage this app writes to, and nothing that misbehaves. */
    private val ALLOWED_FILE_NAME_CHARS: Set<Char> =
        ('a'..'z').toSet() + ('A'..'Z') + ('0'..'9') + setOf('.', '-', '_', ' ', '(', ')')

    /**
     * Roomy enough for a release name to stay readable, small enough that even a name written entirely
     * in four-byte characters stays inside the 255 bytes a single path component is allowed.
     */
    private const val MAX_FILE_NAME_CHARS = 60

    /** Longest tail still treated as an extension rather than as the middle of a very odd name. */
    private const val MAX_EXTENSION_CHARS = 8
}
