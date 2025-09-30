package com.customDB

import com.customDB.api.FieldType.BOOL
import com.customDB.api.FieldType.LONG
import com.customDB.api.FieldType.PK
import com.customDB.api.FieldType.STRING
import com.customDB.api.LocalStorageEngine
import com.customDB.api.Row
import com.customDB.api.RowId
import com.customDB.api.Table
import com.customDB.api.TableSchema
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
            Row(mapOf("lastName" to STRING("Beznosova"), "age" to LONG(23))),
        )
        assertTrue(line.contains("\"id\":1"))

        val got = table.get(RowId(1))
        assertEquals(
            Row(mapOf("lastName" to STRING("Beznosova"), "age" to LONG(23))),
            got,
        )

        val deleted = table.delete(RowId(1))
        assertTrue(deleted)

        val gotAfterDelete = table.get(RowId(1))
        assertNull(gotAfterDelete)
    }
}
