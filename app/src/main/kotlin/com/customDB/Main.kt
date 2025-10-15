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

    val sql = SqlEngine(engine)

    sql.execute("CREATE TABLE Users (id INT, name STRING, age INT)")
    sql.execute("INSERT INTO Users (id, name, age) VALUES (1, 'Alice', 25)")
    val result = sql.execute("SELECT * FROM Users WHERE name = 'Alice'")
    sql.execute("DROP TABLE Users")
    println(result)

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
            println("TOMBSTONE: ${row.tombstone} PAYLOAD:${row.payload}")
        }
    }
    val del = localTable.delete(mapOf("lastName" to STRING("ABC")))
    println("del: $del")
    val t=localTable.compact();

    val u = localTable.get(mapOf("age" to LONG(23)))
    if (u != null) {
        for (row in u) {
            println("TOMBSTONE: ${row.tombstone} PAYLOAD:${row.payload}")
        }
    }
//    val d = localTable.upsert(
//        FieldType.LONG(3), (Row(mutableMapOf("lastName" to FieldType.STRING("Nosova"))))
//    )

//
//    val c = localTable.get(mapOf())
//    if (c != null) {
//        for (row in c) {
//            println("TOMBSTONE: ${row.tombstone} PAYLOAD:${row.payload}")
//        }
//    }
//    val del2 = localTable.delete(mapOf("lastName" to STRING("Nosova")))
//    val c2 = localTable.get(mapOf())
//    if (c2 != null) {
//        for (row in c2) {
//            println("TOMBSTONE2: ${row.tombstone} PAYLOAD2:${row.payload}")
//        }
//    }
}
