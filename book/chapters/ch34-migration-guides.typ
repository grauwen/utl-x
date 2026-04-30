= Migration Guides

== Migrating from XSLT
// - XSLT templates → UTL-X expressions
// - xsl:for-each → map()
// - xsl:if / xsl:choose → if/else / match
// - xsl:value-of → $input.path
// - xsl:attribute → @attr syntax
// - Named templates → user-defined functions
// - Variables → let bindings
// - Side-by-side examples

== Migrating from DataWeave
// - Syntax similarities (both functional)
// - @ for attributes (same convention)
// - map, filter, reduce (same names)
// - writeAttributes option (same name, same default)
// - Key differences: $ prefix, output declaration syntax
// - MuleSoft → UTL-X migration patterns

== Migrating from jq
// - Dot notation (same concept)
// - Pipe operator (same |>)
// - map, select → map, filter
// - @base64, @json → base64Encode, renderJson
// - Key difference: UTL-X handles XML, CSV, YAML — jq is JSON-only

== Migrating from Custom Code
// - Java/C# XML processing → UTL-X
// - Python JSON manipulation → UTL-X
// - When to keep custom code vs when to use UTL-X
// - Hybrid approach: UTL-X for mapping, custom code for business logic

== Migrating from Tibco BW / BWCE
// - Mapper activity → .utlx transformation
// - BW process → UTLXe pipeline
// - Worker model comparison
// - Performance comparison
