package org.apache.utlx.conformance.lsp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * JSON-RPC 2.0 client for communicating with LSP daemon
 */
class JsonRpcClient(
    private val daemonProcess: Process
) {
    private val logger = LoggerFactory.getLogger(JsonRpcClient::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private val writer = BufferedWriter(OutputStreamWriter(daemonProcess.outputStream))
    private val reader = BufferedReader(InputStreamReader(daemonProcess.inputStream))

    private val requestId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, (Map<String, Any?>) -> Unit>()
    private val notifications = mutableListOf<Map<String, Any?>>()

    private var readerThread: Thread? = null
    private var running = false

    /**
     * Start reading responses from daemon
     */
    fun start() {
        running = true
        readerThread = Thread {
            try {
                while (running) {
                    val message = readMessage()
                    if (message != null) {
                        handleMessage(message)
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    logger.error("Error reading from daemon", e)
                }
            }
        }
        readerThread?.start()
    }

    /**
     * Stop the client
     */
    fun stop() {
        running = false
        readerThread?.interrupt()
        writer.close()
        reader.close()
    }

    /**
     * Send a request and wait for response
     */
    fun sendRequest(method: String, params: Any?): Map<String, Any?> {
        val id = requestId.getAndIncrement()
        val request = buildRequest(id, method, params)

        var response: Map<String, Any?>? = null
        val responseLock = Object()

        pendingRequests[id] = { resp ->
            synchronized(responseLock) {
                response = resp
                responseLock.notifyAll()
            }
        }

        sendMessage(request)

        // Wait for response (with timeout)
        synchronized(responseLock) {
            var waited = 0L
            while (response == null && waited < 5000) {
                responseLock.wait(100)
                waited += 100
            }
        }

        pendingRequests.remove(id)

        return response ?: throw RuntimeException("Request timeout for method: $method")
    }

    /**
     * Send a notification (no response expected)
     */
    fun sendNotification(method: String, params: Any?) {
        val notification = buildNotification(method, params)
        sendMessage(notification)
    }

    /**
     * Get all received notifications and clear the list
     */
    fun getNotifications(): List<Map<String, Any?>> {
        synchronized(notifications) {
            val result = notifications.toList()
            notifications.clear()
            return result
        }
    }

    /**
     * Wait for a specific notification
     */
    fun waitForNotification(method: String, timeoutMs: Long = 2000): Map<String, Any?>? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            synchronized(notifications) {
                val notification = notifications.find { it["method"] == method }
                if (notification != null) {
                    notifications.remove(notification)
                    return notification
                }
            }
            Thread.sleep(50)
        }
        return null
    }

    private fun buildRequest(id: Int, method: String, params: Any?): Map<String, Any?> {
        return mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "method" to method,
            "params" to params
        )
    }

    private fun buildNotification(method: String, params: Any?): Map<String, Any?> {
        return mapOf(
            "jsonrpc" to "2.0",
            "method" to method,
            "params" to params
        )
    }

    private fun sendMessage(message: Map<String, Any?>) {
        val json = objectMapper.writeValueAsString(message)
        val contentLength = json.toByteArray(Charsets.UTF_8).size

        logger.debug("Sending: $json")

        writer.write("Content-Length: $contentLength\r\n")
        writer.write("\r\n")
        writer.write(json)
        writer.flush()
    }

    private fun readMessage(): Map<String, Any?>? {
        // Read headers
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.isEmpty() || line == "\r") break

            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                headers[parts[0].trim()] = parts[1].trim()
            }
        }

        val contentLength = headers["Content-Length"]?.toIntOrNull()
            ?: return null

        // Read content
        val buffer = CharArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val read = reader.read(buffer, totalRead, contentLength - totalRead)
            if (read == -1) return null
            totalRead += read
        }

        val json = String(buffer)
        logger.debug("Received: $json")

        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
    }

    private fun handleMessage(message: Map<String, Any?>) {
        val id = message["id"]

        if (id != null && id is Number) {
            // Response to a request
            val callback = pendingRequests[id.toInt()]
            callback?.invoke(message)
        } else {
            // Notification from server
            synchronized(notifications) {
                notifications.add(message)
            }
        }
    }
}
