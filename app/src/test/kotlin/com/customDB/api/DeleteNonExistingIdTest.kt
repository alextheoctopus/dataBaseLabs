package com.customDB

import com.customDB.api.*
import com.customDB.api.FieldType.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DeleteNonExistingIdTest {

    @TempDir
    lateinit var baseDir: File

    private fun newTable(): Table {
        val schema = TableSchema(
            name = "Accounts",
            fields = listOf(
                TableSchema.Column("id", PK(start = 0), false),
                TableSchema.Column("lastName", STRING(""), false),
                TableSchema.Column("age", LONG(0), true),
                TableSchema.Column("active", BOOL(false), true),
            ),
        )
        return LocalStorageEngine(baseDir).getOrCreateTable(schema)
    }

    @Test
    fun delete_non_existing_id_returns_false() {
        val table = newTable()

        table.insert(Row(mutableMapOf("lastName" to STRING("A"), "age" to LONG(10))))
        table.insert(Row(mutableMapOf("lastName" to STRING("B"), "age" to LONG(20))))

        val deleted = table.delete(mutableMapOf("id" to LONG(999)))
        assertFalse(deleted)

        val all = table.get(mutableMapOf()).orEmpty()
        assertEquals(2, all.size)
        assertTrue(all.all { !it.tombstone })
    }
}
