package com.customDB.server

import kotlin.math.abs
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.update.Update
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.LongValue
import net.sf.jsqlparser.expression.operators.relational.EqualsTo
import net.sf.jsqlparser.schema.Column

class ShardLocator(
    private val cluster: ClusterState,
    private val shardKey: String = "id"
) {
    fun shardIdFor(sql: String): String {
        val st: Statement = CCJSqlParserUtil.parse(sql)
        val key: Long? = when (st) {
            is Select -> {
                val ps = st.selectBody as PlainSelect
                eqValueOnShardKey(ps.where)
            }
            is Update -> eqValueOnShardKey(st.where)
            is Delete -> eqValueOnShardKey(st.where)
            else -> null // INSERT без WHERE — отправим по умолчанию (slot 0)
        }
        val k = key ?: 0L
        val slot = abs(k.hashCode()) % cluster.hashSlots
        return cluster.shardBySlot(slot).id
    }

    private fun eqValueOnShardKey(expr: Expression?): Long? {
        val eq = expr as? EqualsTo ?: return null
        val col = (eq.leftExpression as? Column)?.columnName ?: return null
        if (!col.equals(shardKey, ignoreCase = true)) return null
        val rv = eq.rightExpression
        return when (rv) {
            is LongValue -> rv.value
            else -> rv.toString().trim('\'').toLongOrNull()
        }
    }
}
