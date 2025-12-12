package com.customDB.compression

class BitReader(private val data: ByteArray) {
    private var byteIndex = 0
    private var bitIndex = 0
    
    fun readBit(): Int {
        if (byteIndex >= data.size) {
            throw IndexOutOfBoundsException("End of data")
        }
        
        val bit = (data[byteIndex].toInt() shr (7 - bitIndex)) and 1
        bitIndex++
        
        if (bitIndex == 8) {
            bitIndex = 0
            byteIndex++
        }
        
        return bit
    }
    
    fun readBits(numBits: Int): Int {
        var value = 0
        for (i in 0 until numBits) {
            value = (value shl 1) or readBit()
        }
        return value
    }
    
    fun readByte(): Int {
        if (bitIndex > 0) {
            bitIndex = 0
            byteIndex++
        }
        if (byteIndex >= data.size) {
            throw IndexOutOfBoundsException("End of data")
        }
        return data[byteIndex++].toInt() and 0xFF
    }
    
    fun readBytes(length: Int): ByteArray {
        if (bitIndex > 0) {
            bitIndex = 0
            byteIndex++
        }
        if (byteIndex + length > data.size) {
            throw IndexOutOfBoundsException("Not enough data")
        }
        val result = data.sliceArray(byteIndex until byteIndex + length)
        byteIndex += length
        return result
    }
    
    fun hasMore(): Boolean {
        return byteIndex < data.size || (byteIndex == data.size && bitIndex > 0)
    }
}

