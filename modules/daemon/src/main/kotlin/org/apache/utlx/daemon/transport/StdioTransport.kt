// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/transport/StdioTransport.kt
package org.apache.utlx.daemon.transport

import org.apache.utlx.daemon.protocol.*
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * STDIO Transport for JSON-RPC 2.0 / LSP communication
 *
 * Reads JSON-RPC messages from stdin, writes responses to stdout.
 * Uses LSP Content-Length header format.
 *
 * This is the standard transport for IDE integrations (VSCode, IntelliJ, etc.)
 */
class StdioTransport : Transport {

    private val logger = LoggerFactory.getLogger(StdioTransport::class.java)
    private val parser = JsonRpcParser()
    private val running = AtomicBoolean(false)

    private val reader = BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8))
    private val writer = PrintWriter(OutputStreamWriter(System.out, Charsets.UTF_8), true)

    override fun start(messageHandler: (JsonRpcRequest) -> JsonRpcResponse) {
        if (!running.compareAndSet(false, true)) {
            logger.warn("STDIO transport already running")
            return
        }

        logger.info("Starting STDIO transport...")

        try {
            while (running.get()) {
                // Read message with LSP headers
                val message = readMessage() ?: break

                logger.debug("Received message: {}", message.take(200))

                try {
                    // Parse JSON-RPC request
                    val jsonRpcMessage = parser.parseMessage(message)

                    when (jsonRpcMessage) {
                        is JsonRpcMessage.Request -> {
                            val request = jsonRpcMessage.request

                            // Handle request
                            val response = try {
                                messageHandler(request)
                            } catch (e: Exception) {
                                logger.error("Error handling request: ${request.method}", e)
                                JsonRpcResponse.internalError(
                                    request.id,
                                    "Internal error: ${e.message}",
                                    mapOf("exception" to e::class.simpleName)
                                )
                            }

                            // Send response (unless it's a notification)
                            if (!request.isNotification) {
                                sendResponse(response)
                            }
                        }

                        is JsonRpcMessage.Response -> {
                            logger.warn("Received response in server mode (unexpected)")
                        }
                    }
                } catch (e: JsonRpcParseException) {
                    logger.error("Failed to parse JSON-RPC message", e)
                    sendResponse(JsonRpcResponse.parseError(e.message ?: "Parse error"))
                }
            }
        } catch (e: Exception) {
            logger.error("STDIO transport error", e)
        } finally {
            logger.info("STDIO transport stopped")
            running.set(false)
        }
    }

    override fun sendResponse(response: JsonRpcResponse) {
        val json = parser.serializeResponse(response)
        sendMessage(json)
    }

    override fun sendNotification(request: JsonRpcRequest) {
        require(request.isNotification) { "Request must be a notification (id == null)" }
        val json = parser.serializeRequest(request)
        sendMessage(json)
    }

    override fun stop() {
        logger.info("Stopping STDIO transport...")
        running.set(false)
    }

    override fun isRunning(): Boolean = running.get()

    /**
     * Read a message from stdin using LSP Content-Length format
     *
     * Format:
     * ```
     * Content-Length: 123\r\n
     * \r\n
     * {"jsonrpc":"2.0",...}
     * ```
     */
    private fun readMessage(): String? {
        // Read headers
        var contentLength: Int? = null

        while (true) {
            val line = try {
                reader.readLine()
            } catch (e: Exception) {
                logger.debug("EOF or error reading headers", e)
                return null
            }

            if (line == null) {
                // EOF
                return null
            }

            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                // Empty line signals end of headers
                break
            }

            if (trimmed.startsWith("Content-Length:")) {
                contentLength = trimmed.substringAfter("Content-Length:").trim().toIntOrNull()
            }
        }

        if (contentLength == null) {
            logger.error("Missing Content-Length header")
            return null
        }

        // Read content
        val buffer = CharArray(contentLength)
        var totalRead = 0

        while (totalRead < contentLength) {
            val read = reader.read(buffer, totalRead, contentLength - totalRead)
            if (read == -1) {
                logger.error("Unexpected EOF while reading content")
                return null
            }
            totalRead += read
        }

        return String(buffer)
    }

    /**
     * Send a message to stdout using LSP Content-Length format
     */
    private fun sendMessage(json: String) {
        synchronized(writer) {
            val encoded = LspMessageFormat.encode(json)
            writer.print(encoded)
            writer.flush()
            logger.debug("Sent message: {}", json.take(200))
        }
    }
}
