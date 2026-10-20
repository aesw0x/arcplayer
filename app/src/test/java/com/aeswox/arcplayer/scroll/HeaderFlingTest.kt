package com.aeswox.arcplayer.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderFlingTest {

    private val max = 630f

    @Test
    fun aSubpixelNudgeIsNotAnOpenHeader() {
        assertFalse(HeaderFling.isOpen(0f))
        assertFalse(HeaderFling.isOpen(0.4f))
        assertTrue(HeaderFling.isOpen(0.6f))
    }

    @Test
    fun releasedBelowTheLineFallsBackToIt() {
        assertEquals(0f, HeaderFling.snapTargetPx(300f, max), 0.001f)
        assertEquals(0f, HeaderFling.snapTargetPx(1f, max), 0.001f)
    }

    @Test
    fun releasedAtExactlyHalfCommitsToOneHanded() {
        assertEquals(max, HeaderFling.snapTargetPx(max / 2f, max), 0.001f)
    }

    @Test
    fun oneThresholdGovernsBothDirections() {
        // The same fraction decides a header travelling up and one travelling down, which is what
        // the reference recordings show: 49% falls to the line, 51% returns, in either direction.
        val justUnder = max * 0.49f
        val justOver = max * 0.51f
        assertEquals(0f, HeaderFling.snapTargetPx(justUnder, max), 0.001f)
        assertEquals(max, HeaderFling.snapTargetPx(justOver, max), 0.001f)
        assertEquals(0f, HeaderFling.snapTargetPx(justUnder, max), 0.001f)
    }

    @Test
    fun anAlmostClosedAndFullyOpenHeaderSettleWhereTheyAre() {
        assertEquals(0f, HeaderFling.snapTargetPx(0f, max), 0.001f)
        assertEquals(max, HeaderFling.snapTargetPx(max, max), 0.001f)
    }

    @Test
    fun aHeaderOfZeroHeightNeverOpens() {
        assertEquals(0f, HeaderFling.snapTargetPx(0f, 0f), 0.001f)
    }
}
