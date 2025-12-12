package com.customDB.compression

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

class DeflateEncoder {
    
    companion object {
        fun encode(compressed: LZ77.CompressedData): ByteArray {
            val output = ByteArrayOutputStream()
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
            
            val tokens = compressed.tokens
            val data = ByteArray(compressed.originalSize)
            var pos = 0
            
            for (token in tokens) {
                when (token) {
                    is LZ77.Token.Literal -> {
                        if (pos < data.size) {
                            data[pos++] = token.value.toByte()
                        }
                    }
                    is LZ77.Token.Reference -> {
                        val startPos = pos - token.offset
                        if (startPos >= 0) {
                            for (i in 0 until token.length) {
                                if (pos < data.size && startPos + i < pos) {
                                    data[pos++] = data[startPos + i]
                                }
                            }
                        }
                    }
                }
            }
            
            deflater.setInput(data)
            deflater.finish()
            
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                output.write(buffer, 0, count)
            }
            
            deflater.end()
            return output.toByteArray()
        }
        
        fun decode(data: ByteArray): ByteArray {
            val inflater = Inflater(false)
            inflater.setInput(data)
            
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                output.write(buffer, 0, count)
            }
            
            inflater.end()
            return output.toByteArray()
        }
    }
}
