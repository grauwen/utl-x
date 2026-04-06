package org.apache.utlx.engine.pipe

import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicBoolean

class StdioOutputPipe(
    outputStream: OutputStream = System.out,
    override val name: String = "stdout"
) : OutputPipe {

    private val logger = LoggerFactory.getLogger(StdioOutputPipe::class.java)
    private val writer = PrintWriter(outputStream, true, Charsets.UTF_8)
    private val closed = AtomicBoolean(false)

    override fun write(message: Message) {
        if (closed.get()) {
            logger.warn("Attempted to write to closed stdout pipe")
            return
        }

        val output = String(message.payload, Charsets.UTF_8)
        writer.println(output)
        writer.flush()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            logger.debug("Closing stdout pipe")
            writer.flush()
        }
    }
}
