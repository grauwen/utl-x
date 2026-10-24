package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.udm.UDM

/**
 * Shared test double for `validate.assert`, capturing the rules the INTERPRETER hands it.
 *
 * `:core` tests have no `:stdlib` on the classpath, and the interesting behaviour of
 * `validate.assert` lives in the interpreter anyway: it resolves `check` / `when` lambdas (VB01,
 * VB02) and expands quantified rules (F24) before dispatching. Capturing the resolved rules tests
 * exactly that, without the aggregation.
 *
 * **Why this is shared rather than one capture per test class.**
 * `Interpreter.tryRegisterFromLookup` registers into the GLOBAL
 * `StandardLibraryImpl.nativeFunctions` and skips a name already present:
 *
 * ```kotlin
 * if (StandardLibraryImpl.nativeFunctions.containsKey(name)) return true   // first writer wins
 * ```
 *
 * So two test classes each registering their own `validate.assert` would silently share whichever
 * one ran first, and the loser would observe nothing — assertions passing vacuously. One stable
 * holder, reset per test, removes the hazard. (See also B34LogicalShortCircuitTest, where the same
 * global map caused a recorder-name collision.)
 */
object ValidateAssertCapture {

    /** Rules as `validate.assert` received them — already resolved to booleans by the interpreter. */
    val received = mutableListOf<Map<String, UDM>>()

    private val assertImpl: (List<UDM>) -> UDM = { args ->
        received.clear()
        (args.getOrNull(1) as? UDM.Array)?.elements
            ?.filterIsInstance<UDM.Object>()
            ?.forEach { received.add(it.properties) }
        UDM.Object.of("valid" to UDM.Scalar.boolean(true))
    }

    /** The lookup to hand to `Interpreter.setStdlibLookup`, plus any per-test extras. */
    fun lookup(extra: Map<String, (List<UDM>) -> UDM> = emptyMap()): Map<String, (List<UDM>) -> UDM> =
        mapOf("validate.assert" to assertImpl) + extra

    fun reset() = received.clear()

    // ── accessors ──
    fun single(): Map<String, UDM> {
        check(received.size == 1) { "expected exactly one rule, got ${received.size}: ${describe()}" }
        return received[0]
    }

    fun flag(props: Map<String, UDM>, key: String): Any? = (props[key] as? UDM.Scalar)?.value
    fun text(props: Map<String, UDM>, key: String): String? = (props[key] as? UDM.Scalar)?.value as? String

    /** The resolved `check` of every captured rule, in order — what quantifier tests assert on. */
    fun checks(): List<Any?> = received.map { flag(it, "check") }

    fun describe(): String = received.joinToString { "check=${flag(it, "check")} field=${text(it, "field")}" }
}
