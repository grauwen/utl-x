package com.glomidco.utlx.core.semantic

import com.glomidco.utlx.core.ast.Expression
import com.glomidco.utlx.core.ast.Program
import com.glomidco.utlx.core.interpreter.RuntimeError

/**
 * VB05: enforce the `%utlx` namespace version gate **statically**, before any data is processed.
 *
 * The runtime gate in `Interpreter.tryLoadStdlibFunction` fires only when a namespaced call is
 * actually *reached*, because that is where name resolution happens. So a `%utlx 1.0` script could
 * carry `validate.*` calls on a branch that a given payload does not take and run perfectly:
 *
 * ```utlx
 * %utlx 1.0
 * ---
 * if ($input.orderId > 1000) ($input |> validate.required(["orderId"])) else { status: "ok" }
 * ```
 *
 * `orderId: 42` produced `{"status":"ok"}` and exit 0; `orderId: 5000` failed. A version gate is a
 * claim about whether the *program* is legal — the same category as a syntax error — so deciding it
 * per payload meant a 1.0 script using 1.1 features passed testing and failed in production on the
 * first payload to reach the call.
 *
 * This walks the whole body once, up front, and rejects the program if ANY namespaced call needs a
 * newer `%utlx` than the header declares. The runtime gate stays as a backstop.
 *
 * **Shadowing.** A namespaced call is only a namespace when its base identifier is not a bound
 * variable — the runtime path relies on the base being undefined. The walk therefore tracks names
 * bound by `let`, lambda parameters, `try/catch` error variables and match variables, and skips a
 * call whose base is shadowed. So this stays legal under 1.0:
 *
 * ```utlx
 * let validate = { required: 1 } in validate.required      // a member access, not a namespace
 * ```
 */
object LanguageVersionStaticGate {

    /**
     * The highest `%utlx` version any gated namespace requires. A script declaring at least this
     * version cannot possibly reach a namespace it is not allowed to use, so the walk is skipped.
     * Derived from [LanguageNamespaces.NAMESPACE_MIN_VERSION], so it stays correct as namespaces
     * are added.
     */
    private val highestGatedVersion: String =
        LanguageNamespaces.NAMESPACE_MIN_VERSION.values
            .maxWithOrNull { a, b -> LanguageNamespaces.compareVersions(a, b) }
            ?: "0.0"

    /**
     * Throws [RuntimeError] on the first namespaced call the declared version does not allow.
     *
     * Note on cost: `Interpreter.execute` runs **per payload** (`executeBatch` is
     * `inputs.map { execute(it) }`), so a naive walk here would re-traverse the whole AST for every
     * record in a batch. Two things keep that off the hot path:
     *   1. the version fast path below — a script declaring the newest gated version (today
     *      `%utlx 1.1`) skips the walk entirely, which is the forward-looking common case;
     *   2. the per-program memo in the interpreter, so a given program is walked once.
     */
    fun check(program: Program, declaredVersion: String) {
        // Fast path: nothing is gated above the declared version, so no call can violate it.
        if (LanguageNamespaces.compareVersions(declaredVersion, highestGatedVersion) >= 0) return
        walk(program.body, declaredVersion, emptySet())
    }

    private fun violation(name: String, declaredVersion: String, expr: Expression): Nothing {
        val required = LanguageNamespaces.minVersionFor(name)
        val ns = LanguageNamespaces.namespaceOf(name)
        throw RuntimeError(
            "$name requires %utlx $required (script declares %utlx $declaredVersion); " +
                "the $ns.* namespace is a $required language feature.",
            expr.location,
        )
    }

