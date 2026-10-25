package com.glomidco.utlx.stdlib.validate

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.annotations.UTLXFunction
import java.math.BigDecimal

/**
 * `validate.*` — semantic validation (F21, `%utlx 1.1`). Tier-1 shortcuts.
 *
 * These are **pure / stateless / deterministic** UDM-layer functions: no I/O, no parser/serializer
 * touched. Each returns a `ValidationResult` (a plain UDM `Object`, §6 of the spec) — it *decides*; the
 * engine *enforces* (fail-closed routing + per-severity budgets, §8); a guard *locks* that. The functions
 * never prevent output themselves.
 *
 * Calling convention: the payload is piped in as the first argument
 * (`$current |> validate.required([...])` → `validate.required($current, [...])`).
 *
 * `since = "1.1"` tags these for the engine's version gate — a `%utlx 1.0` script may not call them
 * (resolved at name resolution). `assert` (the canonical multi-rule form, §5.10) is implemented
 * separately once the rule-`check` expression scoping is wired.
 */
object ValidateFunctions {

    // ── argument helpers ──
    private fun payloadOf(args: List<UDM>): UDM = args.firstOrNull() ?: UDM.Scalar.nullValue()
    private fun props(payload: UDM): Map<String, UDM> = (payload as? UDM.Object)?.properties ?: emptyMap()
    private fun asString(udm: UDM?): String? = (udm as? UDM.Scalar)?.value?.toString()
    private fun asNumber(udm: UDM?): Double? = ((udm as? UDM.Scalar)?.value as? Number)?.toDouble()
    private fun scalarValue(udm: UDM?): Any? = (udm as? UDM.Scalar)?.value
    private fun isSet(udm: UDM?): Boolean = udm != null && !(udm is UDM.Scalar && udm.value == null)

    /**
     * VB04: a rule that cannot be **constructed** is an error, exactly like a rule that cannot be
     * **evaluated** (VB01's `check`, VB02's `when`).
     *
     * Malformed arguments used to produce a silent PASS: `validate.required("price")` — the array
     * brackets forgotten — ran zero checks and returned `valid:true`, and `unique` pointed at a
     * non-array counted a check as passed. On a fail-closed guard (`maxErrors = 0`) `valid:true`
     * forwards the payload, so a typo in a rule silently disabled that rule and waved the data
     * through. Reporting an error issue instead keeps a broken rule fail-closed.
     */
    private fun malformedRule(payload: UDM, fn: String, reason: String, field: String? = null): UDM =
        validationResult(
            payload,
            listOf(ValidationIssue(field = field, message = "validate.$fn: malformed rule — $reason")),
            totalChecks = 1,
        )

    /** The exact-arity guard the `@UTLXFunction` minArgs/maxArgs declare but nothing enforces here. */
    private fun arityReason(args: List<UDM>, expected: Int, shape: String): String? =
        if (args.size == expected) null
        else "expected $expected arguments ($shape), got ${args.size}"

    /** A field-name argument: must be a non-blank string. */
    private fun fieldArg(args: List<UDM>, i: Int): String? =
        ((args.getOrNull(i) as? UDM.Scalar)?.value as? String)?.takeIf { it.isNotBlank() }

    /** An array argument's elements, or null when the argument is not an array at all. */
    private fun arrayArg(args: List<UDM>, i: Int): List<UDM>? = (args.getOrNull(i) as? UDM.Array)?.elements

    /** Field names from an array argument — null if any element is not a string (silently dropped before). */
    private fun fieldNames(elements: List<UDM>): List<String>? {
        val names = elements.map { (it as? UDM.Scalar)?.value as? String }
        return if (names.any { it == null }) null else names.filterNotNull()
    }

