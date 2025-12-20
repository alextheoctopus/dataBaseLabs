package com.customDB.compression

/**
 * Утилита для записи битов в поток байтов
 */
class BitWriter {
    private val buffer = mutableListOf<Byte>()
    private var currentByte = 0
    private var bitCount = 0
    
    /**
     * Записывает бит (0 или 1)
     */
    fun writeBit(bit: Int) {
        if (bit != 0 && bit != 1) {
            throw IllegalArgumentException("Bit must be 0 or 1")
        }
        currentByte = currentByte or (bit shl bitCount)
        bitCount++
        if (bitCount == 8) {
            buffer.add(currentByte.toByte())
            currentByte = 0
            bitCount = 0
        }
    }
    
    /**
     * Записывает значение как little-endian (LSB first)
     */
    fun writeBits(value: Int, bitCount: Int) {
        for (i in 0 until bitCount) {
            writeBit((value shr i) and 1)
        }
    }
    
    /**
     * Записывает байт
     */
    fun writeByte(byte: Int) {
        writeBits(byte and 0xFF, 8)
    }
    
    /**
     * Записывает 16-битное значение как little-endian
     */
    fun writeShort(value: Int) {
        writeBits(value and 0xFFFF, 16)
    }
    
    /**
     * Завершает запись, добавляя оставшиеся биты
     */
    fun finish(): ByteArray {
        if (bitCount > 0) {
            buffer.add(currentByte.toByte())
        }
        return buffer.toByteArray()
    }
    
    /**
     * Выравнивает до границы байта (дописывает нули если нужно)
     */
    fun alignToByte() {
        if (bitCount > 0) {
            buffer.add(currentByte.toByte())
            currentByte = 0
            bitCount = 0
        }
    }
    
    /**
     * Сбрасывает состояние
     */
    fun reset() {
        buffer.clear()
        currentByte = 0
        bitCount = 0
    }
}
