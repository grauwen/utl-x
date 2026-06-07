// modules/cli/src/test/kotlin/org/apache/utlx/cli/ConvertCommandTest.kt
package org.apache.utlx.cli

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #5 — `convert`: quick, script-free format conversion. It is identity mode (smart flip /
 * --from / --to) with --input/--output file handling, surfaced as a discoverable verb. These
 * integration tests spawn the assembled JAR (path from the `utlx.cli.jar` system property set by
 * build.gradle.kts) and assert on file output / stdout / exit code.
 */
class ConvertCommandTest {

    @TempDir
    lateinit var tempDir: Path

    private data class CliResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun runCli(vararg args: String, stdin: String? = null, timeoutSec: Long = 30): CliResult {
        val jar = System.getProperty("utlx.cli.jar")
        assumeTrue(jar != null && File(jar).exists()) {
            "utlx.cli.jar not set (run via Gradle: ./gradlew :modules:cli:test)"
        }
        val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
        val proc = ProcessBuilder(listOf(javaBin, "-jar", jar!!) + args)
            .redirectErrorStream(false).start()
        proc.outputStream.use { os -> if (stdin != null) os.write(stdin.toByteArray()) }
        val out = StringBuilder(); val err = StringBuilder()
        val tOut = Thread { proc.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
        val tErr = Thread { proc.errorStream.bufferedReader().forEachLine { err.appendLine(it) } }
        tOut.start(); tErr.start()
        assertTrue(proc.waitFor(timeoutSec, TimeUnit.SECONDS), "CLI did not terminate")
        tOut.join(5000); tErr.join(5000)
        return CliResult(proc.exitValue(), out.toString(), err.toString())
    }

    @Test
    fun `convert --input xml --output json (the issue's exact request)`() {
        val xml = tempDir.resolve("some.xml").toFile()
        xml.writeText("<order><id>7</id><cust>Acme</cust></order>")
        val out = tempDir.resolve("out.json").toFile()

        val r = runCli("convert", "--input", xml.absolutePath, "--output", out.absolutePath)

        assertEquals(0, r.exitCode, "stderr:\n${r.stderr}")
        assertTrue(out.exists(), "output file should be written")
        val json = out.readText()
        assertTrue(json.contains("\"order\""), "should be JSON. got:\n$json")
        assertTrue(json.contains("\"id\": 7"), "should carry the data. got:\n$json")
    }

    @Test
    fun `convert with explicit --to and -o file`() {
        val csv = tempDir.resolve("data.csv").toFile()
        csv.writeText("a,b\n1,2\n3,4\n")
        val out = tempDir.resolve("out.yaml").toFile()

        val r = runCli("convert", "-i", csv.absolutePath, "--from", "csv", "--to", "yaml", "-o", out.absolutePath)

        assertEquals(0, r.exitCode, "stderr:\n${r.stderr}")
        val yaml = out.readText()
        assertTrue(yaml.contains("a: 1"), "should be YAML rows. got:\n$yaml")
    }

    @Test
    fun `convert from stdin with --to`() {
        val r = runCli("convert", "--to", "yaml", stdin = "<a><b>1</b></a>")
        assertEquals(0, r.exitCode, "stderr:\n${r.stderr}")
        assertTrue(r.stdout.contains("b: 1"), "stdout should be YAML. got:\n${r.stdout}")
    }

    @Test
    fun `conv alias does the JSON to XML smart flip`() {
        val r = runCli("conv", stdin = """{"id":7}""")
        assertEquals(0, r.exitCode, "stderr:\n${r.stderr}")
        assertTrue(r.stdout.contains("<id>7</id>"), "stdout should be XML. got:\n${r.stdout}")
    }
}
