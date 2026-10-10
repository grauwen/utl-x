package com.glomidco.utlx.daemon.rest

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * IF19: how utlxd finds its workspace root. `resolve()` checks --workspace (CLI, via
 * `cliOverride`) → UTLX_WORKSPACE (env) → -Dutlx.workspace (sysprop) → null. The env var can't be
 * set in-process, so these drive the CLI/sysprop paths and assume the env override isn't set
 * (asserted via assumeTrue, so a CI that pins UTLX_WORKSPACE skips rather than fails).
 */
class BundleWorkspaceTest {

    @AfterEach
    fun reset() {
        BundleWorkspace.cliOverride = null
        System.clearProperty("utlx.workspace")
    }

    @Test
    fun `resolves the sysprop path when it points at a directory`(@TempDir dir: File) {
        assumeTrue(System.getenv("UTLX_WORKSPACE") == null)
        System.setProperty("utlx.workspace", dir.absolutePath)
        try {
            assertEquals(dir.canonicalFile, BundleWorkspace.resolve()?.canonicalFile)
        } finally {
            System.clearProperty("utlx.workspace")
        }
    }

    @Test
    fun `returns null when the configured path is not a directory`(@TempDir dir: File) {
        assumeTrue(System.getenv("UTLX_WORKSPACE") == null)
        val notADir = File(dir, "workspace.txt").apply { writeText("x") }
        System.setProperty("utlx.workspace", notADir.absolutePath)
        try {
            assertNull(BundleWorkspace.resolve())
        } finally {
            System.clearProperty("utlx.workspace")
        }
    }

    @Test
    fun `returns null when nothing is configured`() {
        assumeTrue(System.getenv("UTLX_WORKSPACE") == null)
        System.clearProperty("utlx.workspace")
        assertNull(BundleWorkspace.resolve())
    }

    @Test
    fun `cliOverride takes precedence over the sysprop`(@TempDir cli: File, @TempDir sysprop: File) {
        assumeTrue(System.getenv("UTLX_WORKSPACE") == null)
        System.setProperty("utlx.workspace", sysprop.absolutePath)
        BundleWorkspace.cliOverride = cli.absolutePath
        // --workspace (cliOverride) wins over -Dutlx.workspace
        assertEquals(cli.canonicalFile, BundleWorkspace.resolve()?.canonicalFile)
    }

    @Test
    fun `falls back to the sysprop once the cliOverride is cleared`(@TempDir cli: File, @TempDir sysprop: File) {
        assumeTrue(System.getenv("UTLX_WORKSPACE") == null)
        System.setProperty("utlx.workspace", sysprop.absolutePath)
        BundleWorkspace.cliOverride = cli.absolutePath
        assertEquals(cli.canonicalFile, BundleWorkspace.resolve()?.canonicalFile)
        BundleWorkspace.cliOverride = null
        assertEquals(sysprop.canonicalFile, BundleWorkspace.resolve()?.canonicalFile)
    }

    @Test
    fun `returns null when the cliOverride path is not a directory`(@TempDir dir: File) {
        assumeTrue(System.getenv("UTLX_WORKSPACE") == null)
        val notADir = File(dir, "workspace.txt").apply { writeText("x") }
        BundleWorkspace.cliOverride = notADir.absolutePath
        assertNull(BundleWorkspace.resolve())
    }
}
