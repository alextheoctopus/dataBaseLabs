package com.customDB.api

import java.io.File
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlEngineTest {

    private lateinit var engine: LocalStorageEngine
    private lateinit var sql: SqlEngine
    private val baseDir = File("build/testDB")

    @BeforeAll
    fun setup() {
        // очистка перед запуском
        if (baseDir.exists()) baseDir.deleteRecursively()
        baseDir.mkdirs()
        engine = LocalStorageEngine(baseDir)
        sql = SqlEngine(engine)
    }

    @AfterAll
    fun cleanup() {
        engine.close()
        baseDir.deleteRecursively()
    }

    @Test
    fun `test full CRUD lifecycle`() {
        // CREATE
        sql.execute("CREATE TABLE Users (id INT, name STRING, age INT)")
        val metaFile = File(baseDir, "Users.meta")
        assertTrue(metaFile.exists(), "meta file should be created")

        // INSERT 3 users
        sql.execute("INSERT INTO Users (id, name, age) VALUES (1, 'Alice', 25)")
        sql.execute("INSERT INTO Users (id, name, age) VALUES (2, 'Bob', 30)")
        sql.execute("INSERT INTO Users (id, name, age) VALUES (3, 'Carol', 27)")

        // SELECT check
        val result1 = sql.execute("SELECT * FROM Users WHERE name = 'Alice'") as List<Row>
        println("DEBUG: result1 = $result1")
        assertEquals(1, result1.size)
        assertEquals("Alice", (result1[0].values["name"] as FieldType.STRING).v)
        assertEquals(25L, (result1[0].values["age"] as FieldType.LONG).v)

        // DELETE one user
        sql.execute("DELETE FROM Users WHERE name = 'Bob'")
        val resultAfterDelete = sql.execute("SELECT * FROM Users WHERE name = 'Bob'") as List<Row>
        assertTrue(resultAfterDelete.isEmpty(), "Bob should be deleted")

        // REINSERT same user
        sql.execute("INSERT INTO Users (id, name, age) VALUES (2, 'Bob', 30)")
        val resultAfterReinsert = sql.execute("SELECT * FROM Users WHERE name = 'Bob'") as List<Row>
        assertEquals(1, resultAfterReinsert.size, "Bob should be reinserted")

        // UPDATE one user
        sql.execute("UPDATE Users SET age = 26 WHERE name = 'Alice'")
        val resultUpdated = sql.execute("SELECT * FROM Users WHERE name = 'Alice'") as List<Row>
        assertEquals(26L, (resultUpdated[0].values["age"] as FieldType.LONG).v)

        // DELETE all and compact
        sql.execute("DELETE FROM Users WHERE age = 27")
        val table = engine.getOrCreateTableFromMeta("Users")
        table.compact()
        val all = sql.execute("SELECT * FROM Users WHERE name = 'Carol'") as List<Row>
        assertTrue(all.isEmpty())
    }

    @Test
    fun `test select all without where`() {
        sql.execute("CREATE TABLE People (id INT, name STRING, age INT)")
        sql.execute("INSERT INTO People (id, name, age) VALUES (1, 'John', 22)")
        sql.execute("INSERT INTO People (id, name, age) VALUES (2, 'Mary', 21)")

        val result = sql.execute("SELECT * FROM People") as List<Row>
        assertEquals(2, result.size)
        val names = result.map { (it.values["name"] as FieldType.STRING).v }
        assertTrue(names.containsAll(listOf("John", "Mary")))
    }
}