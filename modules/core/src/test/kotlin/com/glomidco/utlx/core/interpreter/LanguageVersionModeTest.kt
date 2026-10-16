package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F21 — `%utlx` version-header handling + the engine "mode" (build-plan Phase 1).
 *
 * The engine reads the declared `%utlx` version and, before running the body, rejects:
 *   1. a version it does not understand at all (typo / future version), and
 *   2. a version above the engine's configured mode (`maxLanguageVersion`) — the 1.0-only ("UTLXe")
 *      deployment rejects `%utlx 1.1`.
 *
 * One multi-version engine; the mode is a construction-time switch, not a second artifact.
 */
class LanguageVersionModeTest {

    /** Run a trivial passthrough body under [version], on an engine capped at [maxVersion]. */
    private fun run(version: String, maxVersion: String? = null): UDM {
        val src = "%utlx $version\ninput json\noutput json\n---\n\$input"
        val tokens = Lexer(src).tokenize()
        val program = when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        val interpreter = if (maxVersion != null) Interpreter(maxLanguageVersion = maxVersion) else Interpreter()
        return interpreter.execute(program, UDM.Scalar(5.0)).toUDM()
    }

    private fun scalar(udm: UDM) = ((udm as UDM.Scalar).value as Number).toDouble()

    // ── default engine (LATEST mode): accepts every SUPPORTED version ──

    @Test fun `1_0 runs on the default engine`() {
        assertEquals(5.0, scalar(run("1.0")))
    }

    @Test fun `1_1 runs on the default engine`() {
        assertEquals(5.0, scalar(run("1.1")))
    }

    // ── unsupported versions are rejected regardless of mode ──

    @Test fun `unknown future version 2_0 is rejected`() {
        val ex = assertThrows<RuntimeError> { run("2.0") }
        assertTrue(ex.message!!.contains("unsupported %utlx version"), "message was: ${ex.message}")
    }

    @Test fun `unknown version 1_5 is rejected`() {
        val ex = assertThrows<RuntimeError> { run("1.5") }
        assertTrue(ex.message!!.contains("unsupported %utlx version"), "message was: ${ex.message}")
    }

    // ── mode switch: a 1.0-only engine rejects %utlx 1.1 ──

    @Test fun `1_0-only mode rejects a 1_1 script`() {
        val ex = assertThrows<RuntimeError> { run("1.1", maxVersion = "1.0") }
        assertTrue(ex.message!!.contains("not enabled") && ex.message!!.contains("1.0-only mode"),
            "message was: ${ex.message}")
    }

    @Test fun `1_0-only mode still runs a 1_0 script`() {
        assertEquals(5.0, scalar(run("1.0", maxVersion = "1.0")))
    }

    @Test fun `1_1 mode runs a 1_1 script (explicit mode)`() {
        assertEquals(5.0, scalar(run("1.1", maxVersion = "1.1")))
    }
}
