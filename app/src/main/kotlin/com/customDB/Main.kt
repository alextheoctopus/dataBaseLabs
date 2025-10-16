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
//                    mutableMapOf("lastName" to STRING("Beznosova"), "age" to LONG(23)),
//            ),
//
//        )
//    localTable.insert(
//        Row(
//            values =
//                mutableMapOf("lastName" to STRING("ABC"), "age" to LONG(23)),
//
//            ),
//
//        )
//    println("idRow: $idRow")


    val d = localTable.get(mapOf("age" to LONG(23)))
    if (d != null) {
        for (row in d) {
            println("TOMBSTONE: ${row.tombstone} PAYLOAD:${row.payload.values}")
        }
    }
    val del = localTable.delete(mapOf("lastName" to STRING("ABC")))
    println("del: $del")
//    localTable.compact();
//
    val u = localTable.get(mapOf("age" to LONG(23)))
    if (u != null) {
        for (row in u) {
            println("TOMBSTONE: ${row.tombstone} PAYLOAD:${row.payload}")
        }
    }
    localTable.upsert(
        LONG(3), (Row(mutableMapOf("lastName" to FieldType.STRING("Komarov"))))
    )

//
    val c = localTable.get(mapOf())
    if (c != null) {
        for (row in c) {
            println("TOMBSTONE: ${row.tombstone} PAYLOAD:${row.payload}")
        }
    }

}
