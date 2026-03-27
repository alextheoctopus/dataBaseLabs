package com.customDB.compression

/**
 * Реализация DEFLATE формата (RFC 1951)
 * DEFLATE = LZ77 + Huffman кодирование
 */
object Deflate {
    
    /**
     * Сжимает данные используя DEFLATE (фиксированные Huffman коды, BTYPE=01)
     */
    fun compress(data: ByteArray): ByteArray {
        if (data.isEmpty()) {
            val writer = BitWriter()
            // BTYPE=01 (фиксированные коды), BFINAL=1 (последний блок)
            writer.writeBit(1) // BFINAL = 1
            writer.writeBits(1, 2) // BTYPE = 01
            Huffman.writeLiteralLength(writer, 256) // END блок
            writer.alignToByte()
            return writer.finish()
        }
        
        // Используем LZ77 для получения токенов
        val lz77Data = LZ77.compress(data)
        
        val writer = BitWriter()
        
        // Заголовок блока: BFINAL=1, BTYPE=01 (фиксированные Huffman коды)
        writer.writeBit(1) // BFINAL = 1 (последний блок)
        writer.writeBits(1, 2) // BTYPE = 01
        
        // Кодируем токены LZ77 в DEFLATE формат
        for (token in lz77Data.tokens) {
            when (token) {
                is LZ77.Token.Literal -> {
                    // Literal: просто записываем значение (0-255)
                    Huffman.writeLiteralLength(writer, token.value)
                }
                is LZ77.Token.Reference -> {
                    // Reference: нужно преобразовать в (length, distance)
                    val length = token.length
                    val distance = token.offset
                    
                    // Кодируем length (257-285 в DEFLATE)
                    val lengthCode = encodeLength(length)
                    Huffman.writeLiteralLength(writer, lengthCode)
                    
                    // Дополнительные биты для length (если нужно)
                    val lengthExtra = getLengthExtraBits(lengthCode)
                    if (lengthExtra > 0) {
                        val lengthValue = getLengthValue(length, lengthCode)
                        writer.writeBits(lengthValue, lengthExtra)
                    }
                    
                    // Кодируем distance (0-29 в DEFLATE, но нам нужно 0-31)
                    val distanceCode = encodeDistance(distance)
                    Huffman.writeDistance(writer, distanceCode)
                    
                    // Дополнительные биты для distance
                    val distanceExtra = getDistanceExtraBits(distanceCode)
                    if (distanceExtra > 0) {
                        val distanceValue = getDistanceValue(distance, distanceCode)
                        writer.writeBits(distanceValue, distanceExtra)
                    }
                }
            }
        }
        
        // Конец блока (256)
        Huffman.writeLiteralLength(writer, 256)
        
        // Выравниваем до границы байта
        writer.alignToByte()
        
        return writer.finish()
    }
    
