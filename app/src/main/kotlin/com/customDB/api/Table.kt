package com.customDB.api

import kotlinx.serialization.json.Json
import java.io.File

/** Контракт таблицы. */
interface Table {
    val name: String
    val schema: TableSchema
    val record: RecordFormat

    /** Вставить строку. Возвращаем СТРОКУ, записанную в .tbl */
    fun insert(values: Row): String

    fun get(id: RowId): Row?

    fun update(
        id: RowId,
        newValues: Map<String, FieldType?>,
    ): Boolean

    fun upsert(row: Row): RowId

    fun delete(id: RowId): Boolean

    fun scan(
        predicate: Predicate = Q.any(),
        sort: Sort = Sort(),
        limit: Int = Int.MAX_VALUE,
        offset: Int = 0,
    ): Cursor<Row>

    fun countApprox(): Long

    fun compact()
}

class LocalTable(
    override val name: String,
    override val schema: TableSchema,
    baseDir: File,
) : Table {
    private val json = Json { encodeDefaults = true }
    private val dataFile = File(baseDir, "$name.tbl")
    override val record: RecordFormat = RecordFormat(dataFile)

    /** Простой генератор id: 1 + число непустых строк в .tbl */
    private fun nextRowId(): RowId {
        if (!dataFile.exists()) return FieldType.LONG(1)
        val lines = dataFile.useLines { seq -> seq.count { it.isNotBlank() } }
        return FieldType.LONG(lines + 1L)
    }

    /** Вставить строку и вернуть СТРОКУ, которая записана в .tbl */
    override fun insert(values: Row): String {
        val rowId = nextRowId()
        val payload = json.encodeToString(Row.serializer(), values)
        return record.append(
            tombstone = false,
            id = rowId,
            payload = payload,
        )
    }

    override fun get(id: RowId): Row? {
        val targetId = (id as FieldType.LONG).v
        val records = record.readAll()
        val rec = records.lastOrNull { it.id == targetId && !it.tombstone } ?: return null
        return json.decodeFromString(Row.serializer(), rec.payload)
    }


    override fun update(
        id: RowId,
        newValues: Map<String, FieldType?>,
    ): Boolean = TODO()

    override fun upsert(row: Row): RowId = TODO()

    override fun delete(id: RowId): Boolean = TODO()

    override fun scan(
        predicate: Predicate,
        sort: Sort,
        limit: Int,
        offset: Int,
    ): Cursor<Row> = TODO()

    override fun countApprox(): Long = TODO()

    override fun compact() {}
}