    /**
     * VB03: the canonical form of a scalar for VALUE comparison.
     *
     * `scalarValue` hands back the raw boxed value, and the same number reaches us in different
     * boxes depending on where it came from: JSON `50` parses to an Int/Long, while the script
     * literal `50` in `validate.oneOf("price", [50, 60])` is evaluated by the interpreter as a
     * Double. `Any?.equals` is type-sensitive, so `50 != 50.0` and membership silently failed.
     *
     * Numbers are therefore canonicalised to a scale-insensitive BigDecimal (via `toString`, so no
     * binary-float noise is introduced, then `stripTrailingZeros`, so 50, 50.0 and 5E1 are one
     * value). Non-finite doubles keep their boxed form — BigDecimal cannot represent them.
     *
     * Non-numbers are returned unchanged, deliberately: the string `"50"` is NOT the number 50,
     * and `oneOf` is a controlled-value check, not a coercion.
     */
    private fun canonicalScalar(value: Any?): Any? = when (value) {
        null -> null
        is Boolean -> value          // Boolean is not a Number; keep it out of the numeric path
        is BigDecimal -> value.stripTrailingZeros()
        is Double -> if (value.isFinite()) BigDecimal(value.toString()).stripTrailingZeros() else value
        is Float -> if (value.isFinite()) BigDecimal(value.toString()).stripTrailingZeros() else value
        is Number -> BigDecimal(value.toString()).stripTrailingZeros()
        else -> value
    }

    /**
     * VB06: does this UDM look like a `ValidationResult`?
     *
     * Same shape test the engine's `ValidationRoutingPolicy.isValidationResult` uses: the verdict
     * fields. A validator receiving one as its *payload* means the author chained validators —
     * `$input |> validate.required([...]) |> validate.inRange(...)` — so the second call inspected
     * the FIRST call's result instead of the message. That silently discarded the first verdict and
     * nested it under `payload`, where a top-level `valid: true` could then hide a real failure and
     * the engine would forward the payload. Fail-open, from the most natural-looking composition.
     *
     * Validators do not compose by piping. Each takes the message; results are combined by hand
     * (counts with `+`, issues with `flatten([...])`) until a `validate.all(results)` combinator
     * lands. See `examples/utlx/advanced/validation-guard-named-validators.utlx`.
     *
     * **Public on purpose: this is the single definition of the verdict shape.** It sits beside
     * [validationResult], the function that produces that shape, so the producer and every
     * recogniser cannot drift. The engine's `ValidationRoutingPolicy.isValidationResult` delegates
     * here rather than keeping its own copy — it used to have one, in a different spelling
     * (`get("valid") != null` vs `containsKey`), which meant adding a field to a ValidationResult
     * would have required finding two places in two modules.
     */
    fun looksLikeValidationResult(udm: UDM): Boolean {
        val props = (udm as? UDM.Object)?.properties ?: return false
        return props.containsKey("valid") && props.containsKey("failed") && props.containsKey("issues")
    }

    /** VB06: the chaining diagnosis, shared by every validator. */
    private fun chainedPayloadReason(): String =
        "payload is a ValidationResult; validators must not be chained with |> — each takes the " +
            "message, not the previous result"

    /** The only severities a `ValidationResult` counts. Anything else is a malformed rule (VB07). */
    private val SEVERITIES = setOf("error", "warning", "notice")

    /**
     * VB07: recognise a severity, case-insensitively, and return it in canonical lowercase —
     * or null if it is not one of [SEVERITIES].
     *
     * Why this has to be checked rather than passed through: `validationResult` counts issues by
     * **exact** severity match, so an unrecognised value landed in no bucket at all. The rule
     * failed, its issue appeared in `issues[]`, and yet `failed` stayed 0 — so `valid` stayed true
     * and a fail-closed routing policy FORWARDED the payload. A typo silently disabled the rule.
     *
     * `"ERROR"` was the nastiest case: the engine's own `ValidationError` uses uppercase, so anyone
     * following that convention got a silently dead rule. Uppercase is now accepted and normalised
     * rather than rejected, since the two spellings mean the same thing; genuinely unknown values
     * are reported.
     */
    private fun canonicalSeverity(raw: String?): String? {
        if (raw == null) return "error"                 // absent → the documented default
        val lower = raw.trim().lowercase()
        return if (lower in SEVERITIES) lower else null
    }

