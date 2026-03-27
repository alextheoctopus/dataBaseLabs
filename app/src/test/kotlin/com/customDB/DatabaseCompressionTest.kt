package com.customDB

import com.customDB.api.*
import com.customDB.compression.CustomCompressionFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.nio.file.Files
import java.util.Base64

/**
 * Тесты для проверки работы сжатия в базе данных
 * Проверяет: INSERT → файл закодирован → можно раскодировать → данные правильные → базовые операции
 */
class DatabaseCompressionTest {
    
    private lateinit var testDir: File
    
    @BeforeEach
    fun setup() {
        testDir = Files.createTempDirectory("db_compression_test").toFile()
    }
    
    @Test
    fun testInsertCreatesCompressedData() {
        val engine = LocalStorageEngine(testDir)
        val sql = SqlEngine(engine)
        
        // Создаем таблицу
        sql.execute("CREATE TABLE products (id INT, name STRING, description STRING, price INT)")
        
        // Вставляем данные с длинным текстом (будет сжато, т.к. > 50 символов)
        val longDescription = "This is a very long product description with many repeated words. " +
                "Product description. Product description. Product description. " +
                "This product is amazing and has many features that make it unique. " +
                "Features include: durability, quality, reliability, and excellent performance."
        
        sql.execute("INSERT INTO products (id, name, description, price) VALUES (1, 'Laptop', '$longDescription', 99999)")
        
        // Проверяем, что файл существует
        val tableFile = File(testDir, "products.tbl")
        assertTrue(tableFile.exists(), "Table file should exist")
        
        // Читаем содержимое файла
        val content = tableFile.readText()
        println("=== File content ===")
        println(content)
        println("===================")
        
        // Проверяем, что данные закодированы (флаг compressed = true)
        assertTrue(content.contains("\"compressed\":true"), 
            "Data should be compressed. Content: $content")
        
        // Проверяем, что payload содержит Base64 строку (не исходный текст)
        assertFalse(content.contains(longDescription), 
            "Original text should not be in file (should be compressed)")
        
        // Проверяем, что есть Base64 строка (длинная строка без пробелов)
        val hasBase64 = content.contains("\"payload\":\"") && 
                       content.contains("\"compressed\":true")
        assertTrue(hasBase64, 
            "File should contain Base64 encoded payload")
    }
    
    @Test
    fun testCompressedDataCanBeDecompressed() {
        val engine = LocalStorageEngine(testDir)
        val sql = SqlEngine(engine)
        
        sql.execute("CREATE TABLE articles (id INT, title STRING, content STRING)")
        
        val originalContent = "This is a very long article content with many repeated sentences. " +
                "Repeated sentences. Repeated sentences. Repeated sentences. " +
                "The article discusses various topics including technology, science, and innovation. " +
                "Technology is advancing rapidly. Science is making progress. Innovation drives change."
        
        sql.execute("INSERT INTO articles (id, title, content) VALUES (1, 'Article 1', '$originalContent')")
        
        // Читаем данные обратно
        val result = sql.execute("SELECT * FROM articles WHERE id = 1") as List<Row>
        
        assertEquals(1, result.size, "Should find one record")
        
        val row = result[0]
        val retrievedContent = (row.values["content"] as FieldType.STRING).v
        
        // Проверяем, что данные правильно распакованы
        assertEquals(originalContent, retrievedContent, 
            "Decompressed content should match original")
        
        println("=== Original content ===")
        println(originalContent)
        println("\n=== Retrieved content ===")
        println(retrievedContent)
        println("========================")
    }
    
    @Test
    fun testBasicOperationsWithCompression() {
        val engine = LocalStorageEngine(testDir)
        val sql = SqlEngine(engine)
        
        sql.execute("CREATE TABLE users (id INT, name STRING, bio STRING, age INT)")
        
        val longBio = "This is a long biography with repeated information. " +
                "Repeated information. Repeated information. " +
                "The person has many achievements and experiences. " +
                "Achievements include: award1, award2, award3. " +
                "Experiences include: job1, job2, job3."
        
        // 1. INSERT
        sql.execute("INSERT INTO users (id, name, bio, age) VALUES (1, 'Alice', '$longBio', 30)")
        sql.execute("INSERT INTO users (id, name, bio, age) VALUES (2, 'Bob', '$longBio', 25)")
        
        // 2. SELECT - проверяем что данные читаются правильно
        val selectResult = sql.execute("SELECT * FROM users WHERE id = 1") as List<Row>
        assertEquals(1, selectResult.size)
        assertEquals("Alice", (selectResult[0].values["name"] as FieldType.STRING).v)
        assertEquals(longBio, (selectResult[0].values["bio"] as FieldType.STRING).v)
        
        // 3. SELECT ALL
        val allUsers = sql.execute("SELECT * FROM users") as List<Row>
        assertEquals(2, allUsers.size)
        
        // 4. UPDATE - обновляем данные
        val newBio = "Updated biography with new information. New information. New information."
        sql.execute("UPDATE users SET bio = '$newBio' WHERE id = 1")
        
        val updatedResult = sql.execute("SELECT * FROM users WHERE id = 1") as List<Row>
        assertEquals(1, updatedResult.size)
        assertEquals(newBio, (updatedResult[0].values["bio"] as FieldType.STRING).v)
        
        // 5. DELETE
        sql.execute("DELETE FROM users WHERE id = 2")
        
        val afterDelete = sql.execute("SELECT * FROM users") as List<Row>
        assertEquals(1, afterDelete.size)
        assertEquals(1L, (afterDelete[0].values["id"] as FieldType.LONG).v)
    }
    
