package com.customDB.api

sealed class TinyDbException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class TableAlreadyExists(name: String) : TinyDbException("Table already exists: $name")
    class TableNotFound(name: String) : TinyDbException("Table not found: $name")
    class SchemaViolation(msg: String) : TinyDbException("Schema violation: $msg")
    class IoFailure(msg: String, cause: Throwable? = null) : TinyDbException(msg, cause)
}
