package com.customDB.api

import kotlinx.serialization.Serializable


/** Базовые типы поля в таблице. Можно расширять при необходимости. */
@Serializable
sealed class FieldType {
    @Serializable
    data class STRING(val v: String) : FieldType()
    data class LONG(val v: Long) : FieldType()
    data class DOUBLE(val v: Double) : FieldType()
    data class BOOL(val v: Boolean) : FieldType()
    data class INSTANT(val v: Int) : FieldType()
    data class BYTES(val v: ByteArray) : FieldType() {
        override fun equals(other: Any?): Boolean {
            return other is BYTES && v.contentEquals(other.v)
        }

        override fun hashCode(): Int {
            return v.contentHashCode()
        }
    }
}