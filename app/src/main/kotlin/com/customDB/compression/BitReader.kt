package com.customDB.compression

/**
 * Утилита для чтения битов из потока байтов
 */
class BitReader(private val data: ByteArray) {
    private var byteIndex = 0
    private var bitIndex = 0
    
    /**
     * Читает один бит
     */
    fun readBit(): Int {
        if (byteIndex >= data.size) {
            throw IllegalArgumentException("End of data")
        }
        val bit = (data[byteIndex].toInt() shr bitIndex) and 1
        bitIndex++
        if (bitIndex == 8) {
            bitIndex = 0
            byteIndex++
        }
        return bit
    }
    
    /**
     * Читает несколько бит как little-endian (LSB first)
     */
    fun readBits(count: Int): Int {
        var value = 0
        for (i in 0 until count) {
            val bit = readBit()
            value = value or (bit shl i)
        }
        return value
    }
    
    /**
     * Читает байт
     */
    fun readByte(): Int {
        return readBits(8)
    }
    
    /**
     * Читает 16-битное значение как little-endian
     */
    fun readShort(): Int {
        return readBits(16)
    }
    
    /**
     * Пропускает биты до границы байта
     */
    fun alignToByte() {
        if (bitIndex > 0) {
            bitIndex = 0
            byteIndex++
        }
    }
    
    /**
     * Проверяет, достигнут ли конец данных
     */
    fun isEnd(): Boolean {
        return byteIndex >= data.size
    }
    
    /**
     * Возвращает текущую позицию в байтах
     */
    fun getPosition(): Int {
        return byteIndex
    }
}
