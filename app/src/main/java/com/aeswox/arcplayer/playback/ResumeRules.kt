package com.aeswox.arcplayer.playback

/** What one playback tick means for a video's resume point. */
enum class ResumeOutcome {
    /** Remember this position. */
    STORE,

    /** The video is finished enough that a resume point would only be in the way. */
    DROP,

    /** Leave whatever is stored alone. */
    KEEP
}

/**
 * When watch progress is worth keeping, free of Android and of storage so the awkward
 * cases are testable on the JVM.
 */
object ResumeRules {

    /** Below this a clip is over when it is over; a resume point into a 12 second video is noise. */
    const val MIN_TRACKABLE_DURATION_MS = 30_000L

    /** A tick this close to the start has nothing to resume to. */
    const val MIN_RESUME_POSITION_MS = 3_000L

    /** How much of the end counts as finished rather than paused, as a percentage. */
    const val TAIL_PERCENT = 8

    /** The tail's ceiling, so a three hour film does not forget its place fourteen minutes early. */
    const val MAX_TAIL_MS = 90_000L

    /** Milliseconds at the very end where a duration reported by the container is a lie. */
    const val END_SLACK_MS = 4_000L

    fun tailMs(durationMs: Long): Long =
        minOf(durationMs * TAIL_PERCENT / 100L, MAX_TAIL_MS)

    fun decide(positionMs: Long, durationMs: Long): ResumeOutcome {
        if (durationMs <= 0L || positionMs < 0L) return ResumeOutcome.KEEP
        if (durationMs < MIN_TRACKABLE_DURATION_MS) return ResumeOutcome.DROP
        if (positionMs >= durationMs - END_SLACK_MS) return ResumeOutcome.DROP
        if (positionMs >= durationMs - tailMs(durationMs)) return ResumeOutcome.DROP
        if (positionMs < MIN_RESUME_POSITION_MS) return ResumeOutcome.KEEP
        return ResumeOutcome.STORE
    }
}
