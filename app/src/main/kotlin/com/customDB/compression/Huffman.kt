package com.customDB.compression

/**
 * Фиксированные Huffman коды для DEFLATE (BTYPE=01)
 * Согласно RFC 1951
 * 
 * В DEFLATE коды записываются LSB first (младший бит первым)
 */
object Huffman {
    
    // Таблица декодирования: прочитанный код (LSB first) -> значение
    // Ключ: (длина_кода shl 16) or прочитанный_код
    private val LITERAL_LENGTH_DECODE_TABLE = mutableMapOf<Int, Int>()
    private val DISTANCE_DECODE_TABLE = mutableMapOf<Int, Int>()
    
    init {
        // Инициализация таблицы декодирования для literal/length
        // В DEFLATE фиксированные коды определены в MSB first формате в спецификации
        // Но записываются/читаются в LSB first формате
        // Таблица содержит LSB коды (то что мы читаем через BitReader)
        
        // Значения 256-279: 7 бит, коды 0-23 в MSB first
        // При записи/чтении: код 0-23 как есть (LSB first)
        for (i in 256..279) {
            val lsbCode = i - 256 // 0-23, записывается/читается как есть
            LITERAL_LENGTH_DECODE_TABLE[(7 shl 16) or lsbCode] = i
        }
        
        // Значения 0-143: 8 бит, коды 0x30-0xBF в MSB first
        // При записи/чтении: reverseBits(0x30+i, 8)
        for (i in 0..143) {
            val msbCode = 0x30 + i
            val lsbCode = reverseBits(msbCode, 8)
            LITERAL_LENGTH_DECODE_TABLE[(8 shl 16) or lsbCode] = i
        }
        
        // Значения 280-287: 8 бит, коды 0xC0-0xC7 в MSB first
        for (i in 280..287) {
            val msbCode = 0xC0 + (i - 280)
            val lsbCode = reverseBits(msbCode, 8)
            LITERAL_LENGTH_DECODE_TABLE[(8 shl 16) or lsbCode] = i
        }
        
        // Значения 144-255: 9 бит, коды 0x190-0x1FF в MSB first
        for (i in 144..255) {
            val msbCode = 0x190 + (i - 144)
            val lsbCode = reverseBits(msbCode, 9)
            LITERAL_LENGTH_DECODE_TABLE[(9 shl 16) or lsbCode] = i
        }
        
        // Инициализация таблицы декодирования для distance (0-31): 5 бит
        // Distance коды простые: 0-31 напрямую (LSB first)
        for (i in 0..31) {
            DISTANCE_DECODE_TABLE[i] = i
        }
    }
    
    /**
     * Обращает биты в числе (MSB first -> LSB first)
     */
    private fun reverseBits(value: Int, bitCount: Int): Int {
        var result = 0
        var v = value
        for (i in 0 until bitCount) {
            result = (result shl 1) or (v and 1)
            v = v shr 1
        }
        return result
    }
    
    /**
     * Записывает literal/length код (LSB first)
     * В DEFLATE коды определены в MSB first, но записываются LSB first
     */
    fun writeLiteralLength(writer: BitWriter, value: Int) {
        if (value < 0 || value > 287) {
            throw IllegalArgumentException("Literal/length value out of range: $value")
        }
        
        val (msbCode, length) = when {
            value >= 256 && value <= 279 -> {
                // 7 бит: коды 0-23 в MSB first
                (value - 256) to 7
            }
            value >= 0 && value <= 143 -> {
                // 8 бит: коды 0x30-0xBF в MSB first
                (0x30 + value) to 8
            }
            value >= 280 && value <= 287 -> {
                // 8 бит: коды 0xC0-0xC7 в MSB first
                (0xC0 + (value - 280)) to 8
            }
            value >= 144 && value <= 255 -> {
                // 9 бит: коды 0x190-0x1FF в MSB first
                (0x190 + (value - 144)) to 9
            }
            else -> throw IllegalArgumentException("Invalid literal/length value: $value")
        }
        
        // Преобразуем MSB first код в LSB first для записи
        val lsbCode = reverseBits(msbCode, length)
        writer.writeBits(lsbCode, length)
    }
    
    /**
     * Записывает distance код (5 бит, LSB first)
     * Distance коды простые: 0-31 напрямую
     */
    fun writeDistance(writer: BitWriter, value: Int) {
        if (value < 0 || value > 31) {
            throw IllegalArgumentException("Distance value out of range: $value")
        }
        // Distance коды записываются как есть (0-31)
        writer.writeBits(value, 5)
    }
    
    /**
     * Читает literal/length код используя таблицу декодирования
     * В DEFLATE коды читаются LSB first
     */
    fun readLiteralLength(reader: BitReader): Int {
        // Читаем первые 7 бит
        var code = 0
        try {
            for (i in 0 until 7) {
                val bit = reader.readBit()
                code = code or (bit shl i)
            }
            var key = (7 shl 16) or code
            if (LITERAL_LENGTH_DECODE_TABLE.containsKey(key)) {
                return LITERAL_LENGTH_DECODE_TABLE[key]!!
            }
            
            // Читаем 8-й бит
            val bit7 = reader.readBit()
            code = code or (bit7 shl 7)
            key = (8 shl 16) or code
            if (LITERAL_LENGTH_DECODE_TABLE.containsKey(key)) {
                return LITERAL_LENGTH_DECODE_TABLE[key]!!
            }
            
            // Читаем 9-й бит
            val bit8 = reader.readBit()
            code = code or (bit8 shl 8)
            key = (9 shl 16) or code
            if (LITERAL_LENGTH_DECODE_TABLE.containsKey(key)) {
                return LITERAL_LENGTH_DECODE_TABLE[key]!!
            }
        } catch (e: IllegalArgumentException) {
            if (e.message == "End of data") {
                throw IllegalArgumentException("Unexpected end of data while reading literal/length code")
            }
            throw e
        }
        
        throw IllegalArgumentException("Invalid literal/length code: $code (not found in decode table)")
    }
    
    /**
     * Читает distance код (5 бит)
     * Distance коды в DEFLATE читаются как 5 бит напрямую (0-31)
     * Но в DEFLATE используются только коды 0-29
     */
    fun readDistance(reader: BitReader): Int {
        if (reader.isEnd()) {
            throw IllegalArgumentException("End of data while reading distance code")
        }
        val code = reader.readBits(5)
        // Distance коды 0-31 напрямую (не нужно декодировать через таблицу)
        // Но в DEFLATE используются только 0-29
        if (code > 29) {
            throw IllegalArgumentException("Invalid distance code: $code (must be 0-29)")
        }
        return code
    }
}
