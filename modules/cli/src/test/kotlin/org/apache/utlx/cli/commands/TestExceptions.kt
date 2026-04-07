// modules/cli/src/test/kotlin/org/apache/utlx/cli/commands/TestExceptions.kt
package org.apache.utlx.cli.commands

/**
 * Exception thrown to simulate System.exit() in tests
 * Note: In real tests, you'd use a SecurityManager or dependency injection
 * to avoid actual System.exit() calls
 */
class SystemExitException(val exitCode: Int) : RuntimeException("System.exit($exitCode)")
