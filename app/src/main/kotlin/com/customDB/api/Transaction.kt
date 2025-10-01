package com.customDB.api

/** Unit-of-Work поверх физического ресурса (файла/канала/транзакционного лога). */
interface UnitOfWork : AutoCloseable {
    /** Признак активного UoW. */
    val active: Boolean

    fun commit()

    fun rollback()

    override fun close()
}

/** Менеджер транзакций. Реализация сама решит, как выделять/шарить ресурсы. */
interface TxManager : AutoCloseable {
    fun <T> tx(block: (UnitOfWork) -> T): T

    override fun close()
}