    /** Render a scalar for an issue message — `50.0` reads as `50`, so the text matches the script. */
    private fun displayScalar(value: Any?): String =
        when (val c = canonicalScalar(value)) {
            is BigDecimal -> c.toPlainString()
            else -> c.toString()
        }

    @UTLXFunction(
        description = "The canonical form: evaluate a list of rules against the payload, returning one " +
            "aggregated ValidationResult. Each rule's `check` is a LAMBDA `(p) => <boolean>` invoked " +
            "with the payload; a check that cannot be evaluated (missing/mistyped field) becomes a " +
            "reported error issue rather than aborting. All rules are evaluated (no short-circuit).",
        minArgs = 2, maxArgs = 2, category = "Validation",
        parameters = [
            "payload (piped in)",
            "rules: array of { rule, check: (p) => boolean, when?: (p) => boolean, message, field?/fields?, severity? }",
        ],
        returns = "ValidationResult",
        example = "\$current |> validate.assert([{ rule: \"price\", check: (p) => p.unitPrice > 0, message: \"must be positive\", field: \"unitPrice\" }])",
        since = "1.1",
    )
    fun assert(args: List<UDM>): UDM {
        val payload = payloadOf(args)
        // VB06: a chained validator would inspect the previous result, not the message.
        if (looksLikeValidationResult(payload)) return malformedRule(payload, "assert", chainedPayloadReason())
        val rules = (args.getOrNull(1) as? UDM.Array)?.elements?.filterIsInstance<UDM.Object>() ?: emptyList()

        // ── Rule-ID discipline: `rule` identifies a failure in the report, and downstream consumers
        // index on it (a guard maps each ID to one bit of a bounded failed-rule table and uses the
        // set of failing IDs as its audit record). A missing or duplicated ID makes the report
        // unusable for that, so it is a malformed POLICY — a list-level fault, not a per-rule one.
        val ids = rules.map { asString(it.properties["rule"])?.takeIf { s -> s.isNotBlank() } }
        if (ids.any { it == null }) {
            return malformedRule(payload, "assert",
                "every rule needs a non-empty `rule` id (${ids.count { it == null }} of ${ids.size} " +
                    "missing one); the id names the failure in `issues[].rule` and is what consumers index on")
        }
        // Uniqueness is NOT checked here: a quantified rule (`every`) expands to one rule per
        // element, all legitimately carrying the author's single id. The author's pre-expansion
        // list is the thing that must have unique ids, and only the interpreter sees it — so that
        // check lives in `Interpreter.evaluateValidateAssert`.

        val issues = mutableListOf<ValidationIssue>()
        var totalChecks = 0
        for (rule in rules) {
            val p = rule.properties
            totalChecks++
            val ruleId = asString(p["rule"])
            // `when` guard: a rule whose `when` is explicitly false is skipped and counts as passed.
            if ((p["when"] as? UDM.Scalar)?.value == false) continue
            // VB07: an unrecognised severity would land the issue in no count bucket at all, leaving
            // `failed` at 0 and the verdict valid. Report it on THIS rule (per-rule isolation, as
            // VB01 does for a non-evaluable check) rather than discarding the whole batch.
            val severity = canonicalSeverity(asString(p["severity"]))
            if (severity == null) {
                issues.add(ValidationIssue(
                    field = asString(p["field"]),
                    message = "rule '$ruleId' has an unknown severity " +
                        "'${asString(p["severity"])}' — expected one of ${SEVERITIES.sorted().joinToString(", ")}",
                    severity = "error",
                    rule = ruleId,
                ))
                continue
            }
            // `check` is pre-evaluated to a boolean (the rule list is built with `$` = payload via the
            // pipe). true → pass; anything else (false/null/non-bool) → fail (fail-closed on a bad check).
            if ((p["check"] as? UDM.Scalar)?.value == true) continue
            val field = asString(p["field"])
                ?: (p["fields"] as? UDM.Array)?.elements?.mapNotNull { asString(it) }?.joinToString(",")
            issues.add(ValidationIssue(
                field = field,
                message = asString(p["message"]) ?: "rule failed",
                severity = severity,
                rule = ruleId,
            ))
        }
        return validationResult(payload, issues, totalChecks)
    }

