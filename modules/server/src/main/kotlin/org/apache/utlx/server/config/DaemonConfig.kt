// modules/server/src/main/kotlin/org/apache/utlx/server/config/DaemonConfig.kt
package org.apache.utlx.server.config

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import java.io.File
import java.nio.file.Paths

/**
 * Configuration for UTL-X Daemon Server
 *
 * Supports multiple configuration sources with precedence:
 * 1. Command-line arguments (highest)
 * 2. Environment variables
 * 3. Config file (~/.utlx/config.yaml)
 * 4. Built-in defaults (lowest)
 */
data class DaemonConfig(
    val server: ServerConfig = ServerConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val mcp: McpConfig = McpConfig()
) {
    companion object {
        /**
         * Load configuration from all sources
         */
        fun load(
            configPath: String? = null,
            cliOverrides: Map<String, Any> = emptyMap()
        ): DaemonConfig {
            // TODO: Implement full Hoplite configuration loading
            // For now, use defaults with CLI overrides applied via map override
            return try {
                // Start with defaults
                var config = DaemonConfig()

                // Apply CLI overrides (simplified - just handle common cases)
                if (cliOverrides.isNotEmpty()) {
                    // TODO: Properly apply nested overrides
                    config = DaemonConfig()
                }

                config
            } catch (e: Exception) {
                // If config loading fails, return defaults
                println("Warning: Failed to load configuration, using defaults: ${e.message}")
                DaemonConfig()
            }
        }

        /**
         * Create default config file if it doesn't exist
         */
        fun createDefaultConfigFile() {
            val configDir = File("${System.getProperty("user.home")}/.utlx")
            val configFile = File(configDir, "config.yaml")

            if (!configFile.exists()) {
                configDir.mkdirs()
                configFile.writeText("""
                    # UTL-X Daemon Configuration File

                    server:
                      rest_api:
                        enabled: true
                        port: 7778
                        host: "0.0.0.0"
                        cors:
                          enabled: true
                          allowed_origins:
                            - "http://localhost:*"
                            - "https://claude.ai"

                      lsp:
                        enabled: true
                        transport: "stdio"
                        socket_port: 7777

                    logging:
                      level: "INFO"
                      file: "~/.utlx/logs/utlxd.log"

                    mcp:
                      tools_enabled: true
                      max_transform_size_mb: 10
                      timeout_seconds: 30
                """.trimIndent())

                println("Created default config file: ${configFile.absolutePath}")
            }
        }
    }
}

data class ServerConfig(
    val restApi: RestApiConfig = RestApiConfig(),
    val lsp: LspConfig = LspConfig()
)

data class RestApiConfig(
    val enabled: Boolean = true,
    val port: Int = 7778,
    val host: String = "0.0.0.0",
    val cors: CorsConfig = CorsConfig()
)

data class CorsConfig(
    val enabled: Boolean = true,
    val allowedOrigins: List<String> = listOf("http://localhost:*", "https://claude.ai")
)

data class LspConfig(
    val enabled: Boolean = true,
    val transport: String = "stdio",  // stdio | socket
    val socketPort: Int = 7777
)

data class LoggingConfig(
    val level: String = "INFO",  // DEBUG | INFO | WARN | ERROR
    val file: String? = null
)

data class McpConfig(
    val toolsEnabled: Boolean = true,
    val maxTransformSizeMb: Int = 10,
    val timeoutSeconds: Int = 30
)
