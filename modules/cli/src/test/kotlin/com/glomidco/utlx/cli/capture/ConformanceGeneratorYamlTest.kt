package com.glomidco.utlx.cli.capture

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B41 / B42 — the capture writer's output must be **parseable** YAML that asserts the **right thing**.
 *
 * Two defects shipped here because nothing tested this writer, and both were invisible for the same
 * reason: the conformance runner swallows a fixture it cannot load (`Error loading …`, return
 * `None`, skip), so a malformed fixture is neither a pass nor a failure — it simply vanishes from
 * the total. Seven fixtures were in that state, in a corpus whose headline figure is a count of
 * assertions.
 *
 * - **B41** — quotes were escaped but backslashes were not, so a captured value carrying
 *   JSON-escaped content emitted `\\"`, which YAML reads as a backslash followed by the end of the
 *   scalar. Escaping order is the whole bug.
 * - **B42** — the expected field was written as the output **text**, but the runner compares a
 *   string `expected.data` as the expected **value** (it only re-parses a multi-line `{`/`[`
 *   block). For a transformation whose output document is a JSON string the text is one escaping
 *   level deeper than the value, so the fixture failed as soon as it could be run.
 *
 * Every test here therefore does the same thing the runner does: parse the emitted YAML and compare
 * the decoded value. Asserting the emitted *string* would re-encode the bug rather than catch it.
 */
class ConformanceGeneratorYamlTest {

    private val yaml = ObjectMapper(YAMLFactory())

    private fun capture(input: String, output: String, fmt: String = "json") = CapturedTest(
        id = "deadbeef",
        timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        transformation = "%utlx 1.0\ninput $fmt\noutput $fmt\n---\n\$input\n",
        inputData = input,
        inputFormat = fmt,
        outputData = output,
        outputFormat = fmt,
        success = true,
        durationMs = 1
    )

    @Suppress("UNCHECKED_CAST")
    private fun fields(test: CapturedTest): Pair<Any?, Any?> {
        val text = ConformanceGenerator.generateYaml(test)
        val doc = yaml.readValue(text, Map::class.java) as Map<String, Any?>
        val input = (doc["input"] as Map<String, Any?>)["data"]
        val expected = (doc["expected"] as Map<String, Any?>)["data"]
        return input to expected
    }

    /** A JSON document that is a string: `"..."` with the inner quotes escaped. */
    private fun jsonStringDoc(content: String) = "\"" + content.replace("\"", "\\\"") + "\""

    // ── B41: it has to parse at all ──

    @Test
    fun `a value containing JSON-escaped content still parses`() {
        // The exact shape that produced seven unloadable fixtures.
        val payload = jsonStringDoc("{\"name\": \"John\", \"age\": 30}")
        val (input, _) = fields(capture(payload, "{}"))
        assertEquals(payload, input, "input text did not survive the YAML round trip")
    }

    @Test
    fun `a value containing a bare backslash still parses`() {
        val payload = jsonStringDoc("a\\b")
        val (input, _) = fields(capture(payload, "{}"))
        assertEquals(payload, input)
    }

    @Test
    fun `every emitted document parses as YAML`() {
        val payloads = listOf(
            jsonStringDoc("hello"), jsonStringDoc("\"quoted\""), """{"a": 1}""", "[1,2,3]",
            "42", "true", jsonStringDoc("back\\slash"), jsonStringDoc("tab\tand\nnewline")
        )
        for (p in payloads) {
            val text = ConformanceGenerator.generateYaml(capture(p, p))
            runCatching { yaml.readValue(text, Map::class.java) }
                .onFailure { throw AssertionError("emitted unparseable YAML for payload $p: ${it.message}") }
        }
    }

    // ── B42: the expected field must be the VALUE the runner compares ──

    @Test
    fun `a JSON string output is written as its decoded value`() {
        // Engine stdout for upper($input) on a quoted string: a 17-character JSON document
        // whose VALUE is the 13-character string `"HELLO WORLD"`. Built from pieces rather than
        // written as a literal, because miscounting the quotes silently yields a malformed
        // document and the generator then (correctly) declines to decode it.
        val Q = "\""                       // a single quote character
        val BS = "\\"                      // a single backslash character
        val value = Q + "HELLO WORLD" + Q
        val stdout = Q + BS + Q + "HELLO WORLD" + BS + Q + Q
        assertEquals(13, value.length)
        assertEquals(17, stdout.length)
        val (_, expected) = fields(capture(Q + BS + Q + "hello world" + BS + Q + Q, stdout))
        // The runner compares json.loads(stdout), so that is what the fixture must hold.
        assertEquals(value, expected)
    }

    @Test
    fun `a JSON string output carrying JSON is written as its decoded value`() {
        // Engine stdout for renderJson(...) — a JSON string whose content is JSON.
        // The decoded value is a STRING that looks like JSON — it must not become a mapping.
        val value = """{"name":"John"}"""
        val stdout = jsonStringDoc(value)
        val (_, expected) = fields(capture("""{"name": "John"}""", stdout))
        assertEquals(value, expected)
        assertTrue(expected is String, "a decoded JSON-looking string must stay a string")
    }

    @Test
    fun `an object output is unchanged — it already decodes to the compared value`() {
        val (_, expected) = fields(capture("""{"a": 1}""", """{"a": 1}"""))
        assertTrue(expected is Map<*, *>, "expected an inline mapping, got ${expected?.javaClass}")
        assertEquals(1, (expected as Map<*, *>)["a"])
    }

    @Test
    fun `a non-JSON output format is left as text`() {
        // CSV and XML are compared as text, so decoding would be wrong for them.
        val csv = "a,b\n1,2\n"
        val (_, expected) = fields(capture("x", csv, fmt = "csv"))
        assertEquals(csv.trimEnd('\n'), (expected as String).trimEnd('\n'))
    }

