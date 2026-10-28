package com.aeswox.arcplayer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two questions the updater answers wrong in ways that are invisible until a phone has the wrong
 * binary on it: which number is the bigger one, and which file belongs to this architecture.
 */
class UpdateRulesTest {

    @Test
    fun `a tag carries its version without the v`() {
        assertEquals("1.0.1", UpdateRules.versionFromTag("v1.0.1"))
        assertEquals("1.0.1", UpdateRules.versionFromTag("1.0.1"))
        assertEquals("2.0", UpdateRules.versionFromTag("  v2.0  "))
    }

    @Test
    fun `a later segment wins on its value and not its text`() {
        assertTrue(UpdateRules.isNewer("1.0.1", "1.0.0"))
        // The one a string compare loses: "9" sorts after "10".
        assertTrue(UpdateRules.isNewer("1.10", "1.9"))
        assertTrue(UpdateRules.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateRules.isNewer("1.0.0", "1.0.1"))
        assertFalse(UpdateRules.isNewer("1.0", "1.0.1"))
    }

    @Test
    fun `a shorter version is the same version padded with zeros`() {
        assertFalse(UpdateRules.isNewer("1.0", "1.0.0"))
        assertFalse(UpdateRules.isNewer("1.0.0", "1.0"))
        assertTrue(UpdateRules.isNewer("1.0.0.1", "1.0.0"))
    }

    @Test
    fun `a suffix that is not a number counts as nothing`() {
        assertFalse(UpdateRules.isNewer("1.0.0-beta", "1.0.0"))
        assertTrue(UpdateRules.isNewer("1.1.0-rc1", "1.0.9"))
    }

    @Test
    fun `a phone gets its own architecture rather than the fat build`() {
        val assets = listOf(
            ReleaseAsset("SHA256SUMS.txt", "https://example.test/sums"),
            ReleaseAsset("ArcPlayer-1.0.1-arm64-v8a.apk", "https://example.test/arm64"),
            ReleaseAsset("ArcPlayer-1.0.1-x86_64.apk", "https://example.test/x64"),
            ReleaseAsset("ArcPlayer-1.0.1-universal.apk", "https://example.test/universal")
        )
        assertEquals("ArcPlayer-1.0.1-arm64-v8a.apk", UpdateRules.apkFor(assets, listOf("arm64-v8a"))?.name)
        assertEquals("ArcPlayer-1.0.1-x86_64.apk", UpdateRules.apkFor(assets, listOf("x86_64"))?.name)
        // A device that reports both is a compatibility emulator: the first entry is still the one it runs.
        assertEquals("ArcPlayer-1.0.1-arm64-v8a.apk", UpdateRules.apkFor(assets, listOf("arm64-v8a", "x86_64"))?.name)
    }

    @Test
    fun `a release with no match falls back to universal and then to nothing`() {
        val onlyUniversal = listOf(ReleaseAsset("ArcPlayer-1.0.1-universal.apk", "https://example.test/u"))
        assertEquals("ArcPlayer-1.0.1-universal.apk", UpdateRules.apkFor(onlyUniversal, listOf("arm64-v8a"))?.name)
        val noApks = listOf(ReleaseAsset("SHA256SUMS.txt", "https://example.test/sums"))
        assertNull(UpdateRules.apkFor(noApks, listOf("arm64-v8a")))
        assertNull(UpdateRules.apkFor(emptyList(), listOf("arm64-v8a")))
    }

    @Test
    fun `the release's fixed instructions and changelog link do not belong on a card`() {
        val body = """
            Signed APKs for Android 12+. Choose arm64-v8a for most phones, or universal for both.
            SHA256SUMS.txt contains download checksums.

            ## What's Changed
            * Fix the splash crash by @aesw0x in #1

            **Full Changelog**: https://github.com/aesw0x/arcplayer/compare/v1.0.0...v1.0.1
        """.trimIndent()
        val notes = UpdateRules.notes(body)
        assertFalse(notes.contains("Signed APKs"))
        // The second line of that paragraph is only gone because the whole opening paragraph is.
        assertFalse(notes.contains("SHA256SUMS"))
        assertFalse(notes.contains("Full Changelog"))
        assertTrue(notes.contains("What's Changed"))
        assertTrue(notes.contains("Fix the splash crash"))
    }

    @Test
    fun `bullets keep their mark and a long list stops at six lines`() {
        assertEquals("•  one", UpdateRules.notes("- one"))
        assertEquals("•  one", UpdateRules.notes("* one"))
        val nine = (1..9).joinToString("\n") { "- item $it" }
        assertEquals(6, UpdateRules.notes(nine).lines().size)
        assertEquals("", UpdateRules.notes(null))
        assertEquals("", UpdateRules.notes("   "))
    }
}
