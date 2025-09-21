package com.customDB.api

/** Описание схемы таблицы (кроме PK 'id', он фиксированный Long). */
data class TableSchema(
    val name: String,
    /** Порядок важен (LinkedHashMap-поведение): имя_поля -> тип. */
    val fields: List<Column>,
    /** Разрешать ли отсутствующие поля с default-значениями. */
    val allowDefaults: Boolean = false,
) {
    data class Column(
        val name: String,
        val type: FieldType,
        val nullable: Boolean = false,
        val defaultValue: FieldType? = null
    )
}
