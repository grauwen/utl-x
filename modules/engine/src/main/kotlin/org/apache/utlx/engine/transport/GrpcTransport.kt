package org.apache.utlx.engine.transport

import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup
import io.grpc.netty.shaded.io.netty.channel.ServerChannel
import io.grpc.stub.StreamObserver
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.proto.*
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
 * - UDS: --mode grpc --socket /path/to/socket (Linux via epoll, macOS via kqueue)
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
            val socketAddress = io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress(path)
            File(path).delete()

            val os = System.getProperty("os.name", "").lowercase()
            val (channelType, bossGroup, workerGroup) = when {
                os.contains("linux") -> Triple(
                    Class.forName("io.grpc.netty.shaded.io.netty.channel.epoll.EpollServerDomainSocketChannel")
                            as Class<out ServerChannel>,
                    Class.forName("io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoopGroup")
                        .getConstructor(Int::class.java).newInstance(1) as EventLoopGroup,
                    Class.forName("io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoopGroup")
                        .getDeclaredConstructor().newInstance() as EventLoopGroup
                )
                os.contains("mac") || os.contains("darwin") -> Triple(
                    Class.forName("io.grpc.netty.shaded.io.netty.channel.kqueue.KQueueServerDomainSocketChannel")
                            as Class<out ServerChannel>,
                    Class.forName("io.grpc.netty.shaded.io.netty.channel.kqueue.KQueueEventLoopGroup")
                        .getConstructor(Int::class.java).newInstance(1) as EventLoopGroup,
                    Class.forName("io.grpc.netty.shaded.io.netty.channel.kqueue.KQueueEventLoopGroup")
                        .getDeclaredConstructor().newInstance() as EventLoopGroup
                )
                else -> {
                    logger.warn("UDS not supported on OS '{}', falling back to TCP", os)
                    return null
                }
            }

            logger.info("Building gRPC UDS server on {} ({})", path, if (os.contains("linux")) "epoll" else "kqueue")
            NettyServerBuilder
                .forAddress(socketAddress)
                .channelType(channelType)
                .bossEventLoopGroup(bossGroup)
                .workerEventLoopGroup(workerGroup)
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
 * Delegates all logic to TransportHandlers for consistency with StdioProtoTransport.
 */
class UtlxeServiceImpl(
    private val engine: UtlxEngine,
    private val registry: TransformationRegistry
) : UtlxeServiceGrpc.UtlxeServiceImplBase() {

    override fun loadTransformation(
        request: LoadTransformationRequest,
        responseObserver: StreamObserver<LoadTransformationResponse>
    ) {
        responseObserver.onNext(TransportHandlers.handleLoadTransformation(request, engine, registry))
        responseObserver.onCompleted()
    }

    override fun execute(
        request: ExecuteRequest,
        responseObserver: StreamObserver<ExecuteResponse>
    ) {
        responseObserver.onNext(TransportHandlers.handleExecute(request, registry))
        responseObserver.onCompleted()
    }

    override fun executeBatch(
        request: ExecuteBatchRequest,
        responseObserver: StreamObserver<ExecuteBatchResponse>
    ) {
        responseObserver.onNext(TransportHandlers.handleExecuteBatch(request, registry))
        responseObserver.onCompleted()
    }

    override fun executePipeline(
        request: ExecutePipelineRequest,
        responseObserver: StreamObserver<ExecutePipelineResponse>
    ) {
        responseObserver.onNext(TransportHandlers.handleExecutePipeline(request, registry))
        responseObserver.onCompleted()
    }

    override fun unloadTransformation(
        request: UnloadTransformationRequest,
        responseObserver: StreamObserver<UnloadTransformationResponse>
    ) {
        responseObserver.onNext(TransportHandlers.handleUnload(request, registry))
        responseObserver.onCompleted()
    }

    override fun health(
        request: HealthRequest,
        responseObserver: StreamObserver<HealthResponse>
    ) {
        responseObserver.onNext(TransportHandlers.handleHealth(engine, registry))
        responseObserver.onCompleted()
    }
}
