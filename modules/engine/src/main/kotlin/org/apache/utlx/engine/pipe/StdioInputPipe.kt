package org.apache.utlx.engine.pipe

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class StdioInputPipe(
    inputStream: InputStream = System.`in`,
    override val name: String = "stdin"
) : InputPipe {

    private val logger = LoggerFactory.getLogger(StdioInputPipe::class.java)
    private val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
    private val closed = AtomicBoolean(false)

    override fun read(): Message {
        return tryRead() ?: throw IllegalStateException("Input pipe closed or EOF reached")
    }

    override fun tryRead(): Message? {
        if (closed.get()) return null

        return try {
            val line = reader.readLine() ?: run {
                logger.debug("EOF on stdin")
                closed.set(true)
                return null
            }

            if (line.isBlank()) return tryRead()  // skip blank lines

            Message(
                payload = line.toByteArray(Charsets.UTF_8),
                contentType = "application/json"
            )
        } catch (e: Exception) {
            if (!closed.get()) {
                logger.error("Error reading from stdin: {}", e.message)
            }
            null
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            logger.debug("Closing stdin pipe")
            try {
                reader.close()
            } catch (e: Exception) {
                logger.debug("Error closing stdin reader: {}", e.message)
            }
        }
    }
}
