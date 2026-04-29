= Pattern Matching and Control Flow

== Match Expressions
// - Syntax: match (expr) { pattern1 -> result1, pattern2 -> result2 }
// - Value matching: match (status) { "active" -> ..., "inactive" -> ... }
// - Guard clauses: match (x) { n if n > 0 -> "positive", _ -> "non-positive" }
// - Variable binding in patterns

== Try/Catch
// - Syntax: try { expr } catch (e) { fallback }
// - Error handling in transformations
// - When to use try/catch vs ?? (nullish coalescing)
// - Error propagation

== Conditional Patterns
// - if/else chains for complex routing
// - Ternary for inline decisions
// - Combining match with map for polymorphic data

== Real-World Patterns
// - Format detection: route by input structure
// - Version handling: different mappings for API v1 vs v2
// - Error recovery: fallback values for missing data
// - Data cleansing: normalize inconsistent inputs
