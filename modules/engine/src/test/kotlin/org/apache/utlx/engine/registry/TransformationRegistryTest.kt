package org.apache.utlx.engine.registry

import io.mockk.every
import io.mockk.mockk
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.strategy.ExecutionStrategy
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransformationRegistryTest {

    private fun createInstance(name: String): TransformationInstance {
        val strategy = mockk<ExecutionStrategy>(relaxed = true)
        every { strategy.name } returns "TEMPLATE"
        return TransformationInstance(
            name = name,
            source = "transform $name\nfrom data: json\nto json\ndata",
            strategy = strategy,
            config = TransformConfig()
        )
    }

    @Test
    fun `register and retrieve transformation`() {
        val registry = TransformationRegistry()
        val instance = createInstance("test-tx")

        registry.register("test-tx", instance)

        val retrieved = registry.get("test-tx")
        assertNotNull(retrieved)
        assertEquals("test-tx", retrieved.name)
    }

    @Test
    fun `get returns null for unknown transformation`() {
        val registry = TransformationRegistry()
        assertNull(registry.get("nonexistent"))
    }

    @Test
    fun `list returns all registered transformations`() {
        val registry = TransformationRegistry()
        registry.register("tx1", createInstance("tx1"))
        registry.register("tx2", createInstance("tx2"))

        val list = registry.list()
        assertEquals(2, list.size)
    }

    @Test
    fun `remove transformation`() {
        val registry = TransformationRegistry()
        registry.register("tx1", createInstance("tx1"))

        val removed = registry.remove("tx1")
        assertNotNull(removed)
        assertNull(registry.get("tx1"))
        assertEquals(0, registry.size())
    }

    @Test
    fun `size tracks registrations`() {
        val registry = TransformationRegistry()
        assertEquals(0, registry.size())

        registry.register("tx1", createInstance("tx1"))
        assertEquals(1, registry.size())

        registry.register("tx2", createInstance("tx2"))
        assertEquals(2, registry.size())
    }

    @Test
    fun `register overwrites existing`() {
        val registry = TransformationRegistry()
        registry.register("tx1", createInstance("tx1"))

        val newInstance = createInstance("tx1-v2")
        registry.register("tx1", newInstance)

        assertEquals(1, registry.size())
        assertEquals("tx1-v2", registry.get("tx1")?.name)
    }
}
