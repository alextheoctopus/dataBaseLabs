package com.customDB.api

import kotlinx.serialization.Serializable

/** Описание схемы таблицы. PK 'id' — обычная колонка в `fields` (например, FieldType.PK). */
@Serializable
data class TableSchema(
    val name: String,
    /** Порядок важен (сохраняем как список колонок). */
    val fields: List<Column>,
    /** Разрешать ли отсутствующие поля с default-значениями. */
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
