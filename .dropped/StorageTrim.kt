package com.aeswox.arcplayer.cloud

/**
 * Which of the app's local copies of cloud videos to throw away so the pile fits a budget.
 *
 * Streaming a file writes it to disk, so anything watched start to finish is a whole film sitting in
 * the app's private folder with nothing else to reclaim it. This decides the order that folder is
 * emptied in: least recently watched first, and no more than the budget asks for.
 */
object StorageTrim {

    /** One watched file and how much of it Telegram says is on the device right now. */
    data class Copy(
        val stableId: Long,
        val bytesOnDisk: Long,
        val watchedAtMs: Long,
        /** False when Telegram is still pulling the file or refuses to let go of it. */
        val canBeDeleted: Boolean,
    )

    data class Plan(
        val delete: List<Long>,
        val bytesBefore: Long,
        val bytesAfter: Long,
    ) {
        val bytesFreed: Long get() = bytesBefore - bytesAfter
    }

    /**
     * Pick copies to delete until [budgetBytes] is met, oldest watch first.
     *
     * [keepStableId] is the film on screen: it is exempt whatever its age, because the player is
     * reading it byte by byte and a hole under its play head is a freeze, not a re-download.
     */
    fun plan(copies: List<Copy>, budgetBytes: Long, keepStableId: Long? = null): Plan {
        val before = copies.sumOf { it.bytesOnDisk.coerceAtLeast(0L) }
        if (before <= budgetBytes) return Plan(emptyList(), before, before)

        var remaining = before
        val doomed = ArrayList<Long>()
        copies.asSequence()
            .filter { it.canBeDeleted && it.bytesOnDisk > 0L && it.stableId != keepStableId }
            .sortedBy { it.watchedAtMs }
            .forEach { copy ->
                if (remaining <= budgetBytes) return@forEach
                remaining -= copy.bytesOnDisk
                doomed += copy.stableId
            }
        return Plan(doomed, before, remaining)
    }
}
