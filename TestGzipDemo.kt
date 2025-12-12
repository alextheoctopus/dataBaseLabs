package com.customDB

import com.customDB.compression.GzipFormat
import java.io.File

fun main() {
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
    println("COMPRESSED DATA (hex dump, first 100 bytes):")
    println("=".repeat(70))
    val hexPreview = compressed.take(100).joinToString(" ") { 
        String.format("%02X", it.toInt() and 0xFF) 
    }
    println(hexPreview + "...")
    println("Compressed size: ${compressed.size} bytes")
    
    val gzipFile = File("demo_test.gz")
    gzipFile.writeBytes(compressed)
    
    println("\nGZIP FILE CREATED: ${gzipFile.absolutePath}")
    println("Size: ${gzipFile.length()} bytes")
    
    println("\n" + "=".repeat(70))
    println("DECOMPRESSED DATA (using our implementation):")
    println("=".repeat(70))
    val decompressed = GzipFormat.decompress(compressed)
    val decompressedText = String(decompressed)
    println(decompressedText)
    
    if (decompressedText == originalText) {
        println("\n✓ SUCCESS: Decompressed data matches original!")
    }
    
    println("\n" + "=".repeat(70))
    println("Now test with gunzip:")
    println("=".repeat(70))
    println("  gunzip -c ${gzipFile.absolutePath}")
}
