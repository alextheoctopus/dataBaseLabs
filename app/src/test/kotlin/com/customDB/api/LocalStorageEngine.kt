package com.customDB

import com.customDB.api.*
import com.customDB.api.FieldType.BOOL
import com.customDB.api.FieldType.LONG
import com.customDB.api.FieldType.PK
import com.customDB.api.FieldType.STRING
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalTableTest {


    private val tableName = "TestTable"
    private val baseDir = File("src/test") // как в main
    private val tblFile = File(baseDir, "$tableName.tbl")
    private val metaFile = File(baseDir, "$tableName.meta")

    private fun newTable(): Table {
        val schema = TableSchema(
            name = tableName,
            fields = listOf(
                TableSchema.Column("id", PK(start = 0), false),
                TableSchema.Column("lastName", STRING(""), false),
                TableSchema.Column("age", LONG(0), true),
                TableSchema.Column("active", BOOL(false), true),
            ),
        )
        val engine = LocalStorageEngine(baseDir) // как в main
        return engine.getOrCreateTable(schema)
    }

    @AfterEach
    fun cleanup() {
        tblFile.delete()
        metaFile.delete()
        baseDir.delete()
    }

    @Test
    fun insert_get_delete_flow() {
        val table = newTable()

        val line = table.insert(
            Row(mutableMapOf("lastName" to STRING("Beznosova"), "age" to LONG(23))),
        )
        assertTrue(line.contains("\"id\":1"))

        val line2 = table.insert(
            Row(mutableMapOf("lastName" to STRING("Komarov"), "age" to LONG(23))),
        )
        assertTrue(line2.contains("\"id\":2"))

        val got = table.get(mutableMapOf("age" to LONG(23)))
        assertEquals(
            listOf(
                RecordFormat.RecordLineLocal(
                    tombstone = true,
                    id = 1L,
                    Row(mutableMapOf("lastName" to STRING("Beznosova"), "age" to LONG(23)))
                ),
                RecordFormat.RecordLineLocal(
                    tombstone = true,
                    id = 2L,
                    Row(mutableMapOf("lastName" to STRING("Komarov"), "age" to LONG(23)))
                ),
            ),
            got
        )

        val deleted = table.delete(mutableMapOf("id" to LONG(1)))
        assertTrue(deleted)

        val gotAfterDelete = table.get(mutableMapOf("age" to LONG(23)))
        assertEquals(
            listOf(
                RecordFormat.RecordLineLocal(
                    tombstone = true,
                    id = 2L,
                    Row(mutableMapOf("lastName" to STRING("Komarov"), "age" to LONG(23)))
                ),
            ),
            gotAfterDelete
        )

        val upserted = table.upsert(RowId(2), Row(mutableMapOf("lastName" to FieldType.STRING("Sovenko"))));
        assertTrue(upserted)

        val gotAfterUpsert = table.get(mutableMapOf())
        assertEquals(
            listOf(
                RecordFormat.RecordLineLocal(
                    tombstone = true,
                    id = 2L,
                    Row(mutableMapOf("lastName" to STRING("Sovenko"), "age" to LONG(23)))
                ),
            ),
            gotAfterUpsert
        )

        table.compact()
        val gotAfterCompact = table.get(mutableMapOf())
        assertEquals(
            listOf(
                RecordFormat.RecordLineLocal(
                    tombstone = true,
                    id = 2L,
                    Row(mutableMapOf("lastName" to STRING("Sovenko"), "age" to LONG(23)))
                ),
            ),
            gotAfterCompact
        )
    }
}
