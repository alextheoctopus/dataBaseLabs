package com.customDB.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile

class RecordFormat(private val file: File) {
    private val json = Json { encodeDefaults = true }

    @Serializable
    data class RecordLine(
        val tombstone: Boolean = false,
        val id: Long,
        val payload: String,
    )

    /** Записать запись в файл */
    fun append(
        tombstone: Boolean,
        id: RowId,
        payload: String,
    ): String {
        val longId = (id as FieldType.LONG).v
        val line = json.encodeToString(
            RecordLine.serializer(),
            RecordLine(tombstone, longId, payload),
        ) + "\n"

        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(line.toByteArray(Charsets.UTF_8))
        }
        return line.trimEnd('\n')
    }

    /** Прочитать все записи */
    fun readAll(): List<RecordLine> {
        if (!file.exists()) return emptyList()
        return file.readLines()
            .filter { it.isNotBlank() }
            .map { json.decodeFromString(RecordLine.serializer(), it) }
    }
}
