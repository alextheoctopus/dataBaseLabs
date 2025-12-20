package com.customDB.compression

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HuffmanTest {
    
    @Test
    fun testWriteReadLiteral() {
        val writer = BitWriter()
        Huffman.writeLiteralLength(writer, 65) // Буква 'A'
        val data = writer.finish()
        
        val reader = BitReader(data)
        val decoded = Huffman.readLiteralLength(reader)
        
        assertEquals(65, decoded)
    }
    
    @Test
    fun testWriteReadEndBlock() {
        val writer = BitWriter()
        Huffman.writeLiteralLength(writer, 256) // END блок
        val data = writer.finish()
        
        val reader = BitReader(data)
        val decoded = Huffman.readLiteralLength(reader)
        
        assertEquals(256, decoded)
    }
    
    @Test
    fun testWriteReadLengthCode() {
        val writer = BitWriter()
        // Length code 257 = length 3
        Huffman.writeLiteralLength(writer, 257)
        val data = writer.finish()
        
        val reader = BitReader(data)
        val decoded = Huffman.readLiteralLength(reader)
        
        assertEquals(257, decoded)
    }
}
