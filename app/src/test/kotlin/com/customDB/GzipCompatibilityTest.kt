package com.customDB

import com.customDB.compression.GzipFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.nio.file.Files

/**
 * Тест совместимости с внешними утилитами (gunzip, 7z, etc)
 */
class GzipCompatibilityTest {
    
    @Test
    fun testGzipFileFormat() {
        val testData = """
            This is a test file for GZIP compatibility.
            It contains some repeated text: "Hello World" "Hello World" "Hello World"
            And some JSON-like data: {"id":1,"name":"Test","value":123}
            More repetition: ABC ABC ABC
        """.trimIndent()
        
        val data = testData.toByteArray()
        val compressed = GzipFormat.compress(data)
        
        // Сохраняем в файл
        val tempFile = Files.createTempFile("test", ".gz").toFile()
        tempFile.writeBytes(compressed)
        
        println("\n=== GZIP File Compatibility Test ===")
        println("File: ${tempFile.absolutePath}")
        println("Original size: ${data.size} bytes")
        println("Compressed size: ${compressed.size} bytes")
        println("\nTo test with gunzip, run:")
        println("  gunzip -c ${tempFile.absolutePath}")
        println("  or")
        println("  zcat ${tempFile.absolutePath}")
        
        // Проверяем magic numbers
        assertEquals(0x1F.toByte(), compressed[0], "GZIP magic number 1")
        assertEquals(0x8B.toByte(), compressed[1], "GZIP magic number 2")
        assertEquals(0x08.toByte(), compressed[2], "DEFLATE method")
        
        // Проверяем, что можем распаковать нашим методом
        val decompressed = GzipFormat.decompress(compressed)
        assertEquals(testData, String(decompressed))
        
        // Оставляем файл для ручного тестирования
        println("\n✓ File created successfully. You can test with:")
        println("  gunzip -t ${tempFile.absolutePath}  # Test file")
        println("  gunzip -c ${tempFile.absolutePath}  # Decompress to stdout")
        
        // Удаляем файл после теста (можно закомментировать для ручного тестирования)
        // tempFile.delete()
    }
    
    @Test
    fun testLargeDataCompression() {
        // Генерируем большой JSON с повторениями
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{\n")
        jsonBuilder.append("  \"users\": [\n")
        for (i in 1..1000) {
            jsonBuilder.append("    {\n")
            jsonBuilder.append("      \"id\": $i,\n")
            jsonBuilder.append("      \"name\": \"User $i\",\n")
            jsonBuilder.append("      \"email\": \"user$i@example.com\",\n")
            jsonBuilder.append("      \"city\": \"Moscow\",\n")
            jsonBuilder.append("      \"country\": \"Russia\",\n")
            jsonBuilder.append("      \"description\": \"This is a repeated description text that should compress well.\"\n")
            jsonBuilder.append(if (i < 1000) "    },\n" else "    }\n")
        }
        jsonBuilder.append("  ]\n")
        jsonBuilder.append("}\n")
        
        val data = jsonBuilder.toString().toByteArray()
        val compressed = GzipFormat.compress(data)
        val decompressed = GzipFormat.decompress(compressed)
        
        assertEquals(String(data), String(decompressed))
        
        val compressionRatio = (1.0 - compressed.size.toDouble() / data.size) * 100
        println("\n=== Large Data Compression ===")
        println("Original: ${data.size} bytes")
        println("Compressed: ${compressed.size} bytes")
        println("Compression: ${String.format("%.2f", compressionRatio)}%")
        println("Space saved: ${data.size - compressed.size} bytes")
    }
}