    @UTLXFunction(
        description = "Merge several ValidationResults into one aggregated ValidationResult. The " +
            "sanctioned way to apply several rules: each validator takes the MESSAGE, and their " +
            "results are combined here. Counts are recomputed from the merged issues, so they can " +
            "never disagree with them.",
        minArgs = 2, maxArgs = 2, category = "Validation",
        parameters = ["payload (piped in)", "results: array of ValidationResult"],
        returns = "ValidationResult",
        example = "\$current |> validate.all([\$current |> validate.required([\"orderId\"]), " +
            "\$current |> validate.inRange(\"qty\", 1, 100)])",
        since = "1.1",
    )
    fun all(args: List<UDM>): UDM {
        val payload = payloadOf(args)
        // VB06: a chained validator would inspect the previous result, not the message.
        if (looksLikeValidationResult(payload)) return malformedRule(payload, "all", chainedPayloadReason())
        arityReason(args, 2, "payload, results[]")?.let { return malformedRule(payload, "all", it) }
        val elements = arrayArg(args, 1)
            ?: return malformedRule(payload, "all", "results must be an array of ValidationResults")

        val issues = mutableListOf<ValidationIssue>()
        var totalChecks = 0
        for ((i, element) in elements.withIndex()) {
            if (!looksLikeValidationResult(element)) {
                return malformedRule(payload, "all",
                    "results[$i] is not a ValidationResult — pass the OUTPUT of a validate.* call, " +
                        "not a payload or a rule list")
            }
            val props = (element as UDM.Object).properties
            val elementIssues = (props["issues"] as? UDM.Array)?.elements?.filterIsInstance<UDM.Object>()
                ?: emptyList()
            // `passed` + |issues| reconstructs that result's totalChecks, so the merged `passed`
            // is the sum of the parts rather than a recount against a different denominator.
            totalChecks += ((props["passed"] as? UDM.Scalar)?.value as? Number)?.toInt() ?: 0
            totalChecks += elementIssues.size
            for (iss in elementIssues) {
                issues.add(ValidationIssue(
                    field = asString(iss.properties["field"]),
                    message = asString(iss.properties["message"]) ?: "rule failed",
                    // already canonical in a well-formed result; re-checked so a hand-built
                    // result cannot smuggle an uncounted severity through the merge (VB07).
                    severity = canonicalSeverity(asString(iss.properties["severity"])) ?: "error",
                    rule = asString(iss.properties["rule"]),
                ))
            }
        }
        // Counts come from validationResult, recomputed over the merged issues.
        return validationResult(payload, issues, totalChecks)
    }

    @UTLXFunction(
        description = "Assert that each named field is present and non-null; returns a ValidationResult.",
        minArgs = 2, maxArgs = 2,
        category = "Validation",
        parameters = ["payload: the record being validated (piped in)", "fields: string[] of required field names"],
        returns = "ValidationResult (valid/passed/failed/warnings/notices/issues[]/payload)",
        example = "\$current |> validate.required([\"orderId\", \"currency\"])",
        since = "1.1",
    )
    fun required(args: List<UDM>): UDM {
        val payload = payloadOf(args)
        // VB06: a chained validator would inspect the previous result, not the message.
        if (looksLikeValidationResult(payload)) return malformedRule(payload, "required", chainedPayloadReason())
        arityReason(args, 2, "payload, fields[]")?.let { return malformedRule(payload, "required", it) }
        val elements = arrayArg(args, 1)
            ?: return malformedRule(payload, "required", "fields must be an array of field names")
        val fields = fieldNames(elements)
            ?: return malformedRule(payload, "required", "every entry in fields must be a string")
        val p = props(payload)
        val issues = fields
            .filter { f -> val v = p[f]; v == null || (v is UDM.Scalar && v.value == null) }
            .map { ValidationIssue(it, "Required field '$it' is missing") }
        return validationResult(payload, issues, totalChecks = fields.size)
    }

