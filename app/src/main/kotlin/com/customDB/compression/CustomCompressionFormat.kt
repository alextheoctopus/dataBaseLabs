package com.customDB.compression

/**
 * Формат сжатия на основе нашего LZ77 алгоритма
 * Использует LZ77 для сжатия и LZ77Encoder для сериализации
 */
object CustomCompressionFormat {
    
    /**
     * Сжимает данные используя наш LZ77 алгоритм
     * @return сжатые данные в бинарном формате
     */
    fun compress(data: ByteArray): ByteArray {
        if (data.isEmpty()) {
            return byteArrayOf(0, 0, 0, 0) // Пустые данные: только размер (0)
        }
        
        // Используем наш LZ77 для сжатия
        val lz77Compressed = LZ77.compress(data)
        
        // Кодируем токены в бинарный формат
        return LZ77Encoder.encode(lz77Compressed)
    }
    
    /**
     * Распаковывает данные используя наш LZ77 алгоритм
     * @return исходные данные
     */
    fun decompress(compressedData: ByteArray): ByteArray {
        if (compressedData.isEmpty()) {
            return ByteArray(0)
        }
        
        // Декодируем бинарный формат обратно в LZ77.CompressedData
        val lz77Compressed = LZ77Encoder.decode(compressedData)
        
        // Распаковываем используя наш LZ77
        return LZ77.decompress(lz77Compressed)
    }
}

