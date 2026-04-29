= Grammar Reference

== UTLX 1.0 Grammar (EBNF)
// - Complete formal grammar
// - Header syntax: %utlx version, input, output declarations
// - Expression grammar: literals, operators, function calls
// - Statement grammar: let bindings, function definitions
// - Pattern matching grammar

== Header Syntax
// - %utlx 1.0
// - input <format> [options]
// - input: name1 format1, name2 format2 (multi-input)
// - output <format> [options]
// - --- (separator)

== Output Options
// - {encoding: "UTF-8"}
// - {delimiter: ";", headers: true}
// - {writeAttributes: true}
// - {regionalFormat: "european"}
// - {pretty: false}

== Expression Syntax
// - Precedence table (lowest to highest)
// - Associativity rules
// - Operator overloading (none — UTL-X is explicit)

== Reserved Words
// - Keywords: if, else, let, def, match, try, catch, true, false, null
// - Built-in: $input, map, filter, reduce (not reserved — stdlib functions)

== Identifier Rules
// - Start: letter, underscore
// - Continue: letter, digit, underscore, hyphen
// - Case-sensitive
// - Unicode support
