// modules/cli/src/main/kotlin/org/apache/utlx/cli/CommandResult.kt
package org.apache.utlx.cli

/**
 * Result of executing a CLI command.
 *
 * This sealed class allows commands to return their execution status
 * without directly calling exitProcess(), making commands testable and composable.
 *
 * Commands return either Success or Failure, and only Main.kt controls
 * process termination based on these results.
 */
sealed class CommandResult {
    /**
     * Command executed successfully
     */
    object Success : CommandResult()

    /**
     * Command failed with an error
     *
     * @param message Error message to display to user
     * @param exitCode Process exit code (default: 1 for general errors)
     */
    data class Failure(
        val message: String,
        val exitCode: Int = 1
    ) : CommandResult()
}