    @UTLXFunction(
        description = "Assert a numeric field lies within the inclusive [min, max] range.",
        minArgs = 4, maxArgs = 4,
        category = "Validation",
        parameters = ["payload (piped in)", "field: field name", "min: number", "max: number"],
        returns = "ValidationResult",
        example = "\$current |> validate.inRange(\"unitPrice\", 0.01, 999999.99)",
        since = "1.1",
    )
    fun inRange(args: List<UDM>): UDM {
        val payload = payloadOf(args)
        // VB06: a chained validator would inspect the previous result, not the message.
        if (looksLikeValidationResult(payload)) return malformedRule(payload, "inRange", chainedPayloadReason())
        arityReason(args, 4, "payload, field, min, max")?.let { return malformedRule(payload, "inRange", it) }
        val field = fieldArg(args, 1)
            ?: return malformedRule(payload, "inRange", "field must be a non-empty string")
        val min = asNumber(args.getOrNull(2))
            ?: return malformedRule(payload, "inRange", "min must be a number", field)
        val max = asNumber(args.getOrNull(3))
            ?: return malformedRule(payload, "inRange", "max must be a number", field)
        val v = asNumber(props(payload)[field])
        val issues = when {
            v == null -> listOf(ValidationIssue(field, "Field '$field' is not a number (inRange)"))
            min != null && v < min -> listOf(ValidationIssue(field, "Field '$field' ($v) is below minimum $min"))
            max != null && v > max -> listOf(ValidationIssue(field, "Field '$field' ($v) is above maximum $max"))
            else -> emptyList()
        }
        return validationResult(payload, issues, totalChecks = 1)
    }

    @UTLXFunction(
        description = "Assert a field's value is one of a controlled list of allowed values.",
        minArgs = 3, maxArgs = 3,
        category = "Validation",
        parameters = ["payload (piped in)", "field: field name", "values: allowed values"],
        returns = "ValidationResult",
        example = "\$current |> validate.oneOf(\"currency\", [\"EUR\", \"USD\", \"GBP\"])",
        since = "1.1",
    )
    fun oneOf(args: List<UDM>): UDM {
        val payload = payloadOf(args)
        // VB06: a chained validator would inspect the previous result, not the message.
        if (looksLikeValidationResult(payload)) return malformedRule(payload, "oneOf", chainedPayloadReason())
        arityReason(args, 3, "payload, field, values[]")?.let { return malformedRule(payload, "oneOf", it) }
        val field = fieldArg(args, 1)
            ?: return malformedRule(payload, "oneOf", "field must be a non-empty string")
        val allowedValues = (arrayArg(args, 2)
            ?: return malformedRule(payload, "oneOf", "values must be an array", field)).map { scalarValue(it) }
        val actual = scalarValue(props(payload)[field])
        // VB03: compare canonical forms — a JSON Int and a script Double of the same number match.
        val allowed = allowedValues.map { canonicalScalar(it) }
        val issues = if (canonicalScalar(actual) in allowed) emptyList()
            else listOf(ValidationIssue(
                field,
                "Field '$field' value '${displayScalar(actual)}' is not one of " +
                    allowedValues.joinToString(", ", "[", "]") { displayScalar(it) }
            ))
        return validationResult(payload, issues, totalChecks = 1)
    }

