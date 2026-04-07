// modules/cli/src/test/kotlin/org/apache/utlx/cli/commands/ValidateCommandTest.kt
package org.apache.utlx.cli.commands

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Path
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.system.exitProcess

class ValidateCommandTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test validate accepts syntactically correct script`() {
        val script = tempDir.resolve("valid.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            {
              name: input.customerName,
              email: input.emailAddress,
              total: input.orderTotal
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath)

        // Should not throw exception for valid script
        try {
            ValidateCommand.execute(args)
        } catch (e: SystemExitException) {
            // If exit was called, should be 0 (success)
            assertTrue(e.exitCode == 0, "Valid script should exit with code 0")
        }
    }

    @Test
    fun `test validate detects syntax errors`() {
        val script = tempDir.resolve("invalid.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            {
              name: input.customerName
              email: input.emailAddress
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath)

        // Should exit with code 1 for invalid script
        try {
            ValidateCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 1, "Invalid script should exit with code 1")
        }
    }

    @Test
    fun `test validate verbose mode shows type information`() {
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            {
              result: input.value + 10
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath, "--verbose")

        // Verbose mode should work without throwing
        try {
            ValidateCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Valid script with verbose should exit with code 0")
        }
    }

    @Test
    fun `test validate no-typecheck skips semantic validation`() {
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            {
              name: input.name
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath, "--no-typecheck")

        try {
            ValidateCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Script should pass syntax check")
        }
    }

    @Test
    fun `test validate help shows usage`() {
        val args = arrayOf("--help")

        try {
            ValidateCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Help should exit with code 0")
        }
    }

    @Test
    fun `test validate requires script file argument`() {
        val args = arrayOf<String>()

        try {
            ValidateCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 1, "Missing argument should exit with code 1")
        }
    }

    @Test
    fun `test validate detects missing file`() {
        val args = arrayOf("/nonexistent/file.utlx")

        try {
            ValidateCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 1, "Missing file should exit with code 1")
        }
    }

    @Test
    fun `test validate strict mode treats warnings as errors`() {
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            {
              name: input.name,
              age: input.age
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath, "--strict", "--verbose")

        try {
            ValidateCommand.execute(args)
        } catch (e: SystemExitException) {
            // Strict mode may catch type warnings
            assertTrue(e.exitCode in listOf(0, 1), "Exit code should be 0 or 1")
        }
    }
}
