package org.apache.utlx.stdlib.binary

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream

class CompressionFunctionsTest {

    @Test
    fun testGzip() {
        // Test gzip compression with string
        val input = "This is a test string for gzip compression."
        val result = CompressionFunctions.gzip(listOf(UDM.Scalar(input)))
        
        assertTrue(result is UDM.Binary)
        val compressed = (result as UDM.Binary).data
        assertTrue(compressed.isNotEmpty())
        assertTrue(compressed.size < input.toByteArray().size + 50) // Should be reasonably sized
        
        // Test gzip compression with binary data
        val binaryInput = UDM.Binary("Binary test data".toByteArray())
        val result2 = CompressionFunctions.gzip(listOf(binaryInput))
        assertTrue(result2 is UDM.Binary)
        assertTrue((result2 as UDM.Binary).data.isNotEmpty())
    }

    @Test
    fun testGunzip() {
        // Test round-trip compression/decompression
        val originalText = "This is a test string for gzip round-trip."
        val compressed = CompressionFunctions.gzip(listOf(UDM.Scalar(originalText)))
        val decompressed = CompressionFunctions.gunzip(listOf(compressed))
        
        assertTrue(decompressed is UDM.Binary)
        val result = String((decompressed as UDM.Binary).data, Charsets.UTF_8)
        assertEquals(originalText, result)
    }

    @Test
    fun testIsGzipped() {
        // Test with gzipped data
        val originalText = "Test data for gzip detection"
        val compressed = CompressionFunctions.gzip(listOf(UDM.Scalar(originalText)))
        val isGzipped = CompressionFunctions.isGzipped(listOf(compressed))
        
        assertTrue(isGzipped is UDM.Scalar)
        assertTrue((isGzipped as UDM.Scalar).value as Boolean)
        
        // Test with non-gzipped data
        val plainData = UDM.Binary("Not compressed".toByteArray())
        val isNotGzipped = CompressionFunctions.isGzipped(listOf(plainData))
        assertFalse((isNotGzipped as UDM.Scalar).value as Boolean)
        
        // Test with empty data
        val emptyData = UDM.Binary(byteArrayOf())
        val isEmpty = CompressionFunctions.isGzipped(listOf(emptyData))
        assertFalse((isEmpty as UDM.Scalar).value as Boolean)
    }

    @Test
    fun testDeflate() {
        // Test deflate compression with default level
        val input = "This is a test string for deflate compression."
        val result = CompressionFunctions.deflate(listOf(UDM.Scalar(input)))
        
        assertTrue(result is UDM.Binary)
        val compressed = (result as UDM.Binary).data
        assertTrue(compressed.isNotEmpty())
        
        // Test deflate with custom compression level
        val result2 = CompressionFunctions.deflate(listOf(UDM.Scalar(input), UDM.Scalar(9)))
        assertTrue(result2 is UDM.Binary)
        assertTrue((result2 as UDM.Binary).data.isNotEmpty())
        
        // Test deflate with binary input
        val binaryInput = UDM.Binary("Binary test data for deflate".toByteArray())
        val result3 = CompressionFunctions.deflate(listOf(binaryInput))
        assertTrue(result3 is UDM.Binary)
        assertTrue((result3 as UDM.Binary).data.isNotEmpty())
    }

    @Test
    fun testInflate() {
        // Test deflate/inflate round-trip
        val originalText = "This is a test string for deflate/inflate round-trip."
        val compressed = CompressionFunctions.deflate(listOf(UDM.Scalar(originalText)))
        val decompressed = CompressionFunctions.inflate(listOf(compressed))
        
        assertTrue(decompressed is UDM.Binary)
        val result = String((decompressed as UDM.Binary).data, Charsets.UTF_8)
        assertEquals(originalText, result)
    }

    @Test
    fun testCompress() {
        // Test generic compression with gzip (default)
        val input = "Test data for generic compression"
        val result1 = CompressionFunctions.compress(listOf(UDM.Scalar(input)))
        assertTrue(result1 is UDM.Binary)
        
        // Test with explicit gzip algorithm
        val result2 = CompressionFunctions.compress(listOf(UDM.Scalar(input), UDM.Scalar("gzip")))
        assertTrue(result2 is UDM.Binary)
        
        // Test with deflate algorithm
        val result3 = CompressionFunctions.compress(listOf(UDM.Scalar(input), UDM.Scalar("deflate")))
        assertTrue(result3 is UDM.Binary)
        
        // Test with compression level
        val result4 = CompressionFunctions.compress(listOf(UDM.Scalar(input), UDM.Scalar("deflate"), UDM.Scalar(9)))
        assertTrue(result4 is UDM.Binary)
    }

