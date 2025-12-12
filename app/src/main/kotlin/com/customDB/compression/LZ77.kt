package com.customDB.compression

class LZ77 {
    
    data class Match(val offset: Int, val length: Int)
    
    companion object {
        private const val WINDOW_SIZE = 32768
        private const val MIN_MATCH_LENGTH = 3
        private const val MAX_MATCH_LENGTH = 258
        
        fun compress(data: ByteArray): CompressedData {
            if (data.isEmpty()) return CompressedData(emptyList(), data.size)
            
            val tokens = mutableListOf<Token>()
            var i = 0
            
            while (i < data.size) {
                val windowStart = maxOf(0, i - WINDOW_SIZE)
                val searchBuffer = data.sliceArray(windowStart until i)
                
                val match = findLongestMatch(data, i, searchBuffer)
                
                if (match != null && match.length >= MIN_MATCH_LENGTH) {
                    tokens.add(Token.Reference(match.offset, match.length))
                    i += match.length
                } else {
                    tokens.add(Token.Literal(data[i].toInt() and 0xFF))
                    i++
                }
            }
            
            return CompressedData(tokens, data.size)
        }
        
        private fun findLongestMatch(
            data: ByteArray,
            pos: Int,
            searchBuffer: ByteArray
        ): Match? {
            if (searchBuffer.isEmpty() || pos >= data.size) return null
            
            var bestMatch: Match? = null
            var bestLength = MIN_MATCH_LENGTH - 1
            
            val maxOffset = minOf(searchBuffer.size, WINDOW_SIZE)
            
            for (offset in 1..maxOffset) {
                val searchPos = searchBuffer.size - offset
                if (searchPos < 0) continue
                
                var length = 0
                while (length < MAX_MATCH_LENGTH &&
                       pos + length < data.size &&
                       searchPos + length < searchBuffer.size &&
                       searchBuffer[searchPos + length] == data[pos + length]) {
                    length++
                }
                
                if (length > bestLength) {
                    bestLength = length
                    bestMatch = Match(offset, length)
                }
            }
            
            return bestMatch
        }
        
        fun decompress(compressed: CompressedData): ByteArray {
            val output = mutableListOf<Byte>()
            
            for (token in compressed.tokens) {
                when (token) {
                    is Token.Literal -> {
                        output.add(token.value.toByte())
                    }
                    is Token.Reference -> {
                        val startPos = output.size - token.offset
                        if (startPos < 0) {
                            throw IllegalArgumentException("Invalid reference: offset ${token.offset} exceeds output size")
                        }
                        
                        for (i in 0 until token.length) {
                            if (startPos + i >= output.size) {
                                throw IllegalArgumentException("Invalid reference: length ${token.length} exceeds available data")
                            }
                            output.add(output[startPos + i])
                        }
                    }
                }
            }
            
            return output.toByteArray()
        }
    }
    
    sealed class Token {
        data class Literal(val value: Int) : Token()
        data class Reference(val offset: Int, val length: Int) : Token()
    }
    
    data class CompressedData(
        val tokens: List<Token>,
        val originalSize: Int
    )
}

