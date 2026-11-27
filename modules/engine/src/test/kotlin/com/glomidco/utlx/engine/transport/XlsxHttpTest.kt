package com.glomidco.utlx.engine.transport

import com.fasterxml.jackson.databind.ObjectMapper
import com.glomidco.utlx.engine.UtlxEngine
import com.glomidco.utlx.engine.config.EngineConfig
import com.glomidco.utlx.engine.proto.LoadTransformationRequest
import com.glomidco.utlx.formats.xlsx.XlsxParser
import com.glomidco.utlx.formats.xlsx.XlsxSerializer
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XlsxHttpTest {
    @Test
    fun `raw HTTP and JSON envelopes preserve workbook bytes`() {
        val engine = UtlxEngine(EngineConfig().withHealthPort(0))
        engine.initializeEmpty()
        val port = ServerSocket(0).use { it.localPort }
        val transport = HttpTransport(engine, port = port)
        val thread = Thread { transport.start(engine.registry) }.apply { isDaemon = true; start() }
        val mapper = ObjectMapper()
        try {
            load(engine, "write", "%utlx 1.0\ninput json\noutput xlsx\n---\n\$input")
            load(engine, "read", "%utlx 1.0\ninput xlsx\noutput json\n---\n\$input")
            var ready = false
            for (attempt in 0 until 100) {
                try {
                    val connection = URL("http://127.0.0.1:$port/api/health").openConnection()
                        as HttpURLConnection
                    connection.connectTimeout = 100
                    connection.readTimeout = 100
                    try {
                        if (connection.responseCode == 200) { ready = true; break }
                    } finally {
                        connection.disconnect()
                    }
                } catch (_: java.io.IOException) {
                    Thread.sleep(25)
                }
            }
            assertTrue(ready, "HTTP transport did not become ready.")
            val json = "[{\"Name\":\"Reserve\"}]".toByteArray()
            val written = post(port, "/api/transformations/write/run", json, "application/json")
            assertEquals(200, written.status)
            assertTrue(written.contentType.startsWith(XlsxSerializer.CONTENT_TYPE))
            assertEquals(1, XlsxParser.parse(written.body).elements.size)

            val read = post(port, "/api/transformations/read/run", written.body, XlsxSerializer.CONTENT_TYPE)
            assertEquals(200, read.status)
            assertEquals("Reserve", mapper.readTree(read.body)[0]["Name"].asText())

            val encoded = mapper.writeValueAsBytes(mapOf(
                "payload" to Base64.getEncoder().encodeToString(written.body),
                "payloadEncoding" to "base64",
                "contentType" to XlsxSerializer.CONTENT_TYPE
            ))
            val envelope = post(port, "/api/execute/read", encoded, "application/json")
            assertEquals(200, envelope.status)
            val value = mapper.readTree(envelope.body)
            assertEquals("Reserve", mapper.readTree(value["output"].asText())[0]["Name"].asText())

            val request = mapper.writeValueAsBytes(mapOf("payload" to String(json)))
            val output = mapper.readTree(post(port, "/api/execute/write", request, "application/json").body)
            assertEquals("base64", output["outputEncoding"].asText())
            assertEquals(XlsxSerializer.CONTENT_TYPE, output["outputContentType"].asText())
            assertEquals(1, XlsxParser.parse(Base64.getDecoder().decode(output["output"].asText())).elements.size)

            val invalid = post(
                port, "/api/transformations/read/run", byteArrayOf(0, -1, 1), XlsxSerializer.CONTENT_TYPE
            )
            assertEquals(422, invalid.status)
            val invalidBase64 = mapper.writeValueAsBytes(mapOf(
                "payload" to "invalid!",
                "payloadEncoding" to "base64",
                "contentType" to XlsxSerializer.CONTENT_TYPE
            ))
            assertEquals(400, post(port, "/api/execute/read", invalidBase64, "application/json").status)
        } finally {
            transport.stop()
            engine.stop()
            thread.join(5000)
        }
    }

    private fun load(engine: UtlxEngine, name: String, source: String) {
        val response = TransportHandlers.handleLoadTransformation(
            LoadTransformationRequest.newBuilder().setTransformationId(name)
                .setUtlxSource(source).setStrategy("COMPILED").build(),
            engine
        )
        assertTrue(response.success, response.error)
    }

    private data class Response(val status: Int, val contentType: String, val body: ByteArray)

    private fun post(port: Int, path: String, bytes: ByteArray, contentType: String): Response {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.readTimeout = 15000
            connection.setRequestProperty("Content-Type", contentType)
            connection.doOutput = true
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val body = if (status < 400) connection.inputStream else connection.errorStream
            return Response(
                status,
                connection.getHeaderField("Content-Type").orEmpty(),
                body?.use { it.readBytes() } ?: byteArrayOf()
            )
        } finally {
            connection.disconnect()
        }
    }
}
