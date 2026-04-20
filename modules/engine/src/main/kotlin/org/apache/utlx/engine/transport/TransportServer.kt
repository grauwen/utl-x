package org.apache.utlx.engine.transport

import org.apache.utlx.engine.registry.TransformationRegistry

/**
 * TransportServer is the abstraction for how UTLXe communicates with its caller.
 *
 * Three implementations:
 * - StdioJsonTransport: line-delimited JSON over stdin/stdout (backward compat, standalone)
 * - StdioProtoTransport: varint-delimited protobuf over stdin/stdout (Open-M integration)
 * - GrpcTransport: gRPC server on UDS or TCP (external consumers, sidecar)
 *
 * The transport is responsible for:
 * 1. Receiving requests (transform, load, unload, health)
 * 2. Dispatching them to the registry/strategies
 * 3. Sending responses back to the caller
 *
 * The engine lifecycle (CREATED → INITIALIZING → READY → RUNNING → DRAINING → STOPPED)
 * is managed by UtlxEngine. The transport is started after READY and stopped during DRAINING.
 */
interface TransportServer {

    /**
     * Start the transport server. Called when engine transitions to RUNNING.
     * This method should block until the transport is shut down (e.g., EOF on stdin, gRPC server stopped).
     *
     * @param registry The transformation registry containing all loaded transformations.
     *                 For stdio-json mode, registry is pre-populated from bundle.
     *                 For stdio-proto/grpc modes, registry may be empty (transforms loaded dynamically).
     */
    fun start(registry: TransformationRegistry)

    /**
     * Stop the transport server gracefully. Called during engine DRAINING phase.
     * Should stop accepting new requests, drain in-flight requests, and close connections.
     */
    fun stop()

    /**
     * Whether this transport supports dynamic transformation loading.
     * stdio-json: false (transforms loaded from bundle at init)
     * stdio-proto/grpc: true (transforms loaded via LoadTransformation messages)
     */
    val supportsDynamicLoading: Boolean
}