    @UTLXFunction(
        description = "Assert a string field matches a regular-expression pattern.",
        minArgs = 3, maxArgs = 3, category = "Validation",
        parameters = ["payload (piped in)", "field: field name", "pattern: regex"],
        returns = "ValidationResult",
        example = "\$current |> validate.matches(\"iban\", \"^[A-Z]{2}[0-9]{2}.*\")",
        since = "1.1",
    )
    fun matches(args: List<UDM>): UDM {
        val payload = payloadOf(args)
        // VB06: a chained validator would inspect the previous result, not the message.
        if (looksLikeValidationResult(payload)) return malformedRule(payload, "matches", chainedPayloadReason())
        arityReason(args, 3, "payload, field, pattern")?.let { return malformedRule(payload, "matches", it) }
        val field = fieldArg(args, 1)
            ?: return malformedRule(payload, "matches", "field must be a non-empty string")
        val pattern = asString(args.getOrNull(2))
            ?: return malformedRule(payload, "matches", "pattern must be a string", field)
        // VB04: an unparseable regex is a broken RULE, not failing data — report it as such instead
        // of silently reporting "does not match" and blaming the payload.
        val regex = runCatching { Regex(pattern) }.getOrNull()
            ?: return malformedRule(payload, "matches", "pattern is not a valid regular expression: /$pattern/", field)
        val value = asString(props(payload)[field])
        val ok = value != null && regex.matches(value)
        val issues = if (ok) emptyList()
            else listOf(ValidationIssue(field, "Field '$field' value '${value ?: ""}' does not match /$pattern/"))
        return validationResult(payload, issues, totalChecks = 1)
    }

    @UTLXFunction(
        description = "Assert a string field is a valid date in the declared format.",
        minArgs = 3, maxArgs = 3, category = "Validation",
        parameters = ["payload (piped in)", "field: field name", "format: date pattern (e.g. yyyy-MM-dd)"],
        returns = "ValidationResult",
        example = "\$current |> validate.isoDate(\"orderDate\", \"yyyyMMdd\")",
        since = "1.1",
    )
    fun isoDate(args: List<UDM>): UDM {
        val payload = payloadOf(args)
        // VB06: a chained validator would inspect the previous result, not the message.
        if (looksLikeValidationResult(payload)) return malformedRule(payload, "isoDate", chainedPayloadReason())
        arityReason(args, 3, "payload, field, format")?.let { return malformedRule(payload, "isoDate", it) }
        val field = fieldArg(args, 1)
            ?: return malformedRule(payload, "isoDate", "field must be a non-empty string")
        val format = asString(args.getOrNull(2))
            ?: return malformedRule(payload, "isoDate", "format must be a string", field)
        // VB04: as with `matches`, an unusable format is a broken rule, not failing data.
        val formatter = runCatching { java.time.format.DateTimeFormatter.ofPattern(format) }.getOrNull()
            ?: return malformedRule(payload, "isoDate", "format is not a valid date pattern: '$format'", field)
        val value = asString(props(payload)[field])
        val ok = value != null && runCatching {
            java.time.LocalDate.parse(value, formatter); true
        }.getOrDefault(false)
        val issues = if (ok) emptyList()
            else listOf(ValidationIssue(field, "Field '$field' value '${value ?: ""}' is not a valid date in format '$format'"))
        return validationResult(payload, issues, totalChecks = 1)
    }

