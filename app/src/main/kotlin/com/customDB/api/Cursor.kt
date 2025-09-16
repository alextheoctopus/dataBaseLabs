package com.customDB.api

/** Итерируемся по результатам скана/запроса без загрузки всего в память. */
interface Cursor<T> : AutoCloseable, Iterator<T> {
    override fun close()
}
