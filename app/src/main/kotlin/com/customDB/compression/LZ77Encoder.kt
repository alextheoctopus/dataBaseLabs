package com.customDB.compression

import java.io.ByteArrayOutputStream

/**
 * Кодирует LZ77 токены в бинарный формат для хранения
 */
object LZ77Encoder {
    
    /**
     * Сериализует LZ77.CompressedData в бинарный формат
     * Формат:
     * - 4 байта: размер исходных данных (little-endian)
     * - Для каждого токена:
     *   - Literal: 0x00 + 1 байт (значение)
     *   - Reference: 0x01 + 2 байта offset (little-endian) + 1 байт length
     */
    fun encode(compressed: LZ77.CompressedData): ByteArray {
        val output = ByteArrayOutputStream()
        
        // Записываем размер исходных данных (4 байта, little-endian)
        val originalSize = compressed.originalSize
        output.write(originalSize and 0xFF)
        output.write((originalSize shr 8) and 0xFF)
        output.write((originalSize shr 16) and 0xFF)
        output.write((originalSize shr 24) and 0xFF)
        
        // Записываем токены
        for (token in compressed.tokens) {
            when (token) {
                is LZ77.Token.Literal -> {
                    output.write(0x00) // Маркер Literal
                    output.write(token.value and 0xFF)
                }
                is LZ77.Token.Reference -> {
                    output.write(0x01) // Маркер Reference
                    // Offset (2 байта, little-endian, максимум 32768)
                    val offset = token.offset
                    output.write(offset and 0xFF)
                    output.write((offset shr 8) and 0xFF)
                    // Length (2 байта, little-endian, максимум 258)
                    val length = token.length
                    output.write(length and 0xFF)
                    output.write((length shr 8) and 0xFF)
                }
            }
        }
        
        return output.toByteArray()
    }
    
    /**
     * Десериализует бинарный формат обратно в LZ77.CompressedData
     */
    fun decode(data: ByteArray): LZ77.CompressedData {
        if (data.size < 4) {
            throw IllegalArgumentException("Invalid compressed data: too short")
        }
        
        var index = 0
        
        // Читаем размер исходных данных
        val originalSize = (data[index].toInt() and 0xFF) or
                          ((data[index + 1].toInt() and 0xFF) shl 8) or
                          ((data[index + 2].toInt() and 0xFF) shl 16) or
                          ((data[index + 3].toInt() and 0xFF) shl 24)
        index += 4
        
        val tokens = mutableListOf<LZ77.Token>()
        
        // Читаем токены
        while (index < data.size) {
            val marker = data[index].toInt() and 0xFF
            index++
            
            when (marker) {
                0x00 -> {
                    // Literal
                    if (index >= data.size) break
                    val value = data[index].toInt() and 0xFF
                    index++
                    tokens.add(LZ77.Token.Literal(value))
                }
                0x01 -> {
                    // Reference
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

