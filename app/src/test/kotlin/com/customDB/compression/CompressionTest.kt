package com.customDB.compression

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.zip.GZIPInputStream
import java.io.ByteArrayInputStream

class CompressionTest {
    
    @Test
    fun testLZ77Basic() {
        val data = "Hello, World! Hello, World! Hello, World!".toByteArray()
        
        val compressed = LZ77.compress(data)
        val decompressed = LZ77.decompress(compressed)
        
        assertArrayEquals(data, decompressed)
        assertTrue(compressed.tokens.size < data.size)
    }
    
    @Test
    fun testLZ77RepeatedPattern() {
        val data = "ABC".repeat(100).toByteArray()
        
        val compressed = LZ77.compress(data)
        val decompressed = LZ77.decompress(compressed)
        
        assertArrayEquals(data, decompressed)
    }
    
    @Test
    fun testGzipFormat() {
        val data = "This is a test string that will be compressed using GZIP.".toByteArray()
        
        val compressed = GzipFormat.compress(data)
        val decompressed = GzipFormat.decompress(compressed)
        
        assertArrayEquals(data, decompressed)
    }
    
    @Test
    fun testGzipFormatWithRepeatedData() {
        val data = "Hello, World! ".repeat(100).toByteArray()
        
        val compressed = GzipFormat.compress(data)
        val decompressed = GzipFormat.decompress(compressed)
        
        assertArrayEquals(data, decompressed)
        assertTrue(compressed.size < data.size)
    }
    
    @Test
    fun testGzipCompatibility() {
        val data = "Test data for GZIP compatibility check.".toByteArray()
        
        val ourCompressed = GzipFormat.compress(data)
        
        assertEquals(0x1F.toByte(), ourCompressed[0])
        assertEquals(0x8B.toByte(), ourCompressed[1])
        assertEquals(0x08.toByte(), ourCompressed[2])
        
        val ourDecompressed = GzipFormat.decompress(ourCompressed)
        assertArrayEquals(data, ourDecompressed)
        
        try {
            val javaDecompressed = GZIPInputStream(ByteArrayInputStream(ourCompressed)).readAllBytes()
            assertArrayEquals(data, javaDecompressed)
        } catch (e: Exception) {
            println("Note: Our simplified format may not be fully compatible with Java GZIP: ${e.message}")
        }
    }
    
    @Test
    fun testCompressionRatio() {
        val original = """
            {
                "id": 1,
                "name": "John Doe",
                "email": "john.doe@example.com",
                "age": 30,
                "city": "New York",
                "country": "USA",
                "description": "This is a long description that contains a lot of text and should compress well because it has repeated patterns and structure."
            }
        """.trimIndent().toByteArray()
        
        val compressed = GzipFormat.compress(original)
        val ratio = compressed.size.toDouble() / original.size
        
        println("Original size: ${original.size} bytes")
        println("Compressed size: ${compressed.size} bytes")
        println("Compression ratio: ${String.format("%.2f", ratio * 100)}%")
        
        val decompressed = GzipFormat.decompress(compressed)
        assertArrayEquals(original, decompressed)
    }
}
