package com.customDB.api

import kotlinx.serialization.Serializable

/** Кодек сериализации одной записи (payload), чтобы хранить её в файле. */
interface RecordCodec {
    /** Кодирует только values (без id). */
    fun encode(values: Map<String, FieldType?>): ByteArray
    /** Декодирует values (без id). */
    fun decode(bytes: ByteArray): Map<String, Any?>
}

/** Формат файла: как хранить tombstone, id, длину и payload. */
@Serializable
class RecordFormat() {
    /** Записать запись (append) и вернуть физическое смещение начала записи. */
    fun append(tombstone: Boolean, id: FieldType.LONG, payload: String): Long{
        return 0
    }
    /** Прочитать запись с указанного смещения. */
//    fun readAt(offset: Long): Record
//
//    /** Пометить запись как tombstone по смещению. */
//    fun markDeleted(offset: Long)
    @Serializable
    data class RecordFile(
        val tombstone: Boolean,
        val id: RowId,
        val payload: ByteArray
    )
}

