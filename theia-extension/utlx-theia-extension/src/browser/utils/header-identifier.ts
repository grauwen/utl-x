/**
 * Turn a filename stem into a valid UTLX header identifier (the name in
 * "input <name> <format>" / "output <name> <format>").
 *
 * UTLX identifiers (see modules/core lexer) allow letters, digits, '_' and '-',
 * but must START with a letter — a leading digit would lex as a number and a
 * leading '_' is dropped by request. So a numeric/ordering prefix like "07-" is
 * stripped up to the first letter ("07-shipping-manifest" -> "shipping-manifest").
 * Characters that aren't letters/digits/_/- (spaces, parens, dots) would split
 * the name into multiple tokens, so they collapse to '_'; trailing separators
 * are trimmed.
 *
 * Returns '' when no letter remains (caller treats that as "no custom name",
 * e.g. "2024.json" -> just "output json").
 *
 * Shared by the input and output panels so the two stay in lock-step.
 */
export function toHeaderIdentifier(stem: string): string {
    return (stem ?? '')
        .trim()
        .replace(/[^\w-]+/g, '_')    // keep letters/digits/_/- ; collapse the rest
        .replace(/^[^A-Za-z]+/, '')  // drop leading non-letters (digits, -, _) up to first letter
        .replace(/[_-]+$/g, '');     // trim trailing separators
}
