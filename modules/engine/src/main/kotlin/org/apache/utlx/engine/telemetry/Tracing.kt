package org.apache.utlx.engine.telemetry

import io.opentelemetry.api.trace.Span

/**
 * EF14: OpenTelemetry tracing support for UTLXe.
 *
 * The Azure Monitor OpenTelemetry agent (-javaagent) handles:
 * - Automatic span creation for HTTP requests (Ktor/Netty)
 * - Automatic span creation for outgoing HTTP calls (to Dapr)
 * - W3C Trace Context propagation (traceparent/tracestate)
 * - Log-trace correlation via MDC (traceId, spanId)
 * - JVM metrics export to Azure Monitor
 *
 * This class only adds UTLXe-specific custom attributes to the
 * agent-created spans. No manual span creation needed.
 *
 * When the agent is not attached, Span.current() returns a no-op span
 * and setAttribute() calls are silently ignored — zero overhead.
 */
object Tracing {

    /**
     * Add UTLXe-specific attributes to the current span.
     * Called from TransportHandlers.handleExecute() to enrich the
     * agent-created HTTP span with transformation details.
     */
    fun addTransformAttributes(
        transformationName: String,
        strategy: String,
        inputSize: Int,
        messageId: String,
        correlationId: String
    ) {
        val span = Span.current()
        if (!span.spanContext.isValid) return  // no active span (agent not attached)

        span.setAttribute("utlxe.transformation", transformationName)
        span.setAttribute("utlxe.strategy", strategy)
        span.setAttribute("utlxe.input.size", inputSize.toLong())
        span.setAttribute("utlxe.message_id", messageId)
        span.setAttribute("utlxe.correlation_id", correlationId)

        // Override the generic span name with a meaningful one
        span.updateName("utlxe.transform $transformationName")
    }

    /**
     * Check if the Azure Monitor agent is configured (connection string set).
     */
    val isActive: Boolean
        get() = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING") != null

    /**
     * Record transformation result on the current span.
     * Called after execution completes.
     */
    fun recordResult(outputSize: Int?, durationUs: Long, error: String?) {
        val span = Span.current()
        if (!span.spanContext.isValid) return

        span.setAttribute("utlxe.duration_us", durationUs)
        if (outputSize != null) {
            span.setAttribute("utlxe.output.size", outputSize.toLong())
        }
        if (error != null) {
            span.setAttribute("utlxe.error", error)
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, error)
        }
    }
}
