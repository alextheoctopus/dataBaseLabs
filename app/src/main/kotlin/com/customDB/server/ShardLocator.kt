package com.customDB.server

import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.LongValue
import net.sf.jsqlparser.expression.StringValue
import net.sf.jsqlparser.expression.operators.relational.EqualsTo
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.update.Update

class ShardLocator(
    private val shardKey: String = "id"
) {
    /** WHERE id = ? (или ? = id) */
    private fun eqValueOnShardKey(expr: Expression?): Long? {
        val eq = expr as? EqualsTo ?: return null
        val leftCol  = (eq.leftExpression  as? Column)?.columnName
        val rightCol = (eq.rightExpression as? Column)?.columnName

        val valueExpr: Expression = when {
            leftCol?.equals(shardKey, ignoreCase = true) == true -> eq.rightExpression
            rightCol?.equals(shardKey, ignoreCase = true) == true -> eq.leftExpression
            else -> return null
        }

        return when (valueExpr) {
            is LongValue   -> valueExpr.value
            is StringValue -> valueExpr.value.toLongOrNull()
            else           -> valueExpr.toString().trim().trim('\'', '"').toLongOrNull()
        }
    }

    /**
     * INSERT INTO t (id, ...) VALUES (...), (...), ...
     * - колонки берём из AST (Insert.columns)
     * - первую кортеж-скобку значений берём из сырого SQL после ключевого слова VALUES
     */
    private fun keyFromInsert(ins: Insert, sql: String): Long? {
        val cols = ins.columns?.map { it.columnName } ?: return null
        val idx = cols.indexOfFirst { it.equals(shardKey, ignoreCase = true) }
        if (idx < 0) return null

        val valuesStart = indexOfValuesKeyword(sql) ?: return null
        val afterValues = sql.substring(valuesStart)
        val firstTuple = extractFirstTuple(afterValues) ?: return null
        val values = splitTopLevelCsv(firstTuple) // значения первой строки VALUES

        if (idx >= values.size) return null
        val raw = values[idx].trim()
        return raw.trim('\'', '"').toLongOrNull()
    }

    /** Находим позицию сразу после слова VALUES (case-insensitive) */
    private fun indexOfValuesKeyword(sql: String): Int? {
        val m = Regex("""\bvalues\b""", RegexOption.IGNORE_CASE).find(sql) ?: return null
        return m.range.last + 1
    }

    /** Возвращает содержимое ПЕРВОЙ пары круглых скобок после начала переданной строки */
    private fun extractFirstTuple(s: String): String? {
        val start = s.indexOf('(')
        if (start < 0) return null
        var depth = 0
        for (i in start until s.length) {
            when (s[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return s.substring(start + 1, i) // без внешних скобок
                }
            }
        }
        return null
    }

    /** Сплит верхнего уровня по запятым; не режем внутри одинарных кавычек */
    private fun splitTopLevelCsv(s: String): List<String> {
        val out = ArrayList<String>(8)
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when (ch) {
                '\'' -> { inQuotes = !inQuotes; sb.append(ch) }
                ','  -> if (inQuotes) sb.append(ch) else { out += sb.toString().trim(); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty()) out += sb.toString().trim()
        return out
    }
//Определение шарда/слота по id
    fun defineSlotAndShard(sql: String): Pair<Int, String> {
        val st = CCJSqlParserUtil.parse(sql)
        val key: Long? = when (st) {
            is Select -> eqValueOnShardKey((st.selectBody as PlainSelect).where)
            is Update -> eqValueOnShardKey(st.where)
            is Delete -> eqValueOnShardKey(st.where)
            is Insert -> keyFromInsert(st, sql)
            else -> null
        }
        val h = (key ?: 0L).hashCode()//hashCode(id)%1024
    val cluster = ClusterBus.current()
    val slot = Math.floorMod(h, cluster.hashSlots)
        val shardId = cluster.shardBySlot(slot).id
        return slot to shardId
    }

}
