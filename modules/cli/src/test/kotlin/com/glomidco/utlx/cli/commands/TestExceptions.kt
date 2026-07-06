// modules/cli/src/test/kotlin/com/glomidco/utlx/cli/commands/TestExceptions.kt
package com.glomidco.utlx.cli.commands

/**
 * Exception thrown to simulate System.exit() in tests
 * Note: In real tests, you'd use a SecurityManager or dependency injection
 * to avoid actual System.exit() calls
 */
class SystemExitException(val exitCode: Int) : RuntimeException("System.exit($exitCode)")
