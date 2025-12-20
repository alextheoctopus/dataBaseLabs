package com.customDB

import com.customDB.compression.GzipFormat
import java.io.File

fun main() {
    println("=".repeat(70))
    println("Создание GZIP файла через нашу реализацию")
    println("=".repeat(70))
    
    val originalText = """
        This is a test file for demonstrating GZIP compression.
        It contains repeated text: "Hello World" "Hello World" "Hello World"
        More repetition: ABC ABC ABC ABC ABC
        The quick brown fox jumps over the lazy dog.
    """.trimIndent()
    
    println("\nИсходный текст:")
    println(originalText)
    println("\nРазмер исходных данных: ${originalText.toByteArray().size} байт")
    
    // Сжимаем через нашу реализацию
    val compressed = GzipFormat.compress(originalText.toByteArray())
    
    println("Размер сжатых данных: ${compressed.size} байт")
    println("Коэффициент сжатия: ${String.format("%.1f", (1 - compressed.size.toDouble() / originalText.toByteArray().size) * 100)}%")
    
    // Сохраняем в файл в корне проекта
    val projectRoot = File(System.getProperty("user.dir")).parentFile ?: File(".")
    val gzipFile = File(projectRoot, "test_output.gz")
    gzipFile.writeBytes(compressed)
    
    println("\n✓ GZIP файл создан: ${gzipFile.absolutePath}")
    
    // Проверяем магические байты
    println("\nПроверка заголовка GZIP:")
    println("  Магические байты: 0x${String.format("%02X", compressed[0].toInt() and 0xFF)} 0x${String.format("%02X", compressed[1].toInt() and 0xFF)}")
    println("  Метод сжатия: ${compressed[2].toInt() and 0xFF} (8 = DEFLATE)")
    
    // Проверяем наше распаковывание
    println("\nПроверка распаковывания:")
    val decompressed = GzipFormat.decompress(compressed)
    val decompressedText = String(decompressed)
    
    if (decompressedText == originalText) {
        println("  ✓ Данные восстановлены корректно")
    } else {
        println("  ✗ Ошибка: данные не совпадают")
        println("  Оригинал: ${originalText.length} символов")
        println("  Распаковано: ${decompressedText.length} символов")
    }
    
    println("\n" + "=".repeat(70))
    println("Теперь проверьте через gunzip:")
    println("=".repeat(70))
    println("  cd ${projectRoot.absolutePath}")
    println("  gunzip -c test_output.gz")
    println("  gunzip -t test_output.gz")
    println("  zcat test_output.gz")
}
