package com.customDB.api

/** Сортировка результата сканирования. */
data class Sort(
    val orders: List<Order> = emptyList(),
) {
    data class Order(val field: String, val asc: Boolean = true)

    companion object {
        fun by(
            field: String,
            asc: Boolean = true,
        ) = Sort(listOf(Order(field, asc)))
    }
}
