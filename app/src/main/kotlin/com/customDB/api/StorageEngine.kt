package com.customDB.api

import java.nio.file.Path

/** Движок хранения таблиц в локальном каталоге. */
interface StorageEngine : AutoCloseable {
    /** Базовая директория для файлов таблиц/индексов/метаданных. */
    val basePath: Path

    /** Создать таблицу с заданной схемой (ошибка, если существует). */
    fun createTable(schema: TableSchema): Table

    /** Открыть существующую таблицу по имени. */
    fun openTable(name: String): Table

    /** Удалить таблицу (все связанные файлы). Возвращает true, если что-то удалено. */
    fun dropTable(name: String): Boolean

    /** Регистрация индексов (опционально — можно сделать фабрику индексов). */
    fun listTables(): List<String>

    override fun close()
}
