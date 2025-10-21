package com.customDB

import com.customDB.api.*
import com.customDB.api.FieldType.*
import java.io.File

import com.google.protobuf.InvalidProtocolBufferException
import java.nio.ByteBuffer
import kotlin.system.measureNanoTime
import com.customDB.protobuf.RecordOuterClass.Record
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
//Проект по созданию реляционный СУБД с файловым хранением на kotlin
fun main() {
    println("Try to create schema")
    val schemaTable =
        TableSchema(
            name = "SalesDataset",
            fields =
                listOf(
                    TableSchema.Column("id", PK(start = 0), false),
                    TableSchema.Column("InvoiceNo", LONG(0), true),
                    TableSchema.Column("StockCode", STRING(""), true),
                    TableSchema.Column("Description", STRING(""), true),
                    TableSchema.Column("Quantity", LONG(0), true),
                    TableSchema.Column("InvoiceDate", STRING(""), true),
                    TableSchema.Column("UnitPrice", DOUBLE(0.0), true),
                    TableSchema.Column("CustomerID", STRING(""), true),
                    TableSchema.Column("Country", STRING(""), true),
                ),
        )
    println("Schema ok")
    val engine = LocalStorageEngine()
    println("engine ok")
    val localTable: Table = engine.getOrCreateTable(schemaTable)
    println("table ok")

    fun compareEncoding() {
        val basepath = File("src", "datasets");
        val datasetFile = File(basepath, "data.csv")
        val startEncode1 = System.nanoTime()
        var bytesCustom = 0
        val keys = listOf(
            "InvoiceNo",
            "StockCode",
            "Description",
            "Quantity",
            "InvoiceDate",
            "UnitPrice",
            "CustomerID",
            "Country"
        )

        fun reuseMapBuilder(values: List<String>): MutableMap<String, FieldType?> {
            val fieldMap = keys.zip(values).associate { (key, value) ->
                key to when (key) {
                    "Quantity" -> LONG(value.toLongOrNull() ?: 0)
                    "UnitPrice" -> DOUBLE(value.toDoubleOrNull() ?: 0.0)
                    "InvoiceNo" -> LONG(value.toLongOrNull() ?: 0)
                    "StockCode" -> STRING(value)
                    "Description" -> STRING(value)
                    "Quantity" -> LONG(value.toLongOrNull() ?: 0)
                    "InvoiceDate" -> STRING(value)
                    "UnitPrice" -> DOUBLE(value.toDoubleOrNull() ?: 0.0)
                    "CustomerID" -> STRING(value)
                    "Country" -> STRING(value)
                    else -> null
                }
            }.toMutableMap()
            return fieldMap
        }
        datasetFile.useLines { lines ->
            for (line in lines.take(100)) {
                val values = line.split(",")
                val row = Row(reuseMapBuilder(values))
                bytesCustom += localTable.insert(row)
            }
        }
        val timeEncode1 = System.nanoTime() - startEncode1


        val startDecode1 = System.nanoTime()
        localTable.get(mapOf())
        val timeDecode1 = System.nanoTime() - startDecode1


        var totalProtoSize = 0
        val outputStream = ByteArrayOutputStream()

        val startEncode2 = System.nanoTime()
        datasetFile.useLines { lines ->
            for ((index, line) in lines.take(100).withIndex()) {
                val values = line.split(",")
                val row = Row(reuseMapBuilder(values))

                val record = Record.newBuilder()
                    .setId(index + 1L)
                    .setInvoiceNo((row.values["InvoiceNo"] as? LONG)?.v.toString())
                    .setStockCode((row.values["StockCode"] as? STRING)?.v ?: "")
                    .setDescription((row.values["Description"] as? STRING)?.v ?: "")
                    .setQuantity((row.values["Quantity"] as? LONG)?.v ?: 0)
                    .setInvoiceDate((row.values["InvoiceDate"] as? STRING)?.v ?: "")
                    .setUnitPrice((row.values["UnitPrice"] as? DOUBLE)?.v ?: 0.0)
                    .setCustomerID((row.values["CustomerID"] as? STRING)?.v ?: "")
                    .setCountry((row.values["Country"] as? STRING)?.v ?: "")
                    .build()

                val bytes = record.toByteArray()
                totalProtoSize += bytes.size
                outputStream.write(bytes)
            }
        }
        val timeEncode2 = System.nanoTime() - startEncode2

        val allProtoBytes = outputStream.toByteArray()
        val startDecodeProto = System.nanoTime()
        val input = ByteArrayInputStream(allProtoBytes)
        repeat(100) {
            try {
                Record.parseDelimitedFrom(input)
            } catch (_: Exception) {
                // достигнут конец потока
            }
        }
        val decodeTimeProto = System.nanoTime() - startDecodeProto

        println("CustomBinary:")
        println("Size: ${bytesCustom} bytes")
        println("Encode time: ${timeEncode1 / 1_000_000.0} ms")
        println("Decode time: ${timeDecode1 / 1_000_000.0} ms")

        println("Protobuf:")
        println("Size: ${totalProtoSize} bytes")
        println("Encode time: ${timeEncode2 / 1_000_000.0} ms")
        println("Decode time: ${decodeTimeProto / 1_000_000.0} ms")

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