    // Таблицы base length для кодов 257-285
    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
    )
    
    // Количество extra bits для length кодов
    private val LENGTH_EXTRA_BITS = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
    )
    
    // Таблицы base distance для кодов 0-29
    private val DISTANCE_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
        8193, 12289, 16385, 24577
    )
    
    // Количество extra bits для distance кодов
    private val DISTANCE_EXTRA_BITS = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
    )
    
    /**
     * Преобразует длину совпадения в код DEFLATE (257-285)
     */
    private fun encodeLength(length: Int): Int {
        for (code in 257..285) {
            val idx = code - 257
            val base = LENGTH_BASE[idx]
            val extraBits = LENGTH_EXTRA_BITS[idx]
            val maxLength = if (extraBits > 0) {
                base + (1 shl extraBits) - 1
            } else {
                base
            }
            if (length >= base && length <= maxLength) {
                return code
            }
        }
        throw IllegalArgumentException("Invalid length: $length")
    }
    
    /**
     * Возвращает количество дополнительных бит для length кода
     */
    private fun getLengthExtraBits(code: Int): Int {
        if (code < 257 || code > 285) {
            throw IllegalArgumentException("Invalid length code: $code")
        }
        return LENGTH_EXTRA_BITS[code - 257]
    }
    
    /**
     * Возвращает значение дополнительных бит для length
     */
    private fun getLengthValue(length: Int, code: Int): Int {
        if (code < 257 || code > 285) {
            throw IllegalArgumentException("Invalid length code: $code")
        }
        val idx = code - 257
        val base = LENGTH_BASE[idx]
        return length - base
    }
    
    /**
     * Преобразует расстояние в код DEFLATE (0-29)
     */
    private fun encodeDistance(distance: Int): Int {
        for (code in 0..29) {
            val base = DISTANCE_BASE[code]
            val extraBits = DISTANCE_EXTRA_BITS[code]
            val maxDistance = if (extraBits > 0) {
                base + (1 shl extraBits) - 1
            } else {
                base
            }
            if (distance >= base && distance <= maxDistance) {
                return code
            }
        }
        throw IllegalArgumentException("Invalid distance: $distance")
    }
    
    /**
     * Возвращает количество дополнительных бит для distance кода
     */
    private fun getDistanceExtraBits(code: Int): Int {
        if (code < 0 || code > 29) {
            throw IllegalArgumentException("Invalid distance code: $code")
        }
        return DISTANCE_EXTRA_BITS[code]
    }
    
    /**
     * Возвращает значение дополнительных бит для distance
     */
    private fun getDistanceValue(distance: Int, code: Int): Int {
        if (code < 0 || code > 29) {
            throw IllegalArgumentException("Invalid distance code: $code")
        }
        val base = DISTANCE_BASE[code]
        return distance - base
    }
    
    /**
     * Распаковывает DEFLATE данные
     */
    fun decompress(data: ByteArray): ByteArray {
        val reader = BitReader(data)
        val output = mutableListOf<Byte>()
        
        var bfinal = 0
        do {
            bfinal = reader.readBit()
            val btype = reader.readBits(2)
            
            if (btype == 0) {
                // Несжатый блок (не реализован)
                throw UnsupportedOperationException("Uncompressed blocks not supported")
            } else if (btype == 1) {
                // Фиксированные Huffman коды
                decompressFixedBlock(reader, output)
            } else if (btype == 2) {
                // Динамические Huffman коды (не реализован)
                throw UnsupportedOperationException("Dynamic Huffman blocks not supported")
            } else {
                throw IllegalArgumentException("Invalid BTYPE: $btype")
            }
        } while (bfinal == 0)
        
        return output.toByteArray()
    }
    
    /**
     * Распаковывает блок с фиксированными Huffman кодами
     */
    private fun decompressFixedBlock(reader: BitReader, output: MutableList<Byte>) {
        while (true) {
            // Проверяем конец данных перед чтением
            if (reader.isEnd()) {
                throw IllegalArgumentException("Unexpected end of data in DEFLATE block")
            }
            
            val code = try {
                Huffman.readLiteralLength(reader)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Error reading literal/length code: ${e.message}", e)
            }
            
            if (code == 256) {
                // Конец блока
                break
            } else if (code < 256) {
                // Literal
                output.add(code.toByte())
            } else {
                // Length code (257-285) - это ссылка на предыдущие данные
                val length = decodeLength(code, reader)
                
                // Проверяем конец данных перед чтением distance
                if (reader.isEnd()) {
                    throw IllegalArgumentException("Unexpected end of data while reading distance code for length $length")
                }
                
                // Читаем distance код (5 бит напрямую, должен быть 0-29)
                val distanceCode = reader.readBits(5)
                if (distanceCode > 29) {
                    throw IllegalArgumentException("Invalid distance code: $distanceCode (must be 0-29)")
                }
                
                // Декодируем distance из кода
                val distance = decodeDistance(distanceCode, reader)
                
                // Проверяем границы distance
                if (distance < 1) {
                    throw IllegalArgumentException("Invalid distance: $distance (must be >= 1)")
                }
                if (distance > output.size) {
                    throw IllegalArgumentException("Distance $distance exceeds output size ${output.size} (length: $length, distanceCode: $distanceCode)")
                }
                
                // Копируем данные
                val start = output.size - distance
                if (start < 0) {
                    throw IllegalArgumentException("Invalid start position: $start (distance: $distance, output size: ${output.size})")
                }
                for (i in 0 until length) {
                    if (start + i >= output.size) {
                        // Перекрывающееся копирование (когда length > distance)
                        val srcIndex = start + (i % distance)
                        output.add(output[srcIndex])
                    } else {
                        output.add(output[start + i])
                    }
                }
            }
        }
    }
    
    /**
     * Декодирует length из кода DEFLATE
     */
    private fun decodeLength(code: Int, reader: BitReader): Int {
        if (code < 257 || code > 285) {
            throw IllegalArgumentException("Invalid length code: $code")
        }
        val idx = code - 257
        val base = LENGTH_BASE[idx]
        val extraBits = LENGTH_EXTRA_BITS[idx]
        if (extraBits > 0) {
            val extra = reader.readBits(extraBits)
            return base + extra
        }
        return base
    }
    
    /**
     * Декодирует distance из кода DEFLATE
     */
    private fun decodeDistance(code: Int, reader: BitReader): Int {
        if (code < 0 || code > 29) {
            throw IllegalArgumentException("Invalid distance code: $code (must be 0-29), but got: $code")
        }
        val base = DISTANCE_BASE[code]
        val extraBits = DISTANCE_EXTRA_BITS[code]
        if (extraBits > 0) {
            if (reader.isEnd()) {
                throw IllegalArgumentException("Unexpected end of data while reading distance extra bits for code $code")
            }
            val extra = reader.readBits(extraBits)
            val distance = base + extra
            // Проверяем что distance в допустимых пределах
            val maxDistance = base + (1 shl extraBits) - 1
            if (distance < base || distance > maxDistance) {
                throw IllegalArgumentException("Invalid decoded distance: $distance (code: $code, base: $base, extra: $extra, max: $maxDistance)")
            }
            return distance
        }
        return base
    }
}

