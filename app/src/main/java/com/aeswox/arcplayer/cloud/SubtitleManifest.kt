package com.aeswox.arcplayer.cloud

import com.aeswox.arcplayer.subtitle.SubtitleMatch

/**
 * The captions the channel holds, written down beside the shelf's own manifest.
 *
 * A caption is only useful if the player can reach it the moment a film opens, and the video walk that
 * finds it costs seconds the opening cannot spend. The shelf manifest solved the same problem for
 * pictures; this is that, for the handful of names that are not.
 *
 * A separate file rather than extra columns on [ShelfManifest] on purpose: that format drops every line
 * whose column count disagrees with its field list, so widening it would silently empty the grid of
 * anyone upgrading. Captions can fail to load without taking anything else with them.
 */
object SubtitleManifest {

    private const val MAGIC = "arc-shelf-subs"
    private const val VERSION = "1"
    private const val SEPARATOR = "\t"

    private val FIELDS = listOf("stableId", "chatId", "messageId", "fileId", "sizeBytes", "fileName")

    /** Only the captions are written; a channel that gains one later is picked up on the next walk. */
    fun encode(chatId: Long, items: List<CloudSubtitleItem>): String = buildString {
        append(MAGIC).append(SEPARATOR).append(VERSION).append(SEPARATOR).append(chatId).append('\n')
        items.forEach { item ->
            val columns = listOf(
                item.stableId.toString(),
                item.chatId.toString(),
                item.messageId.toString(),
                item.fileId.toString(),
                item.sizeBytes.toString(),
                item.fileName.clean(),
            )
            append(columns.joinToString(SEPARATOR)).append('\n')
        }
    }

    /**
     * The captions belonging to [chatId], or nothing at all when the file was written for another chat.
     *
     * A name that does not parse is passed over rather than failing the file, and a name that is no
     * longer a caption is dropped outright: the shelf is re-read often, so a manifest that has drifted
     * behind the channel costs one missing caption rather than a stale one.
     */
    fun decode(text: String, chatId: Long): List<CloudSubtitleItem> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()?.split(SEPARATOR).orEmpty()
        if (header.size < 3 || header[0] != MAGIC || header[2] != chatId.toString()) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            val parts = line.split(SEPARATOR)
            if (parts.size != FIELDS.size || parts[1].toLongOrNull() != chatId) return@mapNotNull null
            runCatching {
                CloudSubtitleItem(
                    stableId = parts[0].toLong(),
                    chatId = chatId,
                    messageId = parts[2].toLong(),
                    fileId = parts[3].toInt(),
                    sizeBytes = parts[4].toLong(),
                    fileName = parts[5],
                    isFromManifest = true,
                )
            }.getOrNull()?.takeIf { SubtitleMatch.isSubtitle(it.fileName) }
        }
    }

    private fun String.clean(): String = replace(SEPARATOR, " ").replace("\n", " ")
}
