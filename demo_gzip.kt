import com.customDB.compression.GzipFormat
import java.io.File

fun main() {
    val originalText = """
        This is a test file for demonstrating GZIP compression.
        It contains some repeated text: "Hello World" "Hello World" "Hello World"
        And some JSON-like data: {"id":1,"name":"Test","value":123}
        More repetition: ABC ABC ABC ABC ABC
        The quick brown fox jumps over the lazy dog.
        The quick brown fox jumps over the lazy dog.
        The quick brown fox jumps over the lazy dog.
    """.trimIndent()
    
    println("\n" + "=".repeat(70))
    println("ORIGINAL DATA (before compression):")
    println("=".repeat(70))
    println(originalText)
    println("\nOriginal size: ${originalText.toByteArray().size} bytes")
    
    val originalBytes = originalText.toByteArray()
    val compressed = GzipFormat.compress(originalBytes)
    
    println("\n" + "=".repeat(70))
    println("COMPRESSED DATA (hex dump, first 100 bytes):")
    println("=".repeat(70))
    val hexPreview = compressed.take(100).joinToString(" ") { 
        String.format("%02X", it.toInt() and 0xFF) 
    }
    println(hexPreview + "...")
    println("Compressed size: ${compressed.size} bytes")
    println("Compression ratio: ${String.format("%.1f", compressed.size.toDouble() / originalBytes.size * 100)}%")
    
    val gzipFile = File("demo_test.gz")
    gzipFile.writeBytes(compressed)
    
    println("\n" + "=".repeat(70))
    println("GZIP FILE CREATED: ${gzipFile.absolutePath}")
    println("=".repeat(70))
    println("Size: ${gzipFile.length()} bytes")
    println("\nGZIP header (first 10 bytes):")
    val header = compressed.take(10)
    println("  Magic: ${String.format("0x%02X 0x%02X", header[0].toInt() and 0xFF, header[1].toInt() and 0xFF)}")
    println("  Method: ${header[2].toInt() and 0xFF} (8 = DEFLATE)")
    
    println("\n" + "=".repeat(70))
    println("DECOMPRESSED DATA (using our implementation):")
    println("=".repeat(70))
    val decompressed = GzipFormat.decompress(compressed)
    val decompressedText = String(decompressed)
    println(decompressedText)
    println("\nDecompressed size: ${decompressedText.toByteArray().size} bytes")
    
    if (decompressedText == originalText) {
        println("\n✓ SUCCESS: Decompressed data matches original exactly!")
    }
    
    println("\n" + "=".repeat(70))
    println("Now test with gunzip:")
    println("=".repeat(70))
    println("  gunzip -c ${gzipFile.absolutePath}")
    println("  zcat ${gzipFile.absolutePath}")
}

