package com.customDB

import com.customDB.api.*
import com.customDB.api.FieldType.*


fun main() {
    val schemaTable = TableSchema(
        name = "TestTable",
        fields = listOf(
            TableSchema.Column("lastName", STRING(""), false),
            TableSchema.Column("age", LONG(0), true),
        )
    );
    val engine = LocalStorageEngine();
    val localTable: Table = engine.createTable(schemaTable)
    val idRow = localTable.insert(
        Row(
            values =
                mapOf("lastName" to STRING("Beznosova"), "age" to LONG(23))
        )
    )
    println(idRow)
}