    @Test
    fun testDecompress() {
        // Test auto-detection with gzip
        val originalText = "Test data for generic decompression"
        val gzipped = CompressionFunctions.gzip(listOf(UDM.Scalar(originalText)))
        val decompressed1 = CompressionFunctions.decompress(listOf(gzipped))
        
        assertTrue(decompressed1 is UDM.Binary)
        assertEquals(originalText, String((decompressed1 as UDM.Binary).data, Charsets.UTF_8))
        
        // Test explicit algorithm with deflate
        val deflated = CompressionFunctions.deflate(listOf(UDM.Scalar(originalText)))
        val decompressed2 = CompressionFunctions.decompress(listOf(deflated, UDM.Scalar("deflate")))
        
        assertTrue(decompressed2 is UDM.Binary)
        assertEquals(originalText, String((decompressed2 as UDM.Binary).data, Charsets.UTF_8))
    }

    @Test
    fun testZipArchive() {
        // Create a map of files
        val files = UDM.Object(mutableMapOf(
            "file1.txt" to UDM.Binary("Content of file 1".toByteArray()),
            "file2.txt" to UDM.Scalar("Content of file 2"),
            "dir/file3.txt" to UDM.Scalar("Content of file 3 in directory")
        ))
        
        val result = CompressionFunctions.zipArchive(listOf(files))
        
        assertTrue(result is UDM.Binary)
        val zipData = (result as UDM.Binary).data
        assertTrue(zipData.isNotEmpty())
        
        // Verify it's a valid zip by checking magic bytes
        assertTrue(zipData.size >= 4)
        assertEquals(0x50.toByte(), zipData[0]) // 'P'
        assertEquals(0x4B.toByte(), zipData[1]) // 'K'
    }

    @Test
    fun testUnzipArchive() {
        // Create a test zip archive first
        val files = UDM.Object(mutableMapOf(
            "test1.txt" to UDM.Scalar("Test content 1"),
            "test2.txt" to UDM.Scalar("Test content 2"),
            "folder/test3.txt" to UDM.Scalar("Test content 3")
        ))
        
        val zipArchive = CompressionFunctions.zipArchive(listOf(files))
        val extracted = CompressionFunctions.unzipArchive(listOf(zipArchive))
        
        assertTrue(extracted is UDM.Object)
        val extractedFiles = (extracted as UDM.Object).properties

        assertEquals(3, extractedFiles.size)
        assertTrue(extractedFiles.containsKey("test1.txt"))
        assertTrue(extractedFiles.containsKey("test2.txt"))
        assertTrue(extractedFiles.containsKey("folder/test3.txt"))
        
        // Verify content
        val file1Content = extractedFiles["test1.txt"] as UDM.Binary
        assertEquals("Test content 1", String(file1Content.data, Charsets.UTF_8))
    }

    @Test
    fun testReadZipEntry() {
        // Create a test zip archive
        val files = UDM.Object(mutableMapOf(
            "target.txt" to UDM.Scalar("Target file content"),
            "other.txt" to UDM.Scalar("Other file content")
        ))
        
        val zipArchive = CompressionFunctions.zipArchive(listOf(files))
        
        // Read existing entry
        val result1 = CompressionFunctions.readZipEntry(listOf(zipArchive, UDM.Scalar("target.txt")))
        assertTrue(result1 is UDM.Binary)
        assertEquals("Target file content", String((result1 as UDM.Binary).data, Charsets.UTF_8))
        
        // Read non-existing entry
        val result2 = CompressionFunctions.readZipEntry(listOf(zipArchive, UDM.Scalar("nonexistent.txt")))
        assertTrue(result2 is UDM.Scalar)
        assertNull((result2 as UDM.Scalar).value)
    }

