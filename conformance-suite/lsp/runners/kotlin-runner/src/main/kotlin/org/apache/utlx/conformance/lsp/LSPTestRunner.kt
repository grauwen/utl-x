package org.apache.utlx.conformance.lsp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * LSP Conformance Suite Test Runner
 */
class LSPTestRunner(
    private val daemonPath: String,
    private val testsPath: String
) {
    private val logger = LoggerFactory.getLogger(LSPTestRunner::class.java)
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    private val results = mutableListOf<TestResult>()

    /**
     * Run all tests in the specified path
     */
    fun runTests(category: String? = null, testName: String? = null): TestReport {
        logger.info("Starting LSP Conformance Suite")
        logger.info("Daemon: $daemonPath")
        logger.info("Tests: $testsPath")

        // Find test files
        val testFiles = findTestFiles(category, testName)
        logger.info("Found ${testFiles.size} test file(s)")

        // Run each test
        for (testFile in testFiles) {
            runTestFile(testFile)
        }

        // Generate report
        return generateReport()
    }

    private fun findTestFiles(category: String?, testName: String?): List<File> {
        val basePath = if (category != null) {
            File(testsPath, category)
        } else {
            File(testsPath)
        }

        if (!basePath.exists()) {
            logger.warn("Test path does not exist: $basePath")
            return emptyList()
        }

        logger.debug("Searching for test files in: $basePath")

        logger.debug("Filtering by test name: '$testName'")

        val files = mutableListOf<File>()
        basePath.walkTopDown().forEach { file ->
            logger.debug("Checking file: ${file.name} (isFile: ${file.isFile}, ext: ${file.extension})")
            if (file.isFile && (file.extension == "yaml" || file.extension == "yml")) {
                logger.debug("File matched extension filter: ${file.name}, nameWithoutExtension=${file.nameWithoutExtension}, testName='$testName', match=${testName == null || file.nameWithoutExtension == testName}")
                // Filter by test name if specified
                if (testName == null || file.nameWithoutExtension == testName) {
                    logger.debug("Adding test file: ${file.path}")
                    files.add(file)
                }
            }
        }

        logger.debug("Found ${files.size} test files")
        return files.sortedBy { it.path }
    }

    private fun runTestFile(testFile: File) {
        logger.info("Loading test: ${testFile.path}")

        try {
            // Parse test file
            val testCase = yamlMapper.readValue(testFile, TestCase::class.java)

            // Start daemon process for this test
            val process = startDaemon()
            if (process == null) {
                results.add(TestResult.Failure(testCase.name, "Failed to start daemon"))
                return
            }

            try {
                // Create client and executor
                val client = JsonRpcClient(process)
                client.start()

                val executor = TestExecutor(client)

                // Execute test
                val result = executor.execute(testCase)
                results.add(result)

                when (result) {
                    is TestResult.Success -> logger.info("✓ ${testCase.name}")
                    is TestResult.Failure -> logger.error("✗ ${testCase.name}: ${result.reason}")
                    is TestResult.Skipped -> logger.warn("⊘ ${testCase.name}: ${result.reason}")
                }

                // Cleanup
                client.stop()
            } finally {
                // Stop daemon
                stopDaemon(process)
            }
        } catch (e: Exception) {
            logger.error("Error running test file: ${testFile.path}", e)
            results.add(TestResult.Failure(testFile.nameWithoutExtension, e.message ?: "Unknown error"))
        }
    }

    private fun startDaemon(): Process? {
        return try {
            logger.debug("Starting daemon: $daemonPath")

            val processBuilder = ProcessBuilder(daemonPath, "design", "daemon")
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)

            val process = processBuilder.start()

            // Give daemon time to start
            Thread.sleep(500)

            if (!process.isAlive) {
                logger.error("Daemon process failed to start")
                null
            } else {
                logger.debug("Daemon started (PID: ${process.pid()})")
                process
            }
        } catch (e: Exception) {
            logger.error("Failed to start daemon", e)
            null
        }
    }

    private fun stopDaemon(process: Process) {
        try {
            // Try graceful shutdown first
            process.outputStream.close()

            // Wait for process to exit (with timeout)
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                // Force kill if not stopped
                process.destroyForcibly()
            }

            logger.debug("Daemon stopped")
        } catch (e: Exception) {
            logger.warn("Error stopping daemon", e)
            process.destroyForcibly()
        }
    }

    private fun generateReport(): TestReport {
        val total = results.size
        val passed = results.count { it is TestResult.Success }
        val failed = results.count { it is TestResult.Failure }
        val skipped = results.count { it is TestResult.Skipped }

        return TestReport(
            total = total,
            passed = passed,
            failed = failed,
            skipped = skipped,
            results = results
        )
    }
}

/**
 * Test report
 */
data class TestReport(
    val total: Int,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val results: List<TestResult>
) {
    val passRate: Double
        get() = if (total > 0) (passed.toDouble() / total.toDouble()) * 100.0 else 0.0

    fun print() {
        println()
        println("=" .repeat(60))
        println("LSP Conformance Suite Results")
        println("=" .repeat(60))
        println()
        println("Total:   $total")
        println("Passed:  $passed")
        println("Failed:  $failed")
        println("Skipped: $skipped")
        println("Pass Rate: %.2f%%".format(passRate))
        println()

        if (failed > 0) {
            println("Failed Tests:")
            results.filterIsInstance<TestResult.Failure>().forEach { result ->
                println("  ✗ ${result.testName}: ${result.reason}")
            }
            println()
        }

        println("=" .repeat(60))
    }
}

/**
 * Main entry point
 */
fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")

    // Parse arguments
    val daemonPath = System.getenv("UTLX_DAEMON") ?: args.getOrNull(0) ?: "utlx"
    val testsPath = args.getOrNull(1) ?: "../../tests"
    val category = args.getOrNull(2)
    val testName = args.getOrNull(3)

    logger.info("LSP Conformance Suite Runner")
    logger.info("Daemon: $daemonPath")
    logger.info("Tests: $testsPath")

    // Run tests
    val runner = LSPTestRunner(daemonPath, testsPath)
    val report = runner.runTests(category, testName)

    // Print report
    report.print()

    // Exit with appropriate code
    exitProcess(if (report.failed > 0) 1 else 0)
}