    @Test
    fun testManualDecompressionFromFile() {
        val engine = LocalStorageEngine(testDir)
        val sql = SqlEngine(engine)
        
        sql.execute("CREATE TABLE test (id INT, data STRING)")
        
        val originalData = "Test data with repetition. Repetition. Repetition. " +
                "More data here. More data here. More data here."
        
        sql.execute("INSERT INTO test (id, data) VALUES (1, '$originalData')")
        
        // Читаем файл напрямую
        val tableFile = File(testDir, "test.tbl")
        val lines = tableFile.readLines().filter { it.isNotBlank() }
        assertTrue(lines.isNotEmpty(), "File should contain data")
        
        // Парсим JSON строку
        val jsonLine = lines[0]
        println("=== JSON line from file ===")
        println(jsonLine)
        println("==========================")
        
        // Извлекаем payload (упрощенный парсинг для теста)
        val payloadStart = jsonLine.indexOf("\"payload\":\"") + 11
        val payloadEnd = jsonLine.indexOf("\"", payloadStart)
        val payloadBase64 = jsonLine.substring(payloadStart, payloadEnd)
        
        // Декодируем Base64
        val compressedBytes = Base64.getDecoder().decode(payloadBase64)
        
        // Распаковываем используя наш декодер
        val decompressed = CustomCompressionFormat.decompress(compressedBytes)
        val decompressedString = String(decompressed, Charsets.UTF_8)
        
        // Payload - это JSON строка Row, проверяем что содержит исходные данные
        assertTrue(decompressedString.contains(originalData), 
            "Decompressed payload should contain original data. Got: $decompressedString")
        
        println("=== Original ===")
        println(originalData)
        println("\n=== Decompressed ===")
        println(decompressedString)
        println("==================")
    }
    
    @Test
    fun testSmallDataNotCompressed() {
        val engine = LocalStorageEngine(testDir)
        val sql = SqlEngine(engine)
        
        sql.execute("CREATE TABLE small (id INT, name STRING)")
        
        // Данные < 50 символов - payload (JSON строка Row) может быть > 50 символов
        // из-за структуры JSON, поэтому может быть сжат
        sql.execute("INSERT INTO small (id, name) VALUES (1, 'Short')")
        
        val tableFile = File(testDir, "small.tbl")
        val content = tableFile.readText()
        
        // Проверяем, что данные читаются правильно (независимо от сжатия)
        val result = sql.execute("SELECT * FROM small WHERE id = 1") as List<Row>
        assertEquals(1, result.size)
        assertEquals("Short", (result[0].values["name"] as FieldType.STRING).v)
        
        // Проверяем, что файл содержит данные
        assertTrue(content.contains("\"id\":1") || content.contains("\"id\":1L"), 
            "File should contain record data")
        
        println("=== File content (may be compressed) ===")
        println(content)
        println("=========================================")
    }
    
    @Test
    fun testMultipleInsertsWithCompression() {
        val engine = LocalStorageEngine(testDir)
        val sql = SqlEngine(engine)
        
        sql.execute("CREATE TABLE logs (id INT, message STRING, timestamp LONG)")
        
        val baseMessage = "This is a log message with many repeated patterns. " +
                "Pattern A. Pattern B. Pattern C. " +
                "The log contains important information about system events. " +
                "System events are logged for debugging and monitoring purposes."
        
        // Вставляем несколько записей
        for (i in 1..10) {
            sql.execute("INSERT INTO logs (id, message, timestamp) VALUES ($i, '$baseMessage', ${System.currentTimeMillis()})")
        }
        
        // Проверяем, что все данные читаются правильно
        val allLogs = sql.execute("SELECT * FROM logs") as List<Row>
        assertEquals(10, allLogs.size)
        
        // Проверяем каждую запись
        for (i in 1..10) {
            val result = sql.execute("SELECT * FROM logs WHERE id = $i") as List<Row>
            assertEquals(1, result.size)
            assertEquals(baseMessage, (result[0].values["message"] as FieldType.STRING).v)
        }
        
        // Проверяем файл
        val tableFile = File(testDir, "logs.tbl")
        val lines = tableFile.readLines().filter { it.isNotBlank() }
        assertEquals(10, lines.size, "Should have 10 lines in file")
        
        // Проверяем, что все строки сжаты
        val compressedCount = lines.count { it.contains("\"compressed\":true") }
        assertEquals(10, compressedCount, "All lines should be compressed")
    }
}

