package org.apache.utlx.engine.health

import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.EngineState

/**
 * Collects engine metrics and formats them as Prometheus text exposition format.
 *
 * All metrics are derived from existing AtomicLong counters in the registry
 * and engine — no additional instrumentation in the hot path.
 *
 * Endpoint: GET /metrics (same port as /health)
 */
object MetricsCollector {

    /**
     * Generate Prometheus text exposition format.
     * See: https://prometheus.io/docs/instrumenting/exposition_formats/
     */
    fun collect(engine: UtlxEngine): String {
        val sb = StringBuilder()
        val registry = engine.registry
        val transforms = registry.list()

        // ── Engine gauges ──
        sb.appendMetric("utlxe_up", "gauge", "Whether the engine is running",
            if (engine.state == EngineState.RUNNING) 1L else 0L)

        sb.appendMetric("utlxe_uptime_seconds", "gauge", "Engine uptime in seconds",
            engine.uptimeMs() / 1000.0)

        sb.appendMetric("utlxe_transformations_loaded", "gauge", "Number of loaded transformations",
            transforms.size.toLong())

        // ── Execution counters (aggregated) ──
        val totalExecutions = transforms.sumOf { it.executionCount.get() }
        val totalErrors = transforms.sumOf { it.errorCount.get() }

        sb.appendMetric("utlxe_executions_total", "counter", "Total executions across all transformations",
            totalExecutions)

        sb.appendMetric("utlxe_errors_total", "counter", "Total execution errors across all transformations",
            totalErrors)

        // ── Per-transformation metrics ──
        if (transforms.isNotEmpty()) {
            sb.appendLine("# HELP utlxe_transformation_executions_total Executions per transformation")
            sb.appendLine("# TYPE utlxe_transformation_executions_total counter")
            for (tx in transforms) {
                sb.appendLine("utlxe_transformation_executions_total{transformation=\"${escape(tx.name)}\",strategy=\"${tx.strategy.name}\"} ${tx.executionCount.get()}")
            }
            sb.appendLine()

            sb.appendLine("# HELP utlxe_transformation_errors_total Errors per transformation")
            sb.appendLine("# TYPE utlxe_transformation_errors_total counter")
            for (tx in transforms) {
                sb.appendLine("utlxe_transformation_errors_total{transformation=\"${escape(tx.name)}\",strategy=\"${tx.strategy.name}\"} ${tx.errorCount.get()}")
            }
            sb.appendLine()
        }

        // ── JVM metrics (free, useful for production debugging) ──
        val runtime = Runtime.getRuntime()
        sb.appendMetric("utlxe_jvm_memory_used_bytes", "gauge", "JVM heap memory used",
            runtime.totalMemory() - runtime.freeMemory())

        sb.appendMetric("utlxe_jvm_memory_max_bytes", "gauge", "JVM max heap memory",
            runtime.maxMemory())

        sb.appendMetric("utlxe_jvm_threads", "gauge", "Number of active JVM threads",
            Thread.activeCount().toLong())

        return sb.toString()
    }

    private fun StringBuilder.appendMetric(name: String, type: String, help: String, value: Long) {
        appendLine("# HELP $name $help")
        appendLine("# TYPE $name $type")
        appendLine("$name $value")
        appendLine()
    }

    private fun StringBuilder.appendMetric(name: String, type: String, help: String, value: Double) {
        appendLine("# HELP $name $help")
        appendLine("# TYPE $name $type")
        appendLine("$name ${"%.3f".format(value)}")
        appendLine()
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
