package com.customDB

import com.customDB.api.*
import com.customDB.api.FieldType.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GetAllLiveRecordsTest {

    @TempDir
    lateinit var baseDir: File

    private fun newTable(): Table {
        val schema = TableSchema(
            name = "Items",
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
    fun get_all_returns_only_live_rows() {
        val table = newTable()

        table.insert(Row(mutableMapOf("lastName" to STRING("X"), "age" to LONG(1))))
        table.insert(Row(mutableMapOf("lastName" to STRING("Y"), "age" to LONG(2))))
        table.insert(Row(mutableMapOf("lastName" to STRING("Z"), "age" to LONG(3))))

        val deleted = table.delete(mutableMapOf("id" to LONG(2)))
        assertTrue(deleted)

        val all = table.get(mutableMapOf()).orEmpty()
        assertEquals(2, all.size)
        assertTrue(all.all { !it.tombstone })
        assertTrue(all.none { it.id == 2L })
    }
}
