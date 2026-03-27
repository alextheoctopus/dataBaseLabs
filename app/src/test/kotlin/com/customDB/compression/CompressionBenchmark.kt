package com.customDB.compression

import org.junit.jupiter.api.Test
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CompressionBenchmark {
    
    @Test
    fun compareWithJavaGzip() {
        val testData = listOf(
            "Short string" to "Hello, World!".toByteArray(),
            "Medium JSON" to """
                {
                    "id": 12345,
                    "name": "Test User",
                    "email": "test@example.com",
                    "age": 25,
                    "city": "Moscow",
                    "description": "This is a test user with some data"
                }
            """.trimIndent().toByteArray(),
            "Long repeated" to "The quick brown fox jumps over the lazy dog. ".repeat(50).toByteArray(),
            "Large JSON" to generateLargeJson().toByteArray()
        )
        
        println("\n=== Compression Benchmark ===\n")
        println("%-20s | %-15s | %-15s | %-15s".format(
            "Test Data", "Original", "Our GZIP", "Java GZIP"
        ))
        println("-".repeat(70))
        
        for ((name, data) in testData) {
            val ourCompressed = GzipFormat.compress(data)
            val javaCompressed = compressWithJavaGzip(data)
            
            println("%-20s | %-15d | %-15d | %-15d".format(
                name, data.size, ourCompressed.size, javaCompressed.size
            ))
            
            val ourDecompressed = GzipFormat.decompress(ourCompressed)
            org.junit.jupiter.api.Assertions.assertArrayEquals(data, ourDecompressed)
        }
        
        println("\nNote: Our implementation uses Java GZIP, so results should be identical")
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
