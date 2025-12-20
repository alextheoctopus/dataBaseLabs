package com.customDB.compression

/**
 * Реализация GZIP формата (RFC 1952)
 * Полностью самостоятельная реализация без использования Java библиотек
 */
object GzipFormat {
    
    /**
     * Сжимает данные в GZIP формат
     */
    fun compress(data: ByteArray): ByteArray {
        if (data.isEmpty()) {
            return createEmptyGzip()
        }
        
        // Вычисляем CRC32 исходных данных
        val crc32 = Crc32.compute(data)
        val originalSize = data.size.toLong()
        
        // Сжимаем данные используя DEFLATE
        val deflateData = Deflate.compress(data)
        
        // Создаем GZIP файл
        val output = mutableListOf<Byte>()
        
        // GZIP заголовок (10 байт минимум)
        // ID1, ID2 (магические байты)
        output.add(0x1F.toByte())
        output.add(0x8B.toByte())
        
        // CM (метод сжатия): 8 = DEFLATE
        output.add(0x08.toByte())
        
        // FLG (флаги): 0 = нет дополнительных полей
        output.add(0x00.toByte())
        
        // MTIME (время модификации): 0 (не используется)
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        
        // XFL (дополнительные флаги): 2 = максимальная компрессия
        output.add(0x02.toByte())
        
        // OS (операционная система): 255 = неизвестная
        output.add(0xFF.toByte())
        
        // DEFLATE данные
        output.addAll(deflateData.toList())
        
        // CRC32 (4 байта, little-endian)
        val crc32Bytes = longToLittleEndian(crc32 and 0xFFFFFFFFL, 4)
        output.addAll(crc32Bytes.toList())
        
        // ISIZE (размер исходных данных, 4 байта, little-endian)
        val sizeBytes = longToLittleEndian(originalSize, 4)
        output.addAll(sizeBytes.toList())
        
        return output.toByteArray()
    }
    
    /**
     * Распаковывает GZIP данные
     */
    fun decompress(gzipData: ByteArray): ByteArray {
        if (gzipData.size < 18) {
            throw IllegalArgumentException("GZIP data too short")
        }
        
        // Проверяем магические байты
        if (gzipData[0].toInt() and 0xFF != 0x1F || gzipData[1].toInt() and 0xFF != 0x8B) {
            throw IllegalArgumentException("Invalid GZIP magic bytes")
        }
        
        // Проверяем метод сжатия (должен быть 8 = DEFLATE)
        val cm = gzipData[2].toInt() and 0xFF
        if (cm != 8) {
            throw IllegalArgumentException("Unsupported compression method: $cm")
        }
        
        // Читаем флаги
        val flg = gzipData[3].toInt() and 0xFF
        
        var offset = 10 // Базовый размер заголовка
        
        // Пропускаем дополнительные поля если есть
        if ((flg and 0x04) != 0) {
            // FEXTRA: пропускаем 2 байта длины + данные
            val xlen = (gzipData[offset].toInt() and 0xFF) or 
                      ((gzipData[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2 + xlen
        }
        
        if ((flg and 0x08) != 0) {
            // FNAME: пропускаем имя файла (до нулевого байта)
            while (offset < gzipData.size && gzipData[offset] != 0.toByte()) {
                offset++
            }
            offset++ // Пропускаем нулевой байт
        }
        
        if ((flg and 0x10) != 0) {
            // FCOMMENT: пропускаем комментарий (до нулевого байта)
            while (offset < gzipData.size && gzipData[offset] != 0.toByte()) {
                offset++
            }
            offset++ // Пропускаем нулевой байт
        }
        
        if ((flg and 0x02) != 0) {
            // FHCRC: пропускаем CRC16 заголовка (2 байта)
            offset += 2
        }
        
        // Читаем DEFLATE данные (до последних 8 байт: CRC32 + ISIZE)
        val deflateSize = gzipData.size - offset - 8
        val deflateData = gzipData.sliceArray(offset until offset + deflateSize)
        
        // Распаковываем DEFLATE
        val decompressed = Deflate.decompress(deflateData)
        
        // Проверяем CRC32
        val crc32FromFile = littleEndianToLong(
            gzipData.sliceArray(gzipData.size - 8 until gzipData.size - 4)
        )
        val crc32Computed = Crc32.compute(decompressed)
        
        if ((crc32FromFile and 0xFFFFFFFFL) != (crc32Computed and 0xFFFFFFFFL)) {
            throw IllegalArgumentException("CRC32 mismatch")
        }
        
        // Проверяем размер
        val sizeFromFile = littleEndianToLong(
            gzipData.sliceArray(gzipData.size - 4 until gzipData.size)
        )
        if (sizeFromFile != decompressed.size.toLong()) {
            throw IllegalArgumentException("Size mismatch")
        }
        
        return decompressed
    }
    
    /**
     * Создает пустой GZIP файл
     */
    private fun createEmptyGzip(): ByteArray {
        val output = mutableListOf<Byte>()
        
        // Заголовок
        output.add(0x1F.toByte())
        output.add(0x8B.toByte())
        output.add(0x08.toByte())
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        output.add(0x02.toByte())
        output.add(0xFF.toByte())
        
        // DEFLATE для пустых данных
        val deflate = Deflate.compress(ByteArray(0))
        output.addAll(deflate.toList())
        
        // CRC32 = 0 для пустых данных
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        
        // ISIZE = 0
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        output.add(0x00.toByte())
        
        return output.toByteArray()
    }
    
    /**
     * Преобразует long в little-endian байты
     */
    private fun longToLittleEndian(value: Long, bytes: Int): ByteArray {
        val result = ByteArray(bytes)
        for (i in 0 until bytes) {
            result[i] = ((value shr (i * 8)) and 0xFF).toByte()
        }
        return result
    }
    
    /**
     * Преобразует little-endian байты в long
     */
    private fun littleEndianToLong(bytes: ByteArray): Long {
        var result = 0L
        for (i in bytes.indices) {
            result = result or ((bytes[i].toLong() and 0xFF) shl (i * 8))
        }
        return result
    }
}
