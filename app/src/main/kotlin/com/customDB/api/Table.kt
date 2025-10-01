package com.customDB.api

import kotlinx.serialization.json.Json
import java.io.File

/** Контракт таблицы. */
interface Table {
    val name: String
    val schema: TableSchema
    val record: RecordFormat

    /** Вставить строку. Возвращаем СТРОКУ, записанную в .tbl */
    fun insert(values: Row, id: FieldType.LONG?=null , tombstone: FieldType.BOOL?=null ): String

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
    private fun nextRowId(): FieldType.LONG {
        if (!dataFile.exists()) return FieldType.LONG(1)
        val lines = dataFile.useLines { seq -> seq.count { it.isNotBlank() } }
        return FieldType.LONG(lines + 1L)
    }


    /** Вставить строку и вернуть СТРОКУ, которая записана в .tbl */
    override fun insert(values: Row, id: FieldType.LONG?, tombstone: FieldType.BOOL?): String {
        // если id не передан → генерим новый
        val rowId: Long = id?.v ?: nextRowId().v

        // если tombstone не передан → считаем, что запись живая
        val tombstoneFlag = tombstone?.v ?: false

        val payload = json.encodeToString(Row.serializer(), values)

        return record.append(
            tombstone = tombstoneFlag,
            id = FieldType.LONG(rowId),
            payload = payload
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
            if (!rec.tombstone) {//если запись мертвая, то пропустить

                // проверяем, что все поля совпадают и живые
                val matches = fields.all { (key, value) ->
                    rec.payload.values[key] == value
                }

                if (matches) {
                    foundData.add(rec)
                }
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
                RecordFormat.RecordLineLocal(
                    tombstone = true,
                    id = line.id,
                    payload = line.payload
                )

            } else {
                RecordFormat.RecordLineLocal(
                    tombstone = false,
                    id = line.id,
                    payload = line.payload
                )
            }
        }
        dataFile.writeText("")
        for (row in updatedJson) {
            insert(
                values = row.payload,
                id = FieldType.LONG(row.id),
                tombstone = FieldType.BOOL(row.tombstone)
            )
        }


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
