package com.customDB.api

/** Кодек сериализации одной записи (payload), чтобы хранить её в файле. */
interface RecordCodec {
    /** Кодирует только values (без id). */
    fun encode(values: Map<String, Any?>): ByteArray
    /** Декодирует values (без id). */
    fun decode(bytes: ByteArray): Map<String, Any?>
}

/** Формат файла: как хранить tombstone, id, длину и payload. */
interface RecordFormat {
    /** Записать запись (append) и вернуть физическое смещение начала записи. */
    fun append(tombstone: Boolean, id: RowId, payload: ByteArray): Long
    /** Прочитать запись с указанного смещения. */
    fun readAt(offset: Long): Record

    /** Пометить запись как tombstone по смещению. */
    fun markDeleted(offset: Long)

    data class Record(
        val tombstone: Boolean,
        val id: RowId,
        val payload: ByteArray
    )
}
