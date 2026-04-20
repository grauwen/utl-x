package org.apache.utlx.engine

import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.transport.GrpcTransport
import org.apache.utlx.engine.transport.StdioJsonTransport
import org.apache.utlx.engine.transport.StdioProtoTransport
import org.apache.utlx.engine.transport.TransportServer
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("org.apache.utlx.engine.Main")
private const val VERSION = "1.0.1"

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        exitProcess(0)
    }

    var bundlePath: String? = null
    var configPath: String? = null
    var portOverride: Int? = null
    var validateOnly = false
    var mode = "stdio-json"
    var workers: Int? = null
    var socketPath: String? = null
    var grpcAddress: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--bundle", "-b" -> {
                i++
                bundlePath = args.getOrNull(i)
                    ?: exitWithError("--bundle requires a path argument")
            }
            "--config", "-c" -> {
                i++
                configPath = args.getOrNull(i)
                    ?: exitWithError("--config requires a path argument")
            }
            "--port", "-p" -> {
                i++
                val portStr = args.getOrNull(i)
                    ?: exitWithError("--port requires a port number")
                portOverride = portStr.toIntOrNull()
                    ?: exitWithError("--port value must be a number: $portStr")
                if (portOverride !in 1..65535) {
                    exitWithError("--port must be between 1 and 65535: $portOverride")
                }
            }
            "--mode" -> {
                i++
                mode = args.getOrNull(i)
                    ?: exitWithError("--mode requires a value: stdio-json, stdio-proto, grpc")
                if (mode !in listOf("stdio-json", "stdio-proto", "grpc")) {
                    exitWithError("Unknown mode: $mode. Valid: stdio-json, stdio-proto, grpc")
                }
            }
            "--workers" -> {
                i++
                val workersStr = args.getOrNull(i)
                    ?: exitWithError("--workers requires a number")
                workers = workersStr.toIntOrNull()
                    ?: exitWithError("--workers must be a number: $workersStr")
            }
            "--socket" -> {
                i++
                socketPath = args.getOrNull(i)
                    ?: exitWithError("--socket requires a path argument")
            }
            "--address" -> {
                i++
                grpcAddress = args.getOrNull(i)
                    ?: exitWithError("--address requires a host:port argument")
            }
            "--validate" -> {
                validateOnly = true
            }
            "--version", "-v" -> {
                println("utlxe v$VERSION")
                exitProcess(0)
            }
            "--help", "-h" -> {
                printUsage()
                exitProcess(0)
            }
            else -> exitWithError("Unknown argument: ${args[i]}")
        }
        i++
    }

    // Validate: stdio-json requires --bundle; stdio-proto/grpc do not
    if (mode == "stdio-json" && bundlePath == null) {
        exitWithError("--bundle <path> is required for stdio-json mode")
    }

    try {
        var config = if (configPath != null) {
            EngineConfig.load(Paths.get(configPath))
        } else if (bundlePath != null) {
            val bundleConfig = Paths.get(bundlePath).resolve("engine.yaml")
            if (bundleConfig.toFile().exists()) {
                EngineConfig.load(bundleConfig)
            } else {
                EngineConfig.default()
            }
        } else {
            EngineConfig.default()
        }

        if (portOverride != null) {
            config = config.withHealthPort(portOverride)
        }

        val engine = UtlxEngine(config)

        // Initialize: from bundle or empty (for dynamic loading modes)
        if (bundlePath != null) {
            engine.initialize(Paths.get(bundlePath))
        } else {
            engine.initializeEmpty()
        }

        if (validateOnly) {
            val transformations = engine.registry.list()
            println("Bundle validated successfully: ${transformations.size} transformation(s)")
            transformations.forEach { tx ->
                println("  ${tx.name} [${tx.config.strategy}]")
            }
            exitProcess(0)
        }

        // Create transport based on --mode
        val transport: TransportServer = when (mode) {
            "stdio-json" -> StdioJsonTransport()
            "stdio-proto" -> StdioProtoTransport(engine)
            "grpc" -> GrpcTransport(engine, address = grpcAddress, socketPath = socketPath)
            else -> exitWithError("Unknown mode: $mode")
        }

        // Shutdown hook for graceful drain
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown signal received")
            engine.stop()
        })

        // start() blocks until transport shuts down
        engine.start(transport)

    } catch (e: Exception) {
        logger.error("Engine failed: {}", e.message, e)
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    }
}

private fun printUsage() {
    println("""
        utlxe — UTL-X Production Runtime Engine v$VERSION

        Usage:
          utlxe --bundle <path> [options]            Standalone (bundle mode)
          utlxe --mode stdio-proto [options]          Open-M integration (dynamic loading)
          utlxe --mode grpc --socket <path> [options] gRPC server mode

        Options:
          --bundle, -b <path>    Path to .utlxp project bundle directory
          --config, -c <path>    Path to engine.yaml config file
          --mode <mode>          Transport mode: stdio-json (default), stdio-proto, grpc
          --port,   -p <port>    Health endpoint port override (default: 8081)
          --workers <n>          Worker thread pool size (default: CPU cores)
          --socket <path>        Unix Domain Socket path (gRPC mode, Linux)
          --address <host:port>  TCP address (gRPC mode, default: localhost:9090)
          --validate             Load and compile the bundle, then exit (no processing)
          --version, -v          Print version and exit
          --help, -h             Print this help and exit

        Modes:
          stdio-json     Line-delimited JSON over stdin/stdout (default, backward compat)
                         Requires --bundle. Transforms loaded from disk at startup.

          stdio-proto    Varint-delimited protobuf over stdin/stdout (Open-M integration)
                         --bundle optional. Transforms loaded dynamically via
                         LoadTransformation messages from the caller.

          grpc           gRPC server on Unix Domain Socket or TCP
                         --bundle optional. Transforms loaded dynamically via RPCs.
    """.trimIndent())
}

private fun exitWithError(message: String): Nothing {
    System.err.println("Error: $message")
    System.err.println("Run 'utlxe --help' for usage information.")
    exitProcess(1)
}
