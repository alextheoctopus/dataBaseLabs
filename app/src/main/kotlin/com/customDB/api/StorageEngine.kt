package com.customDB.api

import kotlinx.serialization.json.Json
import java.io.File
//import arrow.core.Either

/** Движок хранения таблиц в локальном каталоге. */
class LocalStorageEngine : AutoCloseable {
    /** Базовая директория для файлов таблиц/индексов/метаданных. */
    val basePath: File = File("src", "LocalDB")


    /* Открыть существующую таблицу по имени.*/
    //  открывает meta file и преобразует из структуры json таблицу в оперативную память


    /** Создать таблицу с заданной схемой (ошибка, если существует). */
    fun createTable(table: TableSchema) {

        val pathTableData = File(basePath, "${table.name}.tbl");//Строки таблицы
        val pathTableMeta = File(basePath, "${table.name}.meta");//Поля таблицы

        if (pathTableData.exists()) {
            println(TinyDbException.TableAlreadyExists(pathTableData.name));

            openTable(table.name)

        } else {
            pathTableData.createNewFile();
            pathTableMeta.createNewFile();
            val metaData = Json.encodeToString(table)
            pathTableMeta.appendText("\n" + metaData)

            openTable(table.name)
        }
    }

    override fun close() {
    }

    private fun openTable(name: String) {//Была попытка использовать Either, неудачная...
        val fileLink = File("src/LocalDB/${name}.meta")

        if (fileLink.exists()) {
            //чтение данных о столбцах таблицы из мета файла
            val structureData = fileLink.readText();
            //преобразования к структуре данных 
            println("hey $structureData")

        } else {
            println("${name}.meta")
            throw Error(TinyDbException.TableNotFound(name))
        }
    }
//
//    /** Удалить таблицу (все связанные файлы). Возвращает true, если что-то удалено. */
//    fun dropTable(name: String): Boolean
//
//    /** Регистрация индексов (опционально — можно сделать фабрику индексов). */
//    fun listTables(): List<String>
//
}
