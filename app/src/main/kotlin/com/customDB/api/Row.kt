package com.customDB.api

import kotlinx.serialization.Serializable

typealias RowId = FieldType.LONG

/** Логическая строка таблицы. Значения должны соответствовать схеме. */
@Serializable
data class Row(
    val values: MutableMap<String, FieldType?>, // только поля из TableSchema.fields
)
