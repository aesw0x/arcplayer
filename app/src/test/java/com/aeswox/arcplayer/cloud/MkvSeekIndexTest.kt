package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The reader is checked against a built header, against the first bytes of a real two and a half
 * hour film, and against files that must not yield an index at all.
 */
class MkvSeekIndexTest {

    @Test
    fun `seek head names the cue table relative to the segment`() {
        val layout = MkvSeekIndex.readLayout(file(cuesRelative = 90_000L))
            ?: return fail("no layout in a seek head")
        // A cue position counts from the segment's data start, wherever this fixture's lands.
        assertEquals(90_000L, layout.cuesOffset - layout.segmentDataStart)
        assertEquals(1_000_000L, layout.timecodeScaleNs)
    }

    @Test
    fun `layout honours a coarse timecode scale`() {
        val head = file(cuesRelative = 90_000L, timecodeScale = 500_000L)
        val layout = MkvSeekIndex.readLayout(head) ?: return fail("no layout with a coarse scale")
        assertEquals(500_000L, layout.timecodeScaleNs)
    }

    @Test
    fun `layout of a real film`() {
        // The first 128 bytes of a 2,923,189,910 byte Matroska file, exactly as TDLib holds them.
        // Its seek head puts the cue table just ahead of the last eighth of a megabyte.
        val layout = MkvSeekIndex.readLayout(REAL_HEAD) ?: return fail("no layout in a real header")
        assertEquals(52L, layout.segmentDataStart)
        assertEquals(2_923_084_239L, layout.cuesOffset)
        assertEquals(1_000_000L, layout.timecodeScaleNs)
    }

    @Test
    fun `no layout for something that is not matroska`() {
        assertNull(MkvSeekIndex.readLayout(ByteArray(4096)))
        assertNull(MkvSeekIndex.readLayout("ftypisom".toByteArray() + ByteArray(4088)))
    }

    @Test
    fun `no layout when the seek head has not landed`() {
        // A hole in a partly fetched file stops the walk before the cue table is named, and nothing
        // must be invented from what came before it.
        assertNull(MkvSeekIndex.readLayout(file(cuesRelative = 90_000L).copyOf(20)))
    }

    @Test
    fun `cue table brackets a moment`() {
        val layout = MkvSeekIndex.Layout(segmentDataStart = 52L, cuesOffset = 0L, timecodeScaleNs = 1_000_000L)
        val cues = cueTable(
            0L to 0L,
            60_000L to 1_000_000L,
            120_000L to 2_000_000L,
            180_000L to 3_000_000L,
        )
        val table = MkvSeekIndex.readCueTable(cues, layout) ?: return fail("no cue table")

        assertEquals(4, table.timesMs.size)
        assertEquals(52L, table.clusterBytes[0])
        assertEquals(52L + 3_000_000L, table.clusterBytes[3])

        assertEquals(52L, table.byteFor(0L, 4_000_000L))
        // Halfway between two clusters, the bytes are assumed to arrive halfway between them too.
        assertEquals(52L + 500_000L, table.byteFor(30_000L, 4_000_000L))
        assertEquals(52L + 2_500_000L, table.byteFor(150_000L, 4_000_000L))
        // Past the last cue the table has nothing to bracket with.
        assertNull(table.byteFor(240_000L, 4_000_000L))
    }

    @Test
    fun `cue timecodes count in timecode scale units`() {
        val layout = MkvSeekIndex.Layout(segmentDataStart = 52L, cuesOffset = 0L, timecodeScaleNs = 500_000L)
        val cues = cueTable(0L to 0L, 120_000L to 1_000_000L)
        val table = MkvSeekIndex.readCueTable(cues, layout) ?: return fail("no cue table")
        // Half a millisecond per unit, so a timecode of 120,000 is a minute in.
        assertEquals(60_000L, table.timesMs[1])
    }

