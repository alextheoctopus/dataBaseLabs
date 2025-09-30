package com.customDB.api

import kotlinx.serialization.json.Json

/** Контракт таблицы. Реализация может быть append-only с tombstone и compact().
 * TODO: наподумать. Возможно интерфейс для нашей задачи излишен. Убрать и оставить только класс*/
interface Table {
    val name: String
    val schema: TableSchema
    val record: RecordFormat

    fun insert(values: Row): RowId {
        val rowId=LocalStorageEngine().generateRowId(name)
        record.append(
            id=rowId,
            payload=Json.encodeToString(values),
            tombstone = false,
        )
        return rowId
    }

    /** Прочитать по первичному ключу. */
    fun get(id: RowId): Row?

    /** Полная замена значений (кроме id). Возвращает true, если обновлено. */
    fun update(id: RowId, newValues: Map<String, FieldType?>): Boolean

    /** Вставить или обновить (по наличию id). Возвращает id. */
    fun upsert(row: Row): RowId

    /** Мягкое удаление (tombstone) + обновление индекса. */
    fun delete(id: RowId): Boolean

    /** Последовательный скан с фильтром, сортировкой и пагинацией. */
    fun scan(
        predicate: Predicate = Q.any(),
        sort: Sort = Sort(),
        limit: Int = Int.MAX_VALUE,
        offset: Int = 0
    ): Cursor<Row>

    /** Приблизительное количество «живых» строк (по индексу/метаданным). */
    fun countApprox(): Long

    /** Компактирование файла таблицы (перепаковка без tombstone). */
    fun compact()

//    override fun close()
}

class LocalTable(override val name: String, override val schema: TableSchema) : Table {
    override val record: RecordFormat = RecordFormat()


    override fun get(id: RowId): Row? {
        TODO("Not yet implemented")
    }

    override fun update(id: RowId, newValues: Map<String, FieldType?>): Boolean {
        TODO("Not yet implemented")
    }

    override fun upsert(row: Row): RowId {
        TODO("Not yet implemented")
    }

    override fun delete(id: RowId): Boolean {
        TODO("Not yet implemented")
    }

    override fun scan(predicate: Predicate, sort: Sort, limit: Int, offset: Int): Cursor<Row> {
        TODO("Not yet implemented")
    }

    override fun countApprox(): Long {
        TODO("Not yet implemented")
    }

    override fun compact() {
        TODO("Not yet implemented")
    }
}
