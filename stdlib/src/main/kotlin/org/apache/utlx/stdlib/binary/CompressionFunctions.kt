/**
 * Compression Functions for UTL-X Standard Library
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/binary/CompressionFunctions.kt
 * 
 * IMPORTANT NAMING DISTINCTION:
 * - zip/unzip (arrays) = ArrayFunctions - combine/split arrays
 * - gzip/gunzip (compression) = CompressionFunctions - compress/decompress data
 * - zipArchive/unzipArchive = CompressionFunctions - create/extract zip archives
 * 
 * Provides compression, decompression, and archive operations for:
 * - Gzip compression (HTTP, file compression)
 * - Deflate compression (raw compression)
 * - Zip archives (multiple files)
 * - JAR files (Java archives)
 * 
 * Use Cases:
 * - Compressing HTTP responses
 * - Reading compressed log files
 * - Extracting files from zip/jar archives
 * - Creating deployment packages
 * 
 * Example:
 *   // Compress data
 *   let compressed = gzip(toBinary("Large text data"))
 *   
 *   // Create zip archive
 *   let archive = zipArchive({
 *     "file1.txt": toBinary("content1"),
 *     "file2.json": toBinary("{...}")
 *   })
 */

package org.apache.utlx.stdlib.binary

import org.apache.utlx.core.udm.UDM
import java.io.*
import java.util.zip.*
import java.util.jar.JarInputStream
import java.util.jar.JarEntry
import org.apache.utlx.stdlib.annotations.UTLXFunction

object CompressionFunctions {
    
    // ============================================================================
    // GZIP COMPRESSION (Most common for HTTP)
    // ============================================================================
    
