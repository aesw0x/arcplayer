package com.aeswox.arcplayer.cloud

object Mp4Metadata {

    sealed class Lookup {
        data object NotMp4 : Lookup()
        data object Invalid : Lookup()
        data object Missing : Lookup()
        data class NeedHeader(val offset: Long) : Lookup()
        data class Found(val offset: Long, val size: Long) : Lookup()
    }

    private data class Box(val type: Int, val offset: Long, val size: Long)

    fun scan(head: ByteArray, fileSize: Long): Lookup {
        if (fileSize <= 0L) return Lookup.NotMp4
        var offset = 0L
        var sawFileType = false
        repeat(MAX_SCANNED_BOXES) {
            if (offset >= fileSize) return if (sawFileType) Lookup.Missing else Lookup.NotMp4
            if (offset > head.size - MIN_HEADER_BYTES) {
                return if (sawFileType) Lookup.NeedHeader(offset) else Lookup.NotMp4
            }
            val box = readBox(head, offset.toInt(), offset, fileSize)
                ?: return if (sawFileType) Lookup.Invalid else Lookup.NotMp4
            when (box.type) {
                FILE_TYPE -> sawFileType = true
                MOVIE -> return if (sawFileType) found(box, fileSize) else Lookup.NotMp4
            }
            offset += box.size
        }
        return if (sawFileType) Lookup.Invalid else Lookup.NotMp4
    }

    fun afterHeader(header: ByteArray, offset: Long, fileSize: Long): Lookup {
        val box = readBox(header, 0, offset, fileSize) ?: return Lookup.Invalid
        if (box.type == MOVIE) return found(box, fileSize)
        val next = offset + box.size
        return if (next < fileSize) Lookup.NeedHeader(next) else Lookup.Missing
    }

    private fun found(box: Box, fileSize: Long): Lookup =
        if (box.size < fileSize) Lookup.Found(box.offset, box.size) else Lookup.Invalid

    private fun readBox(bytes: ByteArray, arrayOffset: Int, fileOffset: Long, fileSize: Long): Box? {
        if (fileOffset !in 0L until fileSize) return null
        if (arrayOffset < 0 || arrayOffset > bytes.size - MIN_HEADER_BYTES) return null
        val shortSize = readUInt32(bytes, arrayOffset)
        val type = readInt32(bytes, arrayOffset + 4)
        var headerSize = MIN_HEADER_BYTES
        val size = when (shortSize) {
            0L -> fileSize - fileOffset
            1L -> {
                if (arrayOffset > bytes.size - EXTENDED_HEADER_BYTES) return null
                headerSize = EXTENDED_HEADER_BYTES
                readInt64(bytes, arrayOffset + 8)
            }
            else -> shortSize
        }
        if (size < headerSize || size > fileSize - fileOffset) return null
        return Box(type, fileOffset, size)
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toInt() and 0xff).toLong() shl 24) or
            ((bytes[offset + 1].toInt() and 0xff).toLong() shl 16) or
            ((bytes[offset + 2].toInt() and 0xff).toLong() shl 8) or
            (bytes[offset + 3].toInt() and 0xff).toLong()

    private fun readInt32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun readInt64(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        repeat(8) { index ->
            value = (value shl 8) or (bytes[offset + index].toInt() and 0xff).toLong()
        }
        return value
    }

    private const val MIN_HEADER_BYTES = 8
    private const val EXTENDED_HEADER_BYTES = 16
    private const val MAX_SCANNED_BOXES = 64
    private const val FILE_TYPE = 0x66747970
    private const val MOVIE = 0x6d6f6f76
}
