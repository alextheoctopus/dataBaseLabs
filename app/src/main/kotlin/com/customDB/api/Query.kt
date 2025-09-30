package com.customDB.api

/** Предикат для фильтрации строк. */
fun interface Predicate {
    fun test(row: Row): Boolean
}

/** Небольшой DSL для предикатов. */
object Q {
    fun any(): Predicate = Predicate { true }

    fun eq(
        field: String,
        value: FieldType?,
    ): Predicate = Predicate { it.values[field] == value }

    fun ne(
        field: String,
        value: FieldType?,
    ): Predicate = Predicate { it.values[field] != value }

    fun gt(
        field: String,
        value: Number,
    ): Predicate =
        Predicate {
            (it.values[field] as? Number)?.toDouble()?.let { v -> v > value.toDouble() } == true
        }

    fun gte(
        field: String,
        value: Number,
    ): Predicate =
        Predicate {
            (it.values[field] as? Number)?.toDouble()?.let { v -> v >= value.toDouble() } == true
        }

    fun lt(
        field: String,
        value: Number,
    ): Predicate =
        Predicate {
            (it.values[field] as? Number)?.toDouble()?.let { v -> v < value.toDouble() } == true
        }

    fun lte(
        field: String,
        value: Number,
    ): Predicate =
        Predicate {
            (it.values[field] as? Number)?.toDouble()?.let { v -> v <= value.toDouble() } == true
        }

    fun and(vararg ps: Predicate): Predicate = Predicate { row -> ps.all { it.test(row) } }

    fun or(vararg ps: Predicate): Predicate = Predicate { row -> ps.any { it.test(row) } }

    fun not(p: Predicate): Predicate = Predicate { row -> !p.test(row) }
}