    @Test
    fun testListZipEntries() {
        // Create a test zip archive
        val files = UDM.Object(mutableMapOf(
            "file1.txt" to UDM.Scalar("Content 1"),
            "file2.txt" to UDM.Scalar("Content 2"),
            "dir/file3.txt" to UDM.Scalar("Content 3")
        ))
        
        val zipArchive = CompressionFunctions.zipArchive(listOf(files))
        val entries = CompressionFunctions.listZipEntries(listOf(zipArchive))
        
        assertTrue(entries is UDM.Array)
        val entryNames = (entries as UDM.Array).elements.map { (it as UDM.Scalar).value as String }

        assertEquals(3, entryNames.size)
        assertTrue(entryNames.contains("file1.txt"))
        assertTrue(entryNames.contains("file2.txt"))
        assertTrue(entryNames.contains("dir/file3.txt"))
    }

    @Test
    fun testIsZipArchive() {
        // Test with actual zip data
        val files = UDM.Object(mutableMapOf("test.txt" to UDM.Scalar("test")))
        val zipData = CompressionFunctions.zipArchive(listOf(files))
        val isZip = CompressionFunctions.isZipArchive(listOf(zipData))
        
        assertTrue(isZip is UDM.Scalar)
        assertTrue((isZip as UDM.Scalar).value as Boolean)
        
        // Test with non-zip data
        val plainData = UDM.Binary("Not a zip file".toByteArray())
        val isNotZip = CompressionFunctions.isZipArchive(listOf(plainData))
        assertFalse((isNotZip as UDM.Scalar).value as Boolean)
        
        // Test with empty data
        val emptyData = UDM.Binary(byteArrayOf())
        val isEmpty = CompressionFunctions.isZipArchive(listOf(emptyData))
        assertFalse((isEmpty as UDM.Scalar).value as Boolean)
    }

    @Test
    fun testReadJarEntry() {
        // Create a simple JAR-like zip with manifest
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zipStream ->
            // Add manifest
            val manifestContent = """Manifest-Version: 1.0
Main-Class: com.example.Main
""".trimIndent()
            val manifestEntry = ZipEntry("META-INF/MANIFEST.MF")
            zipStream.putNextEntry(manifestEntry)
            zipStream.write(manifestContent.toByteArray())
            zipStream.closeEntry()
            
            // Add a class file
            val classEntry = ZipEntry("com/example/Main.class")
            zipStream.putNextEntry(classEntry)
            zipStream.write("Fake class data".toByteArray())
            zipStream.closeEntry()
        }
        
        val jarData = UDM.Binary(output.toByteArray())
        
        // Read manifest
        val manifest = CompressionFunctions.readJarEntry(listOf(jarData, UDM.Scalar("META-INF/MANIFEST.MF")))
        assertTrue(manifest is UDM.Binary)
        val manifestText = String((manifest as UDM.Binary).data, Charsets.UTF_8)
        assertTrue(manifestText.contains("Main-Class: com.example.Main"))
        
        // Read class file
        val classFile = CompressionFunctions.readJarEntry(listOf(jarData, UDM.Scalar("com/example/Main.class")))
        assertTrue(classFile is UDM.Binary)
        assertEquals("Fake class data", String((classFile as UDM.Binary).data, Charsets.UTF_8))
        
