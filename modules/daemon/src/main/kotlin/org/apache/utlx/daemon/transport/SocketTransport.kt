// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/transport/SocketTransport.kt
package org.apache.utlx.daemon.transport

import org.apache.utlx.daemon.protocol.*
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Socket Transport for JSON-RPC 2.0 / LSP communication
 *
 * Listens on a TCP port for connections, reads JSON-RPC messages, writes responses.
 * Uses LSP Content-Length header format.
 *
 * This transport is useful for:
 * - Remote debugging
 * - Multiple simultaneous clients
 * - Testing with command-line tools
 */
class SocketTransport(private val port: Int) : Transport {

    private val logger = LoggerFactory.getLogger(SocketTransport::class.java)
    private val parser = JsonRpcParser()
    private val running = AtomicBoolean(false)

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    override fun start(messageHandler: (JsonRpcRequest) -> JsonRpcResponse) {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Socket transport already running")
            return
        }

        try {
            serverSocket = ServerSocket(port)
            logger.info("Socket transport listening on port $port")

            // Accept one client connection (for now, single client mode)
            logger.info("Waiting for client connection...")
            clientSocket = serverSocket!!.accept()
            logger.info("Client connected from: ${clientSocket!!.remoteSocketAddress}")

            reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream(), Charsets.UTF_8))
            writer = PrintWriter(OutputStreamWriter(clientSocket!!.getOutputStream(), Charsets.UTF_8), true)

            // Message loop
            while (running.get()) {
                val message = readMessage() ?: break

                logger.debug("Received message: {}", message.take(200))

                try {
                    val jsonRpcMessage = parser.parseMessage(message)

                    when (jsonRpcMessage) {
                        is JsonRpcMessage.Request -> {
                            val request = jsonRpcMessage.request

                            val response = try {
                                messageHandler(request)
                            } catch (e: Exception) {
                                logger.error("Error handling request: ${request.method}", e)
                                JsonRpcResponse.internalError(
                                    request.id,
                                    "Internal error: ${e.message}"
                                )
                            }

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
            logger.error("Socket transport error", e)
        } finally {
            cleanup()
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
        logger.info("Stopping socket transport...")
        running.set(false)
        cleanup()
    }

    override fun isRunning(): Boolean = running.get()

    private fun readMessage(): String? {
        val r = reader ?: return null

        // Read headers
        var contentLength: Int? = null

        while (true) {
            val line = try {
                r.readLine()
            } catch (e: Exception) {
                logger.debug("EOF or error reading headers", e)
                return null
            }

            if (line == null) {
                return null
            }

            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
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
            val read = r.read(buffer, totalRead, contentLength - totalRead)
            if (read == -1) {
                logger.error("Unexpected EOF while reading content")
                return null
            }
            totalRead += read
        }

        return String(buffer)
    }

    private fun sendMessage(json: String) {
        val w = writer ?: return

        synchronized(w) {
            val encoded = LspMessageFormat.encode(json)
            w.print(encoded)
            w.flush()
            logger.debug("Sent message: {}", json.take(200))
        }
    }

    private fun cleanup() {
        try {
            writer?.close()
            reader?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            logger.error("Error during cleanup", e)
        } finally {
            writer = null
            reader = null
            clientSocket = null
            serverSocket = null
            running.set(false)
            logger.info("Socket transport stopped")
        }
    }
}
