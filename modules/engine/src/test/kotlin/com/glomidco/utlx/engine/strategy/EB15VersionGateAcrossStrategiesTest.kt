package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.engine.config.TransformConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * EB15 — the `%utlx` **version gate** was enforced only on paths that reached the interpreter.
 *
 * `enforceSupportedVersion` lived inside `Interpreter`, so a *compilable* script never met it:
 *
 * ```
 * %utlx 2.0        TEMPLATE -> RuntimeError "unsupported %utlx version '2.0'"
 * input json       COPY     -> ran it, output {"x": 1}
 * output json      COMPILED -> ran it, output {"x": 1}
 * ---
 * ${'$'}input
 * ```
 *
 * That is **fail-open on a version gate**, on the engine's default strategy. A script declaring a
 * version this build does not understand was executed as though it were 1.0 — so any 2.0 semantics
 * it relied on were silently reinterpreted.
 *
 * It had been catalogued as `RUNS_ONLY_ON_TEMPLATE` ("only TEMPLATE can run it"), which reads as a
 * TEMPLATE limitation and is why it sat undiagnosed. The truth was the reverse: TEMPLATE was the
 * only strategy that was right.
 *
 * The version is a **static property of the header**, so the rule now lives in
 * `LanguageVersions.unsupportedReason` and every strategy asks at `initialize` — the earliest point
 * the header is known. The interpreter delegates to the same function, so the rule cannot fork
 * again.
 */
class EB15VersionGateAcrossStrategiesTest {

    private fun strategies(): List<ExecutionStrategy> =
        listOf(TemplateStrategy(), CopyStrategy(), CompiledStrategy())

    private fun run(s: ExecutionStrategy, src: String): String {
        val cfg = TransformConfig(strategy = s.name)
        s.initialize(src, cfg)
        return s.execute("""{"x": 1}""").output
    }

    private fun script(version: String) = """%utlx $version
input json
output json
---
${'$'}input
"""

    @Test
    fun `an unsupported version is rejected on every strategy`() {
        for (s in strategies()) {
            try {
                val out = run(s, script("2.0"))
                fail("${s.name}: %utlx 2.0 must be rejected, but it ran and produced: $out")
            } catch (e: Throwable) {
                assertTrue(
                    (e.message ?: "").contains("unsupported %utlx version '2.0'"),
                    "${s.name}: expected the unsupported-version error, got: ${e::class.simpleName}: ${e.message}"
                )
            }
        }
    }

    @Test
    fun `the strategies report an unsupported version identically`() {
        val messages = strategies().map { s ->
            runCatching { run(s, script("2.0")) }.exceptionOrNull()?.message
                ?: fail("${s.name} did not reject")
        }
        assertEquals(1, messages.distinct().size, "strategies disagreed on the message: $messages")
        // Same exception type too, so a caller can catch one thing.
        val types = strategies().map { s ->
            runCatching { run(s, script("2.0")) }.exceptionOrNull()!!::class.simpleName
        }
        assertEquals(1, types.distinct().size, "strategies threw different types: $types")
    }

    @Test
    fun `a nonsense version is rejected too`() {
        for (v in listOf("9.9", "0.1", "1.2")) {
            for (s in strategies()) {
                val e = runCatching { run(s, script(v)) }.exceptionOrNull()
                    ?: fail("${s.name}: %utlx $v must be rejected")
                assertTrue(
                    (e.message ?: "").contains("unsupported %utlx version"),
                    "${s.name}/$v: got ${e.message}"
                )
            }
        }
    }

    @Test
    fun `supported versions still run on every strategy`() {
        for (v in listOf("1.0", "1.1")) {
            for (s in strategies()) {
                val out = run(s, script(v))
                assertTrue(out.contains("\"x\""), "${s.name}/$v: expected output, got: $out")
            }
        }
    }
}
