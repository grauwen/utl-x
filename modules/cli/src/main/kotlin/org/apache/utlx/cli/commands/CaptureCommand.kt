// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/CaptureCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.cli.CommandResult
import org.apache.utlx.cli.capture.CaptureConfig
import java.io.File

/**
 * Capture command - manage test capture settings
 *
 * Usage:
 *   utlx capture status          # Show current capture configuration
 *   utlx capture enable          # Enable test capture globally
 *   utlx capture disable         # Disable test capture globally
 */
object CaptureCommand {

    fun execute(args: Array<String>): CommandResult {
        if (args.isEmpty()) {
            printUsage()
            return CommandResult.Failure("Subcommand required", 1)
        }

        val subcommand = args[0].lowercase()

        when (subcommand) {
            "status" -> showStatus()
            "enable" -> enableCapture()
            "disable" -> disableCapture()
            "-h", "--help", "help" -> printUsage()
            else -> {
                System.err.println("Unknown capture subcommand: $subcommand")
                printUsage()
                return CommandResult.Failure("Unknown subcommand: $subcommand", 1)
            }
        }

        return CommandResult.Success
    }

    private fun showStatus() {
        val config = CaptureConfig.load()
        val configFile = getConfigFile()

        println("UTL-X Test Capture Status")
        println("=" .repeat(50))
        println()
        println("Enabled: ${if (config.enabled) "✓ YES" else "✗ NO"}")
        println("Capture Location: ${config.captureLocation}")
        println("Deduplicate: ${config.deduplicate}")
        println("Capture Failures: ${config.captureFailures}")
        println("Max Tests Per Function: ${config.maxTestsPerFunction}")
        println("Verbose: ${config.verbose}")
        println()
        println("Ignore Patterns:")
        config.ignorePatterns.forEach { pattern ->
            println("  - $pattern")
        }
        println()
        println("Configuration File: ${configFile.absolutePath}")
        println("File Exists: ${if (configFile.exists()) "yes" else "no"}")

        // Check for environment variable override
        val envOverride = System.getenv("UTLX_CAPTURE_TESTS")
        if (envOverride != null) {
            println()
            println("⚠ Environment Override: UTLX_CAPTURE_TESTS=$envOverride")
            println("  (This takes precedence over config file)")
        }
    }

    private fun enableCapture() {
        val configFile = getConfigFile()

        if (!configFile.exists()) {
            // Create default config file with capture enabled
            createDefaultConfig(configFile, enabled = true)
            println("✓ Test capture enabled")
            println("  Created config file: ${configFile.absolutePath}")
        } else {
            // Update existing config file
            updateConfigEnabled(configFile, enabled = true)
            println("✓ Test capture enabled")
        }

        // Verify change
        val config = CaptureConfig.load()
        if (!config.enabled) {
            println()
            println("⚠ Warning: Capture is still disabled")
            val envOverride = System.getenv("UTLX_CAPTURE_TESTS")
            if (envOverride != null) {
                println("  Environment variable UTLX_CAPTURE_TESTS=$envOverride is overriding config")
                println("  To enable, run: export UTLX_CAPTURE_TESTS=true")
            }
        }
    }

    private fun disableCapture() {
        val configFile = getConfigFile()

        if (!configFile.exists()) {
            // Create default config file with capture disabled
            createDefaultConfig(configFile, enabled = false)
            println("✓ Test capture disabled")
            println("  Created config file: ${configFile.absolutePath}")
        } else {
            // Update existing config file
            updateConfigEnabled(configFile, enabled = false)
            println("✓ Test capture disabled")
        }

        // Verify change
        val config = CaptureConfig.load()
        if (config.enabled) {
            println()
            println("⚠ Warning: Capture is still enabled")
            val envOverride = System.getenv("UTLX_CAPTURE_TESTS")
            if (envOverride != null) {
                println("  Environment variable UTLX_CAPTURE_TESTS=$envOverride is overriding config")
                println("  To disable, run: export UTLX_CAPTURE_TESTS=false")
            }
        }
    }

    private fun getConfigFile(): File {
        val homeDir = File(System.getProperty("user.home"))
        val utlxDir = File(homeDir, ".utlx")
        return File(utlxDir, "capture-config.yaml")
    }

    private fun createDefaultConfig(configFile: File, enabled: Boolean) {
        // Ensure parent directory exists
        configFile.parentFile.mkdirs()

        // Write default configuration
        val content = """
            # UTL-X Test Capture Configuration
            # This file enables automatic test capture for all transformations

            # Enable test capture (set to false to disable)
            enabled: $enabled

            # Where to save captured tests
            capture_location: "conformance-suite/utlx/tests/auto-captured/"

            # Prevent duplicate test captures
            deduplicate: true

            # Capture failing transformations as known issues
            capture_failures: true

            # Maximum auto-tests per function category (prevents bloat)
            max_tests_per_function: 50

            # Patterns to ignore (tests in these locations won't be captured)
            ignore_patterns:
              - "**/tmp/**"
              - "**/test_*.utlx"
              - "**/debug_*.utlx"

            # Show capture info during transformations
            verbose: false
        """.trimIndent()

        configFile.writeText(content)
    }

    private fun updateConfigEnabled(configFile: File, enabled: Boolean) {
        val lines = configFile.readLines().toMutableList()
        var updated = false

        for (i in lines.indices) {
            val line = lines[i]
            if (line.trim().startsWith("enabled:")) {
                lines[i] = "enabled: $enabled"
                updated = true
                break
            }
        }

        if (!updated) {
            // If 'enabled' line not found, add it after the first comment block
            var insertIndex = 0
            for (i in lines.indices) {
                if (!lines[i].trim().startsWith("#") && lines[i].trim().isNotEmpty()) {
                    insertIndex = i
                    break
                }
            }
            lines.add(insertIndex, "enabled: $enabled")
        }

        configFile.writeText(lines.joinToString("\n"))
    }

    private fun printUsage() {
        println("""
            |Manage test capture settings
            |
            |Usage:
            |  utlx capture <subcommand> [options]
            |
            |Subcommands:
            |  status      Show current capture configuration
            |  enable      Enable test capture globally
            |  disable     Disable test capture globally
            |  help        Show this help message
            |
            |Examples:
            |  # Check if capture is enabled
            |  utlx capture status
            |
            |  # Enable test capture for all transformations
            |  utlx capture enable
            |
            |  # Disable test capture
            |  utlx capture disable
            |
            |Configuration:
            |  Config file: ~/.utlx/capture-config.yaml
            |  Environment: UTLX_CAPTURE_TESTS (overrides config file)
            |
            |Per-transformation override:
            |  Use --capture or --no-capture flags with transform command
            |  Example: utlx transform script.utlx input.json --no-capture
        """.trimMargin())
    }
}
