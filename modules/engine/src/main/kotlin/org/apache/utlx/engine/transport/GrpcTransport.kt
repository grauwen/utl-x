package org.apache.utlx.engine.transport

import com.google.protobuf.ByteString
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoopGroup
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollServerDomainSocketChannel
import io.grpc.stub.StreamObserver
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.proto.*
import org.apache.utlx.engine.registry.TransformationInstance
import org.apache.utlx.engine.registry.TransformationRegistry
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * GrpcTransport — gRPC server on TCP or Unix Domain Socket.
 *
 * Used for external consumers, sidecar deployments, and testing tools.
 * Implements the UtlxeService defined in utlxe.proto.
 *
 * Modes:
 * - TCP: --mode grpc --address host:port
 * - UDS: --mode grpc --socket /path/to/socket (Linux only, falls back to TCP on macOS)
 */
class GrpcTransport(
    private val engine: UtlxEngine,
    private val address: String? = null,
    private val socketPath: String? = null
) : TransportServer {

    private val logger = LoggerFactory.getLogger(GrpcTransport::class.java)
    private var server: Server? = null

    override val supportsDynamicLoading = true

    override fun start(registry: TransformationRegistry) {
        val serviceImpl = UtlxeServiceImpl(engine, registry)

        server = if (socketPath != null) {
            buildUdsServer(serviceImpl, socketPath)
                ?: buildTcpServer(serviceImpl, address ?: "localhost:9090")
        } else {
            buildTcpServer(serviceImpl, address ?: "localhost:9090")
        }

        val grpcServer = server!!
        grpcServer.start()

        val listenAddress = if (socketPath != null && grpcServer.port == -1) {
            "unix:$socketPath"
        } else {
            "localhost:${grpcServer.port}"
        }
        logger.info("GrpcTransport: listening on {}", listenAddress)

        // Block until shutdown
        grpcServer.awaitTermination()
    }

    override fun stop() {
        server?.let { s ->
            logger.info("GrpcTransport: shutting down...")
            s.shutdown()
            if (!s.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("GrpcTransport: forcing shutdown after timeout")
                s.shutdownNow()
            }
        }
        // Clean up socket file
        socketPath?.let { File(it).delete() }
        logger.info("GrpcTransport stopped")
    }

    /**
     * Returns the port the server is listening on (useful for tests with port 0).
     */
    fun port(): Int = server?.port ?: -1

    private fun buildTcpServer(service: UtlxeServiceImpl, addr: String): Server {
        val parts = addr.split(":")
        val host = parts.getOrElse(0) { "localhost" }
        val port = parts.getOrElse(1) { "9090" }.toInt()

        logger.info("Building gRPC TCP server on {}:{}", host, port)
        return NettyServerBuilder
            .forAddress(InetSocketAddress(host, port))
            .addService(service)
            .build()
    }

    private fun buildUdsServer(service: UtlxeServiceImpl, path: String): Server? {
        return try {
            // UDS requires epoll (Linux only)
            val socketAddress = io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress(path)
            // Clean up stale socket file
            File(path).delete()

            logger.info("Building gRPC UDS server on {}", path)
            NettyServerBuilder
                .forAddress(socketAddress)
                .channelType(EpollServerDomainSocketChannel::class.java)
                .bossEventLoopGroup(EpollEventLoopGroup(1))
                .workerEventLoopGroup(EpollEventLoopGroup())
                .addService(service)
                .build()
        } catch (e: Exception) {
            logger.warn("UDS not available ({}), falling back to TCP", e.message)
            null
        }
    }
}

/**
 * Implementation of the UtlxeService gRPC service.
 * Each RPC method delegates to the engine and registry.
 */
