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

import java.io.File

class SqlEngine(private val engine: LocalStorageEngine) {

    fun execute(sql: String): Any? {
        val statement: Statement = CCJSqlParserUtil.parse(sql)
        return when (statement) {
            is CreateTable -> handleCreateTable(statement)
            is Insert -> handleInsert(statement)
            is Select -> handleSelect(statement)
            is Update -> handleUpdate(statement)
            is Delete -> handleDelete(statement)
            is Drop -> handleDrop(statement)
            is Alter -> handleAlter(statement)
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
                is LongValue -> FieldType.LONG(expr.value)
                is DoubleValue -> FieldType.DOUBLE(expr.value)
                else -> FieldType.STRING(expr.toString())
            }
            rowValues[col] = value
        }

        table.insert(Row(rowValues))
        return true
    }

    private fun handleSelect(statement: Select): List<Row> {
        val plainSelect = statement.selectBody as PlainSelect
        val tableName = (plainSelect.fromItem as net.sf.jsqlparser.schema.Table).name
        val where = plainSelect.where

        val table = engine.getOrCreateTableFromMeta(tableName)
        val filterFields = mutableMapOf<String, FieldType>()

        if (where is net.sf.jsqlparser.expression.operators.relational.EqualsTo) {
            val column = (where.leftExpression as net.sf.jsqlparser.schema.Column).columnName
            val valueExpr = where.rightExpression
            val fieldValue = when (valueExpr) {
                is net.sf.jsqlparser.expression.StringValue -> FieldType.STRING(valueExpr.value)
                is net.sf.jsqlparser.expression.LongValue -> FieldType.LONG(valueExpr.value)
                else -> FieldType.STRING(valueExpr.toString())
            }
            filterFields[column] = fieldValue
        }

        val result = table.get(filterFields)
        return result?.map { it.payload } ?: emptyList()
    }


    private fun handleUpdate(stmt: Update): Boolean {
        val tableName = stmt.table.name
        val table = engine.getOrCreateTableFromMeta(tableName)

        val setCols = stmt.columns.map { it.columnName }
        val setVals = stmt.expressions.map { it.toString().trim('\'') }

        val newRow = Row(setCols.zip(setVals.map { FieldType.STRING(it) }).toMap().toMutableMap())

        val where = stmt.where.toString().split("=").map { it.trim() }
        val id = where[1].trim('\'').toLong()

        return table.upsert(FieldType.LONG(id), newRow)
    }

    private fun handleDelete(stmt: Delete): Boolean {
        val tableName = stmt.table.name
        val table = engine.getOrCreateTableFromMeta(tableName)
        val where = stmt.where.toString().split("=").map { it.trim() }
        val (field, value) = where
        val cond = mapOf(field to FieldType.STRING(value.trim('\'')))
        return table.delete(cond)
    }

    private fun handleDrop(stmt: Drop): Boolean {
        val tableName = stmt.name.name
        val file = File(engine.basePath, "$tableName.tbl")
        val meta = File(engine.basePath, "$tableName.meta")
        return file.delete() or meta.delete()
    }

    private fun handleAlter(stmt: Alter): Boolean {
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
