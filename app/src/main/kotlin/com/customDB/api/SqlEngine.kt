package com.customDB.api

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.update.Update
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.statement.alter.Alter
import net.sf.jsqlparser.expression.StringValue
import net.sf.jsqlparser.expression.LongValue
import net.sf.jsqlparser.expression.DoubleValue
import net.sf.jsqlparser.expression.operators.relational.EqualsTo
import net.sf.jsqlparser.schema.Column
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * ВАЖНО:
 * - Локальная логика БД не менялась.
 * - Добавлены «хуки» для репликации: публикация RepOp.* через PrimaryReplicator.
 * - Шардирование решает внешний Router. Здесь просто выполняем команды на узле.
 */
class SqlEngine(
    private val engine: LocalStorageEngine,
    private val shardId: String = "s0",                     // id шарда для репликации
    private val replicator: PrimaryReplicator? = null       // null на репликах; задан на лидере
) {

    private fun publish(vararg ops: RepOp) {
        // на реплике replicator == null и публикации не будет
        replicator?.let {
            runBlocking {
                it.publish(RepBatch(shardId, ops.toList()))
            }
        }
    }

    fun execute(sql: String): Any? {
        val statement: Statement = CCJSqlParserUtil.parse(sql)
        return when (statement) {
            is CreateTable -> handleCreateTable(statement)
            is Insert      -> handleInsert(statement)
            is Select      -> handleSelect(statement)
            is Update      -> handleUpdate(statement)
            is Delete      -> handleDelete(statement)
            is Drop        -> handleDrop(statement)
            is Alter       -> handleAlter(statement)
            else -> throw UnsupportedOperationException("Unsupported SQL: ${statement.javaClass.simpleName}")
        }
    }

    private fun handleCreateTable(statement: CreateTable): Boolean {
        val tableName = statement.table.name

        val columns = statement.columnDefinitions.map { def ->
            val colName = def.columnName
            val colType = def.colDataType.dataType.uppercase()
            val fieldType = when (colType) {
                "INT", "INTEGER", "LONG" -> FieldType.LONG(0)
                "STRING", "TEXT", "VARCHAR" -> FieldType.STRING("")
                "DOUBLE", "FLOAT" -> FieldType.DOUBLE(0.0)
                else -> FieldType.STRING("") // fallback
            }
            TableSchema.Column(colName, fieldType, false)
        }

        val schema = TableSchema(name = tableName, fields = columns)
        engine.getOrCreateTable(schema)

        // репликация
        publish(RepOp.CreateTable(schema))
        return true
    }

    private fun handleInsert(statement: Insert): Boolean {
        val tableName = statement.table.name
        val table = engine.getOrCreateTableFromMeta(tableName)
        val columns = statement.columns.map { it.columnName }

        val select = statement.select
            ?: throw IllegalArgumentException("Insert without select/values not supported")

        val values = select.values
            ?: throw IllegalArgumentException("No VALUES found in INSERT")

        val expressions = values.expressions

        val rowValues = mutableMapOf<String, FieldType?>()
        for ((index, col) in columns.withIndex()) {
            val expr = expressions[index]
            val value = when (expr) {
                is StringValue -> FieldType.STRING(expr.value)
                is LongValue   -> FieldType.LONG(expr.value)
                is DoubleValue -> FieldType.DOUBLE(expr.value)
                else           -> FieldType.STRING(expr.toString())
            }
            rowValues[col] = value
        }

        table.insert(Row(rowValues))

        // репликация
        publish(RepOp.Insert(tableName, Row(rowValues), null))
        return true
    }

    private fun handleSelect(statement: Select): List<Row> {
        val plainSelect = statement.selectBody as PlainSelect
        val tableName = (plainSelect.fromItem as net.sf.jsqlparser.schema.Table).name
        val where = plainSelect.where

        val table = engine.getOrCreateTableFromMeta(tableName)
        val filterFields = mutableMapOf<String, FieldType>()

        if (where is EqualsTo) {
            val column = (where.leftExpression as Column).columnName
            val valueExpr = where.rightExpression
            val fieldValue = when (valueExpr) {
                is StringValue -> FieldType.STRING(valueExpr.value)
                is LongValue   -> FieldType.LONG(valueExpr.value)
                else           -> FieldType.STRING(valueExpr.toString())
            }
            filterFields[column] = fieldValue
        }

        val result = table.get(filterFields)
        return result?.map { it.payload } ?: emptyList()
    }

    private fun handleUpdate(stmt: Update): Boolean {
        val tableName = stmt.table.name
        val table = engine.getOrCreateTableFromMeta(tableName)

        // новые значения
        val newRowValues = mutableMapOf<String, FieldType?>()
        stmt.columns.forEachIndexed { i, col ->
            val expr = stmt.expressions[i]
            newRowValues[col.columnName] = when (expr) {
                is StringValue -> FieldType.STRING(expr.value)
                is LongValue   -> FieldType.LONG(expr.value)
                is DoubleValue -> FieldType.DOUBLE(expr.value)
                else           -> FieldType.STRING(expr.toString())
            }
        }

        // фильтр WHERE
        val filterFields = mutableMapOf<String, FieldType>()
        if (stmt.where is EqualsTo) {
            val whereExpr = stmt.where as EqualsTo
            val key = (whereExpr.leftExpression as Column).columnName
            val value = when (val expr = whereExpr.rightExpression) {
                is StringValue -> FieldType.STRING(expr.value)
                is LongValue   -> FieldType.LONG(expr.value)
                is DoubleValue -> FieldType.DOUBLE(expr.value)
                else           -> FieldType.STRING(expr.toString())
            }
            filterFields[key] = value
        }

        val rowsToUpdate = table.get(filterFields) ?: emptyList()

        for (rowLine in rowsToUpdate) {
            val updatedRow = Row(rowLine.payload.values.toMutableMap())
            for ((k, v) in newRowValues) {
                updatedRow.values[k] = v
            }
            table.upsert(FieldType.LONG(rowLine.id), updatedRow)

            // репликация апсёрта по каждой изменённой строке
            publish(RepOp.Upsert(tableName, rowLine.id, updatedRow))
        }

        return true
    }

    private fun handleDelete(stmt: Delete): Boolean {
        val tableName = stmt.table.name
        val table = engine.getOrCreateTableFromMeta(tableName)
        val where = stmt.where as EqualsTo
        val key = (where.leftExpression as Column).columnName
        val rawValue = (where.rightExpression)
        val value: FieldType = when (rawValue) {
            is StringValue -> FieldType.STRING(rawValue.value)
            is LongValue   -> FieldType.LONG(rawValue.value)
            is DoubleValue -> FieldType.DOUBLE(rawValue.value)
            else           -> FieldType.STRING(rawValue.toString())
        }

        val ok = table.delete(mapOf(key to value))

        // репликация
        if (ok) publish(RepOp.Delete(tableName, mapOf(key to value)))
        return ok
    }

    private fun handleDrop(stmt: Drop): Boolean {
        val tableName = stmt.name.name
        val file = File(engine.basePath, "$tableName.tbl")
        val meta = File(engine.basePath, "$tableName.meta")
        val ok = file.delete() or meta.delete()

        // репликация
        if (ok) publish(RepOp.DropTable(tableName))
        return ok
    }

    private fun handleAlter(stmt: Alter): Boolean {
        // В учебной версии не поддерживаем; при необходимости
        // можно транслировать в RepOp.DropTable/RepOp.CreateTable с новым schema
        println("ALTER TABLE ${stmt.table.name} — пока не реализовано")
        return false
    }
}

fun LocalStorageEngine.getOrCreateTableFromMeta(name: String): Table {
    val metaFile = File(basePath, "$name.meta")
    if (!metaFile.exists()) throw TinyDbException.TableNotFound(name)
    val schema = kotlinx.serialization.json.Json.decodeFromString(
        TableSchema.serializer(),
        metaFile.readText()
    )
    return LocalTable(schema.name, schema, basePath)
}