    /**
     * `bound` is the set of names in lexical scope. The `when` is exhaustive over the sealed
     * Expression hierarchy **by design** — it is assigned to a value so that adding a node type to
     * the AST breaks this file at compile time rather than silently opening a hole in the gate.
     */
    private fun walk(expr: Expression, declaredVersion: String, bound: Set<String>) {
        val unit: Unit = when (expr) {
            // ── leaves ──
            is Expression.StringLiteral -> Unit
            is Expression.NumberLiteral -> Unit
            is Expression.BooleanLiteral -> Unit
            is Expression.NullLiteral -> Unit
            is Expression.Identifier -> Unit

            // ── the node this gate exists for ──
            is Expression.FunctionCall -> {
                // Same derivation as Interpreter.evaluateFunctionCall: a MemberAccess on a bare
                // Identifier base is a namespaced stdlib call ("ns.fn"); @attr / ^meta never are.
                val fn = expr.function
                if (fn is Expression.MemberAccess && !fn.isAttribute && !fn.isMetadata) {
                    val base = fn.target as? Expression.Identifier
                    if (base != null && base.name !in bound) {
                        val name = "${base.name}.${fn.property}"
                        if (LanguageNamespaces.namespaceOf(name) != null &&
                            !LanguageNamespaces.isAvailableAt(name, declaredVersion)
                        ) {
                            violation(name, declaredVersion, expr)
                        }
                    }
                }
                walk(expr.function, declaredVersion, bound)
                expr.arguments.forEach { walk(it, declaredVersion, bound) }
            }

            // ── scope-introducing nodes ──
            is Expression.ObjectLiteral -> {
                var scope = bound
                for (b in expr.letBindings) {
                    walk(b.value, declaredVersion, scope)
                    scope = scope + b.name
                }
                for (p in expr.properties) {
                    p.computedKey?.let { walk(it, declaredVersion, scope) }
                    walk(p.value, declaredVersion, scope)
                }
            }
            is Expression.LetBinding -> walk(expr.value, declaredVersion, bound)
            is Expression.Lambda ->
                walk(expr.body, declaredVersion, bound + expr.parameters.map { it.name })
            is Expression.TryCatch -> {
                walk(expr.tryBlock, declaredVersion, bound)
                walk(expr.catchBlock, declaredVersion, bound + listOfNotNull(expr.errorVariable))
            }
            is Expression.Match -> {
                walk(expr.value, declaredVersion, bound)
                for (c in expr.cases) {
                    val scope = bound + patternNames(c.pattern)
                    c.guard?.let { walk(it, declaredVersion, scope) }
                    walk(c.expression, declaredVersion, scope)
                }
            }
            is Expression.Block -> {
                // A block may introduce bindings as it goes (let … in the same block).
                var scope = bound
                for (e in expr.expressions) {
                    walk(e, declaredVersion, scope)
                    if (e is Expression.LetBinding) scope = scope + e.name
                }
            }

            // ── plain structural recursion ──
            is Expression.ArrayLiteral -> expr.elements.forEach { walk(it, declaredVersion, bound) }
            is Expression.MemberAccess -> walk(expr.target, declaredVersion, bound)
            is Expression.SafeNavigation -> walk(expr.target, declaredVersion, bound)
            is Expression.IndexAccess -> {
                walk(expr.target, declaredVersion, bound)
                walk(expr.index, declaredVersion, bound)
            }
            is Expression.BinaryOp -> {
                walk(expr.left, declaredVersion, bound)
                walk(expr.right, declaredVersion, bound)
            }
            is Expression.UnaryOp -> walk(expr.operand, declaredVersion, bound)
            is Expression.Ternary -> {
                walk(expr.condition, declaredVersion, bound)
                walk(expr.thenExpr, declaredVersion, bound)
                walk(expr.elseExpr, declaredVersion, bound)
            }
            is Expression.Conditional -> {
                walk(expr.condition, declaredVersion, bound)
                walk(expr.thenBranch, declaredVersion, bound)
                expr.elseBranch?.let { walk(it, declaredVersion, bound) }
                Unit  // keep the `when` branch type Unit, not Unit? (see exhaustiveness note above)
            }
            is Expression.Pipe -> {
                walk(expr.source, declaredVersion, bound)
                walk(expr.target, declaredVersion, bound)
            }
            is Expression.TemplateApplication -> walk(expr.selector, declaredVersion, bound)
            is Expression.SpreadElement -> walk(expr.expression, declaredVersion, bound)
        }
        return unit
    }

    private fun patternNames(pattern: com.glomidco.utlx.core.ast.Pattern): Set<String> =
        when (pattern) {
            is com.glomidco.utlx.core.ast.Pattern.Variable -> setOf(pattern.name)
            else -> emptySet()
        }
}
