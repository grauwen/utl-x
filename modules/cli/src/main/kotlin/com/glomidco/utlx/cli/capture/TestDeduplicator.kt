// modules/cli/src/main/kotlin/com/glomidco/utlx/cli/capture/TestDeduplicator.kt
package com.glomidco.utlx.cli.capture

import java.io.File
import java.security.MessageDigest

/**
 * Handles test deduplication to prevent capturing identical tests
 */
object TestDeduplicator {

    /**
     * Generate unique ID for a test based on its content
     */
    fun generateTestId(transformation: String, inputData: String, inputFormat: String): String {
        val content = "$transformation|$inputData|$inputFormat"
        return content.hashToMd5().substring(0, 8)
    }

    /**
     * Check if a test with this ID already exists
     */
    fun testExists(testId: String, captureLocation: String): Boolean {
        val captureDir = File(captureLocation)
        if (!captureDir.exists()) return false

        // Search recursively for any YAML file with this test ID in the name
        return captureDir.walk()
            .filter { it.extension == "yaml" || it.extension == "yml" }
            .any { it.nameWithoutExtension.endsWith(testId) }
    }

    /**
     * Check if a similar test exists (same transformation, different input)
     */
    fun findSimilarTests(transformation: String, captureLocation: String): List<File> {
        // Removed in 1.4.0: `val transformationHash = transformation.hashToMd5().substring(0, 8)`,
        // from the Auto-Test-Capture work (c4bb6be7). It mirrors generateTestId() above, so the
        // intent was presumably to recognise a sibling test by a hash of the transformation alone
        // (that being the "same transformation, different input" case this function is named for).
        // That was never wired up: the match below is a plain substring search of each file's text,
        // which is what actually implements "same transformation". The hash went unread.
        //
        // Note the two are not equivalent -- a hash would match the whole transformation exactly,
        // whereas contains() also matches a transformation embedded in a larger one. Switching to
        // hash comparison would be a behaviour change, not a cleanup, so it was left alone.
        val captureDir = File(captureLocation)
        if (!captureDir.exists()) return emptyList()

        return captureDir.walk()
            .filter { it.extension == "yaml" || it.extension == "yml" }
            .filter { file ->
                // Read file and check if transformation matches
                try {
                    val content = file.readText()
                    content.contains(transformation)
                } catch (e: Exception) {
                    false
                }
            }
            .toList()
    }

    /**
     * Check if we've exceeded the max tests for a category
     */
    fun exceedsLimit(category: String, captureLocation: String, maxTests: Int): Boolean {
        val categoryPath = File(captureLocation, category.replace("/", File.separator))
        if (!categoryPath.exists()) return false

        val testCount = categoryPath.walk()
            .filter { it.extension == "yaml" || it.extension == "yml" }
            .count()

        return testCount >= maxTests
    }

    /**
     * Get test count for a category
     */
    fun getTestCount(category: String, captureLocation: String): Int {
        val categoryPath = File(captureLocation, category.replace("/", File.separator))
        if (!categoryPath.exists()) return 0

        return categoryPath.walk()
            .filter { it.extension == "yaml" || it.extension == "yml" }
            .count()
    }

    private fun String.hashToMd5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
