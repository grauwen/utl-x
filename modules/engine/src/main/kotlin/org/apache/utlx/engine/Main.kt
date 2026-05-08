package org.apache.utlx.engine

import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.transport.GrpcTransport
import org.apache.utlx.engine.transport.HttpTransport
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
    var dataDir: String? = null
    var workers: Int? = null
    var socketPath: String? = null
    var grpcAddress: String? = null
    var httpPort: Int = HttpTransport.DEFAULT_PORT
    var grpcPort: Int = GrpcTransport.DEFAULT_PORT
    var alsoHttp = false
    var alsoGrpc = false
    var alsoStdio = false
    var daprComponentsDir: String? = null
    var daprServicebusNamespace: String? = null
    var daprEventhubNamespace: String? = null
    var daprStorageAccount: String? = null

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
            "--admin-port" -> {
                i++
                val portStr = args.getOrNull(i)
                    ?: exitWithError("--admin-port requires a port number")
                portOverride = portStr.toIntOrNull()
                    ?: exitWithError("--admin-port value must be a number: $portStr")
                if (portOverride !in 1..65535) {
                    exitWithError("--admin-port must be between 1 and 65535: $portOverride")
                }
            }
            "--mode" -> {
                i++
                mode = args.getOrNull(i)
                    ?: exitWithError("--mode requires a value: stdio-json, stdio-proto, grpc, http")
                if (mode !in listOf("stdio-json", "stdio-proto", "grpc", "http")) {
                    exitWithError("Unknown mode: $mode. Valid: stdio-json, stdio-proto, grpc, http")
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
            "--http-port" -> {
                i++
                httpPort = args.getOrNull(i)?.toIntOrNull()
                    ?: exitWithError("--http-port requires a port number")
            }
            "--grpc-port" -> {
                i++
                grpcPort = args.getOrNull(i)?.toIntOrNull()
                    ?: exitWithError("--grpc-port requires a port number")
            }
            "--also-http" -> { alsoHttp = true }
            "--also-grpc" -> { alsoGrpc = true }
            "--also-stdio" -> { alsoStdio = true }
            "--data-dir" -> {
                i++
                dataDir = args.getOrNull(i)
                    ?: exitWithError("--data-dir requires a path argument")
            }
            "--dapr-components-dir" -> {
                i++
                daprComponentsDir = args.getOrNull(i)
                    ?: exitWithError("--dapr-components-dir requires a path argument")
            }
            "--dapr-servicebus-namespace" -> {
                i++
                daprServicebusNamespace = args.getOrNull(i)
                    ?: exitWithError("--dapr-servicebus-namespace requires a value")
            }
            "--dapr-eventhub-namespace" -> {
                i++
                daprEventhubNamespace = args.getOrNull(i)
                    ?: exitWithError("--dapr-eventhub-namespace requires a value")
            }
            "--dapr-storage-account" -> {
                i++
                daprStorageAccount = args.getOrNull(i)
                    ?: exitWithError("--dapr-storage-account requires a value")
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

        // EF09: Detect bundle mode (.utlar = locked, directory = open)
        engine.dataDir = dataDir
        val dataDirPath = dataDir?.let { Paths.get(it) }
        engine.bundleInfo = org.apache.utlx.engine.admin.detectBundleMode(dataDirPath)
        logger.info("Bundle mode: {} {}", engine.bundleInfo.mode,
            engine.bundleInfo.bundleVersion?.let { "(v$it)" } ?: "")

        if (engine.bundleInfo.mode == "locked" && engine.bundleInfo.utlarPath != null) {
            // Load from .utlar archive
            val loaded = org.apache.utlx.engine.admin.loadUtlar(engine.bundleInfo.utlarPath!!, engine)
            logger.info("Loaded {} transformation(s) from .utlar", loaded)
        } else if (dataDir != null) {
            // EF03: Scan data dir for persisted transformations (open mode)
            engine.scanDataDir(Paths.get(dataDir))
        }

        // EF10: Create Dapr integration (detection + dynamic component management)
        val daprIntegration = org.apache.utlx.engine.admin.DaprIntegration(
            componentsDir = daprComponentsDir?.let { Paths.get(it) },
            servicebusNamespace = daprServicebusNamespace,
            eventhubNamespace = daprEventhubNamespace,
            storageAccount = daprStorageAccount
        )
        engine.daprIntegration = daprIntegration

        // EF10: Startup reconciliation — auto-sync persisted messaging configs
        daprIntegration.probeSidecar()
        daprIntegration.reconcileOnStartup(engine.registry)

        if (validateOnly) {
            val transformations = engine.registry.list()
            println("Bundle validated successfully: ${transformations.size} transformation(s)")
            transformations.forEach { tx ->
                println("  ${tx.name} [${tx.config.strategy}]")
            }
            exitProcess(0)
        }

        // EF07: Build list of transports (primary + additional)
        val transports = mutableListOf<TransportServer>()

        // Primary transport from --mode
        when (mode) {
            "stdio-json" -> transports.add(StdioJsonTransport())
            "stdio-proto" -> transports.add(StdioProtoTransport(engine, workers = workers ?: Runtime.getRuntime().availableProcessors()))
            "grpc" -> transports.add(GrpcTransport(engine, address = grpcAddress ?: "0.0.0.0:$grpcPort", socketPath = socketPath))

            "http" -> transports.add(HttpTransport(engine, port = httpPort))
            else -> exitWithError("Unknown mode: $mode")
        }

        // Additional transports (--also-http, --also-grpc, --also-stdio)
        if (alsoHttp && mode != "http") {
            transports.add(HttpTransport(engine, port = httpPort))
        }
        if (alsoGrpc && mode != "grpc") {
            transports.add(GrpcTransport(engine, address = grpcAddress ?: "0.0.0.0:$grpcPort", socketPath = socketPath))
        }
        if (alsoStdio && mode != "stdio-proto") {
            transports.add(StdioProtoTransport(engine, workers = workers ?: Runtime.getRuntime().availableProcessors()))
        }

        logger.info("Configured {} transport(s): {}", transports.size, transports.joinToString { it.name })

        // Shutdown hook for graceful drain
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown signal received")
            engine.stop()
        })

        // start() blocks until last transport shuts down
        engine.start(transports)

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
          utlxe --mode http [options]                   Azure / Docker / HTTP clients
          utlxe --mode http --also-grpc [options]       HTTP + gRPC SDK (hybrid)
          utlxe --mode stdio-proto --also-http [options] Open-M + admin API
          utlxe --bundle <path> [options]               Standalone (bundle from disk)

        Primary transport (--mode):
          http           HTTP REST API on data plane + admin/health port
                         For Azure Container Apps, Docker, Dapr, direct HTTP clients.

          stdio-proto    Protobuf over stdin/stdout (Open-M / language wrapper integration)
                         Transforms loaded dynamically via LoadTransformation messages.

          grpc           gRPC server on TCP or Unix Domain Socket
                         Transforms loaded dynamically via RPCs.

          stdio-json     JSON lines over stdin/stdout (CLI, backward compat)
                         Requires --bundle. Transforms loaded from disk at startup.

        Additional transports (run alongside primary):
          --also-http    Also start HTTP data plane + admin API
          --also-grpc    Also start gRPC server
          --also-stdio   Also start stdio-proto transport

        Options:
          --bundle, -b <path>    Path to bundle directory (pre-load transforms from disk)
          --config, -c <path>    Path to engine.yaml config file
          --admin-port <port>    Admin + health + metrics port (default: from engine.yaml, typically 8081)
          --http-port <port>     HTTP data plane port (default: ${HttpTransport.DEFAULT_PORT})
          --grpc-port <port>     gRPC port (default: ${GrpcTransport.DEFAULT_PORT})
          --socket <path>        Unix Domain Socket path (gRPC, Linux/macOS only — falls back to TCP on Windows)
          --address <host:port>  TCP address (gRPC, alternative to --grpc-port)
          --workers <n>          Worker thread pool size (default: CPU cores)
          --data-dir <path>      Persistent storage directory for transformations
          --validate             Load and compile the bundle, then exit (no processing)
          --dapr-components-dir <path>  Directory for Dapr component YAML (enables dynamic mode)
          --dapr-servicebus-namespace <fqdn>  Service Bus namespace FQDN
          --dapr-eventhub-namespace <name>    Event Hub namespace name
          --dapr-storage-account <name>       Storage account for Event Hub checkpointing
          --version, -v          Print version and exit
          --help, -h             Print this help and exit
    """.trimIndent())
}

private fun exitWithError(message: String): Nothing {
    System.err.println("Error: $message")
    System.err.println("Run 'utlxe --help' for usage information.")
    exitProcess(1)
}
