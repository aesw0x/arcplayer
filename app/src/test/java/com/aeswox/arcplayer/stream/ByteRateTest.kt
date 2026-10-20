package com.aeswox.arcplayer.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ByteRateTest {

    private val kilobyte = 1024.0

    @Test
    fun oneSampleIsNotASpeed() {
        assertNull(ByteRate().sample(0L, 0L))
    }

    @Test
    fun aCleanPairReportsTheLink() {
        val meter = ByteRate()
        meter.sample(0L, 0L)
        // 350 KB in one second, which is what the link measured on device does.
        val rate = meter.sample(1_000L, (350.0 * kilobyte).toLong())
        assertNotNull(rate)
        assertEquals(350.0, rate!! / kilobyte, 1.0)
    }

    @Test
    fun aSecondShorterThanATickIsNoise() {
        val meter = ByteRate()
        meter.sample(0L, 0L)
        assertNull(meter.sample(500L, 200_000L))
    }

    @Test
    fun aCounterMovedBackwardsIsRefusedThenRebuilt() {
        val meter = ByteRate()
        meter.sample(0L, 1_000L)
        assertEquals(1_000.0, meter.sample(1_000L, 2_000L)!!, 0.01)
        // The download was broken off and pointed at another range: the history is about a transfer
        // that no longer exists.
        assertNull(meter.sample(2_000L, 500L))
        assertEquals(1_000.0, meter.sample(3_000L, 1_500L)!!, 0.01)
    }

    @Test
    fun anImpossiblyFastJumpIsRefusedNotPrinted() {
        val meter = ByteRate()
        meter.sample(0L, 0L)
        assertNull(meter.sample(1_000L, (20.0 * 1024.0 * 1024.0).toLong()))
        // Twenty megabytes a second on a link that does 350 KB is the counter being rebuilt, and the
        // samples after it are measured from the rebuild rather than from the jump.
        val recovered = meter.sample(2_000L, (20.0 * 1024.0 * 1024.0).toLong() + 28_480L)
        assertEquals(27.8, recovered!! / kilobyte, 0.5)
    }

    @Test
    fun anOldSampleStopsCountingOnceItIsOutdated() {
        val meter = ByteRate()
        meter.sample(0L, 0L)
        meter.sample(1_000L, 1_000L)
        // Four seconds on, the window has slid past the first sample, so the rate is the recent one
        // and not the whole file averaged over it.
        val rate = meter.sample(5_000L, 1_000L + (1_400_000L))
        assertEquals(341.8, rate!! / kilobyte, 1.0)
    }

    @Test
    fun labelsReadTheWayAViewerExpects() {
        assertEquals("350 KB/s", ByteRate.speedLabel(350.0 * kilobyte))
        assertEquals("1.2 MB/s", ByteRate.speedLabel(1.2 * 1024.0 * 1024.0))
        assertNull(ByteRate.speedLabel(null))
    }
}
