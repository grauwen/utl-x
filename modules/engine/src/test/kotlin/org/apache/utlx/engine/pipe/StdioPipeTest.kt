package org.apache.utlx.engine.pipe

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StdioPipeTest {

    @Test
    fun `StdioInputPipe reads line as message`() {
        val input = """{"name": "test"}""" + "\n"
        val stream = ByteArrayInputStream(input.toByteArray())
        val pipe = StdioInputPipe(inputStream = stream, name = "test-stdin")

        val message = pipe.tryRead()

        assertNotNull(message)
        assertEquals("""{"name": "test"}""", String(message.payload, Charsets.UTF_8))
        assertEquals("application/json", message.contentType)
        assertEquals("test-stdin", pipe.name)
    }

    @Test
    fun `StdioInputPipe returns null on EOF`() {
        val stream = ByteArrayInputStream(ByteArray(0))
        val pipe = StdioInputPipe(inputStream = stream)

        val message = pipe.tryRead()
        assertNull(message)
    }

    @Test
    fun `StdioInputPipe reads multiple lines`() {
        val input = """{"id":1}""" + "\n" + """{"id":2}""" + "\n" + """{"id":3}""" + "\n"
        val stream = ByteArrayInputStream(input.toByteArray())
        val pipe = StdioInputPipe(inputStream = stream)

        val messages = mutableListOf<Message>()
        var msg = pipe.tryRead()
        while (msg != null) {
            messages.add(msg)
            msg = pipe.tryRead()
        }

        assertEquals(3, messages.size)
        assertEquals("""{"id":1}""", String(messages[0].payload, Charsets.UTF_8))
        assertEquals("""{"id":2}""", String(messages[1].payload, Charsets.UTF_8))
        assertEquals("""{"id":3}""", String(messages[2].payload, Charsets.UTF_8))
    }

    @Test
    fun `StdioInputPipe skips blank lines`() {
        val input = """{"id":1}""" + "\n\n\n" + """{"id":2}""" + "\n"
        val stream = ByteArrayInputStream(input.toByteArray())
        val pipe = StdioInputPipe(inputStream = stream)

        val msg1 = pipe.tryRead()
        val msg2 = pipe.tryRead()

        assertNotNull(msg1)
        assertNotNull(msg2)
        assertEquals("""{"id":1}""", String(msg1.payload, Charsets.UTF_8))
        assertEquals("""{"id":2}""", String(msg2.payload, Charsets.UTF_8))
    }

    @Test
    fun `StdioInputPipe returns null after close`() {
        val input = """{"id":1}""" + "\n"
        val stream = ByteArrayInputStream(input.toByteArray())
        val pipe = StdioInputPipe(inputStream = stream)

        pipe.close()
        val message = pipe.tryRead()
        assertNull(message)
    }

    @Test
    fun `StdioOutputPipe writes message as line`() {
        val outputStream = ByteArrayOutputStream()
        val pipe = StdioOutputPipe(outputStream = outputStream, name = "test-stdout")

        val message = Message(
            payload = """{"result": "ok"}""".toByteArray(Charsets.UTF_8),
            contentType = "application/json"
        )
        pipe.write(message)

        val output = outputStream.toString(Charsets.UTF_8).trim()
        assertEquals("""{"result": "ok"}""", output)
        assertEquals("test-stdout", pipe.name)
    }

    @Test
    fun `StdioOutputPipe writes multiple messages`() {
        val outputStream = ByteArrayOutputStream()
        val pipe = StdioOutputPipe(outputStream = outputStream)

        pipe.write(Message(payload = "line1".toByteArray()))
        pipe.write(Message(payload = "line2".toByteArray()))

        val lines = outputStream.toString(Charsets.UTF_8).trim().lines()
        assertEquals(2, lines.size)
        assertEquals("line1", lines[0])
        assertEquals("line2", lines[1])
    }

    @Test
    fun `Message equality considers payload content`() {
        val msg1 = Message(payload = "hello".toByteArray(), correlationId = "1")
        val msg2 = Message(payload = "hello".toByteArray(), correlationId = "1")
        val msg3 = Message(payload = "world".toByteArray(), correlationId = "1")

        assertEquals(msg1, msg2)
        assert(msg1 != msg3)
    }
}
