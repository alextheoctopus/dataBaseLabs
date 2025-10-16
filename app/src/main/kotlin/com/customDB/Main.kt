package com.customDB

import com.customDB.api.*
import com.customDB.api.FieldType.*
import com.google.protobuf.InvalidProtocolBufferException
import java.nio.ByteBuffer
import kotlin.system.measureNanoTime
import com.customDB.protobuf.RecordOuterClass.Record

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

    fun compareEncoding() {
        val row = Row(mutableMapOf("id" to LONG(1), "lastName" to STRING("Beznosova"), "age" to LONG(23)))

        // --- Твой формат ---
        val startEncode1 = System.nanoTime()
        val bytesCustom = localTable.insert(row) // используем твой бинарный формат
        val timeEncode1 = System.nanoTime() - startEncode1

        val startDecode1 = System.nanoTime()
        localTable.get(mapOf())
        val timeDecode1 = System.nanoTime() - startDecode1

        // --- Protobuf ---
        val protoRecord = Record.newBuilder()
            .setId(1)
            .setLastName("Beznosova")
            .setAge(23)
            .build()

        val startEncode2 = System.nanoTime()
        val bytesProto = protoRecord.toByteArray()
        val timeEncode2 = System.nanoTime() - startEncode2

        val startDecode2 = System.nanoTime()
        Record.parseFrom(bytesProto)
        val timeDecode2 = System.nanoTime() - startDecode2

        println("CustomBinary:")
        println("Size: ${bytesCustom} bytes")
        println("Encode time: ${timeEncode1 / 1_000.0} ms")
        println("Decode time: ${timeDecode1 / 1_000.0} ms")

        println("Protobuf:")
        println("Size: ${bytesProto.size} bytes")
        println("Encode time: ${timeEncode2 / 1_000.0} ms")
        println("Decode time: ${timeDecode2 / 1_000.0} ms")
    }
    compareEncoding()
}

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


//    val d = localTable.get(mapOf("age" to LONG(23)))
//    if (d != null) {
//        for (row in d) {
//            println("TOMBSTONE: ${row.tombstone} PAYLOAD:${row.payload.values}")
//        }
//    }
//    val del = localTable.delete(mapOf("lastName" to STRING("ABC")))
//    println("del: $del")
//    localTable.compact();
//
//    val u = localTable.get(mapOf("age" to LONG(23)))
//    if (u != null) {
//        for (row in u) {
//            println("TOMBSTONE: ${row.tombstone} PAYLOAD:${row.payload}")
//        }
//    }
//    localTable.upsert(
//        LONG(3), (Row(mutableMapOf("lastName" to FieldType.STRING("Komarov"))))
//    )

//
//    val c = localTable.get(mapOf())
//    if (c != null) {
//        for (row in c) {
//            println("TOMBSTONE: ${row.tombstone} PAYLOAD:${row.payload}")
//        }
//    }