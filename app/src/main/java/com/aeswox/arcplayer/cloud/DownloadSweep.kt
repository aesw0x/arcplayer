package com.aeswox.arcplayer.cloud

/**
 * Which of the shelf's copies this device is holding for nobody.
 *
 * A watch that ends at the back arrow puts its file down through Telegram. A watch that ends because
 * the app was swiped out of Recents does not, and those bytes stay for the life of the install. This
 * decides which of the shelf's own entries are in that state, so the launch sweep can ask Telegram to
 * put each one down the same way the back arrow does.
 *
 * The decision is made from Telegram's record of each file rather than from a walk of its folder, and
 * that is a correction, not a preference. The first version of this listed the folder and deleted what
 * the listing said was unclaimed. It reclaimed 1.2 GB on one device and 1.9 GB on another, and it broke
 * both: Telegram's record for a file it can no longer read still reports the film as complete, so the
 * next open skips the fetch, stretches a shell of the right length over the hole, and the player waits
 * at 0 KB/s forever. Only Telegram can stop believing its own record, so only Telegram may remove the
 * bytes.
 *
 * Two consequences follow, and both are load-bearing:
 * - Nothing here looks at a path. A file the app cannot name with a live file id is left alone, however
 *   much space it costs, because the only way to reach it is the one that breaks the shelf.
 * - [claimedSizeBytes] has to agree with the shelf before an id is believed. Telegram hands its file
 *   numbers out again, so a stale id usually resolves to a three-kilobyte thumbnail; deleting that would
 *   cost a poster and buy nothing.
 */
object DownloadSweep {

    /** What Telegram's record for one file says about its bytes at the moment it was asked. */
    data class Record(
        /** The length Telegram believes this file is. Read from the record, never from the shelf. */
        val claimedSizeBytes: Long,
        /** Whether the record reports anything local — landed bytes, or a completion flag. */
        val reportsLocalBytes: Boolean,
        /** Whether a transfer is running right now, which is a download in progress, not a leftover. */
        val isDownloading: Boolean,
    )

    /** One shelf entry, reduced to what the decision needs: which file, and how big it should be. */
    data class Claim(val fileId: Int, val shelfSizeBytes: Long)

    /**
     * Why one shelf entry was or was not handed back.
     *
     * Every answer but [STRANDED] is a reason to leave the bytes alone, and the launch sweep reports how
     * many of each it saw: "put down 0 of 61" on a device holding 134 MB of leftovers is not a result
     * anyone can act on, while "0 of 61, 2 still arriving" says exactly where to look next.
     */
    enum class Verdict {
        /** No watch of this session owns it and Telegram agrees the bytes are there: hand it back. */
        STRANDED,

        /** The player has it, or is being handed it right now. */
        IN_USE,

        /** Telegram did not answer for this id inside a round trip. */
        NO_ANSWER,

        /** Telegram is fetching it at this moment, which is a download, not a leftover. */
        STILL_ARRIVING,

        /** The record names nothing local, so there is no copy to reclaim. */
        NOTHING_LOCAL,

        /** The record's length disagrees with the shelf's, so this id is somebody else's file now. */
        NOT_THIS_FILM,
    }

    /**
     * What the sweep should do about one shelf entry.
     *
     * A null [record] means Telegram did not answer, which is a reason to skip the file, not to delete
     * it. [inUse] covers the copy the player was handed — or is being handed — since the app came up.
     */
    fun verdict(claim: Claim, record: Record?, inUse: Boolean): Verdict = when {
        inUse -> Verdict.IN_USE
        record == null -> Verdict.NO_ANSWER
        record.isDownloading -> Verdict.STILL_ARRIVING
        !record.reportsLocalBytes -> Verdict.NOTHING_LOCAL
        record.claimedSizeBytes != claim.shelfSizeBytes -> Verdict.NOT_THIS_FILM
        else -> Verdict.STRANDED
    }

    /** Whether this shelf entry's local copy belongs to no watch and can be handed back. */
    fun isStranded(claim: Claim, record: Record?, inUse: Boolean): Boolean =
        verdict(claim, record, inUse) == Verdict.STRANDED

    /** The shelf entries to put down, in the order they were listed. */
    fun stranded(
        claims: List<Claim>,
        records: Map<Int, Record>,
        inUseFileIds: Set<Int>,
    ): List<Claim> = claims.filter { isStranded(it, records[it.fileId], it.fileId in inUseFileIds) }
}
