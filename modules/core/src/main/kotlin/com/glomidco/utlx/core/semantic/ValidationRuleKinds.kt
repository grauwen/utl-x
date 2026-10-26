package com.glomidco.utlx.core.semantic

/**
 * F25 — the single definition of the rule kinds a `validate.assert` rule may carry.
 *
 * It lives in `:core` because **both** sides need it and only this direction is legal:
 * `:stdlib` depends on `:core`, never the reverse (the boundary whose reflective workaround caused
 * B19/B25 on the native image). So the list is defined here and imported by
 * `:stdlib ValidateFunctions`, which dispatches on it, and used by the `:core` interpreter, which
 * must recognise a declarative kind to diagnose it being combined with an `every` / `some`
 * quantifier.
 *
 * Exactly one kind per rule: a rule is one unit of policy — one id, one severity, and (for a guard)
 * one bit of a failed-rule table.
 */
object ValidationRuleKinds {

    /** Kinds that declare a check as **data**, each delegating to the named validator of that name. */
    val DECLARATIVE: List<String> = listOf(
        "required", "inRange", "oneOf", "matches", "isoDate", "unique",
        "mutuallyExclusive", "atLeastOne",
    )

    /** The lambda escape hatch (VB01), plus every declarative kind. */
    val ALL: List<String> = listOf("check") + DECLARATIVE
}
