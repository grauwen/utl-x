# UTL-X vs. Apache Camel Simple Language

> **TL;DR:** Camel Simple is an expression language embedded in Camel routes for picking values off an Exchange and making small inline decisions. UTL-X is a standalone, format-agnostic transformation *language* for converting and reshaping entire payloads. They overlap at the edges — both can pull a field out of a message — but they aren't competing for the same job. Most real systems end up using both: Camel for routing and orchestration, UTL-X for the actual data transformation Camel routes to.

## Overview

| | Apache Camel Simple | UTL-X |
|---|---|---|
| **What it is** | Built-in expression/predicate language for Camel routes | Standalone functional transformation language |
| **Primary use** | Routing decisions, header/property access, lightweight inline expressions | Full payload transformation: XML ↔ JSON ↔ CSV ↔ YAML ↔ OData |
| **Runs where** | Only inside a Camel `CamelContext`, as part of a route | Anywhere — CLI, JVM library, or any language via UTLXe (Protobuf piped I/O) |
| **Designed for** | "Grab this header, test this condition, build this short string" | "Take this whole document and turn it into a different document" |
| **Syntax style** | `${...}` placeholder tokens inside plain strings | Declarative functional pipeline (`|>`, `map`, `filter`, pattern matching) |
| **Output shape** | A scalar value (String, Boolean, Integer, the evaluated object) | A fully structured document (object graph, then serialized to any target format) |
| **Type system** | Untyped/dynamic, relies on Java object coercion | Strongly typed, compile-time error checking |
| **License** | Apache-2.0 | AGPL-3.0 |
| **Standalone use** | No — requires Camel runtime and an Exchange | Yes — no host framework required |

## Why the comparison even comes up

Simple is part of Apache Camel, and Camel routes very often need to massage a message body between a `from()` and a `to()`. Teams reach for Simple because it's already there — zero extra dependency, same DSL as the rest of the route. The question that comes up is: *"Can I just do my XML-to-JSON mapping in Simple instead of adding a transformation layer?"*

The honest answer: Simple was never designed for that, and it shows the moment the mapping gets non-trivial.

## What Camel Simple is actually for

Simple's own documentation describes it as covering header/body access, conditionals for `filter()`/`choice()`, and short inline string building — not document transformation. A typical use looks like this:

```java
from("seda:order")
    .filter(simple("${header.foo} == 'high'"))
    .to("mock:fooOrders");
```

Or building a short string with embedded values:

```java
.setBody(simple("Hello ${in.header.name}, your order ${body.id} is ready"))
```

Or a date comparison:

```java
simple("${header.date} == ${date:now:yyyyMMdd}")
```

This is the right tool for these jobs. It's terse, it's already wired into the Exchange, and you don't pull in anything new.

## Where it breaks down

The moment you need to reshape a *document* — rename fields, restructure nesting, convert XML attributes to JSON keys, aggregate a list, join two payloads — Simple has no native vocabulary for it. There's no concept of "take this whole input and emit this whole output." You're stuck either:

- Chaining many `setBody(simple(...))` steps and string-building your way to a new structure, or
- Dropping into a `Processor` with raw Java/Groovy code, or
- Calling out to a real transformation engine (XSLT for XML, or DataWeave in the MuleSoft world) from inside the route.

In practice, almost nobody hand-builds JSON or XML documents with Simple expressions. It's used to pick a value out, not put a whole document together.

## Side-by-side: the same transformation

**Goal:** From an order, build an invoice with a customer block and a computed total.

### Camel Simple — not really built for this

Simple has no `map`/`filter`/object-construction model, so this isn't expressible as a Simple expression at all. The realistic Camel equivalent drops out of Simple entirely and into a `Processor`:

```java
.process(exchange -> {
    Order order = exchange.getIn().getBody(Order.class);
    Map<String, Object> invoice = new HashMap<>();
    invoice.put("id", "INV-" + order.getId());
    invoice.put("customerName", order.getCustomer().getName());
    double total = order.getItems().stream()
        .mapToDouble(i -> i.getQuantity() * i.getPrice())
        .sum();
    invoice.put("grandTotal", total * 1.08);
    exchange.getIn().setBody(invoice);
})
```

This is Java, not Simple — which is itself the point: Simple can't carry this logic.

### UTL-X — this is the core use case

```
%utlx 1.0
input xml
output json
---
{
  invoice: {
    id: "INV-" + $input.Order.@id,
    customer: {
      name: $input.Order.Customer.Name
    },
    grandTotal: sum($input.Order.Items.Item |> map(item =>
      parseNumber(item.@quantity) * parseNumber(item.@price)
    )) * 1.08
  }
}
```

One declarative expression, no host-language escape hatch, runs the same way regardless of whether the input is XML, JSON, CSV, or YAML.

## Where Simple genuinely wins

To be fair to Simple — it's the right choice when:

- You're already inside a Camel route and just need a routing predicate (`choice()`, `filter()`)
- You need a one-line header/property lookup, not a transformation
- You want zero additional dependencies beyond `camel-core`
- The logic is truly "if this header equals that value"

UTL-X would be overkill for a one-line routing condition — that's not what it's for either.

## How they fit together

These aren't mutually exclusive. A common, sensible pattern:

```java
from("direct:incomingOrder")
    .filter(simple("${header.orderType} == 'standard'"))   // Simple: routing
    .to("exec:utlx?args=transform order-to-invoice.utlx")    // UTL-X: transformation
    .to("jms:queue:invoices");
```

Camel handles *where the message goes and whether it should go there*. UTL-X handles *what the message looks like once it gets there*. Each language does the thing it was actually designed to do.

## Summary

| Question | Camel Simple | UTL-X |
|---|---|---|
| Picking a header or testing a condition? | ✅ Yes, this is its job | Overkill |
| Building/reshaping a structured document? | ❌ Not designed for it | ✅ Yes, this is its job |
| Need it to run outside Camel? | ❌ No, Camel-only | ✅ CLI, library, or any language via UTLXe |
| Need format conversion (XML ↔ JSON ↔ CSV ↔ YAML)? | ❌ No | ✅ Native |
| Need compile-time type checking? | ❌ Dynamic/untyped | ✅ Strongly typed |

---

*See also: [UTL-X vs. XSLT](#) · [UTL-X vs. DataWeave](#) · [UTL-X vs. JSONata](#)*
