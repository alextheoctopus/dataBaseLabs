package com.customDB.api

/** Контракт таблицы. Реализация может быть append-only с tombstone и compact(). */
interface Table{
    val name: String
    val schema: TableSchema

    /** Создать запись. Если id == 0L — сгенерировать. Возвращает фактический id. */
    fun insert(row: Row): RowId

    /** Прочитать по первичному ключу. */
    fun get(id: RowId): Row?

    /** Полная замена значений (кроме id). Возвращает true, если обновлено. */
    fun update(id: RowId, newValues: Map<String, Any?>): Boolean

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
