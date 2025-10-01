package com.customDB

import com.customDB.api.*
import com.customDB.api.FieldType.*

fun main() {
    println("Try to create schema")
    val schemaTable =
        TableSchema(
            name = "TestTable",
            fields =
                listOf(
                    TableSchema.Column("id", PK(start = 0), false),
                    TableSchema.Column("lastName", STRING(""), false),
                    TableSchema.Column("age", LONG(0), true),
                ),
        )
    println("Schema ok")
    val engine = LocalStorageEngine()
    println("engine ok")
    val localTable: Table = engine.getOrCreateTable(schemaTable)
    println("table ok")

//    val idRow =
//        localTable.insert(
//            Row(
//                values =
//                    mapOf("lastName" to STRING("Beznosova"), "age" to LONG(23)),
//            ),
//
//        )
//    localTable.insert(
//        Row(
//            values =
//                mapOf("lastName" to STRING("ABC"), "age" to LONG(23)),
//        ),
//
//        )
//    println("idRow: $idRow")


    val a = localTable.get(mapOf("age" to LONG(23)))
    println("a: $a")

    val del = localTable.delete(mapOf("lastName" to STRING("ABC")))
    println("del: $del")

//    val a2 = localTable.get(RowId(1))
//    println("a: $a2")
}
