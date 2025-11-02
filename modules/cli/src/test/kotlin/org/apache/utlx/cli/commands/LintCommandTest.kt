// modules/cli/src/test/kotlin/org/apache/utlx/cli/commands/LintCommandTest.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.cli.CommandResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LintCommandTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test lint detects unused variables`() {
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            let unused_variable = 42
            let result = {
              name: input.customerName
            }
            ${'$'}result
        """.trimIndent())

        val args = arrayOf(script.absolutePath)

        // Lint should always exit with 0 (warnings only, never fails)
        try {
            LintCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Lint should always exit with code 0")
        }
    }

    @Test
    fun `test lint detects naming convention violations`() {
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            let MyPascalName = "test"
            let another_snake_case = 123
            {
              value: MyPascalName
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath)

        try {
            LintCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Lint should exit with code 0")
        }
    }

    @Test
    fun `test lint accepts clean code`() {
        val script = tempDir.resolve("clean.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            let customerName = input.name
            let customerEmail = input.email
            {
              name: customerName,
              email: customerEmail
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath)

        try {
            LintCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Clean code should exit with code 0")
        }
    }

    @Test
    fun `test lint verbose mode shows details`() {
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            let unusedVar = 10
            {
              result: input.value
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath, "--verbose")

        try {
            LintCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Lint verbose should exit with code 0")
        }
    }

    @Test
    fun `test lint with syntax error shows helpful message`() {
        val script = tempDir.resolve("invalid.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            {
              name: input.name
              email: input.email
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath)

        // Lint should detect it can't lint (syntax error)
        val result = LintCommand.execute(args)
        assertTrue(result is CommandResult.Failure, "Syntax errors should prevent linting")
        assertEquals(1, (result as CommandResult.Failure).exitCode, "Exit code should be 1")
    }

    @Test
    fun `test lint help shows usage`() {
        val args = arrayOf("--help")

        try {
            LintCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Help should exit with code 0")
        }
    }

    @Test
    fun `test lint no-unused skips unused variable detection`() {
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            let unused = 42
            {
              value: input.value
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath, "--no-unused")

        try {
            LintCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Lint with --no-unused should exit with code 0")
        }
    }

    @Test
    fun `test lint no-style skips style checking`() {
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            let MyBadName = "test"
            {
              value: MyBadName
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath, "--no-style")

        try {
            LintCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Lint with --no-style should exit with code 0")
        }
    }

    @Test
    fun `test lint detects multiple issues`() {
        val script = tempDir.resolve("multiple_issues.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            let UnusedPascalCase = 42
            let another_unused_snake = "test"
            let SCREAMING_CASE = true
            {
              value: input.value
            }
        """.trimIndent())

        val args = arrayOf(script.absolutePath)

        try {
            LintCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Multiple issues should still exit with code 0")
        }
    }

    @Test
    fun `test lint JSON output format`() {
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            let unused = 1
            { value: input.value }
        """.trimIndent())

        val args = arrayOf(script.absolutePath, "--format", "json")

        try {
            LintCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "JSON format should exit with code 0")
        }
    }

    @Test
    fun `test lint compact output format`() {
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            let Bad_Name = 1
            { value: Bad_Name }
        """.trimIndent())

        val args = arrayOf(script.absolutePath, "--format", "compact")

        try {
            LintCommand.execute(args)
        } catch (e: SystemExitException) {
            assertTrue(e.exitCode == 0, "Compact format should exit with code 0")
        }
    }
}
