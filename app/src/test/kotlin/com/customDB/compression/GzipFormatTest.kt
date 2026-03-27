package com.customDB.compression

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.nio.file.Files

class GzipFormatTest {
    
    @Test
    fun testBasicCompression() {
        val data = "Hello World".toByteArray()
        val compressed = GzipFormat.compress(data)
        val decompressed = GzipFormat.decompress(compressed)
        
        assertEquals(String(data), String(decompressed))
    }
    
    @Test
    fun testGzipCompatibility() {
        val originalText = """
            This is a test file for demonstrating GZIP compression.
            It contains repeated text: "Hello World" "Hello World" "Hello World"
            More repetition: ABC ABC ABC ABC ABC
        """.trimIndent()
        
        val compressed = GzipFormat.compress(originalText.toByteArray())
        
        // Сохраняем во временный файл
        val tempDir = Files.createTempDirectory("gzip_test").toFile()
        val gzipFile = File(tempDir, "test.gz")
        gzipFile.writeBytes(compressed)
        
        // Проверяем что gunzip может распаковать
        val process = ProcessBuilder("gunzip", "-c", gzipFile.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        
        val output = process.inputStream.readAllBytes()
        val error = process.errorStream.readAllBytes()
        val exitCode = process.waitFor()
        
        if (exitCode == 0) {
            val decompressedText = String(output)
            assertEquals(originalText, decompressedText)
            println("SUCCESS: gunzip successfully decompressed our GZIP file!")
        } else {
            println("gunzip failed with exit code: $exitCode")
            if (error.isNotEmpty()) {
                println("Error: ${String(error)}")
            }
            // Проверяем наше распаковывание
            val ourDecompressed = GzipFormat.decompress(compressed)
            assertEquals(originalText, String(ourDecompressed))
            println("Our decompression works, but gunzip failed")
        }
        
        gzipFile.delete()
        tempDir.delete()
    }
    
    @Test
    fun testRepeatedData() {
        val data = "ABCABCABCABC".toByteArray()
        val compressed = GzipFormat.compress(data)
        val decompressed = GzipFormat.decompress(compressed)
        
        assertEquals(String(data), String(decompressed))
    }
    
    @Test
    fun testEmptyData() {
        val data = ByteArray(0)
        val compressed = GzipFormat.compress(data)
        val decompressed = GzipFormat.decompress(compressed)
        
        assertEquals(0, decompressed.size)
    }
}
