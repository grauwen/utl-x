= Writing Transformations

_A crash course in UTL-X — just enough to write production transformations. For the complete language reference, see the companion book "UTL-X: One Language, All Formats."_

== The .utlx File Structure

// Header (format declarations) + body (transformation expression)
// %utlx 1.0
// input json
// output json
// ---
// { ... transformation body ... }

== Property Access

// $input.customer.name
// $input.items[0].price
// $input.@id (XML attributes)

== Object Construction

// {
//   orderId: $input.id,
//   customer: $input.buyer.name,
//   total: $input.amount
// }

== Arrays: map, filter, reduce

// map($input.items, (item) -> { name: item.product, price: item.unitPrice * item.qty })
// filter($input.items, (item) -> item.price > 100)
// reduce($input.items, 0, (acc, item) -> acc + item.price)

== Conditionals

// if ($input.total > 1000) "premium" else "standard"
// match $input.status { "A" -> "Active", "I" -> "Inactive", _ -> "Unknown" }

== Let Bindings

// {
//   let subtotal = reduce($input.items, 0, (acc, i) -> acc + i.price)
//   let tax = subtotal * 0.21
//   subtotal: subtotal,
//   tax: tax,
//   total: subtotal + tax
// }

== Common Standard Library Functions

// String: concat, upperCase, lowerCase, trim, replace, contains, startsWith, substring, length
// Array: map, filter, reduce, find, sortBy, groupBy, flatMap, size, first, last
// Math: round, floor, ceil, abs, min, max
// Date: now, formatDate, parseDate
// Type: toString, toNumber, toBoolean, isNull, typeOf

== Multi-Format Transformations

// input json → output xml
// input xml → output json
// input csv → output json
// The header declares formats; the body works with the universal data model

== User-Defined Functions

// function discount(amount, tier) {
//   match tier {
//     "gold" -> amount * 0.9,
//     "silver" -> amount * 0.95,
//     _ -> amount
//   }
// }

== Worked Example: Invoice Transformation

// Complete example: Dynamics 365 JSON → UBL 2.1 XML
// Shows: property mapping, array iteration, conditional logic, date formatting

== Where to Learn More

// - Full language reference: "UTL-X: One Language, All Formats" (companion book)
// - Standard library: 650+ functions documented in ch16/ch17 of the companion book
// - Examples: github.com/grauwen/utl-x/examples/