class UtlxeServiceImpl(
    private val engine: UtlxEngine,
    private val registry: TransformationRegistry
) : UtlxeServiceGrpc.UtlxeServiceImplBase() {

    private val logger = LoggerFactory.getLogger(UtlxeServiceImpl::class.java)

    override fun loadTransformation(
        request: LoadTransformationRequest,
        responseObserver: StreamObserver<LoadTransformationResponse>
    ) {
        val startTime = System.nanoTime()

        try {
            val id = request.transformationId
            val source = request.utlxSource
            val strategyName = request.strategy.ifEmpty { "TEMPLATE" }
            val validationPolicy = request.validationPolicy.ifEmpty { "SKIP" }
            val maxConcurrent = if (request.maxConcurrent > 0) request.maxConcurrent else 1

            logger.info("Loading transformation '{}' [strategy={}]", id, strategyName)

            val config = TransformConfig(
                strategy = strategyName,
                validationPolicy = validationPolicy,
                maxConcurrent = maxConcurrent
            )

            val strategy = engine.createStrategy(config)
            val parseStart = System.nanoTime()
            strategy.initialize(source, config)
            val parseEnd = System.nanoTime()

            val instance = TransformationInstance(
                name = id,
                source = source,
                strategy = strategy,
                config = config
            )
            registry.register(id, instance)

            val totalDuration = (System.nanoTime() - startTime) / 1000

            logger.info("Transformation '{}' loaded in {}μs", id, totalDuration)

            responseObserver.onNext(
                LoadTransformationResponse.newBuilder()
                    .setSuccess(true)
                    .setMetrics(
                        LoadMetrics.newBuilder()
                            .setParseDurationUs((parseEnd - parseStart) / 1000)
                            .setTotalDurationUs(totalDuration)
                            .build()
                    )
                    .build()
            )
            responseObserver.onCompleted()

        } catch (e: Exception) {
            logger.error("Failed to load transformation '{}': {}", request.transformationId, e.message, e)
            responseObserver.onNext(
                LoadTransformationResponse.newBuilder()
                    .setSuccess(false)
                    .setError(e.message ?: "Unknown error")
                    .build()
            )
            responseObserver.onCompleted()
        }
    }

    override fun execute(
        request: ExecuteRequest,
        responseObserver: StreamObserver<ExecuteResponse>
    ) {
        val startTime = System.nanoTime()

        val instance = registry.get(request.transformationId)
        if (instance == null) {
            responseObserver.onNext(
                ExecuteResponse.newBuilder()
                    .setSuccess(false)
                    .setError("Transformation not found: ${request.transformationId}")
                    .setErrorClass(ErrorClass.PERMANENT)
                    .setErrorPhase(ErrorPhase.INTERNAL)
                    .setCorrelationId(request.correlationId)
                    .build()
            )
            responseObserver.onCompleted()
            return
        }

        try {
            val input = request.payload.toStringUtf8()
            instance.recordExecution()

            val result = instance.strategy.execute(input)
            val durationUs = (System.nanoTime() - startTime) / 1000

            val builder = ExecuteResponse.newBuilder()
                .setSuccess(true)
                .setOutput(ByteString.copyFromUtf8(result.output))
                .setCorrelationId(request.correlationId)
                .setMetrics(
                    ExecuteMetrics.newBuilder()
                        .setExecuteDurationUs(durationUs)
                        .build()
                )

            result.validationErrors.forEach { err ->
                builder.addValidationErrors(
                    org.apache.utlx.engine.proto.ValidationError.newBuilder()
                        .setMessage(err.message)
                        .setPath(err.path ?: "")
                        .setSeverity(err.severity)
                        .build()
                )
            }

            responseObserver.onNext(builder.build())
            responseObserver.onCompleted()

        } catch (e: Exception) {
            instance.recordError()
            val durationUs = (System.nanoTime() - startTime) / 1000
            logger.error("Execution error for '{}': {}", request.transformationId, e.message)

            responseObserver.onNext(
                ExecuteResponse.newBuilder()
                    .setSuccess(false)
                    .setError(e.message ?: "Unknown error")
                    .setErrorClass(ErrorClass.PERMANENT)
                    .setErrorPhase(ErrorPhase.TRANSFORMATION)
                    .setCorrelationId(request.correlationId)
                    .setMetrics(
                        ExecuteMetrics.newBuilder()
                            .setExecuteDurationUs(durationUs)
                            .build()
                    )
                    .build()
            )
            responseObserver.onCompleted()
        }
    }

    override fun executeBatch(
        request: ExecuteBatchRequest,
        responseObserver: StreamObserver<ExecuteBatchResponse>
    ) {
        val instance = registry.get(request.transformationId)
        val builder = ExecuteBatchResponse.newBuilder()

        if (instance == null) {
            request.itemsList.forEach { item ->
                builder.addResults(
                    ExecuteResponse.newBuilder()
                        .setSuccess(false)
                        .setError("Transformation not found: ${request.transformationId}")
                        .setErrorClass(ErrorClass.PERMANENT)
                        .setErrorPhase(ErrorPhase.INTERNAL)
                        .setCorrelationId(item.correlationId)
                        .build()
                )
            }
            responseObserver.onNext(builder.build())
            responseObserver.onCompleted()
            return
        }

        request.itemsList.forEach { item ->
            val startTime = System.nanoTime()
            try {
                instance.recordExecution()
                val result = instance.strategy.execute(item.payload.toStringUtf8())
                val durationUs = (System.nanoTime() - startTime) / 1000

                builder.addResults(
                    ExecuteResponse.newBuilder()
                        .setSuccess(true)
                        .setOutput(ByteString.copyFromUtf8(result.output))
                        .setCorrelationId(item.correlationId)
                        .setMetrics(
                            ExecuteMetrics.newBuilder()
                                .setExecuteDurationUs(durationUs)
                                .build()
                        )
                        .build()
                )
            } catch (e: Exception) {
                instance.recordError()
                val durationUs = (System.nanoTime() - startTime) / 1000
                builder.addResults(
                    ExecuteResponse.newBuilder()
                        .setSuccess(false)
                        .setError(e.message ?: "Unknown error")
                        .setErrorClass(ErrorClass.PERMANENT)
                        .setErrorPhase(ErrorPhase.TRANSFORMATION)
                        .setCorrelationId(item.correlationId)
                        .setMetrics(
                            ExecuteMetrics.newBuilder()
                                .setExecuteDurationUs(durationUs)
                                .build()
                        )
                        .build()
                )
            }
        }

        responseObserver.onNext(builder.build())
        responseObserver.onCompleted()
    }

    override fun unloadTransformation(
        request: UnloadTransformationRequest,
        responseObserver: StreamObserver<UnloadTransformationResponse>
    ) {
        val success = registry.unload(request.transformationId)
        if (success) {
            logger.info("Unloaded transformation '{}'", request.transformationId)
        } else {
            logger.warn("Transformation '{}' not found for unload", request.transformationId)
        }

        responseObserver.onNext(
            UnloadTransformationResponse.newBuilder()
                .setSuccess(success)
                .build()
        )
        responseObserver.onCompleted()
    }

    override fun health(
        request: HealthRequest,
        responseObserver: StreamObserver<HealthResponse>
    ) {
        val totalExecutions = registry.list().sumOf { it.executionCount.get() }
        val totalErrors = registry.list().sumOf { it.errorCount.get() }

        responseObserver.onNext(
            HealthResponse.newBuilder()
                .setState(engine.state.name)
                .setUptimeMs(engine.uptimeMs())
                .setLoadedTransformations(registry.size())
                .setTotalExecutions(totalExecutions)
                .setTotalErrors(totalErrors)
                .build()
        )
        responseObserver.onCompleted()
    }
}
