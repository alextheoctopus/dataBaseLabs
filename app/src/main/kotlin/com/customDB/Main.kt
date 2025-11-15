package com.customDB

import com.customDB.api.*
import com.customDB.api.FieldType.*
import com.customDB.api.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.*
import org.apache.spark.sql.RowFactory
import java.io.File
import kotlin.system.measureTimeMillis

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

    localTable.insert(
        Row(
            mutableMapOf<String, FieldType?>(
                "id" to FieldType.LONG(1),
                "name" to FieldType.STRING("Alice"),
                "age" to FieldType.LONG(25)
            )
        )
    )

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

    convertTableToParquetOrc("output")
}

fun convertTableToParquetOrc(outputDir: String) {
    val spark = SparkSession.builder()
        .appName("CustomDB to Parquet/ORC")
        .master("local[*]")
        .config("spark.driver.bindAddress", "127.0.0.1")
        .config("spark.driver.host", "127.0.0.1")
        .getOrCreate()

    val allRecords: List<RecordFormat.RecordLineLocal> = (1..1_000_000).map { i ->
        RecordFormat.RecordLineLocal(
            id = i.toLong(),
            tombstone = false,
            payload = Row(
                mutableMapOf(
                    "id" to FieldType.LONG(i.toLong()),
                    "name" to FieldType.STRING("User$i"),
                    "age" to FieldType.LONG((18 + i % 50).toLong())
                )
            )
        )
    }

    println("DEBUG: allRecords loaded = ${allRecords.size}")

    // Преобразуем в Spark Rows
    val sparkRows = allRecords.map { rec ->
        RowFactory.create(
            rec.id as java.lang.Long, // RowFactory требует java.lang.Long
            rec.payload.values["name"]?.toString(),
            (rec.payload.values["age"] as? FieldType.LONG)?.v as java.lang.Long?
        )
    }

    val schema = StructType(
        arrayOf(
            StructField("id", DataTypes.LongType, false, org.apache.spark.sql.types.Metadata.empty()),
            StructField("name", DataTypes.StringType, true, org.apache.spark.sql.types.Metadata.empty()),
            StructField("age", DataTypes.LongType, true, Metadata.empty())
        )
    )

    val df = spark.createDataFrame(sparkRows, schema)
    val filteredDF = df.filter("age > 20")

    // Сохраняем Parquet и ORC
    val parquetDir = "$outputDir/parquet"
    val orcDir = "$outputDir/orc"
    filteredDF.write().mode("overwrite").parquet(parquetDir)
    filteredDF.write().mode("overwrite").orc(orcDir)

    // Замер времени чтения
    val parquetReadTime = measureTimeMillis { spark.read().parquet(parquetDir).show() }
    val orcReadTime = measureTimeMillis { spark.read().orc(orcDir).show() }

    // Размер файлов
    val parquetSize = File(parquetDir).walkTopDown().sumOf { if (it.isFile) it.length() else 0L }
    val orcSize = File(orcDir).walkTopDown().sumOf { if (it.isFile) it.length() else 0L }

    println("Parquet size = $parquetSize bytes, read time = $parquetReadTime ms")
    println("ORC size = $orcSize bytes, read time = $orcReadTime ms")

    spark.stop()
}
