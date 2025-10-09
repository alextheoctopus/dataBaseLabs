package com.customDB

import com.customDB.api.*
import com.customDB.api.FieldType.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FilterAndDeleteByPayloadTest {

    @TempDir
    lateinit var baseDir: File

    private fun newTable(): Table {
        val schema = TableSchema(
            name = "Users",
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
    fun filter_and_delete_by_payload_fields() {
        val table = newTable()

        table.insert(Row(mutableMapOf("lastName" to STRING("Smith"),  "age" to LONG(30), "active" to BOOL(true))))
        table.insert(Row(mutableMapOf("lastName" to STRING("Smith"),  "age" to LONG(31), "active" to BOOL(true))))
        table.insert(Row(mutableMapOf("lastName" to STRING("Adams"),  "age" to LONG(30), "active" to BOOL(false))))
        table.insert(Row(mutableMapOf("lastName" to STRING("Smith"),  "age" to LONG(30), "active" to BOOL(false))))

        val got = table.get(mutableMapOf("lastName" to STRING("Smith"), "age" to LONG(30))).orEmpty()
        assertEquals(2, got.size, "Expected 2 rows for (Smith, age=30), but got: $got")
        assertTrue(
            got.all { it.payload.values["lastName"] == STRING("Smith") && it.payload.values["age"] == LONG(30) },
            "Filtered rows must all match (Smith, age=30), but got: $got"
        )

        val deleted = table.delete(mutableMapOf("lastName" to STRING("Smith"), "active" to BOOL(false)))
        assertTrue(deleted, "Delete by payload (Smith, active=false) should return true")

        val after = table.get(mutableMapOf()).orEmpty()
        assertTrue(
            after.none { it.payload.values["lastName"] == STRING("Smith") && it.payload.values["active"] == BOOL(false) },
            "Rows with (Smith, active=false) must be absent after delete, but got: $after"
        )
    }
}
