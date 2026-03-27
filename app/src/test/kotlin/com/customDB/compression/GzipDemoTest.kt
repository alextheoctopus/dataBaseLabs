package com.customDB.compression

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class GzipDemoTest {
    
    @Test
    fun demonstrateGzipCompatibility() {
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
        println("Compression ratio: ${String.format("%.1f", compressed.size.toDouble() / originalBytes.size * 100)}%")
        
        val tempDir = Files.createTempDirectory("gzip_demo").toFile()
        val gzipFile = File(tempDir, "test.gz")
        gzipFile.writeBytes(compressed)
        
        println("\n" + "=".repeat(70))
        println("GZIP FILE CREATED:")
        println("=".repeat(70))
        println("File: ${gzipFile.absolutePath}")
        println("Size: ${gzipFile.length()} bytes")
        println("\nGZIP header (first 10 bytes):")
        val header = compressed.take(10)
        println("  Magic: ${String.format("0x%02X 0x%02X", header[0].toInt() and 0xFF, header[1].toInt() and 0xFF)}")
        println("  Method: ${header[2].toInt() and 0xFF} (8 = DEFLATE)")
        println("  Flags: ${header[3].toInt() and 0xFF}")
        
        println("\n" + "=".repeat(70))
        println("TESTING WITH GUNZIP:")
        println("=".repeat(70))
        
        val process = ProcessBuilder("gunzip", "-c", gzipFile.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        
        val output = process.inputStream.readAllBytes()
        val error = process.errorStream.readAllBytes()
        val exitCode = process.waitFor()
        
        if (exitCode == 0) {
            val decompressedText = String(output)
            println("✓ gunzip successfully decompressed the file!")
            println("\nDECOMPRESSED DATA (from gunzip):")
            println("=".repeat(70))
            println(decompressedText)
            println("\nDecompressed size: ${decompressedText.toByteArray().size} bytes")
            
            if (decompressedText == originalText) {
                println("\n✓ SUCCESS: Decompressed data matches original exactly!")
            } else {
                println("\n✗ WARNING: Decompressed data differs from original")
            }
        } else {
            println("✗ gunzip failed with exit code: $exitCode")
            if (error.isNotEmpty()) {
                println("Error: ${String(error)}")
            }
            println("\nTrying with our decompression:")
            val ourDecompressed = GzipFormat.decompress(compressed)
            val ourText = String(ourDecompressed)
            println(ourText)
            if (ourText == originalText) {
                println("\n✓ Our decompression works correctly")
            }
        }
        
        println("\n" + "=".repeat(70))
        println("FILE INFORMATION:")
        println("=".repeat(70))
        println("You can test manually:")
        println("  gunzip -t ${gzipFile.absolutePath}")
        println("  gunzip -c ${gzipFile.absolutePath}")
        println("  zcat ${gzipFile.absolutePath}")
        println("  7z x ${gzipFile.absolutePath}")
        
        gzipFile.delete()
        tempDir.delete()
    }
}

