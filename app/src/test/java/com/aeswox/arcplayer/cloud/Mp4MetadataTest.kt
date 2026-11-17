package com.aeswox.arcplayer.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class Mp4MetadataTest {

    @Test
    fun nonMp4InputIsLeftToTheOtherContainerReaders() {
        val mkv = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte()) + ByteArray(60)
        assertEquals(Mp4Metadata.Lookup.NotMp4, Mp4Metadata.scan(mkv, 4096L))
    }

    @Test
    fun moovInTheHeadNeedsNoFurtherDiscovery() {
        val head = fileType() + header("moov", 40L) + ByteArray(32) + header("mdat", 8L)
        val found = Mp4Metadata.scan(head, head.size.toLong()) as Mp4Metadata.Lookup.Found
        assertEquals(24L, found.offset)
        assertEquals(40L, found.size)
    }

    @Test
    fun moovAfterMdatAsksForItsExactHeaderOffset() {
        val fileSize = 4096L
        val moovOffset = 4024L
        val head = fileType() + header("mdat", 4000L) + ByteArray(32)

        val need = Mp4Metadata.scan(head, fileSize) as Mp4Metadata.Lookup.NeedHeader
        assertEquals(moovOffset, need.offset)

        val found = Mp4Metadata.afterHeader(header("moov", 72L), moovOffset, fileSize)
            as Mp4Metadata.Lookup.Found
        assertEquals(moovOffset, found.offset)
        assertEquals(72L, found.size)
    }

    @Test
    fun extendedSizesWorkForBothMdatAndMoov() {
        val fileSize = 4048L
        val moovOffset = 4024L
        val head = fileType() + header("mdat", 4000L, extended = true) + ByteArray(24)
        val need = Mp4Metadata.scan(head, fileSize) as Mp4Metadata.Lookup.NeedHeader
        assertEquals(moovOffset, need.offset)

        val found = Mp4Metadata.afterHeader(
            header("moov", 24L, extended = true),
            moovOffset,
            fileSize
        ) as Mp4Metadata.Lookup.Found
        assertEquals(24L, found.size)
    }

    @Test
    fun unrelatedBoxesBetweenMdatAndMoovAreSkipped() {
        val fileSize = 8192L
        val freeOffset = 8000L
        val next = Mp4Metadata.afterHeader(header("free", 32L), freeOffset, fileSize)
            as Mp4Metadata.Lookup.NeedHeader
        assertEquals(8032L, next.offset)

        val found = Mp4Metadata.afterHeader(header("moov", 160L), next.offset, fileSize)
            as Mp4Metadata.Lookup.Found
        assertEquals(8032L, found.offset)
        assertEquals(160L, found.size)
    }

    @Test
    fun zeroSizedFinalMoovEndsAtTheFileBoundary() {
        val head = fileType() + header("mdat", 32L) + ByteArray(24) + header("moov", 0L)
        val found = Mp4Metadata.scan(head, 96L) as Mp4Metadata.Lookup.Found
        assertEquals(56L, found.offset)
        assertEquals(40L, found.size)
    }

    @Test
    fun missingMoovIsReportedWithoutInventingARange() {
        val head = fileType() + header("mdat", 56L)
        assertEquals(Mp4Metadata.Lookup.Missing, Mp4Metadata.scan(head, 80L))
    }

    @Test
    fun malformedBoxSizesAreRejected() {
        val tooSmall = fileType() + header("mdat", 4L)
        assertEquals(Mp4Metadata.Lookup.Invalid, Mp4Metadata.scan(tooSmall, 80L))

        val beyondEnd = fileType() + header("mdat", 5000L)
        assertEquals(Mp4Metadata.Lookup.Invalid, Mp4Metadata.scan(beyondEnd, 80L))
    }

    @Test
    fun metadataCanNeverRequestTheWholeFile() {
        val result = Mp4Metadata.afterHeader(header("moov", 100L), 0L, 100L)
        assertEquals(Mp4Metadata.Lookup.Invalid, result)
    }

    @Test
    fun truncatedNextHeaderRequestsThatOffsetInsteadOfReadingZeroes() {
        val head = fileType() + header("mdat", 32L) + ByteArray(20)
        val need = Mp4Metadata.scan(head, 128L) as Mp4Metadata.Lookup.NeedHeader
        assertEquals(56L, need.offset)
    }

    private fun fileType(): ByteArray = header("ftyp", 24L) + ByteArray(16)

    private fun header(type: String, size: Long, extended: Boolean = false): ByteArray {
        val bytes = ByteArray(if (extended) 16 else 8)
        if (extended) {
            putUInt32(bytes, 0, 1L)
            putInt64(bytes, 8, size)
        } else {
            putUInt32(bytes, 0, size)
        }
        type.forEachIndexed { index, char -> bytes[4 + index] = char.code.toByte() }
        return bytes
    }

    private fun putUInt32(target: ByteArray, offset: Int, value: Long) {
        repeat(4) { index ->
            target[offset + index] = (value shr (24 - index * 8)).toByte()
        }
    }

    private fun putInt64(target: ByteArray, offset: Int, value: Long) {
        repeat(8) { index ->
            target[offset + index] = (value shr (56 - index * 8)).toByte()
        }
    }
}
