package com.customDB.api

/** Контракт индекса (встроенного или внешнего). */
interface Index : AutoCloseable {
    val name: String
    val table: String
    val fields: List<String>           // по каким полям строится индекс
    val unique: Boolean

    /** Зарегистрировать актуальную позицию записи (смещение/ключ реализации). */
    fun upsert(rowId: RowId, values: Map<String, FieldType?>, physicalRef: Long)

    /** Удалить запись из индекса. */
    fun remove(rowId: RowId)

    /** Поисковые операции по индексу. Возвращают RowId (или физические ссылки). */
    fun findExact(lookup: Map<String, FieldType?>): Sequence<RowId>
    fun range(field: String, from: FieldType?, to: FieldType?, inclusive: Boolean = true): Sequence<RowId>

    override fun close()
}