    @Test
    fun `reading a cue table costs one pass over it`() {
        val layout = MkvSeekIndex.Layout(segmentDataStart = 52L, cuesOffset = 0L, timecodeScaleNs = 1_000_000L)
        // A three hour film at one cue per keyframe. The reader used to hand every cue point's child
        // scan the end of the whole table, so each of these walked all the ones after it: quadratic,
        // and measured on the phone as five ANRs with the main thread inside the loop.
        val points = Array(50_000) { index -> (index * 60_000L) to (index * 80_000L) }
        val cues = cueTable(*points)
        val startedAt = System.nanoTime()
        val table = MkvSeekIndex.readCueTable(cues, layout) ?: return fail("no cue table")
        val tookMs = (System.nanoTime() - startedAt) / 1_000_000L

        assertEquals(50_000, table.timesMs.size)
        assertEquals(52L + 49_999L * 80_000L, table.clusterBytes[49_999])
        assertTrue("parsed 50,000 cues in ${tookMs}ms", tookMs < 5_000L)
    }

    @Test
    fun `no cue table from bytes that are not one`() {
        val layout = MkvSeekIndex.Layout(segmentDataStart = 52L, cuesOffset = 0L, timecodeScaleNs = 1_000_000L)
        assertNull(MkvSeekIndex.readCueTable(ByteArray(4096), layout))
        assertNull(MkvSeekIndex.readCueTable(cueTable(0L to 0L), layout))
    }

    @Test
    fun `cue table with one point only is refused`() {
        val layout = MkvSeekIndex.Layout(segmentDataStart = 52L, cuesOffset = 0L, timecodeScaleNs = 1_000_000L)
        // Two points at the same time code cannot bracket anything either.
        val cues = cueTable(0L to 0L, 0L to 900_000L)
        assertTrue(MkvSeekIndex.readCueTable(cues, layout) == null || MkvSeekIndex.readCueTable(cues, layout)!!.isEmpty)
    }

    /** EBML header, then a segment whose seek head and info name the cue table and the scale. */
    private fun file(cuesRelative: Long, timecodeScale: Long = 1_000_000L): ByteArray {
        val head = MkvSeekIndex.ElementBuilder()
        head.container(0x1A45DFA3L) { uint(0x42F7L, 1L) }
        head.container(0x18538067L) {
            container(0x114D9B74L) {
                container(0x4DBBL) {
                    raw(0x53ABL, fourBytes(0x1C53BB6BL))
                    raw(0x53ACL, fourBytes(cuesRelative))
                }
            }
            container(0x1549A966L) {
                uint(0x2AD7B1L, timecodeScale)
            }
            // Padding stands for the tracks and the first clusters that would follow.
            raw(0xEC, ByteArray(4027))
            container(0x1F43B675L) { uint(0xE7L, 0L) }
        }
        return head.toByteArray()
    }

    private fun cueTable(vararg points: Pair<Long, Long>): ByteArray {
        val builder = MkvSeekIndex.ElementBuilder()
        builder.container(0x1C53BB6BL) {
            points.forEach { (time, cluster) ->
                container(0xBBL) {
                    uint(0xB3L, time)
                    container(0xB7L) {
                        uint(0xF7L, 1L)
                        uint(0xF1L, cluster)
                    }
                }
            }
        }
        return builder.toByteArray()
    }

    private fun fourBytes(value: Long): ByteArray = ByteArray(4).also { bytes ->
        for (i in 0 until 4) bytes[i] = ((value shr (8 * (3 - i))) and 0xFF).toByte()
    }

    private companion object {
        /**
         * The first 128 bytes of the film that deep seeks were failing on, eight bytes to a line.
         * The header names the cue table at 52 + 2,923,084,187 and then falls into padding.
         */
        val REAL_HEAD: ByteArray = (
            "1a45dfa3a3428681" + "0142f7810142f281" + "0442f38108428288" + "6d6174726f736b61" +
            "4287810442858102" + "1853806701000000" + "ae3c5662114d9b74" + "c04dbb8c53ab8415" +
            "49a96653ac821003" + "4dbb8c53ab841654" + "ae6b53ac8210d34d" + "bb8e53ab841c53bb" +
            "6b53ac84ae3ab99b" + "4dbb8e53ab841254" + "c36753ac84ae3c51" + "61ec4fbb00000000"
            ).let { hex ->
                ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
            }
    }
}
