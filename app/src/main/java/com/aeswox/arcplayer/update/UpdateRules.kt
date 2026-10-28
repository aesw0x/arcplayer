package com.aeswox.arcplayer.update

/** One file hanging off a GitHub release, as far as this app cares: what it is called and where it is. */
data class ReleaseAsset(val name: String, val downloadUrl: String)

/** A release, narrowed to the three things the card asks about. */
data class Release(val version: String, val notes: String, val assets: List<ReleaseAsset>)

/**
 * The deciding half of the updater, with no network and no Android in it.
 *
 * Which of two version strings is the newer one, and which of the release's files a given phone should
 * pull, are the two questions where a wrong answer installs the wrong binary or never offers an update at
 * all. Both are answered here, over plain values, so they can be put in front of a test instead of in
 * front of a phone.
 */
internal object UpdateRules {

    /** The release's own number, from a tag written the way the workflow writes them: `v1.0.1`. */
    fun versionFromTag(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    /**
     * Whether [candidate] is a later build than [current].
     *
     * Segment by segment on purpose. Comparing the strings gets "1.9" ahead of "1.10", and comparing the
     * whole thing as a decimal gets "1.10" wrong the same way; a missing tail counts as zero, so `1.0.1`
     * beats `1.0` and `1.0` is not ahead of `1.0.0`.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val wanted = segments(candidate)
        val running = segments(current)
        for (index in 0 until maxOf(wanted.size, running.size)) {
            val a = wanted.getOrElse(index) { 0L }
            val b = running.getOrElse(index) { 0L }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * Which APK to hand this phone.
     *
     * [abis] arrives in the order the system reports them, so its first entry is the architecture the
     * device actually runs on and the only one worth matching on: a build carrying both `arm64-v8a` and
     * `x86_64` slices is a universal one, and taking the first name that merely contains either would
     * ship a phone a fat binary it has no use for.
     */
    fun apkFor(assets: List<ReleaseAsset>, abis: List<String>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        for (abi in abis) {
            apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }
        return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) } ?: apks.firstOrNull()
    }

    /**
     * The release's notes, cut down to what fits a card.
     *
     * The workflow pastes the same "which file do I want" paragraph at the top of every release, and the
     * viewer is about to be told which file they want by the button underneath, so the whole opening
     * paragraph goes rather than just the sentence that gives it away. A bullet is the one piece of
     * markdown worth keeping a mark for; anything else a changelog carries arrives as written.
     */
    fun notes(body: String?): String {
        if (body.isNullOrBlank()) return ""
        val lines = body.lines().map { it.trim() }
        val instructions = lines.firstOrNull()?.startsWith(BOILERPLATE) == true
        val past = if (instructions) lines.indexOfFirst { it.isEmpty() }.takeIf { it >= 0 }?.plus(1) ?: lines.size else 0
        return lines.drop(past)
            .filterNot { it.startsWith(CHANGELOG_LINK) }
            .map { line -> BULLET.find(line)?.let { "•  ${line.removeRange(it.range)}" } ?: line }
            .filter { it.isNotEmpty() }
            .take(MAX_NOTE_LINES)
            .joinToString("\n")
    }

    private fun segments(version: String): List<Long> = version.split('.')
        .map { part -> part.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L }

    private val BULLET = Regex("^[-*]\\s+")
    private const val BOILERPLATE = "Signed APKs for Android"
    private const val CHANGELOG_LINK = "**Full Changelog**"
    private const val MAX_NOTE_LINES = 6
}
