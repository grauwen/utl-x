package org.apache.utlx.stdlib.io

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.core.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction
import java.io.File

/**
 * File I/O Functions for UTL-X
 *
 * Provides file reading and writing capabilities for transformations.
 */
object IOFunctions {

    @UTLXFunction(
        category = "I/O",
        description = "Read file contents as a string",
        parameters = ["path: File path (string)"],
        returns = "File contents as string",
        example = """readFile("/path/to/data.xml")""",
        performance = "I/O bound - depends on file size",
        threadSafety = "Thread-safe"
    )
    fun readFile(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("readFile() requires a file path argument")
        }

        val path = when (val arg = args[0]) {
            is UDM.Scalar -> arg.value?.toString()
            else -> null
        } ?: throw FunctionArgumentException("readFile() requires a string path argument")

        return try {
            val file = File(path)
            if (!file.exists()) {
                throw FunctionArgumentException("readFile(): File not found: $path")
            }
            if (!file.canRead()) {
                throw FunctionArgumentException("readFile(): Cannot read file: $path")
            }

            val content = file.readText()
            UDM.Scalar.string(content)
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            throw FunctionArgumentException("readFile(): Error reading '$path': ${e.message}")
        }
    }

    @UTLXFunction(
        category = "I/O",
        description = "Check if a file exists",
        parameters = ["path: File path (string)"],
        returns = "Boolean - true if file exists",
        example = """fileExists("/path/to/file.xml")""",
        performance = "Fast",
        threadSafety = "Thread-safe"
    )
    fun fileExists(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("fileExists() requires a file path argument")
        }

        val path = when (val arg = args[0]) {
            is UDM.Scalar -> arg.value?.toString()
            else -> null
        } ?: throw FunctionArgumentException("fileExists() requires a string path argument")

        val exists = File(path).exists()
        return UDM.Scalar.boolean(exists)
    }

    @UTLXFunction(
        category = "I/O",
        description = "Get file size in bytes",
        parameters = ["path: File path (string)"],
        returns = "Number - file size in bytes",
        example = """fileSize("/path/to/data.xml")""",
        performance = "Fast",
        threadSafety = "Thread-safe"
    )
    fun fileSize(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("fileSize() requires a file path argument")
        }

        val path = when (val arg = args[0]) {
            is UDM.Scalar -> arg.value?.toString()
            else -> null
        } ?: throw FunctionArgumentException("fileSize() requires a string path argument")

        val file = File(path)
        if (!file.exists()) {
            throw FunctionArgumentException("fileSize(): File not found: $path")
        }

        return UDM.Scalar.number(file.length().toDouble())
    }
}
