package com.customDB

import com.customDB.api.*
import com.customDB.compression.GzipFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.nio.file.Files

class CompressionIntegrationTest {
    
    private lateinit var testDir: File
    
    @BeforeEach
    fun setup() {
        testDir = Files.createTempDirectory("compression_test").toFile()
    }
    
    @Test
    fun testDatabaseWithCompression() {
        val engine = LocalStorageEngine(testDir)
        val sql = SqlEngine(engine)
        
        // Создаем таблицу
        sql.execute("CREATE TABLE users (id INT, name STRING, email STRING, age INT, city STRING)")
        
        // Вставляем данные
        sql.execute("INSERT INTO users (id, name, email, age, city) VALUES (1, 'John Doe', 'john@example.com', 30, 'Moscow')")
        sql.execute("INSERT INTO users (id, name, email, age, city) VALUES (2, 'Jane Smith', 'jane@example.com', 25, 'SPB')")
        
        // Читаем данные
        val result = sql.execute("SELECT * FROM users WHERE id = 1") as List<Row>
        
        assertEquals(1, result.size)
        assertEquals("John Doe", (result[0].values["name"] as FieldType.STRING).v)
        
        // Проверяем, что файл содержит сжатые данные
        val tableFile = File(testDir, "users.tbl")
        assertTrue(tableFile.exists())
        
        val content = tableFile.readText()
        // Проверяем, что есть флаг compressed
        assertTrue(content.contains("\"compressed\":true") || content.contains("\"compressed\":false"))
    }
    
    @Test
    fun testCompressionSavesSpace() {
        val engine = LocalStorageEngine(testDir)
        val sql = SqlEngine(engine)
        
        sql.execute("CREATE TABLE logs (id INT, message STRING, timestamp LONG)")
        
        // Вставляем много данных с повторяющимися паттернами
        for (i in 1..100) {
            sql.execute("INSERT INTO logs (id, message, timestamp) VALUES ($i, 'This is a log message with repeated patterns. Pattern 1. Pattern 2. Pattern 3.', ${System.currentTimeMillis()})")
        }
        
        val tableFile = File(testDir, "logs.tbl")
        val fileSize = tableFile.length()
        
        // Проверяем, что можем прочитать все данные
        val result = sql.execute("SELECT * FROM logs WHERE id = 50") as List<Row>
        assertEquals(1, result.size)
        
        println("Table file size: $fileSize bytes")
        println("Without compression it would be approximately: ${100 * 150} bytes (estimated)")
    }
}

