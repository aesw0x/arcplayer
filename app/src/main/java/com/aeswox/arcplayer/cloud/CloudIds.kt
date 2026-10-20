package com.aeswox.arcplayer.cloud

/**
 * Stable negative [Long] keys for Telegram videos.
 *
 * Arc Player keys resume positions, favorites, watch history and playlists by MediaStore `_ID`,
 * which is always a small positive number. Deriving a cloud key that is always negative lets
 * Telegram items share those maps without a migration and without any chance of collision.
 */
object CloudIds {

    /**
     * A negative, deterministic key for one video in one chat.
     *
     * Uses a 64-bit FNV-1a mix rather than a pair object because the result has to survive being
     * written into the existing JSON preference maps, where keys are strings and values are numbers.
     *
     * [messageId] and [remoteFileId] have to be the *server's* identifiers. TDLib's local `Message.id`
     * and `File.id` are reassigned whenever it rebuilds its database, so a key built from them changes
     * between sessions and everything hung off it — resume position, favorites, a catalogue match —
     * silently starts over.
     */
    fun forVideo(chatId: Long, messageId: Long, remoteFileId: String): Long {
        var hash = FNV_OFFSET
        for (value in listOf(chatId, messageId)) {
            var v = value
            repeat(8) {
                hash = hash xor (v and 0xFF)
                hash = hash * FNV_PRIME
                v = v shr 8
            }
        }
        for (character in remoteFileId) {
            hash = hash xor (character.code.toLong() and 0xFF)
            hash = hash * FNV_PRIME
        }
        return -(hash and Long.MAX_VALUE) - 1L
    }

    /** A negative, deterministic key for one chat, used by the cloud browse history. */
    fun forChat(chatId: Long): Long = -(chatId and Long.MAX_VALUE) - 1L

    /**
     * A negative, deterministic key for one file sitting on the device.
     *
     * Telegram loses local records for files it still wrote — three complete episodes were found in
     * its `documents` folder with no shelf video naming their path — and a copy with no owner still
     * needs a key so it can be ordered against the copies that have one. The two zero ids make the
     * result depend on the path and nothing else, and it stays negative like every other key here.
     */
    fun forPath(path: String): Long = forVideo(0L, 0L, path)

    /**
     * Whether a key refers to cloud content rather than a file on the device.
     */
    fun isCloud(id: Long): Boolean = id < 0L

    private const val FNV_OFFSET = -0x34095f7a08817f1cL
    private const val FNV_PRIME = 0x100000001b3L
}
