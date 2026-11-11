# UTL-X vs MapStruct

## Overview

**MapStruct** is a Java annotation processor that generates type-safe bean mapping code at compile time. It maps between Java POJOs — e.g., converting a `CustomerEntity` to a `CustomerDTO`.

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

## What MapStruct Does Well

MapStruct excels at **Java-to-Java** object mapping:

```java
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

## UTL-X Equivalent

The same mapping in UTL-X:

```bash
echo '{"firstName":"Alice","lastName":"Johnson","emailAddress":"alice@example.com"}' | utlx -e '{
  fullName: .firstName + " " + .lastName,
  email: .emailAddress
}'
```

**But UTL-X goes further** — the same works with XML, CSV, or YAML input:

```bash
# XML input (same expression)
cat customer.xml | utlx -e '{fullName: .Customer.firstName + " " + .Customer.lastName, email: .Customer.emailAddress}'

# CSV input
cat customers.csv | utlx -e '. |> map(c => {fullName: c.firstName + " " + c.lastName, email: c.emailAddress})' --from csv
```

## Real-World Architecture Comparison

### Typical MapStruct Flow

```
XML File -> JAXB Unmarshal -> Java POJO -> MapStruct -> Java DTO -> Jackson Serialize -> JSON
```

Requires: JAXB annotations, Jackson annotations, MapStruct mapper, Spring wiring, build plugin.

### Equivalent UTL-X Flow

```bash
cat order.xml | utlx -e '{id: .Order.@id, customer: .Order.Customer.Name}' --to json
```

One command. No Java code.

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

```bash
cat order.json | utlx -e '{
  invoiceId: .orderId,
  customerName: .customer.name,
  total: sum(.items |> map(i => i.price * i.quantity)),
  itemCount: count(.items)
}'
```

Works with XML, JSON, CSV, or YAML input — no POJOs, no annotations, no build plugins.

## When to Choose UTL-X Over MapStruct

- Need to convert between **data formats** (XML, JSON, CSV, YAML)
- Need **CLI tooling** for shell scripts, pipelines, CI/CD
- Need **schema transformation** (XSD to JSON Schema, Avro to Protobuf)
- Need to combine data from **multiple sources and formats**
- Want transformations **decoupled from Java code**
- Need to process data **without writing a Java application**

## When to Choose MapStruct

- Map between **Java POJOs within a Java application**
- Need **compile-time type safety** with Java's type system
- Want **zero runtime overhead** (generated code, no reflection)
- Are already in a **Spring/Jakarta EE ecosystem**
- Only work with Java objects, never with raw data files

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
