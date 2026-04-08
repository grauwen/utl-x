# UTL-X vs MapStruct

## Overview

**MapStruct** is a Java annotation processor that generates type-safe bean mapping code at compile time. It maps between Java POJOs (Plain Old Java Objects) — e.g., converting a `CustomerEntity` to a `CustomerDTO`.

**UTL-X** is a format-agnostic transformation language that converts between data formats (XML, JSON, CSV, YAML, OData) and schema formats (XSD, JSON Schema, Avro, Protobuf) using a declarative functional syntax.

They solve **different problems** but often appear in the same integration architecture.

## Quick Comparison

| Feature | MapStruct | UTL-X |
|---------|-----------|-------|
| **Purpose** | Java object-to-object mapping | Format-agnostic data transformation |
| **Input/Output** | Java POJOs only | XML, JSON, CSV, YAML, OData, schemas |
| **Language** | Java annotations + generated code | Dedicated transformation DSL |
| **Runtime** | JVM only | JVM or GraalVM native binary |
| **Type Safety** | Compile-time (Java types) | Compile-time (UTL-X type system) |
| **License** | Apache 2.0 | AGPL-3.0 / Commercial |
| **Standalone CLI** | No (library only) | Yes (`cat data.xml \| utlx`) |
| **Format Awareness** | None (works with Java objects) | Built-in parsers for 11 formats |
| **Schema Support** | None | XSD, JSON Schema, Avro, Protobuf, OData/EDMX |
| **Stdlib** | N/A (uses Java methods) | 652 built-in functions |
| **Learning Curve** | Low (if you know Java) | Low-Medium |
| **Code Generation** | Yes (annotation processor) | No (interpreted/compiled DSL) |

## What MapStruct Does Well

MapStruct excels at **Java-to-Java** object mapping:

```java
// Source POJO
public class CustomerEntity {
    private String firstName;
    private String lastName;
    private String emailAddress;
    // getters/setters
}

// Target POJO
public class CustomerDTO {
    private String fullName;
    private String email;
    // getters/setters
}

// MapStruct mapper
@Mapper
public interface CustomerMapper {
    @Mapping(source = "emailAddress", target = "email")
    @Mapping(target = "fullName", expression = "java(entity.getFirstName() + \" \" + entity.getLastName())")
    CustomerDTO toDTO(CustomerEntity entity);
}
```

MapStruct generates the implementation at compile time — zero reflection, zero runtime overhead.

## What MapStruct Cannot Do

1. **No format conversion** — Cannot read XML, JSON, CSV, or YAML files directly
2. **No CLI usage** — Cannot pipe data through the command line
3. **No standalone operation** — Requires a Java application to run
4. **No schema transformation** — Cannot convert between XSD, JSON Schema, Avro, Protobuf
5. **No cross-format joins** — Cannot combine XML + JSON + CSV in one operation
6. **Java-only** — Tightly coupled to JVM and Java type system

## UTL-X Equivalent

The same CustomerEntity → CustomerDTO mapping in UTL-X:

```utlx
%utlx 1.0
input json
output json
---
{
  fullName: $input.firstName + " " + $input.lastName,
  email: $input.emailAddress
}
```

Run it:
```bash
echo '{"firstName":"Alice","lastName":"Johnson","emailAddress":"alice@example.com"}' | utlx
```

Output:
```json
{
  "fullName": "Alice Johnson",
  "email": "alice@example.com"
}
```

**But UTL-X goes further** — the same transformation works regardless of format:

```bash
# JSON input
echo '{"firstName":"Alice","lastName":"Johnson","emailAddress":"alice@example.com"}' | utlx transform mapper.utlx

# XML input (same script!)
echo '<Customer><firstName>Alice</firstName><lastName>Johnson</lastName><emailAddress>alice@example.com</emailAddress></Customer>' | utlx transform mapper.utlx

# CSV input (same script!)
echo 'firstName,lastName,emailAddress
Alice,Johnson,alice@example.com' | utlx transform mapper.utlx --from csv
```

MapStruct would need separate Jackson/JAXB deserialization code for each format before mapping.

## Real-World Architecture Comparison

### Typical MapStruct Flow

```
XML File → JAXB Unmarshal → Java POJO → MapStruct → Java DTO → Jackson Serialize → JSON
```

Requires:
- JAXB annotations on source POJOs
- Jackson annotations on target DTOs
- MapStruct mapper interface
- Spring/CDI wiring
- Build plugin configuration

### Equivalent UTL-X Flow

```
XML File → utlx → JSON
```

One command:
```bash
cat order.xml | utlx --to json
```

Or with transformation logic:
```bash
utlx transform order-to-invoice.utlx order.xml -o invoice.json
```

## When to Choose UTL-X Over MapStruct

**Choose UTL-X if you:**

- Need to convert between **data formats** (XML, JSON, CSV, YAML)
- Need **CLI tooling** for shell scripts, pipelines, CI/CD
- Need **schema transformation** (XSD to JSON Schema, Avro to Protobuf)
- Need to combine data from **multiple sources and formats**
- Want transformations **decoupled from Java code** (no recompile on mapping changes)
- Need to process data **without writing a Java application**
- Want a single tool for **ETL pipelines** across formats
- Need **652 built-in functions** for string, date, math, encoding, etc.

**Choose MapStruct if you:**

- Map between **Java POJOs within a Java application**
- Need **compile-time type safety** with Java's type system
- Want **zero runtime overhead** (generated code, no reflection)
- Are already in a **Spring/Jakarta EE ecosystem**
- Need **IDE support** (refactoring, auto-complete on Java types)
- Only work with Java objects, never with raw data files

## Side-by-Side: Complex Mapping

### MapStruct

```java
@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(source = "orderId", target = "invoiceId")
    @Mapping(source = "customer.name", target = "customerName")
    @Mapping(target = "total", expression = "java(order.getItems().stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum())")
    @Mapping(target = "itemCount", expression = "java(order.getItems().size())")
    InvoiceDTO toInvoice(OrderEntity order);
}
```

Plus: POJO classes, getters/setters, Jackson config, JAXB config, Spring wiring.

### UTL-X

```utlx
%utlx 1.0
input auto
output json
---
{
  invoiceId: $input.orderId,
  customerName: $input.customer.name,
  total: sum($input.items |> map(i => i.price * i.quantity)),
  itemCount: count($input.items)
}
```

Works with XML, JSON, CSV, or YAML input — no POJOs, no annotations, no build plugins.

## Coexistence

UTL-X and MapStruct are not mutually exclusive:

- Use **MapStruct** for internal Java object mapping within your application
- Use **UTL-X** for external data transformation (file conversion, API integration, ETL)
- Use **UTL-X CLI** in CI/CD pipelines to validate and transform data before it enters your Java application

```bash
# Transform incoming XML to JSON, then feed to your Java service
cat incoming-order.xml | utlx --to json | curl -X POST -d @- http://localhost:8080/api/orders
```

## Bottom Line

MapStruct is the right tool for mapping Java objects within a Java application — fast, type-safe, and zero-overhead. UTL-X is the right tool for everything outside the Java type system: format conversion, multi-format ETL, schema transformation, and CLI-based data processing. In a modern integration architecture, you'd likely use both.
