package com.customDB.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class LocalStorageEngine : AutoCloseable {
    val basePath: File = File("src", "LocalDB")

    private fun tableDataFile(tableName: String): File = File(basePath, "$tableName.tbl")

    private fun tableMetaFile(tableName: String): File = File(basePath, "$tableName.meta")

    fun createTable(table: TableSchema): Table {
        val pathTableData = tableDataFile(table.name)
        val pathTableMeta = tableMetaFile(table.name)

        pathTableData.parentFile?.mkdirs()
        pathTableMeta.parentFile?.mkdirs()

        if (pathTableData.exists()) {
            throw TinyDbException.TableAlreadyExists(pathTableData.name)
        } else {
            pathTableData.createNewFile()
            pathTableMeta.createNewFile()

            val metaData = Json.encodeToString(TableSchema.serializer(), table)
            pathTableMeta.writeText(metaData)
        }
        return openTable(table.name)
    }

    fun getOrCreateTable(table: TableSchema): Table {
        return try {
            createTable(table)
        } catch (e: TinyDbException.TableAlreadyExists) {
            openTable(table.name)
        }
    }

    private fun updateMetaField(
        tableName: String,
        field: String,
        value: String,
    ) {
        val metaFile = tableMetaFile(tableName)

        val text = metaFile.readText()
        val json = Json.parseToJsonElement(text).jsonObject

        val updated =
            JsonObject(
                json.toMutableMap().apply {
                    this[field] = JsonPrimitive(value)
                },
            )

        metaFile.writeText(Json.encodeToString(JsonObject.serializer(), updated))
    }

    private fun getMetaField(
        tableName: String,
        field: String,
    ): String? {
        val metaFile = tableMetaFile(tableName)

        val text = metaFile.readText()
        val json = Json.parseToJsonElement(text).jsonObject

        return json[field]?.jsonPrimitive?.contentOrNull
    }

    override fun close() {
    }

    private fun openTable(tableName: String): Table { // Была попытка использовать Either, неудачная...
        val meta = tableMetaFile(tableName)
        val structureData: String = meta.readText()
        // TODO: перенести в метод декодирования
        val tableSchemaFromFile = Json.decodeFromString(TableSchema.serializer(), structureData)
        return LocalTable(tableSchemaFromFile.name, tableSchemaFromFile, basePath)
    }

    fun generateRowId(tableName: String): FieldType.LONG {
        val id = getMetaField(tableName, "id")?.toLongOrNull() ?: 0L
        return FieldType.LONG(id)
    }

//
//    /** Удалить таблицу (все связанные файлы). Возвращает true, если что-то удалено. */
//    fun dropTable(name: String): Boolean
//
//    /** Регистрация индексов (опционально — можно сделать фабрику индексов). */
//    fun listTables(): List<String>
//
}
