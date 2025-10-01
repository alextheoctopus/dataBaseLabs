package com.customDB.api

import kotlinx.serialization.Serializable

/** Базовые типы поля в таблице. Можно расширять при необходимости. */
@Serializable
sealed class FieldType {
    @Serializable
    data class PK(val start: Long) : FieldType()

    @Serializable
    data class STRING(val v: String) : FieldType()

    @Serializable
    data class LONG(val v: Long) : FieldType()

    @Serializable
    data class DOUBLE(val v: Double) : FieldType()

    @Serializable
    data class BOOL(val v: Boolean) : FieldType()

    @Serializable
    data class INSTANT(val v: Int) : FieldType()

    @Serializable
    data class BYTES(val v: ByteArray) : FieldType() {
        override fun equals(other: Any?): Boolean {
            return other is BYTES && v.contentEquals(other.v)
        }

        override fun hashCode(): Int {
            return v.contentHashCode()
        }
    }
}
