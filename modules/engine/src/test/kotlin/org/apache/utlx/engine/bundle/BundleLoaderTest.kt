package org.apache.utlx.engine.bundle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BundleLoaderTest {

    private val validUtlxSource = "%utlx 1.0\ninput json\noutput json\n---\ninput\n"

    @Test
    fun `load discovers transformation from bundle directory`(@TempDir tempDir: Path) {
        createTransformation(tempDir, "xml-to-json", "TEMPLATE")

        val loader = BundleLoader()
        val discovered = loader.load(tempDir)

        assertEquals(1, discovered.size)
        assertEquals("xml-to-json", discovered[0].name)
        assertEquals("TEMPLATE", discovered[0].config.strategy)
        assertTrue(discovered[0].source.contains("%utlx"))
    }

    @Test
    fun `load discovers multiple transformations`(@TempDir tempDir: Path) {
        createTransformation(tempDir, "tx-alpha", "TEMPLATE")
        createTransformation(tempDir, "tx-beta", "COPY")

        val loader = BundleLoader()
        val discovered = loader.load(tempDir)

        assertEquals(2, discovered.size)
    }

    @Test
    fun `load skips directory without transform yaml`(@TempDir tempDir: Path) {
        val txDir = tempDir.resolve("transformations/incomplete")
        txDir.toFile().mkdirs()
        txDir.resolve("something.utlx").toFile().writeText(validUtlxSource)
        // No transform.yaml

        val loader = BundleLoader()
        val discovered = loader.load(tempDir)

        assertEquals(0, discovered.size)
    }

    @Test
    fun `load skips directory without utlx source`(@TempDir tempDir: Path) {
        val txDir = tempDir.resolve("transformations/no-source")
        txDir.toFile().mkdirs()
        txDir.resolve("transform.yaml").toFile().writeText("strategy: TEMPLATE\ninputs:\n  - name: input")
        // No .utlx file

        val loader = BundleLoader()
        val discovered = loader.load(tempDir)

        assertEquals(0, discovered.size)
    }

    @Test
    fun `load returns empty for bundle with no transformations dir`(@TempDir tempDir: Path) {
        val loader = BundleLoader()
        val discovered = loader.load(tempDir)
        assertEquals(0, discovered.size)
    }

    @Test
    fun `load fails for nonexistent path`() {
        val loader = BundleLoader()
        assertFailsWith<IllegalArgumentException> {
            loader.load(Path.of("/nonexistent/path"))
        }
    }

    @Test
    fun `load finds utlx file matching directory name`(@TempDir tempDir: Path) {
        val txDir = tempDir.resolve("transformations/my-transform")
        txDir.toFile().mkdirs()
        txDir.resolve("transform.yaml").toFile().writeText("""
            strategy: TEMPLATE
            inputs:
              - name: input
        """.trimIndent())
        txDir.resolve("my-transform.utlx").toFile().writeText(validUtlxSource)

        val loader = BundleLoader()
        val discovered = loader.load(tempDir)

        assertEquals(1, discovered.size)
        assertTrue(discovered[0].source.contains("%utlx"))
    }

    @Test
    fun `load parses input slots from transform yaml`(@TempDir tempDir: Path) {
        val txDir = tempDir.resolve("transformations/multi-input")
        txDir.toFile().mkdirs()
        txDir.resolve("transform.yaml").toFile().writeText("""
            strategy: COPY
            validationPolicy: WARN
            inputs:
              - name: order
                schema: schemas/order.xsd
              - name: inventory
                schema: schemas/inventory.json
            output:
              schema: schemas/output.json
            maxConcurrent: 4
        """.trimIndent())
        txDir.resolve("multi-input.utlx").toFile().writeText(
            "%utlx 1.0\ninput: order json, inventory json\noutput json\n---\n{ order: order, inventory: inventory }\n"
        )

        val loader = BundleLoader()
        val discovered = loader.load(tempDir)

        assertEquals(1, discovered.size)
        val config = discovered[0].config
        assertEquals("COPY", config.strategy)
        assertEquals("WARN", config.validationPolicy)
        assertEquals(2, config.inputs.size)
        assertEquals("order", config.inputs[0].name)
        assertEquals("schemas/order.xsd", config.inputs[0].schema)
        assertEquals(4, config.maxConcurrent)
    }

    private fun createTransformation(bundleDir: Path, name: String, strategy: String) {
        val txDir = bundleDir.resolve("transformations/$name")
        txDir.toFile().mkdirs()

        txDir.resolve("transform.yaml").toFile().writeText("""
            strategy: $strategy
            inputs:
              - name: input
            maxConcurrent: 1
        """.trimIndent())

        txDir.resolve("$name.utlx").toFile().writeText(validUtlxSource)
    }
}
