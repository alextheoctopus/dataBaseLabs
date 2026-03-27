package com.customDB.compression

import java.io.ByteArrayOutputStream

object LZ77Encoder {
    
    fun encode(compressed: LZ77.CompressedData): ByteArray {
        val output = ByteArrayOutputStream()
        
        val originalSize = compressed.originalSize
        output.write(originalSize and 0xFF)
        output.write((originalSize shr 8) and 0xFF)
        output.write((originalSize shr 16) and 0xFF)
        output.write((originalSize shr 24) and 0xFF)
        
        for (token in compressed.tokens) {
            when (token) {
                is LZ77.Token.Literal -> {
                    output.write(0x00) // Маркер Literal
                    output.write(token.value and 0xFF)
                }
                is LZ77.Token.Reference -> {
                    output.write(0x01) // Маркер Reference
                    val offset = token.offset
                    output.write(offset and 0xFF)
                    output.write((offset shr 8) and 0xFF)
                    val length = token.length
                    output.write(length and 0xFF)
                    output.write((length shr 8) and 0xFF)
                }
            }
        }
        
        return output.toByteArray()
    }
    
    fun decode(data: ByteArray): LZ77.CompressedData {
        if (data.size < 4) {
            throw IllegalArgumentException("Invalid compressed data: too short")
        }
        
        var index = 0
        
        val originalSize = (data[index].toInt() and 0xFF) or
                          ((data[index + 1].toInt() and 0xFF) shl 8) or
                          ((data[index + 2].toInt() and 0xFF) shl 16) or
                          ((data[index + 3].toInt() and 0xFF) shl 24)
        index += 4
        
        val tokens = mutableListOf<LZ77.Token>()
        
        while (index < data.size) {
            val marker = data[index].toInt() and 0xFF
            index++
            
            when (marker) {
                0x00 -> {
                    if (index >= data.size) break
                    val value = data[index].toInt() and 0xFF
                    index++
                    tokens.add(LZ77.Token.Literal(value))
                }
                0x01 -> {
                    if (index + 3 >= data.size) break
                    val offset = (data[index].toInt() and 0xFF) or
                                ((data[index + 1].toInt() and 0xFF) shl 8)
                    index += 2
                    val length = (data[index].toInt() and 0xFF) or
                                ((data[index + 1].toInt() and 0xFF) shl 8)
                    index += 2
                    
                    tokens.add(LZ77.Token.Reference(offset, length))
                }
                else -> {
                    throw IllegalArgumentException("Invalid marker: $marker")
                }
            }
        }
        
        return LZ77.CompressedData(tokens, originalSize)
    }
}

