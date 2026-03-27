package com.customDB.compression

import org.junit.jupiter.api.Test
import java.io.File

class CreateGzipFileTest {
    
    @Test
    fun createGzipFileForGunzipTest() {
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
        println("ORIGINAL DATA (before compression):")
        println("=".repeat(70))
        println(originalText)
        println("\nOriginal size: ${originalText.toByteArray().size} bytes")
        
        val originalBytes = originalText.toByteArray()
        val compressed = GzipFormat.compress(originalBytes)
        
        println("\n" + "=".repeat(70))
        println("COMPRESSED DATA:")
        println("=".repeat(70))
        println("Compressed size: ${compressed.size} bytes")
        println("Compression ratio: ${String.format("%.1f", compressed.size.toDouble() / originalBytes.size * 100)}%")
        
        val gzipFile = File("test_output.gz")
        gzipFile.writeBytes(compressed)
        
        println("\nGZIP file created: ${gzipFile.absolutePath}")
        println("File size: ${gzipFile.length()} bytes")
        
        println("\n" + "=".repeat(70))
        println("DECOMPRESSED DATA (using our implementation):")
        println("=".repeat(70))
        val decompressed = GzipFormat.decompress(compressed)
        val decompressedText = String(decompressed)
        println(decompressedText)
        
        if (decompressedText == originalText) {
            println("\n✓ SUCCESS: Decompressed data matches original exactly!")
        }
        
        println("\n" + "=".repeat(70))
        println("Now test with gunzip:")
        println("=".repeat(70))
        println("  gunzip -c ${gzipFile.absolutePath}")
        println("  zcat ${gzipFile.absolutePath}")
        println("  7z x ${gzipFile.absolutePath}")
    }
}
