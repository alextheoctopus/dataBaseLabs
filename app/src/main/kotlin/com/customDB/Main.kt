package com.customDB

import com.customDB.api.FieldType.STRING
import com.customDB.api.LocalStorageEngine
import com.customDB.api.TableSchema



fun main() {
    val t_table = TableSchema(
        name = "TestTable",
        fields = listOf(
            TableSchema.Column("id", STRING(""), false),
            TableSchema.Column("test_field", STRING(""), true),
        )
    )
    LocalStorageEngine().createTable(t_table)
}