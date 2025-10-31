// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/transport/Transport.kt
package org.apache.utlx.daemon.transport

import org.apache.utlx.daemon.protocol.JsonRpcRequest
import org.apache.utlx.daemon.protocol.JsonRpcResponse

/**
 * Transport interface for JSON-RPC 2.0 communication
 *
 * Implementations:
 * - StdioTransport: Communicates via stdin/stdout (standard for IDE plugins)
 * - SocketTransport: Communicates via TCP socket (for remote access)
 *
 * Both implementations use the same LSP protocol with Content-Length headers.
 */
interface Transport {

    /**
     * Start the transport and begin listening for messages
     *
     * @param messageHandler Callback invoked for each received request
     */
    fun start(messageHandler: (JsonRpcRequest) -> JsonRpcResponse)

    /**
     * Send a response back to the client
     *
     * @param response The JSON-RPC response to send
     */
    fun sendResponse(response: JsonRpcResponse)

    /**
     * Send a notification to the client (request with no id)
     *
     * @param request The JSON-RPC notification to send
     */
    fun sendNotification(request: JsonRpcRequest)

    /**
     * Stop the transport and cleanup resources
     */
    fun stop()

    /**
     * Check if the transport is currently running
     */
    fun isRunning(): Boolean
}

/**
 * LSP Message Format
 *
 * Messages use Content-Length header:
 * ```
 * Content-Length: 123\r\n
 * \r\n
 * {"jsonrpc":"2.0","id":1,"method":"initialize","params":{...}}
 * ```
 */
object LspMessageFormat {

    private const val CONTENT_LENGTH_HEADER = "Content-Length: "
    private const val HEADER_DELIMITER = "\r\n\r\n"
    private const val LINE_DELIMITER = "\r\n"

    /**
     * Encode a JSON message with LSP headers
     *
     * @param json The JSON string to encode
     * @return The encoded message with Content-Length header
     */
    fun encode(json: String): String {
        val contentBytes = json.toByteArray(Charsets.UTF_8)
        val contentLength = contentBytes.size

        return buildString {
            append(CONTENT_LENGTH_HEADER)
            append(contentLength)
            append(HEADER_DELIMITER)
            append(json)
        }
    }

    /**
     * Decode an LSP message from a reader
     *
     * Reads headers, then reads the content based on Content-Length
     *
     * @param reader Function to read a line from the input
     * @param read Function to read exact number of bytes
     * @return The decoded JSON string, or null if EOF
     */
    fun decode(reader: () -> String?, read: (Int) -> String?): String? {
        // Read headers
        var contentLength: Int? = null

        while (true) {
            val line = reader() ?: return null

            if (line.isEmpty() || line == "\r\n") {
                // Empty line signals end of headers
                break
            }

            if (line.startsWith(CONTENT_LENGTH_HEADER)) {
                contentLength = line.substring(CONTENT_LENGTH_HEADER.length).trim().toIntOrNull()
            }
        }

        if (contentLength == null) {
            throw IllegalStateException("Missing Content-Length header")
        }

        // Read content
        return read(contentLength)
    }

    /**
     * Parse Content-Length from a header line
     */
    fun parseContentLength(headerLine: String): Int? {
        if (!headerLine.startsWith(CONTENT_LENGTH_HEADER)) {
            return null
        }
        return headerLine.substring(CONTENT_LENGTH_HEADER.length).trim().toIntOrNull()
    }
}
