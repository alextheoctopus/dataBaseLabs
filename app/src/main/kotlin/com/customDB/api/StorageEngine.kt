package com.customDB.api

import kotlinx.serialization.json.Json
import java.io.File

/** Движок хранения таблиц в локальном каталоге. */
class LocalStorageEngine : AutoCloseable {
    /** Базовая директория для файлов таблиц/индексов/метаданных. */
    val basePath: File = File("src", "LocalDB")


    /** Создать таблицу с заданной схемой (ошибка, если существует). */
    fun createTable(table: TableSchema) {

        val pathTableData = File(basePath, "${table.name}.tbl");//Строки таблицы
        val pathTableMeta = File(basePath, "${table.name}.meta");//Поля таблицы

        if (pathTableData.exists()) {
            println(TinyDbException.TableAlreadyExists(pathTableData.name));
        } else {
            pathTableData.createNewFile();
            pathTableMeta.createNewFile();
            val metaData = Json.encodeToString(table)
            pathTableMeta.appendText("\n"+metaData)

        }
    }

    override fun close() {
    }
//
//    /** Открыть существующую таблицу по имени. */
//    fun openTable(name: String): Table
//
//    /** Удалить таблицу (все связанные файлы). Возвращает true, если что-то удалено. */
//    fun dropTable(name: String): Boolean
//
//    /** Регистрация индексов (опционально — можно сделать фабрику индексов). */
//    fun listTables(): List<String>
//
}
