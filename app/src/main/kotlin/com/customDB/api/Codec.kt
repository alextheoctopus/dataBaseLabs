package com.customDB.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile

class RecordFormat(private val file: File) {
    private val json = Json { encodeDefaults = true }

//    @Serializable
//    data class RecordLine(
//        val tombstone: Boolean,
//        val id: Long,
//        val payload: String,
//    )

    @Serializable
    data class RecordLineLocal(
        var tombstone: Boolean,
        val id: Long,
        val payload: Row,
    )

    private fun getSizeDynamically(record: RecordLineLocal, schema: TableSchema): Int {
        var size = 0

        size += 1 // tombstone
        size += 8 // id

        for (column in schema.fields) {
            size += 1 // null-flag

            val value = record.payload.values[column.name]
            if (value == null) continue

            size += when (column.type) {
                is FieldType.STRING -> {
                    val bytes = (value as FieldType.STRING).v.toByteArray(Charsets.UTF_8)
                    4 + bytes.size // 4 байта длина + содержимое
                }

                is FieldType.LONG, is FieldType.PK -> 8
                is FieldType.DOUBLE -> 8
                is FieldType.BOOL -> 1
                is FieldType.BYTES -> {
                    val bytes = (value as FieldType.BYTES).v
                    4 + bytes.size
                }

                else -> 0
            }
        }

        return size
    }


    fun append(record: RecordLineLocal, schema: TableSchema) {
        val page = java.nio.ByteBuffer.allocate(getSizeDynamically(record, schema))//4kb
        page.put(if (record.tombstone) 1 else 0)
        page.putLong(record.id)

        for (field in schema.fields) {
            val value = record.payload.values[field.name]

            page.put(if (value == null) 1 else 0)
            if (value == null) continue

            when (field.type) {
                is FieldType.STRING -> {
                    val bytes = (value as FieldType.STRING).v.toByteArray(Charsets.UTF_8)
                    page.putInt(bytes.size)
                    page.put(bytes)
                }

                is FieldType.LONG, is FieldType.PK -> {
                    val v = when (value) {
                        is FieldType.LONG -> value.v
                        is FieldType.PK -> value.start
                        else -> error("Unexpected LONG-like value")
                    }
                    page.putLong(v)
                }

                is FieldType.DOUBLE -> page.putDouble((value as FieldType.DOUBLE).v)
                is FieldType.BOOL -> page.put(if ((value as FieldType.BOOL).v) 1 else 0)
                is FieldType.BYTES -> {
                    val bytes = (value as FieldType.BYTES).v
                    page.putInt(bytes.size)
                    page.put(bytes)
                }

                else -> error("Unsupported type ${field.type}")
            }
        }

        page.flip()
        val bytes = ByteArray(page.limit())
        page.get(bytes)

        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(bytes)
        }
    }

    fun readAll(schema: TableSchema): List<RecordLineLocal> {
        if (!file.exists()) return emptyList()
        val list = mutableListOf<RecordLineLocal>()
        val bytes = file.readBytes()
        val buffer = java.nio.ByteBuffer.wrap(bytes)

        while (buffer.remaining() > 0) {
            val tombstone = buffer.get().toInt() == 1
            val id = buffer.long

            val values = mutableMapOf<String, FieldType?>()
            for (column in schema.fields) {
                val isNull = buffer.get().toInt() == 1
                if (isNull) {
                    continue
                }

                val field = when (column.type) {
                    is FieldType.STRING -> {
                        val len = buffer.int
                        val strBytes = ByteArray(len)
                        buffer.get(strBytes)
                        FieldType.STRING(String(strBytes, Charsets.UTF_8))
                    }

                    is FieldType.LONG, is FieldType.PK -> FieldType.LONG(buffer.long)
                    is FieldType.DOUBLE -> FieldType.DOUBLE(buffer.double)
                    is FieldType.BOOL -> FieldType.BOOL(buffer.get().toInt() == 1)
                    is FieldType.BYTES -> {
                        val len = buffer.int
                        val b = ByteArray(len)
                        buffer.get(b)
                        FieldType.BYTES(b)
                    }

                    else -> null
                }
                values[column.name] = field
            }
            list.add(RecordLineLocal(tombstone, id, Row(values)))
        }

        return list
    }

}
