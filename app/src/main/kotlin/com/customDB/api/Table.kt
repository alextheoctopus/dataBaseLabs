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

    fun get(fields: Map<String, FieldType>): List<RecordFormat.RecordLineLocal>?

    fun update(
        id: RowId,
        newValues: Map<String, FieldType?>,
    ): Boolean

    fun upsert(row: Row): RowId

    fun delete(fields: Map<String, FieldType>): Boolean

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
//Возвращает живые строки по заданным полям
    override fun get(fields: Map<String, FieldType>): List<RecordFormat.RecordLineLocal>? {
        if (fields.isEmpty()) return null
    val recordJson: List<RecordFormat.RecordLineLocal> = record.readAll()
        .map { rec ->
            RecordFormat.RecordLineLocal(
                tombstone = rec.tombstone,
                id = rec.id,
                payload = json.decodeFromString(Row.serializer(), rec.payload)
            )
        }
        val foundData = mutableListOf<RecordFormat.RecordLineLocal>()

        for (rec in recordJson) {
            // проверяем, что все поля совпадают и живые
            val matches = fields.all { (key, value) ->
                rec.payload.values[key] == value //&& !rec.tombstone
            }

            if (matches) {
                foundData.add(rec)
            }
        }

        return if (foundData.isNotEmpty()) foundData else null
    }


    override fun update(
        id: RowId,
        newValues: Map<String, FieldType?>,
    ): Boolean = TODO()

    override fun upsert(row: Row): RowId = TODO()

    override fun delete(fields: Map<String, FieldType>): Boolean {
        // Найти строки по условию
        val rowsToDelete = get(fields) ?: return false

        val recordJson: List<RecordFormat.RecordLineLocal> = record.readAll()
            .map { rec ->
                RecordFormat.RecordLineLocal(
                    tombstone = rec.tombstone,
                    id = rec.id,
                    payload = json.decodeFromString(Row.serializer(), rec.payload)
                )
            }

        // Пометить tombstone = true для нужных строк
        val updatedJson: List<RecordFormat.RecordLineLocal> = recordJson.map { line ->
            if (rowsToDelete.any { it.id == line.id }) {
                line.copy(tombstone = true) // ✅ делаем копию с изменённым tombstone
            } else {
                line
            }
        }

        dataFile.writeText(
            updatedJson.joinToString("\n") { line ->
                json.encodeToString(RecordFormat.RecordLineLocal.serializer(), line)
            }
        )

        return true
    }

    override fun scan(
        predicate: Predicate,
        sort: Sort,
        limit: Int,
        offset: Int,
    ): Cursor<Row> = TODO()

    override fun countApprox(): Long = TODO()

    override fun compact() {}
}