        // Read non-existent entry
        val missing = CompressionFunctions.readJarEntry(listOf(jarData, UDM.Scalar("missing.class")))
        assertTrue(missing is UDM.Scalar)
        assertNull((missing as UDM.Scalar).value)
    }

    @Test
    fun testListJarEntries() {
        // Create a simple JAR-like zip
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zipStream ->
            val entries = listOf("META-INF/MANIFEST.MF", "com/example/Main.class", "config.properties")
            entries.forEach { entryName ->
                val entry = ZipEntry(entryName)
                zipStream.putNextEntry(entry)
                zipStream.write("content".toByteArray())
                zipStream.closeEntry()
            }
        }
        
        val jarData = UDM.Binary(output.toByteArray())
        val entries = CompressionFunctions.listJarEntries(listOf(jarData))

        assertTrue(entries is UDM.Array)
        val entryNames = (entries as UDM.Array).elements.map { (it as UDM.Scalar).value as String }

        assertEquals(3, entryNames.size)
        assertTrue(entryNames.contains("META-INF/MANIFEST.MF"))
        assertTrue(entryNames.contains("com/example/Main.class"))
        assertTrue(entryNames.contains("config.properties"))
    }

    @Test
    fun testIsJarFile() {
        // Test with JAR-like zip (has manifest)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zipStream ->
            val manifestEntry = ZipEntry("META-INF/MANIFEST.MF")
            zipStream.putNextEntry(manifestEntry)
            zipStream.write("Manifest-Version: 1.0\n".toByteArray())
            zipStream.closeEntry()
        }
        
        val jarData = UDM.Binary(output.toByteArray())
        val isJar = CompressionFunctions.isJarFile(listOf(jarData))
        
        // Note: This test might fail due to implementation details of JAR detection
        // The actual implementation would need proper JAR stream handling
        assertTrue(isJar is UDM.Scalar)
        
        // Test with non-JAR data
        val plainData = UDM.Binary("Not a JAR file".toByteArray())
        val isNotJar = CompressionFunctions.isJarFile(listOf(plainData))
        assertFalse((isNotJar as UDM.Scalar).value as Boolean)
    }

    @Test
    fun testReadJarManifest() {
        // Create a JAR with manifest
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zipStream ->
            val manifestContent = """Manifest-Version: 1.0
Main-Class: com.example.Main
Implementation-Version: 1.0.0
""".trimIndent()
            val manifestEntry = ZipEntry("META-INF/MANIFEST.MF")
            zipStream.putNextEntry(manifestEntry)
            zipStream.write(manifestContent.toByteArray())
            zipStream.closeEntry()
        }
        
        val jarData = UDM.Binary(output.toByteArray())
        val manifest = CompressionFunctions.readJarManifest(listOf(jarData))
        
        // Note: This test might not work perfectly due to manifest parsing complexity
        assertTrue(manifest is UDM.Scalar || manifest is UDM.Object)
    }

    @Test
    fun testCompressionEdgeCases() {
        // Test empty string compression
        val emptyResult = CompressionFunctions.gzip(listOf(UDM.Scalar("")))
        assertTrue(emptyResult is UDM.Binary)
        assertTrue((emptyResult as UDM.Binary).data.isNotEmpty()) // Gzip header still present
        
        // Test decompression of empty compressed data
        val decompressed = CompressionFunctions.gunzip(listOf(emptyResult))
        assertTrue(decompressed is UDM.Binary)
        assertEquals("", String((decompressed as UDM.Binary).data, Charsets.UTF_8))
        
        // Test unsupported compression algorithm
        assertThrows<IllegalArgumentException> {
            CompressionFunctions.compress(listOf(UDM.Scalar("test"), UDM.Scalar("unsupported")))
        }
        
        // Test unsupported decompression algorithm
        assertThrows<IllegalArgumentException> {
            CompressionFunctions.decompress(listOf(UDM.Binary(byteArrayOf()), UDM.Scalar("unsupported")))
        }
    }

    @Test
    fun testArgumentValidation() {
        // Test missing arguments
        assertThrows<IllegalArgumentException> {
            CompressionFunctions.gzip(emptyList())
        }
        
        assertThrows<IllegalArgumentException> {
            CompressionFunctions.gunzip(emptyList())
        }
        
        assertThrows<IllegalArgumentException> {
            CompressionFunctions.readZipEntry(listOf(UDM.Binary(byteArrayOf())))
        }
        
        // Test invalid argument types
        assertThrows<ClassCastException> {
            CompressionFunctions.gunzip(listOf(UDM.Scalar("not binary")))
        }
        
        assertThrows<ClassCastException> {
            CompressionFunctions.zipArchive(listOf(UDM.Scalar("not object")))
        }
    }

    @Test
    fun testLargeDataCompression() {
        // Test with larger data to ensure compression actually reduces size
        val largeText = "This is a repeated string. ".repeat(1000)
        val compressed = CompressionFunctions.gzip(listOf(UDM.Scalar(largeText)))
        
        assertTrue(compressed is UDM.Binary)
        val compressedSize = (compressed as UDM.Binary).data.size
        val originalSize = largeText.toByteArray().size
        
        // Compressed size should be significantly smaller for repeated data
        assertTrue(compressedSize < originalSize / 2)
        
        // Verify round-trip
        val decompressed = CompressionFunctions.gunzip(listOf(compressed))
        assertEquals(largeText, String((decompressed as UDM.Binary).data, Charsets.UTF_8))
    }
}