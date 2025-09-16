package com.customDB.api

typealias RowId = Long

/** Логическая строка таблицы. Значения должны соответствовать схеме. */
data class Row(
    val id: RowId = 0L,                      // 0L => автогенерация при insert
    val values: Map<String, Any?>            // только поля из TableSchema.fields
)
