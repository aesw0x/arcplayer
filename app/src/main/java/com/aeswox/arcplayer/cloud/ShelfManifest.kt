package com.aeswox.arcplayer.cloud

/**
 * The shelf's file list, written down so the grid has something to draw before Telegram answers.
 *
 * The names and artwork were already cached; what was never cached was the list itself, so every
 * entry into the tab held up its cards until the message walk came back. A manifest closes that gap
 * and costs nothing when it is wrong, because the walk replaces the whole list the moment it lands.
 *
 * Tab-separated rather than JSON, and with no Android types in it, so the round trip is a pure
 * function a test can hold. File names are server-supplied text, so the writer strips the two
 * characters the format cannot carry instead of trusting them to be absent.
 */
object ShelfManifest {

    private const val MAGIC = "arc-shelf-manifest"
    private const val VERSION = "1"
    private const val SEPARATOR = "\t"

    /** One item per line, in the order [FIELDS] names; the free text sits at the end. */
    private val FIELDS = listOf(
        "stableId", "chatId", "messageId", "fileId", "sizeBytes", "durationMs",
        "width", "height", "supportsStreaming", "mimeType", "chatTitle", "title"
    )

    fun encode(chatId: Long, items: List<CloudVideoItem>): String = buildString {
        append(MAGIC).append(SEPARATOR).append(VERSION).append(SEPARATOR).append(chatId).append('\n')
        items.forEach { item ->
            val columns = listOf(
                item.stableId.toString(),
                item.chatId.toString(),
                item.messageId.toString(),
                item.fileId.toString(),
                item.sizeBytes.toString(),
                item.durationMs.toString(),
                item.width.toString(),
                item.height.toString(),
                item.supportsStreaming.toString(),
                item.mimeType.clean(),
                item.chatTitle.clean(),
                item.title.clean(),
            )
            append(columns.joinToString(SEPARATOR)).append('\n')
        }
    }

    /**
     * The items that belonged to [chatId] last time the shelf was read, marked as coming from disk.
     *
     * A line that does not parse is passed over rather than failing the whole file: one bad name in a
     * thousand should cost that one card, not the instant grid every other card is drawn from.
     */
    fun decode(text: String, chatId: Long): List<CloudVideoItem> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()?.split(SEPARATOR).orEmpty()
        if (header.size < 3 || header[0] != MAGIC || header[2] != chatId.toString()) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            val parts = line.split(SEPARATOR)
            if (parts.size != FIELDS.size || parts[1].toLongOrNull() != chatId) return@mapNotNull null
            runCatching {
                CloudVideoItem(
                    stableId = parts[0].toLong(),
                    chatId = chatId,
                    chatTitle = parts[10],
                    messageId = parts[2].toLong(),
                    fileId = parts[3].toInt(),
                    title = parts[11],
                    sizeBytes = parts[4].toLong(),
                    durationMs = parts[5].toLong(),
                    width = parts[6].toInt(),
                    height = parts[7].toInt(),
                    mimeType = parts[9],
                    supportsStreaming = parts[8].toBoolean(),
                    isFromManifest = true,
                )
            }.getOrNull()
        }
    }

    private fun String.clean(): String = replace(SEPARATOR, " ").replace("\n", " ")
}
