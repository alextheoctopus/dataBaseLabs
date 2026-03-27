package com.customDB.compression

/**
 * Реализация CRC32 для GZIP (полином 0xEDB88320)
 */
object Crc32 {
    private val TABLE = IntArray(256)
    
    init {
        val polynomial = 0xEDB88320L
        for (i in 0..255) {
            var crc = i.toLong()
            for (j in 0..7) {
                crc = if ((crc and 1) != 0L) {
                    (crc ushr 1) xor polynomial
                } else {
                    crc ushr 1
                }
            }
            TABLE[i] = crc.toInt()
        }
    }
    
    /**
     * Вычисляет CRC32 для данных
     */
    fun compute(data: ByteArray): Long {
        var crc: Long = 0xFFFFFFFFL
        for (byte in data) {
            val index = ((crc xor byte.toLong()) and 0xFF).toInt()
            crc = (crc ushr 8) xor (TABLE[index].toLong() and 0xFFFFFFFFL)
        }
        return crc xor 0xFFFFFFFFL
    }
    
    /**
     * Обновляет CRC32 с новыми данными
     */
    fun update(crc: Long, data: ByteArray): Long {
        var currentCrc = crc
        for (byte in data) {
            val index = ((currentCrc xor byte.toLong()) and 0xFF).toInt()
            currentCrc = (currentCrc ushr 8) xor (TABLE[index].toLong() and 0xFFFFFFFFL)
        }
        return currentCrc
    }
}