    // ── B43: the round-trip property, over values chosen to break each branch ──

    /**
     * `input.data` must survive the YAML round trip **exactly**.
     *
     * This is the property that matters and the one that was missing: the three earlier defects
     * (B41 escaping, B42 text-vs-value, B43 lossy encodings) were all discoverable from it, and
     * none of them was discoverable from a test that only checked the emitted string.
     *
     * The values are chosen one per branch of the writer, including the ones that silently
     * corrupted: a leading-zero integer that YAML 1.1 reads as **octal**, an exponent form it reads
     * as a **string**, a carriage return that a quoted scalar **drops**, and multi-line content a
     * block scalar **gains a newline** on.
     *
     * Numeric and object/array values are deliberately absent: they are emitted as YAML numbers and
     * structures rather than as text, which is what the runner needs, so they are asserted by value
     * in the two tests that follow.
     */
    @Test
    fun `input data survives the YAML round trip exactly`() {
        val q = "\""
        val bs = "\\"
        val cases = linkedMapOf(
            "plain" to "hello",
            "empty" to "",
            "quote inside" to q + "quoted" + q,
            "backslash only" to bs,
            "json-escaped content" to q + "{" + bs + q + "name" + bs + q + ": 1}" + q,
            "multi-line, no trailing LF" to "a\nb",
            "multi-line, trailing LF" to "a\nb\n",
            "blank line inside" to "a\n\nb",
            "trailing spaces on a line" to "a   \nb",
            "tab inside" to "a\tb",
            "carriage return inside" to "a\rb",
            "leading and trailing spaces" to "  pad  ",
            "YAML 1.1 booleanish" to "yes",
            "null text" to "null",
            "tilde" to "~",
            "hash" to "a # b",
            "colon space" to "a: b",
            "unicode" to "caf\u00e9 \u4e2d",
            "malformed json doc" to q + bs + q + "HELLO" + bs + q
        )
        for ((label, v) in cases) {
            val (input, _) = fields(capture(v, "{}"))
            assertEquals(v, input, "input did not round-trip for case: $label")
        }
    }

    /**
     * A numeric form YAML 1.1 would mis-read is normalised to the same **number**.
     *
     * The text deliberately does not survive here, and that is the fix rather than a shortfall:
     *
     * | captured | YAML 1.1 used to read | now emitted as | reads back as |
     * |---|---|---|---|
     * | `0123` | `83` — **octal** | `123` | `123` |
     * | `007` | `7` | `7` | `7` |
     * | `1e5` | the **string** `"1e5"` | `100000` | `100000` |
     *
     * Neither `0123` nor `1e5` is valid JSON for a number in the first place, so there is no text
     * worth preserving; what matters is that the fixture carries the right *value* and the right
     * *type*. Quoting them instead would turn a number into a string, which is a different defect.
     */
    @Test
    fun `a numeric form YAML would mis-read is normalised to the same number`() {
        for ((captured, expectedValue) in listOf(
            "0123" to 123L, "007" to 7L, "1e5" to 100000L, "-0" to 0L, "5." to 5L
        )) {
            val (input, _) = fields(capture(captured, "{}"))
            assertTrue(input is Number, "$captured should stay a number, got ${input?.javaClass}")
            assertEquals(expectedValue, (input as Number).toLong(),
                "$captured must keep its value")
        }
        // The canonical forms are untouched, so normalisation cannot creep.
        for (canonical in listOf("42", "-7", "3.14", "0")) {
            val (input, _) = fields(capture(canonical, "{}"))
            assertEquals(canonical, input.toString(), "$canonical should pass through unchanged")
        }
    }

    /**
     * An object or array is emitted raw, so the *text* changes while the *value* does not — which
     * is what the runner compares. Asserted separately so the round-trip test above can demand
     * exact text everywhere else.
     */
    @Test
    fun `an object or array round-trips as an equal structure, not as text`() {
        val (obj, _) = fields(capture("""{"a": 1, "b": [2, 3]}""", "{}"))
        assertTrue(obj is Map<*, *>, "expected a mapping, got ${obj?.javaClass}")
        assertEquals(1, (obj as Map<*, *>)["a"])
        assertEquals(listOf(2, 3), obj["b"])

        val (arr, _) = fields(capture("[1, 2]", "{}"))
        assertEquals(listOf(1, 2), arr)
    }

    /**
     * A malformed JSON output document must be left alone, not half-decoded.
     *
     * This shape — one quote short of a valid document — is what a mis-counted literal produces,
     * and the decode path must decline rather than guess. Worth pinning because the decode is
     * wrapped in a `runCatching` whose failure branch is otherwise invisible.
     */
    @Test
    fun `a malformed JSON output document is left as text`() {
        val q = "\""
        val bs = "\\"
        val malformed = q + bs + q + "HELLO" + bs + q      // no closing quote
        val (_, expected) = fields(capture("{}", malformed))
        assertEquals(malformed, expected, "a document that cannot be decoded must survive verbatim")
    }

    // ── The asymmetry is deliberate: input stays text, expected becomes value ──

    @Test
    fun `input keeps the text form even when expected is decoded`() {
        val Q = "\""                       // a single quote character
        val BS = "\\"                      // a single backslash character
        val inputText = Q + BS + Q + "hello" + BS + Q + Q
        val stdout = Q + BS + Q + "HELLO" + BS + Q + Q
        val (input, expected) = fields(capture(inputText, stdout))
        assertEquals(inputText, input, "input must stay as JSON text — the runner uses it as-is")
        assertEquals(Q + "HELLO" + Q, expected, "expected must be the decoded value")
    }
}
