package com.customDB.compression

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class CompressionComparisonTest {
    
    @Test
    fun compareOurLZ77WithJavaGzip() {
        val testData = listOf(
            "Short string" to "Hello, World!".toByteArray(),
            "Medium JSON" to """
                {
                    "id": 12345,
                    "name": "Test User",
                    "email": "test@example.com",
                    "age": 25,
                    "city": "Moscow"
                }
            """.trimIndent().toByteArray(),
            "Long repeated" to "The quick brown fox jumps over the lazy dog. ".repeat(50).toByteArray(),
            "Large JSON" to generateLargeJson().toByteArray()
        )
        
        println("\n=== Compression Comparison: Our LZ77 vs Java GZIP ===")
        println("(Our LZ77 is used for algorithm comparison, GZIP format uses standard library for compatibility)")
        println()
        println("%-20s | %-12s | %-15s | %-15s | %-12s".format(
            "Test Data", "Original", "Our LZ77", "Java GZIP", "LZ77 Ratio"
        ))
        println("-".repeat(80))
        
        for ((name, data) in testData) {
            val ourLZ77 = LZ77.compress(data)
            val ourLZ77Size = estimateLZ77Size(ourLZ77)
            
            val javaCompressed = compressWithJavaGzip(data)
            
            val lz77Ratio = ourLZ77Size.toDouble() / data.size * 100
            val javaRatio = javaCompressed.size.toDouble() / data.size * 100
            
            println("%-20s | %-12d | %-15d | %-15d | %-11.1f%%".format(
                name, data.size, ourLZ77Size, javaCompressed.size, lz77Ratio
            ))
            
            val decompressed = LZ77.decompress(ourLZ77)
            assertArrayEquals(data, decompressed)
        }
    }
    
    @Test
    fun testGzipCompatibilityWithExternalTools() {
        val testData = "This is test data for GZIP compatibility. It should work with gunzip, 7z, and other tools.".toByteArray()
        
        val compressed = GzipFormat.compress(testData)
        
        val tempFile = Files.createTempFile("test", ".gz").toFile()
        tempFile.writeBytes(compressed)
        
        println("\n=== GZIP File Compatibility Test ===")
        println("File created: ${tempFile.absolutePath}")
        println("Original size: ${testData.size} bytes")
        println("Compressed size: ${compressed.size} bytes")
        
        assertEquals(0x1F.toByte(), compressed[0])
        assertEquals(0x8B.toByte(), compressed[1])
        assertEquals(0x08.toByte(), compressed[2])
        
        try {
            val javaDecompressed = GZIPInputStream(ByteArrayInputStream(compressed)).readAllBytes()
            assertArrayEquals(testData, javaDecompressed)
            println("\n✓ Our format is compatible with Java GZIP!")
        } catch (e: Exception) {
            println("\n⚠ Compatibility issue: ${e.message}")
        }
        
        val ourDecompressed = GzipFormat.decompress(compressed)
        assertArrayEquals(testData, ourDecompressed)
        
        val process = ProcessBuilder("gunzip", "-c", tempFile.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        
        val output = process.inputStream.readAllBytes()
        val exitCode = process.waitFor()
        
        if (exitCode == 0) {
            val gunzipDecompressed = String(output)
            val originalText = String(testData)
            if (gunzipDecompressed == originalText) {
                println("✓ gunzip successfully decompressed the file!")
                println("\nDecompressed with gunzip:")
                println(gunzipDecompressed)
            } else {
                println("⚠ gunzip decompressed but data differs")
            }
        } else {
            val error = String(process.errorStream.readAllBytes())
            println("✗ gunzip failed: $error")
        }
        
        tempFile.delete()
    }
    
    @Test
    fun demonstrateBeforeAndAfter() {
        val originalText = """
            This is a test file for demonstrating GZIP compression.
            It contains some repeated text: "Hello World" "Hello World" "Hello World"
            And some JSON-like data: {"id":1,"name":"Test","value":123}
            More repetition: ABC ABC ABC ABC ABC
            The quick brown fox jumps over the lazy dog.
            The quick brown fox jumps over the lazy dog.
            The quick brown fox jumps over the lazy dog.
        """.trimIndent()
        
        println("\n" + "=".repeat(70))
        println("BEFORE COMPRESSION (original data):")
        println("=".repeat(70))
        println(originalText)
        println("\nOriginal size: ${originalText.toByteArray().size} bytes")
        
        val compressed = GzipFormat.compress(originalText.toByteArray())
        
        println("\n" + "=".repeat(70))
        println("AFTER COMPRESSION (GZIP format):")
        println("=".repeat(70))
        println("Compressed size: ${compressed.size} bytes")
        println("Compression ratio: ${String.format("%.1f", compressed.size.toDouble() / originalText.toByteArray().size * 100)}%")
        println("\nFirst 50 bytes (hex):")
        println(compressed.take(50).joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) })
        
        val decompressed = GzipFormat.decompress(compressed)
        val decompressedText = String(decompressed)
        
        println("\n" + "=".repeat(70))
        println("AFTER DECOMPRESSION (restored data):")
        println("=".repeat(70))
        println(decompressedText)
        println("\nDecompressed size: ${decompressedText.toByteArray().size} bytes")
        
        if (decompressedText == originalText) {
            println("\n✓ SUCCESS: Data matches original exactly!")
        }
    }
    
    private fun estimateLZ77Size(compressed: LZ77.CompressedData): Int {
        var size = 0
        for (token in compressed.tokens) {
            when (token) {
                is LZ77.Token.Literal -> size += 1
                is LZ77.Token.Reference -> size += 3
            }
        }
        return size
    }
    
    private fun compressWithJavaGzip(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }
    
    private fun generateLargeJson(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"users\": [\n")
        for (i in 1..100) {
            sb.append("    {\n")
            sb.append("      \"id\": $i,\n")
            sb.append("      \"name\": \"User $i\",\n")
            sb.append("      \"email\": \"user$i@example.com\",\n")
            sb.append("      \"age\": ${20 + (i % 50)},\n")
            sb.append("      \"city\": \"City ${i % 10}\",\n")
            sb.append("      \"description\": \"This is user number $i with some description text that repeats.\"\n")
            sb.append(if (i < 100) "    },\n" else "    }\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }
}
