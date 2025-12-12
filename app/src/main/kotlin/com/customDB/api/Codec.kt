package com.customDB.api

import com.customDB.compression.GzipFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64

class RecordFormat(private val file: File, private val enableCompression: Boolean = true) {
    private val json = Json { encodeDefaults = true }

    @Serializable
    data class RecordLine(
        val tombstone: Boolean,
        val id: Long,
        val payload: String,
        val compressed: Boolean = false,
    )
    @Serializable
    data class RecordLineLocal(
        var tombstone: Boolean,
        val id: Long,
        val payload: Row,
    )

    fun append(
        tombstone: Boolean,
        id: RowId,
        payload: String,
    ): String {
        val longId = (id as FieldType.LONG).v
        
        val (finalPayload, isCompressed) = if (enableCompression && payload.length > 50) {
            try {
                val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                val compressed = GzipFormat.compress(payloadBytes)
                val base64Compressed = Base64.getEncoder().encodeToString(compressed)
                base64Compressed to true
            } catch (e: Exception) {
                payload to false
            }
        } else {
            payload to false
        }
        
        val line = json.encodeToString(
            RecordLine.serializer(),
            RecordLine(tombstone, longId, finalPayload, isCompressed),
        ) + "\n"

        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(line.toByteArray(Charsets.UTF_8))
        }
        return line.trimEnd('\n')
    }

    fun readAll(): List<RecordLine> {
        if (!file.exists()) return emptyList()
        return file.readLines()
            .filter { it.isNotBlank() }
            .map { lineStr ->
                val record = json.decodeFromString(RecordLine.serializer(), lineStr)
                
                val decompressedPayload = if (record.compressed) {
                    try {
                        val compressedBytes = Base64.getDecoder().decode(record.payload)
                        val decompressed = GzipFormat.decompress(compressedBytes)
                        String(decompressed, Charsets.UTF_8)
                    } catch (e: Exception) {
                        record.payload
                    }
                } else {
                    record.payload
                }
                
                record.copy(payload = decompressedPayload, compressed = false)
            }
    }
}
