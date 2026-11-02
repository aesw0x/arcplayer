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
 *
 * A third source joins the two Telegram ones once the viewer asks to keep a copy, and it is the only one
 * that can overrule a delete: [Verdict.KEPT] comes from this app's own [DownloadLedger], not from a
 * record that has no word for "somebody meant this file to stay". Without it, a finished download is
 * indistinguishable here from the leftovers this sweep exists to reclaim, and the launch pass would hand
 * back every film taken offline.
 *
 * The same two sources are also the only answers to *why a transfer is running*, and that is the other
 * half of the bargain. Telegram's `is_downloading` says nothing about who asked — TDLib resumes what it
 * was asked to fetch before the process died, and a watch whose release never ran leaves the same
 * signature. Reading a running transfer as proof of an owner therefore protects exactly the downloads
 * this app cannot see and would rather not be paying for, so [Verdict.UNOWNED_FETCH] breaks them off.
 */
object DownloadSweep {

    /** What Telegram's record for one file says about its bytes at the moment it was asked. */
    data class Record(
        /** The length Telegram believes this file is. Read from the record, never from the shelf. */
        val claimedSizeBytes: Long,
        /** Whether the record reports anything local — landed bytes, or a completion flag. */
        val reportsLocalBytes: Boolean,
        /**
         * Whether a transfer is running right now.
         *
         * On its own this says nothing about who asked for it, which is the whole question once the
         * viewer can start a download — see [Verdict.UNOWNED_FETCH].
         */
        val isDownloading: Boolean,
    )

    /** One shelf entry, reduced to what the decision needs: which file, and how big it should be. */
    data class Claim(val fileId: Int, val shelfSizeBytes: Long)

    /**
     * Why one shelf entry was or was not handed back.
     *
     * Every answer but the two that [isReclaimable] names is a reason to leave the bytes alone, and the
     * launch sweep reports how many of each it saw: "put down 0 of 61" on a device holding 134 MB of
     * leftovers is not a result anyone can act on, while "0 of 61, 2 still arriving" says exactly where
     * to look next.
     */
    enum class Verdict {
        /** No watch of this session owns it and Telegram agrees the bytes are there: hand it back. */
        STRANDED,

        /** The player has it, or is being handed it right now. */
        IN_USE,

        /** Telegram did not answer for this id inside a round trip. */
        NO_ANSWER,

        /**
         * Telegram is transferring this film and nothing in the app is waiting for the bytes.
         *
         * The rule this replaces read a running transfer as proof of an owner, which was true until the
         * download feature arrived and is not true at all now. Three things leave a fetch running with
         * nobody behind it: TDLib resuming a download it was asked for before the process died, a watch
         * whose release never ran because the Activity was recreated underneath it, and a keep this app
         * gave up on. All three spend the link while a film is trying to play, and none of them is
         * visible on the Downloads page — the only record they leave is Telegram's own `is_downloading`.
         *
         * So a transfer with no claim and no promise is broken off like the bytes it is writing. The id
         * still has to match the shelf's length first, which is what stops this from cancelling some
         * poster Telegram is fetching for itself.
         */
        UNOWNED_FETCH,

        /** The record names nothing local, so there is no copy to reclaim. */
        NOTHING_LOCAL,

        /** The record's length disagrees with the shelf's, so this id is somebody else's file now. */
        NOT_THIS_FILM,

        /**
         * The viewer asked for this copy to stay, through [DownloadLedger].
         *
         * This one does not come from Telegram's record at all, and it has to outrank every verdict that
         * does: a download that has finished looks exactly like the leftovers this sweep exists to
         * reclaim, because nothing in TDLib's answer says anybody meant it to be there. The promise is
         * keyed on the shelf entry, so it holds even while the record says nothing local — a file being
         * filled, or one whose bytes have yet to be verified — and even if its id now resolves to
         * something whose size disagrees.
         */
        KEPT,
    }

    /**
     * What the sweep should do about one shelf entry.
     *
     * A null [record] means Telegram did not answer, which is a reason to skip the file, not to delete
     * it. [inUse] covers the copy the player was handed — or is being handed — since the app came up.
     * [kept] covers the copies the viewer asked to keep, which is a promise this app wrote down itself
     * and therefore the only answer that does not depend on Telegram being honest about its own bytes.
     *
     * The length guard sits ahead of the transfer, not behind it. Both of the reclaiming verdicts reach
     * Telegram's file with `CancelDownloadFile` and `DeleteFile`, and those take the id at face value —
     * so an id that resolves to something other than this film must be refused before either one fires,
     * whether what it holds is bytes or somebody else's download.
     */
    fun verdict(claim: Claim, record: Record?, inUse: Boolean, kept: Boolean = false): Verdict = when {
        inUse -> Verdict.IN_USE
        kept -> Verdict.KEPT
        record == null -> Verdict.NO_ANSWER
        !record.reportsLocalBytes && !record.isDownloading -> Verdict.NOTHING_LOCAL
        record.claimedSizeBytes != claim.shelfSizeBytes -> Verdict.NOT_THIS_FILM
        record.isDownloading -> Verdict.UNOWNED_FETCH
        else -> Verdict.STRANDED
    }

    /** Whether one answer means the file belongs to nobody and should be handed back. */
    fun isReclaimable(verdict: Verdict): Boolean =
        verdict == Verdict.STRANDED || verdict == Verdict.UNOWNED_FETCH

    /** Whether this shelf entry's copy belongs to no watch and can be handed back. */
    fun isReclaimable(claim: Claim, record: Record?, inUse: Boolean, kept: Boolean = false): Boolean =
        isReclaimable(verdict(claim, record, inUse, kept))

    /** The shelf entries to put down, in the order they were listed. */
    fun reclaim(
        claims: List<Claim>,
        records: Map<Int, Record>,
        inUseFileIds: Set<Int>,
        keptFileIds: Set<Int> = emptySet(),
    ): List<Claim> = claims.filter {
        isReclaimable(it, records[it.fileId], it.fileId in inUseFileIds, it.fileId in keptFileIds)
    }
}