    @UTLXFunction(
        description = "Assert no duplicate values of a key field exist within an array column.",
        minArgs = 3, maxArgs = 3, category = "Validation",
        parameters = ["payload (piped in)", "arrayField: array field name", "keyField: key within each element"],
        returns = "ValidationResult",
        example = "\$current |> validate.unique(\"orderLines\", \"lineId\")",
        since = "1.1",
    )
    fun unique(args: List<UDM>): UDM {
        val payload = payloadOf(args)
        // VB06: a chained validator would inspect the previous result, not the message.
        if (looksLikeValidationResult(payload)) return malformedRule(payload, "unique", chainedPayloadReason())
        arityReason(args, 3, "payload, arrayField, keyField")?.let { return malformedRule(payload, "unique", it) }
        val arrayField = fieldArg(args, 1)
            ?: return malformedRule(payload, "unique", "arrayField must be a non-empty string")
        val keyField = fieldArg(args, 2)
            ?: return malformedRule(payload, "unique", "keyField must be a non-empty string", arrayField)
        // VB04: previously a non-array (or absent) field meant zero keys, no duplicates, and a
        // silent PASS — the worst direction for a guard. Nothing was checked, so it is an error.
        val array = (props(payload)[arrayField] as? UDM.Array)?.elements
            ?: return malformedRule(payload, "unique", "'$arrayField' is not an array on this payload", arrayField)
        val keys = array.mapNotNull { scalarValue((it as? UDM.Object)?.properties?.get(keyField)) }
        // VB03: group on the canonical form, so an Int 7 and a Double 7.0 count as the same key.
        val dupes = keys.groupingBy { canonicalScalar(it) }.eachCount().filterValues { it > 1 }.keys
        val issues = if (dupes.isEmpty()) emptyList()
            else listOf(ValidationIssue(
                arrayField,
                "Duplicate '$keyField' values in '$arrayField': " +
                    dupes.joinToString(", ", "[", "]") { displayScalar(it) }
            ))
        return validationResult(payload, issues, totalChecks = 1)
    }

    @UTLXFunction(
        description = "Assert at most one of the named fields is set (non-null).",
        minArgs = 2, maxArgs = 2, category = "Validation",
        parameters = ["payload (piped in)", "fields: string[] of mutually-exclusive field names"],
        returns = "ValidationResult",
        example = "\$current |> validate.mutuallyExclusive([\"shipToId\", \"shipToAddress\"])",
        since = "1.1",
    )
    fun mutuallyExclusive(args: List<UDM>): UDM {
        val payload = payloadOf(args)
        // VB06: a chained validator would inspect the previous result, not the message.
        if (looksLikeValidationResult(payload)) return malformedRule(payload, "mutuallyExclusive", chainedPayloadReason())
        arityReason(args, 2, "payload, fields[]")?.let { return malformedRule(payload, "mutuallyExclusive", it) }
        val fields = arrayArg(args, 1)?.let { fieldNames(it) }
            ?: return malformedRule(payload, "mutuallyExclusive", "fields must be an array of field-name strings")
        val set = fields.filter { isSet(props(payload)[it]) }
        val issues = if (set.size <= 1) emptyList()
            else listOf(ValidationIssue(null, "At most one of $fields may be set; found $set"))
        return validationResult(payload, issues, totalChecks = 1)
    }

    @UTLXFunction(
        description = "Assert at least one of the named fields is set (non-null).",
        minArgs = 2, maxArgs = 2, category = "Validation",
        parameters = ["payload (piped in)", "fields: string[] of field names"],
        returns = "ValidationResult",
        example = "\$current |> validate.atLeastOne([\"email\", \"phone\", \"fax\"])",
        since = "1.1",
    )
    fun atLeastOne(args: List<UDM>): UDM {
        val payload = payloadOf(args)
        // VB06: a chained validator would inspect the previous result, not the message.
        if (looksLikeValidationResult(payload)) return malformedRule(payload, "atLeastOne", chainedPayloadReason())
        arityReason(args, 2, "payload, fields[]")?.let { return malformedRule(payload, "atLeastOne", it) }
        val fields = arrayArg(args, 1)?.let { fieldNames(it) }
            ?: return malformedRule(payload, "atLeastOne", "fields must be an array of field-name strings")
        val issues = if (fields.any { isSet(props(payload)[it]) }) emptyList()
            else listOf(ValidationIssue(null, "At least one of $fields must be set"))
        return validationResult(payload, issues, totalChecks = 1)
    }
}
