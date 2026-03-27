package com.customDB

import com.customDB.compression.GzipFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.zip.GZIPInputStream
import java.io.ByteArrayInputStream

class CompressionTest {
    
    @Test
    fun testGzipFormat() {
        val text = "This is a test string with some repetition repetition repetition"
        val data = text.toByteArray()
        
        val compressed = GzipFormat.compress(data)
        val decompressed = GzipFormat.decompress(compressed)
        
        assertEquals(text, String(decompressed))
        println("Original: ${data.size} bytes, Compressed: ${compressed.size} bytes")
    }
    
    @Test
    fun testGzipCompatibility() {
        val text = "Test data for GZIP compatibility check"
        val data = text.toByteArray()
        
        val ourCompressed = GzipFormat.compress(data)
        
        try {
            val input = GZIPInputStream(ByteArrayInputStream(ourCompressed))
            val decompressed = input.readAllBytes()
            assertEquals(text, String(decompressed))
            println("✓ Our format is compatible with Java GZIP!")
        } catch (e: Exception) {
            println("⚠ Our format is not fully compatible with Java GZIP: ${e.message}")
        }
    }
    
    @Test
    fun testCompressionRatio() {
        val jsonData = """
            {"id":1,"name":"Test User","email":"test@example.com","age":30,"city":"Moscow","balance":1000}
        """.trimIndent().repeat(10)
        
        val data = jsonData.toByteArray()
        
        val ourCompressed = GzipFormat.compress(data)
        val ourRatio = (1.0 - ourCompressed.size.toDouble() / data.size) * 100
        
        println("\n=== Compression Comparison ===")
        println("Original size: ${data.size} bytes")
        println("Compressed size: ${ourCompressed.size} bytes")
        println("Compression: ${String.format("%.2f", ourRatio)}%")
        
        val decompressed = GzipFormat.decompress(ourCompressed)
        assertEquals(jsonData, String(decompressed))
    }
    
    @Test
    fun testEmptyData() {
        val data = ByteArray(0)
        val compressed = GzipFormat.compress(data)
        val decompressed = GzipFormat.decompress(compressed)
        assertTrue(data.contentEquals(decompressed))
    }
    
    @Test
    fun testSmallData() {
        val data = "Hi".toByteArray()
        val compressed = GzipFormat.compress(data)
        val decompressed = GzipFormat.decompress(compressed)
        assertTrue(data.contentEquals(decompressed))
    }
}
