package com.glomidco.utlx.bundle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * IF19: file-level Bundle CRUD, incl. the path-traversal guard that a stateful filesystem API
 * lives or dies by.
 */
class BundleStoreTest {

    @Test
    fun `create, read, list and delete a transformation`(@TempDir root: File) {
        val store = BundleStore(root)
        assertTrue(store.listTransformations().isEmpty())

        store.putTransformation(
            "order-ack",
            "%utlx 1.0\ninput json\noutput json\n---\n\$input",
            "strategy: COMPILED\n",
        )

        val list = store.listTransformations()
        assertEquals(1, list.size)
        assertEquals("order-ack", list[0].name)
        assertTrue(list[0].hasSource)
        assertTrue(list[0].hasConfig)

        val tx = store.getTransformation("order-ack")!!
        assertTrue(tx.source!!.contains("%utlx 1.0"))
        assertEquals("strategy: COMPILED\n", tx.config)

        assertTrue(store.deleteTransformation("order-ack"))
        assertTrue(store.listTransformations().isEmpty())
        assertNull(store.getTransformation("order-ack"))
    }

    @Test
    fun `schemas CRUD`(@TempDir root: File) {
        val store = BundleStore(root)
        store.putSchema("order.json", """{"type":"object"}""")
        assertEquals(listOf("order.json"), store.listSchemas().map { it.name })
        assertEquals("""{"type":"object"}""", store.getSchema("order.json"))
        assertTrue(store.deleteSchema("order.json"))
        assertTrue(store.listSchemas().isEmpty())
        assertNull(store.getSchema("order.json"))
    }

    @Test
    fun `schema name must carry an extension`(@TempDir root: File) {
        val store = BundleStore(root)
        assertThrows(IllegalArgumentException::class.java) { store.putSchema("noext", "{}") }
    }

    @Test
    fun `info reflects contents`(@TempDir root: File) {
        val store = BundleStore(root)
        store.putTransformation("a", "src", null)
        store.putSchema("s.json", "{}")
        store.putEngineConfig("threads: 4\n")

        val info = store.info()
        assertEquals(1, info.transformationCount)
        assertEquals(1, info.schemaCount)
        assertTrue(info.hasEngineConfig)
    }

    @Test
    fun `leading-digit transformation names are kept verbatim (ordering)`(@TempDir root: File) {
        // Bundle Format §3 rule 1: leading digits are legal and meaningful; no stripping.
        val store = BundleStore(root)
        store.putTransformation("00-enterprise-order", "src", null)
        assertEquals("00-enterprise-order", store.listTransformations().single().name)
        assertTrue(File(root, "transformations/00-enterprise-order/00-enterprise-order.utlx").isFile)
    }

    @Test
    fun `test-input fixtures are listed on a transformation`(@TempDir root: File) {
        val store = BundleStore(root)
        store.putTransformation("t", "src", null)
        File(root, "transformations/t/test-input-order.json").writeText("{}")
        assertEquals(listOf("test-input-order.json"), store.getTransformation("t")!!.testInputs)
    }

    @Test
    fun `path traversal in a name is rejected on every mutating and reading path`(@TempDir root: File) {
        val store = BundleStore(root)
        assertThrows(IllegalArgumentException::class.java) { store.putTransformation("../evil", "x", null) }
        assertThrows(IllegalArgumentException::class.java) { store.getTransformation("../../etc/passwd") }
        assertThrows(IllegalArgumentException::class.java) { store.deleteTransformation("a/b") }
        assertThrows(IllegalArgumentException::class.java) { store.putSchema("../x.json", "{}") }
        assertThrows(IllegalArgumentException::class.java) { store.getSchema("..") }
    }
}
