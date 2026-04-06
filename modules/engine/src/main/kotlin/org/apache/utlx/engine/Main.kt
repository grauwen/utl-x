package org.apache.utlx.engine

import org.apache.utlx.engine.config.EngineConfig
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("org.apache.utlx.engine.Main")
private const val VERSION = "1.0.0-SNAPSHOT"

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        exitProcess(0)
    }

    var bundlePath: String? = null
    var configPath: String? = null
    var portOverride: Int? = null
    var validateOnly = false

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

    if (bundlePath == null) {
        exitWithError("--bundle <path> is required")
    }

    try {
        var config = if (configPath != null) {
            EngineConfig.load(Paths.get(configPath))
        } else {
            // Try loading engine.yaml from bundle root
            val bundleConfig = Paths.get(bundlePath).resolve("engine.yaml")
            if (bundleConfig.toFile().exists()) {
                EngineConfig.load(bundleConfig)
            } else {
                EngineConfig.default()
            }
        }

        if (portOverride != null) {
            config = config.withHealthPort(portOverride)
        }

        val engine = UtlxEngine(config)

        engine.initialize(Paths.get(bundlePath))

        if (validateOnly) {
            val transformations = engine.registry.list()
            println("Bundle validated successfully: ${transformations.size} transformation(s)")
            transformations.forEach { tx ->
                println("  ${tx.name} [${tx.config.strategy}]")
            }
            exitProcess(0)
        }

        // Shutdown hook for graceful drain
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown signal received")
            engine.stop()
        })

        engine.start()

    } catch (e: Exception) {
        logger.error("Engine failed: {}", e.message, e)
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    }
}

private fun printUsage() {
    println("""
        utlxe — UTL-X Production Runtime Engine v$VERSION

        Usage: utlxe --bundle <path> [options]

        Options:
          --bundle, -b <path>    Path to .utlxp project bundle directory (required)
          --config, -c <path>    Path to engine.yaml config file (optional)
          --port,   -p <port>    Health endpoint port override (optional)
          --validate             Load and compile the bundle, then exit (no processing)
          --version, -v          Print version and exit
          --help, -h             Print this help and exit

        The engine loads transformations from the bundle, connects pipes,
        and processes messages until a shutdown signal is received.

        If the health port is already in use, the engine automatically tries
        up to 10 higher port numbers before failing.

        Phase 1: single transformation, stdin/stdout pipes, TEMPLATE strategy.
    """.trimIndent())
}

private fun exitWithError(message: String): Nothing {
    System.err.println("Error: $message")
    System.err.println("Run 'utlxe --help' for usage information.")
    exitProcess(1)
}
