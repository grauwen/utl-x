package org.apache.utlx.engine.admin

import org.apache.utlx.engine.config.MessagingEndpoint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DaprIntegrationTest {

    // ── Mode detection ──

    @Test
    fun `mode is http-only when no components dir and no sidecar`() {
        val dapr = DaprIntegration()
        assertEquals("http-only", dapr.mode)
    }

    @Test
    fun `mode is dynamic when components dir is set`(@TempDir dir: Path) {
        val dapr = DaprIntegration(componentsDir = dir)
        assertEquals("dynamic", dapr.mode)
    }

    // ── Sync state tracking ──

    @Test
    fun `initial sync state is no_dapr`() {
        val dapr = DaprIntegration()
        val state = dapr.getSyncState("test")
        assertEquals("no_dapr", state.status)
    }

    @Test
    fun `markDraft sets draft status with pending changes`() {
        val dapr = DaprIntegration()
        dapr.markDraft("test", listOf("messaging.input", "messaging.output"))
        val state = dapr.getSyncState("test")
        assertEquals("draft", state.status)
        assertEquals(listOf("messaging.input", "messaging.output"), state.pendingChanges)
    }

    @Test
    fun `markNoDapr sets no_dapr status`() {
        val dapr = DaprIntegration()
        dapr.markDraft("test", listOf("messaging.input"))
        dapr.markNoDapr("test")
        assertEquals("no_dapr", dapr.getSyncState("test").status)
    }

    @Test
    fun `removeSyncState removes tracking`() {
        val dapr = DaprIntegration()
        dapr.markDraft("test", listOf("messaging.input"))
        dapr.removeSyncState("test")
        assertEquals("no_dapr", dapr.getSyncState("test").status)
    }

    @Test
    fun `allSyncStates returns all tracked transformations`() {
        val dapr = DaprIntegration()
        dapr.markDraft("a", listOf("messaging.input"))
        dapr.markDraft("b", listOf("messaging.output"))
        val all = dapr.allSyncStates()
        assertEquals(2, all.size)
        assertTrue(all.containsKey("a"))
        assertTrue(all.containsKey("b"))
    }

    // ── Sync in http-only mode ──

    @Test
    fun `sync in http-only mode succeeds as no-op`() {
        val dapr = DaprIntegration()
        dapr.markDraft("test", listOf("messaging.input"))
        val result = dapr.sync("test", MessagingEndpoint(queue = "q1"), null)
        assertTrue(result.success)
        assertEquals("synced", dapr.getSyncState("test").status)
    }

    @Test
    fun `sync with null endpoints sets no_dapr`() {
        val dapr = DaprIntegration()
        dapr.markDraft("test", listOf("messaging.removed"))
        val result = dapr.sync("test", null, null)
        assertTrue(result.success)
        assertEquals("no_dapr", dapr.getSyncState("test").status)
    }

    // ── Sync in dynamic mode — YAML generation ──

    @Test
    fun `sync generates queue binding YAML`(@TempDir dir: Path) {
        val dapr = DaprIntegration(
            componentsDir = dir,
            servicebusNamespace = "myco.servicebus.windows.net"
        )
        dapr.markDraft("orders-in", listOf("messaging.input"))

        val result = dapr.sync("orders-in", MessagingEndpoint(queue = "orders-in"), null)
        assertTrue(result.success)
        assertEquals("synced", dapr.getSyncState("orders-in").status)

        // Verify YAML was written
        val yamlFile = dir.resolve("binding-orders-in.yaml")
        assertTrue(Files.exists(yamlFile))
        val content = Files.readString(yamlFile)
        assertTrue(content.contains("bindings.azure.servicebusqueues"))
        assertTrue(content.contains("queueName"))
        assertTrue(content.contains("orders-in"))
        assertTrue(content.contains("myco.servicebus.windows.net"))
        assertTrue(content.contains("utlxe.io/managed: \"true\""))
    }

    @Test
    fun `sync generates pubsub YAML for topic`(@TempDir dir: Path) {
        val dapr = DaprIntegration(
            componentsDir = dir,
            servicebusNamespace = "myco.servicebus.windows.net"
        )

        val result = dapr.sync("inv",
            MessagingEndpoint(topic = "raw-invoices", subscription = "utlxe"),
            null
        )
        assertTrue(result.success)

        val yamlFile = dir.resolve("pubsub-utlxe-servicebus.yaml")
        assertTrue(Files.exists(yamlFile))
        val content = Files.readString(yamlFile)
        assertTrue(content.contains("pubsub.azure.servicebus.topics"))
        assertTrue(content.contains("myco.servicebus.windows.net"))
    }

    @Test
    fun `sync generates eventhub binding YAML`(@TempDir dir: Path) {
        val dapr = DaprIntegration(
            componentsDir = dir,
            eventhubNamespace = "myco-eventhubs"
        )

        val result = dapr.sync("telemetry",
            MessagingEndpoint(eventhub = "iot-telemetry"),
            null
        )
        assertTrue(result.success)

        val yamlFile = dir.resolve("binding-iot-telemetry.yaml")
        assertTrue(Files.exists(yamlFile))
        val content = Files.readString(yamlFile)
        assertTrue(content.contains("bindings.azure.eventhubs"))
        assertTrue(content.contains("myco-eventhubs"))
    }

    @Test
    fun `sync generates eventhub pubsub YAML when consumerGroup set`(@TempDir dir: Path) {
        val dapr = DaprIntegration(
            componentsDir = dir,
            eventhubNamespace = "myco-eventhubs",
            storageAccount = "mystorage"
        )

        val result = dapr.sync("telemetry",
            MessagingEndpoint(eventhub = "iot-telemetry", consumerGroup = "utlxe"),
            null
        )
        assertTrue(result.success)

        val yamlFile = dir.resolve("pubsub-iot-telemetry.yaml")
        assertTrue(Files.exists(yamlFile))
        val content = Files.readString(yamlFile)
        assertTrue(content.contains("pubsub.azure.eventhubs"))
        assertTrue(content.contains("consumerGroup"))
        assertTrue(content.contains("storageAccountName"))
        assertTrue(content.contains("mystorage"))
    }

    @Test
    fun `sync generates both input and output components`(@TempDir dir: Path) {
        val dapr = DaprIntegration(
            componentsDir = dir,
            servicebusNamespace = "myco.servicebus.windows.net"
        )

        val result = dapr.sync("orders",
            MessagingEndpoint(queue = "orders-in"),
            MessagingEndpoint(queue = "orders-out")
        )
        assertTrue(result.success)
        assertEquals(2, result.actions.size)

        assertTrue(Files.exists(dir.resolve("binding-orders-in.yaml")))
        assertTrue(Files.exists(dir.resolve("binding-orders-out.yaml")))
    }

    @Test
    fun `sync with mixed input queue and output topic`(@TempDir dir: Path) {
        val dapr = DaprIntegration(
            componentsDir = dir,
            servicebusNamespace = "myco.servicebus.windows.net"
        )

        val result = dapr.sync("orders",
            MessagingEndpoint(queue = "orders-in"),
            MessagingEndpoint(topic = "processed-orders")
        )
        assertTrue(result.success)
        assertEquals(2, result.actions.size)

        // Input: queue binding
        assertTrue(Files.exists(dir.resolve("binding-orders-in.yaml")))
        // Output: shared pub/sub component
        assertTrue(Files.exists(dir.resolve("pubsub-utlxe-servicebus.yaml")))
    }

    // ── removeOnDelete ──

    @Test
    fun `removeOnDelete cleans up YAML and sync state`(@TempDir dir: Path) {
        val dapr = DaprIntegration(
            componentsDir = dir,
            servicebusNamespace = "myco.servicebus.windows.net"
        )

        // Sync first
        dapr.sync("orders-in", MessagingEndpoint(queue = "orders-in"), null)
        assertTrue(Files.exists(dir.resolve("binding-orders-in.yaml")))
        assertEquals("synced", dapr.getSyncState("orders-in").status)

        // Delete
        dapr.removeOnDelete("orders-in")
        assertFalse(Files.exists(dir.resolve("binding-orders-in.yaml")))
        assertEquals("no_dapr", dapr.getSyncState("orders-in").status)
    }

    @Test
    fun `removeOnDelete does not remove unmanaged YAML`(@TempDir dir: Path) {
        // Write an unmanaged YAML file
        val unmanaged = dir.resolve("binding-legacy.yaml")
        Files.writeString(unmanaged, "apiVersion: dapr.io/v1alpha1\nkind: Component\nmetadata:\n  name: legacy")

        val dapr = DaprIntegration(componentsDir = dir)
        dapr.removeOnDelete("orders-in")

        // Unmanaged file should still exist
        assertTrue(Files.exists(unmanaged))
    }

    // ── Sync actions reporting ──

    @Test
    fun `sync reports created action for new component`(@TempDir dir: Path) {
        val dapr = DaprIntegration(componentsDir = dir, servicebusNamespace = "ns")
        val result = dapr.sync("test", MessagingEndpoint(queue = "q1"), null)
        assertEquals(1, result.actions.size)
        assertEquals("created", result.actions[0].action)
        assertEquals("q1", result.actions[0].component)
    }

    @Test
    fun `sync reports updated action for existing component`(@TempDir dir: Path) {
        val dapr = DaprIntegration(componentsDir = dir, servicebusNamespace = "ns")
        // First sync
        dapr.sync("test", MessagingEndpoint(queue = "q1"), null)
        // Second sync (update)
        val result = dapr.sync("test", MessagingEndpoint(queue = "q1"), null)
        assertEquals(1, result.actions.size)
        assertEquals("updated", result.actions[0].action)
    }

    // ── Sidecar probe (no actual sidecar in tests) ──

    @Test
    fun `probeSidecar marks unreachable when no sidecar running`() {
        val dapr = DaprIntegration()
        dapr.probeSidecar()
        assertFalse(dapr.sidecarReachable)
        assertNull(dapr.sidecarVersion)
    }
}
