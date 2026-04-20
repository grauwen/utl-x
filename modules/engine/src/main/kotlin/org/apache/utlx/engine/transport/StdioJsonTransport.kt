package org.apache.utlx.engine.transport

import org.apache.utlx.engine.pipe.*
import org.apache.utlx.engine.registry.TransformationRegistry
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * StdioJsonTransport — line-delimited JSON over stdin/stdout.
 *
 * This is the original UTLXe transport mode (backward compatible).
 * Used for standalone operation and shell scripting.
 *
 * Protocol: one JSON line per message on stdin, one JSON line per result on stdout.
 * Single transformation only (uses first registered transformation).
 * No dynamic loading — all transforms must be loaded from bundle before start.
 */
class StdioJsonTransport : TransportServer {

    private val logger = LoggerFactory.getLogger(StdioJsonTransport::class.java)
    private val running = AtomicBoolean(false)
    private var inputPipe: InputPipe? = null
    private var outputPipe: OutputPipe? = null

    override val supportsDynamicLoading = false

    override fun start(registry: TransformationRegistry) {
        val transformation = registry.list().firstOrNull()
            ?: throw IllegalStateException("No transformations registered. StdioJsonTransport requires bundle-loaded transforms.")

        inputPipe = StdioInputPipe()
        outputPipe = StdioOutputPipe()
        running.set(true)

        val input = inputPipe!!
        val output = outputPipe!!

        logger.info("StdioJsonTransport: processing messages using transformation '{}'", transformation.name)

        while (running.get()) {
            val message = input.tryRead() ?: break // EOF

            try {
                val result = transformation.strategy.execute(
                    String(message.payload, Charsets.UTF_8)
                )
                val outputMessage = Message(
                    correlationId = message.correlationId,
                    payload = result.output.toByteArray(Charsets.UTF_8),
                    contentType = "application/json",
                    headers = message.headers
                )
                output.write(outputMessage)
            } catch (e: Exception) {
                logger.error("Error processing message: {}", e.message, e)
                val errorMessage = Message(
                    correlationId = message.correlationId,
                    payload = """{"error": "${e.message?.replace("\"", "\\\"") ?: "unknown"}"}""".toByteArray(Charsets.UTF_8),
                    contentType = "application/json"
                )
                output.write(errorMessage)
            }
        }
    }

    override fun stop() {
        running.set(false)
        inputPipe?.close()
        outputPipe?.close()
        logger.info("StdioJsonTransport stopped")
    }
}
