package com.customDB.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile

class RecordFormat(private val file: File) {
    private val json = Json { encodeDefaults = true }

    fun makeLine(
        tombstone: Boolean,
        id: RowId,
        payload: String,
    ): String =
        json.encodeToString(
            RecordLine.serializer(),
            RecordLine(tombstone = tombstone, id = id, payload = payload),
        )

    fun append(
        tombstone: Boolean,
        id: RowId,
        payload: String,
    ): String {
        val line = makeLine(tombstone, id, payload) + "\n"
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(line.toByteArray(Charsets.UTF_8))
        }
        return line.trimEnd('\n')
    }

    @Serializable
    private data class RecordLine(
        val tombstone: Boolean = false,
        val id: RowId,
        val payload: String,
    )
}
