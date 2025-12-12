package com.customDB.api

import kotlinx.serialization.Serializable

/** Описание схемы таблицы. PK 'id' — обычная колонка в `fields` (например, FieldType.PK). */
@Serializable
data class TableSchema(
    val name: String,
    val fields: List<Column>,
    val allowDefaults: Boolean = false,
) {
    @Serializable
    data class Column(
        val name: String,
        val type: FieldType,
        val nullable: Boolean = false,
        val defaultValue: FieldType? = null,
    )
}
