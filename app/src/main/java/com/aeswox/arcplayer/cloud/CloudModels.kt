package com.aeswox.arcplayer.cloud

/** A chat the signed-in account can browse videos in. */
data class CloudChat(
    val chatId: Long,
    val title: String,
    val isChannel: Boolean,
)

/**
 * One video that lives on Telegram's servers rather than on the device.
 *
 * [stableId] is the negative key from [CloudIds], so this can be dropped straight into the same
 * resume / favorites / history maps the local library uses.
 */
data class CloudVideoItem(
    val stableId: Long,
    val chatId: Long,
    val chatTitle: String,
    val messageId: Long,
    val fileId: Int,
    val title: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val supportsStreaming: Boolean,
    /**
     * Whether this came off disk rather than out of Telegram's answer to the current session.
     *
     * [fileId] is TDLib's own number for the file, handed out by the database that is open right now,
     * so a manifest item's copy of it is a claim rather than a fact. Anything that reads the file has
     * to have it re-asked — everything else on the item is a name and a size, which Telegram wrote
     * into the message itself and does not hand out again.
     */
    val isFromManifest: Boolean = false,
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000L
            return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
        }

    val sizeFormatted: String
        get() = formatCloudBytes(sizeBytes)
}

/**
 * A caption forwarded into the same channel as the picture it belongs to.
 *
 * This is deliberately not a [CloudVideoItem]. Everything the shelf knows about videos — grouping them
 * into works, ranking one upload against another — is a question about pictures, and a subtitle that
 * answers them turns into a phantom second copy of the episode it was meant to help. So it travels in
 * its own list and is only ever read by the player.
 */
data class CloudSubtitleItem(
    val stableId: Long,
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val fileName: String,
    val sizeBytes: Long,
    /** Same bargain as the video manifest's: a file id off disk is a claim, not a fact. */
    val isFromManifest: Boolean = false,
)

/** A file size short enough to sit in a card's caption or a shelf footer. */
fun formatCloudBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / 1048576.0)
    else -> "%.0f KB".format(bytes / 1024.0)
}

/** How much of a cloud video is readable right now, for the grid and the player's buffer bar. */
data class CloudProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val isDownloading: Boolean,
    val isComplete: Boolean,
    /**
     * Every byte TDLib has taken for this file, including the ranges outside the window being read.
     * The only number that answers "how much of this film is on the device", because the window
     * itself moves and its prefix falls back to zero each time it does.
     */
    val bytesOnDisk: Long = downloadedBytes,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f
        else (downloadedBytes.coerceAtLeast(bytesOnDisk).toFloat() / totalBytes).coerceIn(0f, 1f)
}