    @UTLXFunction(
        description = "Compress data using Gzip",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "gzip(...) => result",
        notes = """Example:
gzip(toBinary("Large text data..."))
gzip("Text string")  // Auto-converts to binary""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Compress data using Gzip
     * 
     * @param args[0] data - Binary data or string to compress
     * @return ByteArray of compressed data
     * 
     * Example:
     *   gzip(toBinary("Large text data..."))
     *   gzip("Text string")  // Auto-converts to binary
     */
    fun gzip(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "gzip requires 1 argument: data" }
        
        val data = when {
            args[0] is UDM.Binary -> (args[0] as UDM.Binary).data
            args[0] is UDM.Scalar && (args[0] as UDM.Scalar).value is String -> ((args[0] as UDM.Scalar).value as String).toByteArray(Charsets.UTF_8)
            else -> throw IllegalArgumentException("gzip requires binary data or string")
        }
        
        return try {
            val output = ByteArrayOutputStream()
            GZIPOutputStream(output).use { gzipStream ->
                gzipStream.write(data)
            }
            UDM.fromNative(output.toByteArray())
        } catch (e: IOException) {
            throw CompressionException("Failed to gzip data: ${e.message}", e)
        }
    }
    
    @UTLXFunction(
        description = "Decompress Gzip data",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "gunzip(...) => result",
        notes = """Example:
let original = gunzip(compressed)
let text = binaryToString(original)""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Decompress Gzip data
     * 
     * @param args[0] compressedData - Gzip compressed binary data
     * @return ByteArray of decompressed data
     * 
     * Example:
     *   let original = gunzip(compressed)
     *   let text = binaryToString(original)
     */
    fun gunzip(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "gunzip requires 1 argument: compressedData" }
        
        val compressedData = (args[0] as UDM.Binary).data
        
        return try {
            val input = ByteArrayInputStream(compressedData)
            val output = ByteArrayOutputStream()
            
            GZIPInputStream(input).use { gzipStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (gzipStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
            
            UDM.fromNative(output.toByteArray())
        } catch (e: IOException) {
            throw CompressionException("Failed to gunzip data: ${e.message}", e)
        }
    }
    
    @UTLXFunction(
        description = "Check if data is gzip compressed",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Boolean indicating the result",
        example = "isGzipped(...) => result",
        notes = """Example:
isGzipped(data)""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Check if data is gzip compressed
     * 
     * @param args[0] data - Binary data to check
     * @return Boolean true if gzip format
     * 
     * Example:
     *   isGzipped(data)
     */
    fun isGzipped(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "isGzipped requires 1 argument: data" }
        
        val data = (args[0] as UDM.Binary).data
        
        // Gzip magic number: 0x1f 0x8b
        val isGzip = data.size >= 2 && 
                     data[0] == 0x1f.toByte() && 
                     data[1] == 0x8b.toByte()
        
        return UDM.fromNative(isGzip)
    }
    
    // ============================================================================
    // DEFLATE COMPRESSION (Raw compression without headers)
    // ============================================================================
    
    @UTLXFunction(
        description = "Compress data using Deflate algorithm (raw)",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "deflate(...) => result",
        notes = """Example:
deflate(data)
deflate(data, 9)  // Maximum compression""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Compress data using Deflate algorithm (raw)
     * 
     * @param args[0] data - Binary data or string to compress
     * @param args[1] (optional) level - Compression level 0-9 (default: 6)
     * @return ByteArray of compressed data
     * 
     * Example:
     *   deflate(data)
     *   deflate(data, 9)  // Maximum compression
     */
    fun deflate(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "deflate requires 1-2 arguments: data, [level]" }
        
        val data = when {
            args[0] is UDM.Binary -> (args[0] as UDM.Binary).data
            args[0] is UDM.Scalar && (args[0] as UDM.Scalar).value is String -> ((args[0] as UDM.Scalar).value as String).toByteArray(Charsets.UTF_8)
            else -> throw IllegalArgumentException("deflate requires binary data or string")
        }
        
        val level = if (args.size > 1) {
            (args[1] as UDM.Scalar).value.toString().toIntOrNull() ?: 6.coerceIn(0, 9)
        } else {
            Deflater.DEFAULT_COMPRESSION
        }
        
        return try {
            val deflater = Deflater(level)
            deflater.setInput(data)
            deflater.finish()
            
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                output.write(buffer, 0, count)
            }
            
            deflater.end()
            UDM.fromNative(output.toByteArray())
        } catch (e: Exception) {
            throw CompressionException("Failed to deflate data: ${e.message}", e)
        }
    }
    
    @UTLXFunction(
        description = "Decompress Deflate data",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "inflate(...) => result",
        notes = """Example:
inflate(compressed)""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Decompress Deflate data
     * 
     * @param args[0] compressedData - Deflate compressed binary data
     * @return ByteArray of decompressed data
     * 
     * Example:
     *   inflate(compressed)
     */
    fun inflate(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "inflate requires 1 argument: compressedData" }
        
        val compressedData = (args[0] as UDM.Binary).data
        
        return try {
            val inflater = Inflater()
            inflater.setInput(compressedData)
            
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                output.write(buffer, 0, count)
            }
            
            inflater.end()
            UDM.fromNative(output.toByteArray())
        } catch (e: Exception) {
            throw CompressionException("Failed to inflate data: ${e.message}", e)
        }
    }
    
    // ============================================================================
    // GENERIC COMPRESSION (Algorithm selection)
    // ============================================================================
    
    @UTLXFunction(
        description = "Compress data using specified algorithm",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "compress(...) => result",
        notes = """Example:
compress(data, "gzip")
compress(data, "deflate", 9)""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Compress data using specified algorithm
     * 
     * @param args[0] data - Binary data or string to compress
     * @param args[1] (optional) algorithm - "gzip", "deflate" (default: "gzip")
     * @param args[2] (optional) level - Compression level 0-9 (default: 6)
     * @return ByteArray of compressed data
     * 
     * Example:
     *   compress(data, "gzip")
     *   compress(data, "deflate", 9)
     */
    fun compress(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "compress requires 1-3 arguments: data, [algorithm], [level]" }
        
        val algorithm = if (args.size > 1) {
            args[1].asString().lowercase()
        } else {
            "gzip"
        }
        
        return when (algorithm) {
            "gzip" -> gzip(listOf(args[0]))
            "deflate" -> deflate(args)
            else -> throw IllegalArgumentException("Unsupported compression algorithm: $algorithm")
        }
    }
    
    @UTLXFunction(
        description = "Decompress data using specified algorithm",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "decompress(...) => result",
        notes = """Example:
decompress(compressed)
decompress(compressed, "deflate")""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Decompress data using specified algorithm
     * 
     * @param args[0] compressedData - Compressed binary data
     * @param args[1] (optional) algorithm - "gzip", "deflate" (default: auto-detect)
     * @return ByteArray of decompressed data
     * 
     * Example:
     *   decompress(compressed)
     *   decompress(compressed, "deflate")
     */
    fun decompress(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "decompress requires 1-2 arguments: compressedData, [algorithm]" }
        
        val compressedData = (args[0] as UDM.Binary).data
        
        val algorithm = if (args.size > 1) {
            args[1].asString().lowercase()
        } else {
            // Auto-detect
            detectCompressionAlgorithm(compressedData)
        }
        
        return when (algorithm) {
            "gzip" -> gunzip(listOf(args[0]))
            "deflate" -> inflate(listOf(args[0]))
            else -> throw IllegalArgumentException("Unsupported compression algorithm: $algorithm")
        }
    }
    
    // ============================================================================
    // ZIP ARCHIVE OPERATIONS (Multiple files)
    // ============================================================================
    
    @UTLXFunction(
        description = "Create a zip archive from multiple files",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "zipArchive(...) => result",
        notes = """Example:
zipArchive({
"file1.txt": toBinary("content1"),
"file2.json": toBinary("{...}"),
"dir/file3.xml": toBinary("<xml>...")
})""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Create a zip archive from multiple files
     * 
     * @param args[0] files - Map of filename -> binary data
     * @return ByteArray of zip archive
     * 
     * Example:
     *   zipArchive({
     *     "file1.txt": toBinary("content1"),
     *     "file2.json": toBinary("{...}"),
     *     "dir/file3.xml": toBinary("<xml>...")
     *   })
     */
    fun zipArchive(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "zipArchive requires 1 argument: files (map)" }
        
        val files = (args[0] as UDM.Object).properties
        
        return try {
            val output = ByteArrayOutputStream()
            
            ZipOutputStream(output).use { zipStream ->
                for ((filename, contentUdm) in files) {
                    val content = when (contentUdm) {
                        is UDM.Binary -> contentUdm.data
                        is UDM.Scalar -> (contentUdm.value as? String ?: contentUdm.value.toString()).toByteArray(Charsets.UTF_8)
                        else -> contentUdm.toString().toByteArray(Charsets.UTF_8)
                    }
                    
                    val entry = ZipEntry(filename)
                    zipStream.putNextEntry(entry)
                    zipStream.write(content)
                    zipStream.closeEntry()
                }
            }
            
            UDM.fromNative(output.toByteArray())
        } catch (e: IOException) {
            throw CompressionException("Failed to create zip archive: ${e.message}", e)
        }
    }
    
    @UTLXFunction(
        description = "Extract all files from a zip archive",
        minArgs = 2,
        maxArgs = 2,
        category = "Binary",
        parameters = [
            "array: Input array to process",
        "entryName: Entryname value"
        ],
        returns = "Result of the operation",
        example = "unzipArchive(...) => result",
        notes = """Example:
let files = unzipArchive(zipData)
let content = files["file1.txt"]""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Extract all files from a zip archive
     * 
     * @param args[0] zipData - Binary data of zip archive
     * @return Map of filename -> binary data
     * 
     * Example:
     *   let files = unzipArchive(zipData)
     *   let content = files["file1.txt"]
     */
    fun unzipArchive(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "unzipArchive requires 1 argument: zipData" }
        
        val zipData = (args[0] as UDM.Binary).data
        
        return try {
            val input = ByteArrayInputStream(zipData)
            val files = mutableMapOf<String, ByteArray>()
            
            ZipInputStream(input).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        
                        files[entry.name] = output.toByteArray()
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
            
            UDM.fromNative(files)
        } catch (e: IOException) {
            throw CompressionException("Failed to extract zip archive: ${e.message}", e)
        }
    }
    
    @UTLXFunction(
        description = "Read a single entry from a zip archive",
        minArgs = 2,
        maxArgs = 2,
        category = "Binary",
        parameters = [
            "array: Input array to process",
        "entryName: Entryname value"
        ],
        returns = "Result of the operation",
        example = "readZipEntry(...) => result",
        notes = """Example:
readZipEntry(zipData, "config/app.json")""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Read a single entry from a zip archive
     * 
     * @param args[0] zipData - Binary data of zip archive
     * @param args[1] entryName - Name of entry to read
     * @return ByteArray of entry content, or null if not found
     * 
     * Example:
     *   readZipEntry(zipData, "config/app.json")
     */
    fun readZipEntry(args: List<UDM>): UDM {
        require(args.size >= 2) { "readZipEntry requires 2 arguments: zipData, entryName" }
        
        val zipData = (args[0] as UDM.Binary).data
        val entryName = args[1].asString()
        
        return try {
            val input = ByteArrayInputStream(zipData)
            
            ZipInputStream(input).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (entry.name == entryName && !entry.isDirectory) {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        
                        return UDM.fromNative(output.toByteArray())
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
            
            UDM.Scalar(null)  // Entry not found
        } catch (e: IOException) {
            throw CompressionException("Failed to read zip entry: ${e.message}", e)
        }
    }
    
    @UTLXFunction(
        description = "List all entries in a zip archive",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "listZipEntries(...) => result",
        notes = """Example:
listZipEntries(zipData)
// Returns: ["file1.txt", "dir/file2.json", ...]""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * List all entries in a zip archive
     * 
     * @param args[0] zipData - Binary data of zip archive
     * @return Array of entry names
     * 
     * Example:
     *   listZipEntries(zipData)
     *   // Returns: ["file1.txt", "dir/file2.json", ...]
     */
    fun listZipEntries(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "listZipEntries requires 1 argument: zipData" }
        
        val zipData = (args[0] as UDM.Binary).data
        
        return try {
            val input = ByteArrayInputStream(zipData)
            val entries = mutableListOf<String>()
            
            ZipInputStream(input).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    entries.add(entry.name)
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
            
            UDM.fromNative(entries)
        } catch (e: IOException) {
            throw CompressionException("Failed to list zip entries: ${e.message}", e)
        }
    }
    
    @UTLXFunction(
        description = "Check if data is a zip archive",
        minArgs = 2,
        maxArgs = 2,
        category = "Binary",
        parameters = [
            "array: Input array to process",
        "entryName: Entryname value"
        ],
        returns = "Boolean indicating the result",
        example = "isZipArchive(...) => result",
        notes = """Example:
isZipArchive(data)""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Check if data is a zip archive
     * 
     * @param args[0] data - Binary data to check
     * @return Boolean true if zip format
     * 
     * Example:
     *   isZipArchive(data)
     */
    fun isZipArchive(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "isZipArchive requires 1 argument: data" }
        
        val data = (args[0] as UDM.Binary).data
        
        // Zip magic number: 0x50 0x4B (PK)
        val isZip = data.size >= 4 && 
                    data[0] == 0x50.toByte() && 
                    data[1] == 0x4B.toByte()
        
        return UDM.fromNative(isZip)
    }
    
    // ============================================================================
    // JAR FILE OPERATIONS (JAR is specialized zip)
    // ============================================================================
    
    @UTLXFunction(
        description = "Read a single entry from a JAR file",
        minArgs = 2,
        maxArgs = 2,
        category = "Binary",
        parameters = [
            "array: Input array to process",
        "entryName: Entryname value"
        ],
        returns = "Result of the operation",
        example = "readJarEntry(...) => result",
        notes = """Example:
readJarEntry(jarData, "META-INF/MANIFEST.MF")
readJarEntry(jarData, "com/example/Main.class")""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Read a single entry from a JAR file
     * 
     * @param args[0] jarData - Binary data of JAR file
     * @param args[1] entryName - Name of entry to read
     * @return ByteArray of entry content, or null if not found
     * 
     * Example:
     *   readJarEntry(jarData, "META-INF/MANIFEST.MF")
     *   readJarEntry(jarData, "com/example/Main.class")
     */
    fun readJarEntry(args: List<UDM>): UDM {
        require(args.size >= 2) { "readJarEntry requires 2 arguments: jarData, entryName" }
        
        val jarData = (args[0] as UDM.Binary).data
        val entryName = args[1].asString()
        
        return try {
            val input = ByteArrayInputStream(jarData)
            
            JarInputStream(input).use { jarStream ->
                var entry: JarEntry? = jarStream.nextJarEntry
                while (entry != null) {
                    if (entry.name == entryName && !entry.isDirectory) {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (jarStream.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        
                        return UDM.fromNative(output.toByteArray())
                    }
                    jarStream.closeEntry()
                    entry = jarStream.nextJarEntry
                }
            }
            
            UDM.Scalar(null)  // Entry not found
        } catch (e: IOException) {
            throw CompressionException("Failed to read JAR entry: ${e.message}", e)
        }
    }
    
    @UTLXFunction(
        description = "List all entries in a JAR file",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "listJarEntries(...) => result",
        notes = """Example:
listJarEntries(jarData)""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * List all entries in a JAR file
     * 
     * @param args[0] jarData - Binary data of JAR file
     * @return Array of entry names
     * 
     * Example:
     *   listJarEntries(jarData)
     */
    fun listJarEntries(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "listJarEntries requires 1 argument: jarData" }
        
        val jarData = (args[0] as UDM.Binary).data
        
        return try {
            val input = ByteArrayInputStream(jarData)
            val entries = mutableListOf<String>()
            
            JarInputStream(input).use { jarStream ->
                var entry: JarEntry? = jarStream.nextJarEntry
                while (entry != null) {
                    entries.add(entry.name)
                    jarStream.closeEntry()
                    entry = jarStream.nextJarEntry
                }
            }
            
            UDM.fromNative(entries)
        } catch (e: IOException) {
            throw CompressionException("Failed to list JAR entries: ${e.message}", e)
        }
    }
    
    @UTLXFunction(
        description = "Check if data is a JAR file",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Boolean indicating the result",
        example = "isJarFile(...) => result",
        notes = """Example:
isJarFile(data)""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Check if data is a JAR file
     * 
     * @param args[0] data - Binary data to check
     * @return Boolean true if JAR format
     * 
     * Example:
     *   isJarFile(data)
     */
    fun isJarFile(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "isJarFile requires 1 argument: data" }
        
        val data = (args[0] as UDM.Binary).data
        
        // JAR is a zip file, check if it has META-INF/MANIFEST.MF
        return try {
            val input = ByteArrayInputStream(data)
            var hasManifest = false
            
            JarInputStream(input).use { jarStream ->
                val manifest = jarStream.manifest
                hasManifest = (manifest != null)
            }
            
            UDM.fromNative(hasManifest)
        } catch (e: IOException) {
            UDM.fromNative(false)
        }
    }
    
    @UTLXFunction(
        description = "Read JAR manifest",
        minArgs = 1,
        maxArgs = 1,
        category = "Binary",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "readJarManifest(...) => result",
        notes = """Example:
let manifest = readJarManifest(jarData)
let mainClass = manifest["Main-Class"]""",
        tags = ["binary"],
        since = "1.0"
    )
    /**
     * Read JAR manifest
     * 
     * @param args[0] jarData - Binary data of JAR file
     * @return Map of manifest attributes, or null if no manifest
     * 
     * Example:
     *   let manifest = readJarManifest(jarData)
     *   let mainClass = manifest["Main-Class"]
     */
    fun readJarManifest(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "readJarManifest requires 1 argument: jarData" }
        
        val jarData = (args[0] as UDM.Binary).data
        
        return try {
            val input = ByteArrayInputStream(jarData)
            
            JarInputStream(input).use { jarStream ->
                val manifest = jarStream.manifest
                if (manifest != null) {
                    val attributes = mutableMapOf<String, String>()
                    for ((key, value) in manifest.mainAttributes) {
                        attributes[key.toString()] = value.toString()
                    }
                    UDM.fromNative(attributes)
                } else {
                    UDM.Scalar(null)
                }
            }
        } catch (e: IOException) {
            throw CompressionException("Failed to read JAR manifest: ${e.message}", e)
        }
    }
    
    // ============================================================================
    // HELPER FUNCTIONS
    // ============================================================================
    
    /**
     * Auto-detect compression algorithm from magic bytes
     */
    private fun detectCompressionAlgorithm(data: ByteArray): String {
        return when {
            data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte() -> "gzip"
            else -> "deflate"  // Default fallback
        }
    }
}

/**
 * Exception thrown for compression/decompression errors
 */
class CompressionException(message: String, cause: Throwable? = null) : 
    RuntimeException(message, cause)

// ============================================================================
// INTEGRATION INTO Functions.kt
// 
// Add to stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt:
// ============================================================================

/*

import org.apache.utlx.stdlib.binary.CompressionFunctions

// In registerAllFunctions():
private fun registerAllFunctions() {
    // ... existing registrations ...
    registerCompressionFunctions()
}

// Add new registration method:
private fun registerCompressionFunctions() {
    // Gzip compression
    register("gzip", CompressionFunctions::gzip)
    register("gunzip", CompressionFunctions::gunzip)
    register("isGzipped", CompressionFunctions::isGzipped)
    
    // Deflate compression
    register("deflate", CompressionFunctions::deflate)
    register("inflate", CompressionFunctions::inflate)
    
    // Generic compression
    register("compress", CompressionFunctions::compress)
    register("decompress", CompressionFunctions::decompress)
    
    // Zip archive operations
    register("zipArchive", CompressionFunctions::zipArchive)
    register("unzipArchive", CompressionFunctions::unzipArchive)
    register("readZipEntry", CompressionFunctions::readZipEntry)
    register("listZipEntries", CompressionFunctions::listZipEntries)
    register("isZipArchive", CompressionFunctions::isZipArchive)
    
    // JAR file operations
    register("readJarEntry", CompressionFunctions::readJarEntry)
    register("listJarEntries", CompressionFunctions::listJarEntries)
    register("isJarFile", CompressionFunctions::isJarFile)
    register("readJarManifest", CompressionFunctions::readJarManifest)
}

// Update function count comment:
/**
 * UTL-X Standard Library Function Registry
 * 
 * ENTERPRISE EDITION: 242+ functions
 * - 224 existing functions
 * - 18 new compression functions
 */

*/
