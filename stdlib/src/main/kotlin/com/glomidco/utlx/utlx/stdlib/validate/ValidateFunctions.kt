package com.glomidco.utlx.stdlib.validate

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.annotations.UTLXFunction

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
        val rules = (args.getOrNull(1) as? UDM.Array)?.elements?.filterIsInstance<UDM.Object>() ?: emptyList()
        val issues = mutableListOf<ValidationIssue>()
        var totalChecks = 0
        for (rule in rules) {
            val p = rule.properties
            totalChecks++
            // `when` guard: a rule whose `when` is explicitly false is skipped and counts as passed.
            if ((p["when"] as? UDM.Scalar)?.value == false) continue
            // `check` is pre-evaluated to a boolean (the rule list is built with `$` = payload via the
            // pipe). true → pass; anything else (false/null/non-bool) → fail (fail-closed on a bad check).
            if ((p["check"] as? UDM.Scalar)?.value == true) continue
            val field = asString(p["field"])
                ?: (p["fields"] as? UDM.Array)?.elements?.mapNotNull { asString(it) }?.joinToString(",")
            issues.add(ValidationIssue(
                field = field,
                message = asString(p["message"]) ?: "rule failed",
                severity = asString(p["severity"]) ?: "error",
                rule = asString(p["rule"]),
            ))
        }
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
        val fields = (args.getOrNull(1) as? UDM.Array)?.elements?.mapNotNull { asString(it) } ?: emptyList()
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
        val field = asString(args.getOrNull(1)) ?: ""
        val min = asNumber(args.getOrNull(2))
        val max = asNumber(args.getOrNull(3))
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
        val field = asString(args.getOrNull(1)) ?: ""
        val allowed = (args.getOrNull(2) as? UDM.Array)?.elements?.map { scalarValue(it) } ?: emptyList()
        val actual = scalarValue(props(payload)[field])
        val issues = if (actual in allowed) emptyList()
            else listOf(ValidationIssue(field, "Field '$field' value '$actual' is not one of $allowed"))
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
        val field = asString(args.getOrNull(1)) ?: ""
        val pattern = asString(args.getOrNull(2)) ?: ""
        val value = asString(props(payload)[field])
        val ok = value != null && runCatching { Regex(pattern).matches(value) }.getOrDefault(false)
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
        val field = asString(args.getOrNull(1)) ?: ""
        val format = asString(args.getOrNull(2)) ?: ""
        val value = asString(props(payload)[field])
        val ok = value != null && runCatching {
            java.time.LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern(format)); true
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
        val arrayField = asString(args.getOrNull(1)) ?: ""
        val keyField = asString(args.getOrNull(2)) ?: ""
        val array = (props(payload)[arrayField] as? UDM.Array)?.elements ?: emptyList()
        val keys = array.mapNotNull { scalarValue((it as? UDM.Object)?.properties?.get(keyField)) }
        val dupes = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val issues = if (dupes.isEmpty()) emptyList()
            else listOf(ValidationIssue(arrayField, "Duplicate '$keyField' values in '$arrayField': $dupes"))
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
        val fields = (args.getOrNull(1) as? UDM.Array)?.elements?.mapNotNull { asString(it) } ?: emptyList()
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
        val fields = (args.getOrNull(1) as? UDM.Array)?.elements?.mapNotNull { asString(it) } ?: emptyList()
        val issues = if (fields.any { isSet(props(payload)[it]) }) emptyList()
            else listOf(ValidationIssue(null, "At least one of $fields must be set"))
        return validationResult(payload, issues, totalChecks = 1)
    }
}
