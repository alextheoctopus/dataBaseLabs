package com.customDB.compression

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream
import java.io.ByteArrayInputStream

object GzipFormat {
    
    fun compress(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(data) }
        return output.toByteArray()
    }
    
    fun decompress(gzipData: ByteArray): ByteArray {
        val input = ByteArrayInputStream(gzipData)
        return GZIPInputStream(input).readAllBytes()
    }
}
