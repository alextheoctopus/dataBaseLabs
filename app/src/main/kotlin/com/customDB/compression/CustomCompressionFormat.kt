package com.customDB.compression

object CustomCompressionFormat {
    fun compress(data: ByteArray): ByteArray {
        if (data.isEmpty()) {
            return byteArrayOf(0, 0, 0, 0) // Пустые данные: только размер (0)
        }
        
        // Используем наш LZ77 для сжатия
        val lz77Compressed = LZ77.compress(data)
        
        // Кодируем токены в бинарный формат
        return LZ77Encoder.encode(lz77Compressed)
    }
    
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

