package com.aeswox.arcplayer.cloud

/**
 * Who gets the link: the film on screen, or the one quietly being kept.
 *
 * A keep is not urgent and a picture is, and [CloudRepository]'s priority gap only partly says so — a
 * transfer Telegram has already started keeps going when a higher-priority request arrives, so a film
 * that began at bulk priority holds a lane beside the sixty-odd megabytes the play head needs. The only
 * way the link actually comes back is to cancel the other transfer and ask for it again later; the bytes
 * already landed stay on disk, so the re-ask resumes the film rather than restarting it.
 *
 * The pause is per watch and it is bounded, and the bound is the reason this is a decision rather than
 * an `if`. Which file the player has been handed is remembered in a claim set that is emptied when a
 * watch closes — and a watch that ended because the Activity was recreated underneath it never ran its
 * close. Standing down for a claim that outlives its watch would turn "downloads are slow while I watch"
 * into "downloads never happen again", so a keep holds off one watch for one budget and then finishes
 * what it was asked to do.
 */
object KeepQueue {

    /** What the pump does about the link on one lap of its poll. */
    enum class Turn {
        /** Break the transfer off and wait: the player is reading a different file. */
        STEP_ASIDE,

        /** Already stood down and still owed the wait — nothing to say, nothing to ask. */
        HOLD,

        /** Ask for the transfer again, because the watch let go or the hold ran out. */
        STEP_BACK_IN,

        /** Nothing changed; fill bytes like any other lap. */
        FILL,
    }

    /** The watch this pump last stood down for, and whether it is standing down right now. */
    data class Stance(
        /** Which file the keep yielded to, or null when it has yielded to nothing this stretch. */
        val watchFileId: Int? = null,
        /** Whether the transfer is cancelled at the moment of this decision. */
        val standingDown: Boolean = false,
        /** When standing down for [watchFileId] stops being owed, in elapsed-realtime terms. */
        val untilMs: Long = 0L,
    )

    /** The lap's action and the stance to carry into the next one. */
    data class Decision(val turn: Turn, val stance: Stance)

    /**
     * Decide one lap.
     *
     * [watchFileId] is a file the player is reading that is not this keep's own, or null when there is
     * none. A fresh watch always buys a hold, including one that arrives while this pump is already
     * standing down for a watch that has since gone away — the budget is owed to the picture, not to the
     * accident that stopped it. When nothing is on screen the memory of which watch this keep gave up on
     * goes with it, so the next one is a watch again.
     */
    fun decide(watchFileId: Int?, stance: Stance, nowMs: Long, budgetMs: Long): Decision = when {
        watchFileId == null ->
            if (stance.standingDown) Decision(Turn.STEP_BACK_IN, Stance()) else Decision(Turn.FILL, Stance())

        watchFileId != stance.watchFileId ->
            if (stance.standingDown) Decision(Turn.HOLD, Stance(watchFileId, standingDown = true, untilMs = nowMs + budgetMs))
            else Decision(Turn.STEP_ASIDE, Stance(watchFileId, standingDown = true, untilMs = nowMs + budgetMs))

        nowMs < stance.untilMs ->
            if (stance.standingDown) Decision(Turn.HOLD, stance) else Decision(Turn.FILL, stance)

        stance.standingDown -> Decision(Turn.STEP_BACK_IN, stance.copy(standingDown = false))

        else -> Decision(Turn.FILL, stance)
    }
}
