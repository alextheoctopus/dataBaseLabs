package com.customDB.compression

import java.io.ByteArrayOutputStream

class BitWriter {
    private val buffer = ByteArrayOutputStream()
    private var currentByte = 0
    private var bitCount = 0
    
    fun writeBit(bit: Int) {
        if (bit != 0 && bit != 1) {
            throw IllegalArgumentException("Bit must be 0 or 1")
        }
        
        currentByte = currentByte or (bit shl (7 - bitCount))
        bitCount++
        
        if (bitCount == 8) {
            flush()
        }
    }
    
    fun writeBits(value: Int, numBits: Int) {
        for (i in numBits - 1 downTo 0) {
            writeBit((value shr i) and 1)
        }
    }
    
    fun writeByte(byte: Int) {
        if (bitCount > 0) {
            flush()
        }
        buffer.write(byte and 0xFF)
    }
    
    fun writeBytes(bytes: ByteArray) {
        if (bitCount > 0) {
            flush()
        }
        buffer.write(bytes)
    }
    
    private fun flush() {
        if (bitCount > 0) {
            buffer.write(currentByte)
            currentByte = 0
            bitCount = 0
        }
    }
    
    fun toByteArray(): ByteArray {
        flush()
        return buffer.toByteArray()
    }
}

