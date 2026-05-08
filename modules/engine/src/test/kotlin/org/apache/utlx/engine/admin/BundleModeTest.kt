package org.apache.utlx.engine.admin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundleModeTest {

    @Test
    fun `open mode when no data dir`() {
        val info = detectBundleMode(null)
        assertEquals("open", info.mode)
        assertNull(info.bundleVersion)
    }

    @Test
    fun `open mode when data dir has no utlar`(@TempDir dir: Path) {
        val info = detectBundleMode(dir)
        assertEquals("open", info.mode)
    }

    @Test
    fun `open mode when data dir has directory structure`(@TempDir dir: Path) {
        val txDir = dir.resolve("transformations/test")
        Files.createDirectories(txDir)
        Files.writeString(txDir.resolve("test.utlx"), "%utlx 1.0\ninput json\noutput json\n---\n\$input")

        val info = detectBundleMode(dir)
        assertEquals("open", info.mode)
    }

    @Test
    fun `locked mode when bundle utlar exists`(@TempDir dir: Path) {
        createUtlar(dir, null)

        val info = detectBundleMode(dir)
        assertEquals("locked", info.mode)
        assertNotNull(info.utlarPath)
        assertNotNull(info.bundleChecksum)
        assertTrue(info.bundleChecksum!!.startsWith("sha256:"))
    }

    @Test
    fun `locked mode reads manifest version`(@TempDir dir: Path) {
        val manifest = """{"format":"utlar","format_version":"1.0","version":"v3.2.1","created":"2026-05-07T10:00:00Z"}"""
        createUtlar(dir, manifest)

        val info = detectBundleMode(dir)
        assertEquals("locked", info.mode)
        assertEquals("v3.2.1", info.bundleVersion)
        assertEquals("2026-05-07T10:00:00Z", info.bundleCreated)
    }

    @Test
    fun `locked mode with missing manifest still works`(@TempDir dir: Path) {
        // Create a minimal .utlar without manifest.json
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("transformations/test/test.utlx"))
            zos.write("%utlx 1.0\ninput json\noutput json\n---\n\$input".toByteArray())
            zos.closeEntry()
        }
        Files.write(dir.resolve("bundle.utlar"), baos.toByteArray())

        val info = detectBundleMode(dir)
        assertEquals("locked", info.mode)
        assertNull(info.bundleVersion) // no manifest → no version
    }

    @Test
    fun `loadUtlar loads transformations into engine`(@TempDir dir: Path) {
        createUtlar(dir, null)

        val engine = createTestEngine()
        val loaded = loadUtlar(dir.resolve("bundle.utlar"), engine)

        assertEquals(1, loaded)
        assertNotNull(engine.registry.get("test"))
    }

    @Test
    fun `loadUtlar loads transform yaml config`(@TempDir dir: Path) {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write("""{"format":"utlar","version":"v1.0"}""".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("transformations/orders/orders.utlx"))
            zos.write("%utlx 1.0\ninput json\noutput json\n---\n\$input".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("transformations/orders/transform.yaml"))
            zos.write("strategy: COMPILED\nvalidationPolicy: strict".toByteArray())
            zos.closeEntry()
        }
        Files.write(dir.resolve("bundle.utlar"), baos.toByteArray())

        val engine = createTestEngine()
        loadUtlar(dir.resolve("bundle.utlar"), engine)

        val instance = engine.registry.get("orders")
        assertNotNull(instance)
        assertEquals("strict", instance.config.validationPolicy)
    }

    @Test
    fun `loadUtlar loads multiple transformations`(@TempDir dir: Path) {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write("""{"format":"utlar","version":"v2.0"}""".toByteArray())
            zos.closeEntry()

            for (name in listOf("alpha", "beta", "gamma")) {
                zos.putNextEntry(ZipEntry("transformations/$name/$name.utlx"))
                zos.write("%utlx 1.0\ninput json\noutput json\n---\n{name: \"$name\"}".toByteArray())
                zos.closeEntry()
            }
        }
        Files.write(dir.resolve("bundle.utlar"), baos.toByteArray())

        val engine = createTestEngine()
        val loaded = loadUtlar(dir.resolve("bundle.utlar"), engine)

        assertEquals(3, loaded)
        assertNotNull(engine.registry.get("alpha"))
        assertNotNull(engine.registry.get("beta"))
        assertNotNull(engine.registry.get("gamma"))
    }

    // ── Helpers ──

    private fun createUtlar(dir: Path, manifest: String?) {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            if (manifest != null) {
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifest.toByteArray())
                zos.closeEntry()
            } else {
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write("""{"format":"utlar","version":"v1.0.0"}""".toByteArray())
                zos.closeEntry()
            }
            zos.putNextEntry(ZipEntry("transformations/test/test.utlx"))
            zos.write("%utlx 1.0\ninput json\noutput json\n---\n\$input".toByteArray())
            zos.closeEntry()
        }
        Files.write(dir.resolve("bundle.utlar"), baos.toByteArray())
    }

    private fun createTestEngine(): org.apache.utlx.engine.UtlxEngine {
        val engine = org.apache.utlx.engine.UtlxEngine(
            org.apache.utlx.engine.config.EngineConfig(
                engine = org.apache.utlx.engine.config.EngineSettings(
                    name = "test-bundle",
                    monitoring = org.apache.utlx.engine.config.MonitoringConfig(
                        health = org.apache.utlx.engine.config.HealthConfig(port = 0)
                    )
                )
            )
        )
        engine.initializeEmpty()
        return engine
    }
}
