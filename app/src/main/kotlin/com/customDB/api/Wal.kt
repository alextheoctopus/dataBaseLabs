//интерфейсы WAL (LSN, батчи)
package com.customDB.api

data class LsnRange(val base: Long, val last: Long)
data class WalChunk(val base: Long, val last: Long, val bytes: ByteArray)

interface WalWriter {
    /** records — сериализованные операции вашей БД (можно использовать ваш Codec) */
    fun append(records: List<ByteArray>): LsnRange
    fun read(fromLsn: Long, maxBytes: Int = 1_000_000): WalChunk?
    fun lastLsn(): Long
}
