package com.customDB.api

typealias RowId = FieldType.LONG

/** Логическая строка таблицы. Значения должны соответствовать схеме. */
data class Row(
    //val id: RowId = 0L,                          // Предлагаю генерировать самим
    val values: Map<String, FieldType?>            // только поля из TableSchema.fields
)
