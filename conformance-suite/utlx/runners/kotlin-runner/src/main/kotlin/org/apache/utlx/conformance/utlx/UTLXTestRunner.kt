package org.apache.utlx.conformance.utlx

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * UTLX Conformance Suite Test Runner (Kotlin version)
 *
 * Reads same YAML test format as Python runner but runs natively in Kotlin/JVM.
 * No Python dependency required!
 */
class UTLXTestRunner(
    private val utlxCli: String,
    private val testsPath: String,
    private val checkPerformance: Boolean = false  // Disabled by default to match Python runner
) {
    private val logger = LoggerFactory.getLogger(UTLXTestRunner::class.java)
    private val yamlMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val results = mutableListOf<TestResult>()

    /**
     * Run all tests in the specified path
     */
    fun runTests(category: String? = null, testName: String? = null): TestReport {
        logger.info("Starting UTLX Conformance Suite (Kotlin Runner)")
        logger.info("UTL-X CLI: $utlxCli")
        logger.info("Tests: $testsPath")
        if (checkPerformance) {
            logger.info("Performance checking: ENABLED")
        }

        // Find test files
        val testFiles = findTestFiles(category, testName)
        logger.info("Found ${testFiles.size} test file(s)")

        // Create executor
        val executor = UTLXTestExecutor(utlxCli, checkPerformance)

        // Run each test
        for (testFile in testFiles) {
            runTestFile(testFile, executor)
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

        val files = mutableListOf<File>()
        basePath.walkTopDown().forEach { file ->
            if (file.isFile && (file.extension == "yaml" || file.extension == "yml")) {
                // Filter by test name if specified
                if (testName == null || file.nameWithoutExtension == testName) {
                    files.add(file)
                }
            }
        }

        logger.debug("Found ${files.size} test files")
        return files.sortedBy { it.path }
    }

    private fun runTestFile(testFile: File, executor: UTLXTestExecutor) {
        logger.debug("Loading test: ${testFile.path}")

        try {
            // Parse test file
            var testCase = yamlMapper.readValue(testFile, UTLXTestCase::class.java)

            // Use filename as name if not provided (like Python runner)
            val testName = testCase.name ?: testFile.nameWithoutExtension
            testCase = testCase.copy(name = testName)

            // Execute test
            val result = executor.execute(testCase)
            results.add(result)

            when (result) {
                is TestResult.Success -> logger.info("✓ ${testCase.name}")
                is TestResult.Failure -> logger.error("✗ ${testCase.name}: ${result.reason}")
                is TestResult.Skipped -> logger.warn("⊘ ${testCase.name}: ${result.reason}")
            }

            // Run variants if present
            testCase.variants.forEach { variant ->
                runVariant(testCase, variant, executor)
            }

        } catch (e: Exception) {
            // Gracefully handle YAML parsing errors (like Python runner)
            logger.error("Error loading ${testFile.path}: ${e.message}")
            // Don't add to results - skip malformed files like Python does
        }
    }

    private fun runVariant(baseTest: UTLXTestCase, variant: TestVariant, executor: UTLXTestExecutor) {
        val variantName = "${baseTest.name}_${variant.name}"
        logger.info("Running variant: $variantName")

        try {
            // Merge variant with base test
            // Important: If variant has expected output, clear errorExpected (mutually exclusive)
            // If variant has errorExpected, clear expected (mutually exclusive)
            val testCase = baseTest.copy(
                name = variantName,
                input = variant.input ?: baseTest.input,
                transformation = variant.transformation ?: baseTest.transformation,
                expected = if (variant.expected != null) variant.expected else if (variant.errorExpected != null) null else baseTest.expected,
                errorExpected = if (variant.errorExpected != null) variant.errorExpected else if (variant.expected != null) null else baseTest.errorExpected
            )

            val result = executor.execute(testCase)
            results.add(result)

            when (result) {
                is TestResult.Success -> logger.info("✓ $variantName")
                is TestResult.Failure -> logger.error("✗ $variantName: ${result.reason}")
                is TestResult.Skipped -> logger.warn("⊘ $variantName: ${result.reason}")
            }

        } catch (e: Exception) {
            logger.error("Error running variant: $variantName", e)
            results.add(TestResult.Failure(variantName, e.message ?: "Unknown error"))
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
        println("UTLX Conformance Suite Results (Kotlin Runner)")
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
            results.filterIsInstance<TestResult.Failure>().take(20).forEach { result ->
                println("  ✗ ${result.testName}: ${result.reason.take(100)}")
            }
            if (failed > 20) {
                println("  ... and ${failed - 20} more")
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

    // Parse arguments - support --check-performance flag
    var checkPerformance = false
    val filteredArgs = mutableListOf<String>()

    for (arg in args) {
        if (arg == "--check-performance") {
            checkPerformance = true
        } else {
            filteredArgs.add(arg)
        }
    }

    val utlxCli = System.getenv("UTLX_CLI") ?: filteredArgs.getOrNull(0) ?: "utlx"
    val testsPath = filteredArgs.getOrNull(1) ?: "../../tests"
    val category = filteredArgs.getOrNull(2)
    val testName = filteredArgs.getOrNull(3)

    logger.info("UTLX Conformance Suite Runner (Kotlin)")
    logger.info("UTL-X CLI: $utlxCli")
    logger.info("Tests: $testsPath")

    // Check CLI exists
    val cliFile = File(utlxCli)
    if (!cliFile.exists() || !cliFile.canExecute()) {
        System.err.println("Error: UTL-X CLI not found or not executable: $utlxCli")
        System.err.println("Set UTLX_CLI environment variable or provide as first argument")
        exitProcess(1)
    }

    // Run tests
    val runner = UTLXTestRunner(utlxCli, testsPath, checkPerformance)
    val report = runner.runTests(category, testName)

    // Print report
    report.print()

    // Exit with appropriate code
    exitProcess(if (report.failed > 0) 1 else 0)
}
