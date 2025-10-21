# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

# Universal Transformation Language Extended (UTL-X): An Open Source Alternative to DataWeave

## Project Leadership

**Concept Originator & Project Driver:** Ir. Marcel A. Grauwen

## Executive Summary

DataWeave, now owned by Salesforce through MuleSoft acquisition, demonstrates that a **format-agnostic functional transformation language** can elegantly handle XML, JSON, CSV, and other data formats. This document proposes **UTL-X (Universal Transformation Language Extended)**, an open-source alternative with similar capabilities but dual-licensed governance and XSLT-inspired declarative principles.

**Licensing Model:** UTL-X is dual-licensed under:
* **GNU Affero General Public License v3.0 (AGPL-3.0)** - Open Source
* **Commercial License** - For proprietary use

**Note:** The designation "UTL-X" is used to avoid confusion with other existing UTL (Universal Template Language, Universal Test Language) projects.

---

## 1. DataWeave: Lessons Learned

### 1.1 What DataWeave Got Right

**1. Format Abstraction:**
```dataweave
%dw 2.0
output application/json
---
{
  invoice: {
    id: payload.Order.@id,
    customer: payload.Order.Customer.Name,
    total: sum(payload.Order.Items.*Item map ($.@price * $.@quantity))
  }
}
```

This same logic works whether `payload` is XML, JSON, or CSV—the language abstracts the source format.

**2. Functional Programming Paradigm:**
- Immutable data structures
- Pure functions (no side effects)
- Higher-order functions (map, filter, reduce)
- Pattern matching
- Type inference

**3. Built-in Format Readers/Writers:**
- `application/xml` → Parse XML
- `application/json` → Parse JSON
- `application/csv` → Parse CSV
- `application/java` → Java objects
- Custom formats via modules

**4. Expression-Based (Not Statement-Based):**
Every construct returns a value—no void functions, everything composes.

**5. Selector Syntax:**
- XML: `payload.Order.Customer.Name`
- JSON: `payload.order.customer.name`
- Unified navigation regardless of source format

### 1.2 DataWeave Limitations

**1. Proprietary:**
- Owned by Salesforce/MuleSoft
- License restrictions
- Vendor lock-in
- No option for commercial use without MuleSoft platform

**2. Limited Community:**
- Small ecosystem outside MuleSoft
- Few third-party tools
- No independent governance

**3. Performance:**
- JVM-based (startup overhead)
- Not optimized for high-frequency transformations

**4. Learning Curve:**
- Custom syntax (not based on existing standards)
- Limited documentation outside MuleSoft

---

## 2. Existing Open Source Alternatives (Incomplete)

### 2.1 JSONata (JavaScript-focused)

**What it is:**
Lightweight query and transformation language for JSON, inspired by XPath.

**Example:**
```jsonata
{
  "invoice": {
    "id": Order.id,
    "customer": Order.customer.name,
    "total": $sum(Order.items.(price * quantity))
  }
}
```

**Strengths:**
- MIT licensed (truly open source)
- JavaScript native
- XPath-inspired selector syntax
- Good for JSON

**Limitations:**
- **JSON-only** (no XML, CSV support)
- No compilation/optimization
- Limited type system
- Small community

### 2.2 JOLT (Declarative JSON-to-JSON)

**What it is:**
JSON-to-JSON transformation using declarative specifications.

**Example:**
```json
{
  "operation": "shift",
  "spec": {
    "order": {
      "id": "invoice.id",
      "customer": {
        "name": "invoice.customer"
      },
      "items": {
        "*": {
          "price": "invoice.items[&1].price"
        }
      }
    }
  }
}
```

**Strengths:**
- Apache 2.0 licensed
- Declarative
- Good for simple mappings

**Limitations:**
- **JSON-only**
- Not functional/programmable
- Complex transformations become unwieldy
- No arithmetic/logic expressions

### 2.3 JQ (Command-line JSON processor)

**What it is:**
Command-line tool for JSON processing with its own query language.

**Example:**
```jq
{
  invoice: {
    id: .order.id,
    customer: .order.customer.name,
    total: [.order.items[] | .price * .quantity] | add
  }
}
```

**Strengths:**
- MIT licensed
- Powerful for JSON
- Functional programming style
- Active community

**Limitations:**
- **JSON-only** (though can read XML with plugins)
- Command-line tool (not library)
- C implementation (harder to embed)

### 2.4 XSLT (XML-only)

**Already discussed extensively.**

**Limitation:** Only works with XML, despite being the most mature transformation language.

---

## 3. Proposed Solution: UTL-X (Universal Transformation Language Extended)

### 3.1 Design Principles

**1. Format Agnostic:**
Input and output can be XML, JSON, CSV, YAML, or custom formats—transformation logic remains the same.

**2. Functional Programming:**
Inspired by DataWeave's functional approach but with XSLT's declarative template matching.

**3. XSLT Heritage:**
Learn from 25+ years of XSLT design, adoption patterns, and mistakes.

**4. Dual-Licensed Model:**
- **AGPL-3.0** for open source projects (with copyleft requirements)
- **Commercial License** for proprietary applications (without copyleft restrictions)

**5. Performance First:**
Compile-time optimization, efficient runtime, minimal memory footprint.

**6. Multi-Runtime:**
JVM, JavaScript (Node/Browser), Native (GraalVM/LLVM) implementations.

---

### 3.2 UTL-X Language Specification (v1.0 Draft)

#### 3.2.1 Basic Syntax

**Hello World:**
```utlx
%utlx 1.0
input auto      // Auto-detect format (XML, JSON, CSV)
output json     // Output as JSON
---
{
  message: "Hello, " + input.name
}
```

**If input is XML:**
```xml
<person><name>World</name></person>
```

**If input is JSON:**
```json
{"person": {"name": "World"}}
```

**Output (both cases):**
```json
{"message": "Hello, World"}
```

#### 3.2.2 Selectors (Format-Agnostic)

**Universal Path Syntax:**
```utlx
// Works for XML, JSON, CSV
input.Order.Customer.Name          // Simple path
input.Order.Items[*]               // Array/element iteration
input.Order.Items[0]               // Index access
input.Order.@id                    // Attribute (XML) / Property (JSON)
input..ProductCode                 // Recursive descent
input.Order[Total > 1000]          // Predicate filtering
```

**Translation to native format:**
- **XML:** `input.Order.Customer.Name` → XPath `/Order/Customer/Name`
- **JSON:** `input.Order.Customer.Name` → JSONPath `$.Order.Customer.Name`
- **CSV:** `input.Order.Customer.Name` → Column access with headers

#### 3.2.3 Template Matching (XSLT-inspired)

```utlx
%utlx 1.0
input xml
output json
---

// Template matching (like XSLT)
template match="Order" {
  invoice: {
    id: @id,
    date: @date,
    customer: apply(Customer),
    items: apply(Items/Item),
    total: sum(Items/Item/(@price * @quantity))
  }
}

template match="Customer" {
  name: Name,
  email: Email
}

template match="Item" {
  sku: @sku,
  quantity: @quantity,
  price: @price,
  subtotal: @price * @quantity
}
```

**Key Features:**
- Pattern matching on structure
- Recursive template application
- Context-aware transformations

#### 3.2.4 Functional Constructs

**Map/Filter/Reduce:**
```utlx
{
  expensiveItems: input.items 
    |> filter(item => item.price > 100)
    |> map(item => {
         sku: item.sku,
         discount: item.price * 0.10
       })
    |> sortBy(item => item.sku),
    
  totalValue: input.items 
    |> map(item => item.price * item.quantity)
    |> sum()
}
```

**Pattern Matching:**
```utlx
match input.orderType {
  "express" => {
    processingTime: "24 hours",
    shippingCost: 15.00
  },
  "standard" => {
    processingTime: "3-5 days",
    shippingCost: 5.00
  },
  _ => {
    processingTime: "unknown",
    shippingCost: 0
  }
}
```

**Conditionals:**
```utlx
{
  discount: if (input.customer.type == "VIP") 
              input.total * 0.20 
            else if (input.total > 1000) 
              input.total * 0.10 
            else 
              0,
              
  freeShipping: input.customer.type == "VIP" && input.total > 500
}
```

#### 3.2.5 Functions (User-Defined)

**Naming Requirement**: User-defined functions **MUST** start with an uppercase letter (PascalCase) to prevent collisions with stdlib functions.

```utlx
// ✅ VALID - User-defined functions start with uppercase (PascalCase)
function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
}

function FormatCurrency(value: Number): String {
  "$" + value.toFixed(2)
}

// Usage
{
  subtotal: input.total,
  tax: CalculateTax(input.total, 0.08),
  total: FormatCurrency(input.total * 1.08)
}

// ❌ INVALID - lowercase function names are reserved for stdlib
function calculateTax(amount, rate) {
  // ERROR: User-defined functions must start with uppercase letter (PascalCase).
  // Got: 'calculateTax'. Try: 'CalculateTax'.
  // This prevents collisions with stdlib functions which use lowercase/camelCase.
}
```

**Why PascalCase?**
- **Prevents collisions**: All stdlib functions use lowercase/camelCase (`sum`, `map`, `filter`, etc.)
- **Visual distinction**: Immediately clear which functions are user-defined
- **Future-proof**: Enables module system in future versions

**Built-in Functions:**
```utlx
// String functions
upper(str), lower(str), trim(str), substring(str, start, end)
concat(str1, str2, ...), split(str, delimiter), join(array, delimiter)

// Array functions
map(array, fn), filter(array, fn), reduce(array, fn, initial)
sum(array), avg(array), min(array), max(array)
sort(array), sortBy(array, fn), reverse(array)
first(array), last(array), take(array, n), drop(array, n)

// Date functions
now(), parseDate(str, format), formatDate(date, format)
addDays(date, n), diffDays(date1, date2)

// Math functions
abs(n), round(n), ceil(n), floor(n), pow(base, exp)

// Type functions
typeOf(value), isString(value), isNumber(value), isArray(value)
```

#### 3.2.6 Variables and Let Bindings

```utlx
{
  let subtotal = sum(input.items.(price * quantity)),
  let taxRate = if (input.customer.state == "CA") 0.0875 else 0.06,
  let tax = subtotal * taxRate
  
  invoice: {
    subtotal: subtotal,
    tax: tax,
    total: subtotal + tax
  }
}
```

#### 3.2.7 Format-Specific Handling

**CSV Input:**
```utlx
%utlx 1.0
input csv {
  headers: true,
  delimiter: ",",
  quote: "\""
}
output json
---
{
  customers: input.rows |> map(row => {
    id: row.CustomerID,
    name: row.Name,
    email: row.Email
  })
}
```

**XML Namespaces:**
```utlx
%utlx 1.0
input xml {
  namespaces: {
    "ord": "http://example.com/orders",
    "cust": "http://example.com/customers"
  }
}
output json
---
{
  orderId: input.{"ord:Order"}.@id,
  customer: input.{"ord:Order"}.{"cust:Customer"}.Name
}
```

#### 3.2.7 Multiple Inputs (✅ IMPLEMENTED - 2025-10-21)

**Implemented Syntax:**
```utlx
%utlx 1.0
input: input1 xml, input2 json, input3 csv
output xml
---
{
  Combined: {
    FromXML: @input1.Customer,
    FromJSON: @input2.order,
    FromCSV: @input3.rows[0]
  }
}
```

**CLI Usage:**
```bash
utlx transform script.utlx \
  --input input1=file1.xml \
  --input input2=file2.json \
  --input input3=file3.csv \
  -o output.xml
```

**Key Features:**
- ✅ Comma-separated input declarations with colon
- ✅ Named inputs accessible via `@inputName`
- ✅ Per-input format options
- ✅ Backward compatible with single input
- ✅ Encoding detection per input
- ✅ CLI support for multiple `--input` flags

**Documentation:** See `docs/language-guide/multiple-inputs-outputs.md`

**Advantages over DataWeave:**
- Cleaner syntax (`:` separator vs `%input` directive)
- Inline in header (no separate directives)
- Consistent `@` prefix for inputs

#### 3.2.8 Multiple Outputs (📋 PLANNED)

**Proposed Syntax:**
```utlx
%utlx 1.0
input xml
output: summary json, details xml, report csv
---
{
  summary: {
    orderCount: count(@input.Orders.Order),
    totalValue: sum(@input.Orders.Order.Total)
  },
  details: @input,
  report: {
    headers: ["Order ID", "Customer", "Total"],
    rows: @input.Orders.Order |> map(order => [
      order.@id,
      order.Customer.Name,
      order.Total
    ])
  }
}
```

**Planned CLI Usage:**
```bash
utlx transform script.utlx -i data.xml \
  --output summary=summary.json \
  --output details=details.xml \
  --output report=report.csv
```

**Status:** Syntax designed, implementation pending

**Note:** This gives UTL-X a major advantage over DataWeave, which requires multiple Transform Message components for multiple outputs.

---

## 4. UTL-X Architecture

### 4.1 Compilation Pipeline

```
UTL-X Source (.utlx)
    ↓
[Lexer] → Tokens
    ↓
[Parser] → Abstract Syntax Tree (AST)
    ↓
[Type Checker] → Typed AST
    ↓
[Optimizer] → Optimized AST
    ↓
[Code Generator]
    ↓
┌─────────┬──────────┬──────────┐
│         │          │          │
JVM      JavaScript  Native    WASM
Bytecode   Module    Binary   Module
```

### 4.2 Runtime Architecture

```
Input Data (XML/JSON/CSV/...)
    ↓
[Format Parser] → Universal Data Model (UDM)
    ↓                      ↓
[UTL-X Runtime]      [Compiled Transform]
    ↓
Output UDM
    ↓
[Format Serializer] → Output Data (XML/JSON/CSV/...)
```

**Universal Data Model (UDM):**
Internal representation that abstracts over all formats:

```typescript
type UDM = 
  | Scalar(value: string | number | boolean | null)
  | Array(elements: UDM[])
  | Object(properties: Map<string, UDM>, attributes: Map<string, string>)
  | Date(value: DateTime)
```

**Key Insight:** Once data is in UDM, transformation logic is identical regardless of source/target format.

### 4.3 Performance Optimizations

**Init Time (Compilation):**
1. Parse UTL source
2. Type inference and checking
3. Constant folding
4. Dead code elimination
5. Template inlining (for small templates)
6. Generate optimized code

**Runtime:**
1. Parse input format → UDM (streaming where possible)
2. Execute compiled transformation
3. Serialize UDM → output format

**Caching:**
- Compiled transforms cached (like XSLT Templates)
- Format parsers cached
- Serializers cached

**Expected Performance:**
- Init time: 50-200ms (compilation)
- Runtime: 3-15ms per document (competitive with DataWeave)

---

## 5. Implementation Roadmap

### 5.1 Phase 1: Core Language (Months 1-6)

**Deliverables:**
- Language specification v1.0
- Parser and AST generator
- Type system implementation
- JVM runtime implementation
- Support for JSON and XML

**Tech Stack:**
- Language: Kotlin (for JVM implementation)
- Parser: ANTLR4 (grammar definition)
- Testing: JUnit, property-based testing

**Example Project Structure:**
```
utlx-lang/
├── spec/              # Language specification
├── grammar/           # ANTLR4 grammar
├── core/              # Core language implementation
│   ├── lexer/
│   ├── parser/
│   ├── ast/
│   ├── types/
│   └── runtime/
├── formats/           # Format parsers/serializers
│   ├── json/
│   ├── xml/
│   └── csv/
├── stdlib/            # Standard library functions
└── tests/
```

### 5.2 Phase 2: Format Support (Months 7-9)

**Add Support For:**
- CSV (with various dialects)
- YAML
- Properties files
- Custom formats via plugins

**Plugin Architecture:**
```kotlin
interface FormatParser {
    fun parse(input: InputStream): UDM
    fun serialize(udm: UDM): OutputStream
}

// Users can implement custom formats
class ProtobufParser : FormatParser { ... }
```

### 5.3 Phase 3: Tooling (Months 10-12)

**Deliverables:**
- VS Code extension (syntax highlighting, autocomplete)
- IntelliJ IDEA plugin
- CLI tool (`utl transform input.xml transform.utl`)
- Maven/Gradle plugins
- Online playground (try UTL in browser)

### 5.4 Phase 4: JavaScript Runtime (Months 13-15)

**Transpile to JavaScript:**
```
UTL-X Source → JavaScript Module (runs in Node.js or browser)
```

**Benefits:**
- Frontend transformations
- Serverless functions
- Cross-platform compatibility

### 5.5 Phase 5: Native Runtime (Months 16-18)

**Compile to Native:**
```
UTL-X Source → LLVM IR → Native Binary
```

**Benefits:**
- Maximum performance
- No JVM/Node.js dependency
- Embedded systems support

---

## 6. Comparison: UTL-X vs DataWeave vs XSLT

| Feature | XSLT | DataWeave | UTL-X (Proposed) |
|---------|------|-----------|------------------|
| **License** | W3C (Open) | Proprietary | AGPL-3.0 / Commercial |
| **Commercial Use** | Free | Requires MuleSoft | Commercial License Available |
| **Formats** | XML only | XML, JSON, CSV, Java | XML, JSON, CSV, YAML, extensible |
| **Paradigm** | Declarative templates | Functional expressions | Hybrid (templates + functional) |
| **Learning Curve** | Steep | Medium | Medium |
| **Performance** | Good (compiled) | Good | Excellent (optimized compilation) |
| **Tooling** | Excellent (mature) | Good (MuleSoft ecosystem) | TBD (planned) |
| **Community** | Large, aging | Medium (vendor-locked) | TBD (dual-licensed community) |
| **Runtime** | JVM, .NET, C++ | JVM only | JVM, JavaScript, Native |
| **Type System** | XSD-aware | Strong, inferred | Strong, inferred |
| **Functions** | Built-in + custom (XSLT 2.0+) | Rich standard library | Rich standard library |
| **Pattern Matching** | Yes (templates) | Yes (case/match) | Yes (templates + match) |
| **Governance** | W3C | MuleSoft/Salesforce | Independent / Commercial |
| **Project Leadership** | W3C Working Group | Salesforce/MuleSoft | Ir. Marcel A. Grauwen |
| **Business Model** | Standards body | SaaS Platform | Open Core (AGPL + Commercial) |

---

## 7. Adoption Strategy

### 7.1 Target Audiences

**Primary:**
1. **Open Source Projects:** Developers building AGPL-compatible applications
2. **Enterprise Customers:** Organizations needing commercial support and proprietary licensing
3. **Integration Teams:** ESB/ETL developers currently using XSLT or custom code

**Secondary:**
1. **API Developers:** REST/GraphQL API response transformations
2. **Data Engineers:** ETL pipelines with multiple formats
3. **Frontend Developers:** JavaScript runtime for client-side transformations

### 7.2 Migration Paths

**From XSLT:**
```
XSLT Template → UTL Template (1:1 mapping for most cases)
XPath expressions → UTL selectors (similar syntax)
```

**From DataWeave:**
```
DataWeave scripts → UTL scripts (syntax similarity)
MuleSoft modules → UTL standard library
```

**From Custom Code:**
```
Java/JavaScript transformation logic → UTL declarative transformation
```

### 7.3 Integration Points

**Apache Camel:**
```java
from("file:input?noop=true")
  .to("utlx:transform.utlx?output=json")
  .to("kafka:orders");
```

**Spring Integration:**
```xml
<int:transformer 
  input-channel="xmlChannel" 
  output-channel="jsonChannel"
  ref="utlxTransformer">
  <constructor-arg value="classpath:transform.utlx"/>
</int:transformer>
```

**Standalone Library:**
```java
UTLXEngine engine = UTLXEngine.builder()
    .compile(new File("transform.utlx"))
    .build();

String output = engine.transform(inputXML, Format.JSON);
```

---

## 8. Governance and Licensing Model

### 8.1 Dual-License Structure

**Open Source License: GNU AGPL-3.0**
- **Use Case:** Open source projects, educational use, evaluation
- **Requirements:**
  - Source code modifications must be shared
  - Network use triggers copyleft (AGPL provision)
  - Derivative works must use AGPL-3.0
- **Benefits:**
  - Free to use
  - Full access to source code
  - Community-driven improvements
  - No vendor lock-in

**Commercial License**
- **Use Case:** Proprietary applications, SaaS products, embedded systems
- **Benefits:**
  - No copyleft requirements
  - Proprietary modifications allowed
  - Priority support
  - Legal protection and indemnification
  - Custom feature development
- **Pricing Models:**
  - Per-developer licensing
  - Runtime/deployment licensing
  - Enterprise site licenses
  - OEM/redistribution licenses

### 8.2 Why AGPL-3.0?

**Rationale:**
1. **Strong Copyleft:** Ensures improvements benefit the community
2. **Network Provision:** Prevents "SaaS loophole" (services must share code)
3. **Enterprise-Friendly:** Forces commercial decision for proprietary use
4. **Sustainable Model:** Generates revenue for continued development
5. **Clear Licensing:** Binary choice: open source (AGPL) or commercial

**Comparison with Alternatives:**
- **Apache 2.0:** Too permissive, allows proprietary forks without contribution
- **GPL-3.0:** Doesn't cover network services (SaaS loophole)
- **AGPL-3.0:** Perfect for transformation services (API/integration layer)

### 8.3 Project Structure

```
UTL-X Project
├── Core Team (Led by Ir. Marcel A. Grauwen)
│   ├── Project Lead
│   ├── Technical Architects
│   ├── Core Developers
│   └── Community Managers
├── Open Source Components (AGPL-3.0)
│   ├── utlx-core (language runtime)
│   ├── utlx-cli (command-line tools)
│   ├── utlx-formats (format parsers)
│   └── utlx-examples (documentation, examples)
├── Commercial Components
│   ├── Enterprise connectors
│   ├── Performance optimizations
│   ├── Management console
│   └── Advanced tooling
└── Community Contributors
    ├── Feature proposals
    ├── Bug reports
    ├── Documentation
    └── Extensions
```

### 8.4 Governance Model

**Decision Making:**
1. **Technical Decisions:** Core team with community input
2. **Language Specification:** Public RFC process
3. **Commercial Features:** Core team based on customer needs
4. **Community Contributions:** Meritocracy-based acceptance

**Release Process:**
- Open source releases: Every 3 months
- Commercial releases: Every 6 months (includes enterprise features)
- Security patches: As needed (both tracks)
- LTS versions: Annually with 3-year support

**Contribution Guidelines:**
- AGPL-3.0 for core contributions
- Contributor License Agreement (CLA) for code ownership clarity
- Code review by core team members
- Automated testing and CI/CD

### 8.5 Revenue Model

**Income Sources:**
1. **Commercial Licenses:** Primary revenue stream
2. **Support Contracts:** Enterprise support (SLAs, dedicated support)
3. **Training & Certification:** Official UTL-X training programs
4. **Professional Services:** Implementation assistance, custom development
5. **Cloud Hosting:** Managed UTL-X transformation services

**Reinvestment:**
- 60% core development
- 20% community programs (documentation, tools)
- 10% marketing and evangelism
- 10% infrastructure and operations

---

## 9. Success Metrics

### 9.1 Adoption Metrics (Year 1)

- 5,000+ GitHub stars
- 100+ contributors
- 1,000+ production deployments (AGPL)
- 50+ commercial customers
- 10+ enterprise partners

### 9.2 Technical Metrics

- Performance within 10% of DataWeave
- Support for 6+ data formats
- 95% XSLT 1.0 feature parity
- Sub-5ms average transformation time

### 9.3 Community Metrics

- 50+ blog posts/tutorials
- 10+ conference presentations
- Active forum with <24hr response time
- Bi-weekly community calls

### 9.4 Business Metrics

- Break-even within 18 months
- $1M+ ARR by Year 2
- 30% year-over-year growth
- 80%+ customer retention rate

---

## 10. Conclusion

### 10.1 Why UTL-X Can Succeed

**1. Real Need:**
- DataWeave proves the concept works
- Vendor lock-in concerns drive demand for open alternative
- XML ecosystem needs modern transformation approach
- AGPL creates clear path to commercial revenue

**2. Strong Foundation:**
- Learn from 25+ years of XSLT experience
- Incorporate DataWeave's best ideas
- Leverage modern language design (type inference, functional programming)

**3. Sustainable Business Model:**
- AGPL ensures community contributions
- Commercial licensing funds development
- Clear value proposition for enterprises
- No vendor lock-in fears

**4. Multi-Runtime:**
- JVM for enterprise
- JavaScript for web/serverless
- Native for performance-critical

**5. Visionary Leadership:**
- Ir. Marcel A. Grauwen brings deep expertise in XML/JSON transformation ecosystems
- Clear vision for bridging the gap between proprietary and open-source solutions
- Commitment to sustainable, community-driven development
- Experience in both open source and commercial software models

### 10.2 Challenges

**1. Dual-License Complexity:**
- Need clear communication about licensing
- Must balance open source and commercial interests
- Potential community resistance to AGPL
- Requires legal expertise for licensing

**2. Network Effects:**
- DataWeave has existing user base
- XSLT has 25-year ecosystem
- Need critical mass quickly

**3. Resource Requirements:**
- Estimated 3-4 full-time developers for 18 months
- Legal costs for licensing framework
- Sales and marketing for commercial licenses
- Community management

**4. Competition:**
- DataWeave continues evolving
- JSONata gaining traction for JSON
- Custom code always an option
- Other dual-licensed projects

### 10.3 Call to Action

**For Open Source Users:**
1. **Try UTL-X** under AGPL-3.0 (free for open source projects)
2. **Contribute** to specification and implementation
3. **Provide feedback** on features and use cases
4. **Build extensions** and share with community

**For Commercial Users:**
1. **Evaluate** UTL-X for proprietary applications
2. **Request** commercial license quotes
3. **Pilot projects** with commercial support
4. **Partner** for custom feature development

**For Investors:**
1. **Fund initial development** (seed round)
2. **Strategic partnerships** with integration vendors
3. **Growth investment** for scaling team and sales

**For the Community:**
1. **Join discussions** on GitHub and forums
2. **Write tutorials** and share experiences
3. **Speak at conferences** about UTL-X
4. **Build tools** and integrations

---

## 11. Licensing FAQ

### 11.1 When Do I Need a Commercial License?

**You NEED a commercial license if:**
- Building proprietary/closed-source software
- Creating SaaS products without releasing source code
- Embedding UTL-X in commercial products
- Redistributing UTL-X without source code disclosure
- Modifying UTL-X without sharing modifications

**You DON'T need a commercial license if:**
- Building open source software (AGPL-compatible)
- Using UTL-X internally without distribution
- Evaluating UTL-X for potential use
- Learning and education purposes
- Contributing to the UTL-X project

### 11.2 What Does AGPL-3.0 Require?

**Key Requirements:**
1. **Source Code Sharing:** Must share source code of modifications
2. **Network Use:** Services using UTL-X must offer source code to users
3. **License Preservation:** Derivative works must use AGPL-3.0
4. **Attribution:** Must credit UTL-X project

**What You CAN Do:**
- Use UTL-X in open source projects
- Modify UTL-X for your needs (must share changes)
- Distribute UTL-X (with source code)
- Build services using UTL-X (must offer source)

### 11.3 Commercial License Benefits

**What You Get:**
- **No copyleft:** Keep your code proprietary
- **No disclosure:** Don't share modifications
- **Priority support:** SLAs and dedicated support team
- **Legal protection:** Indemnification and warranty
- **Custom features:** Request specific functionality
- **Multiple runtime options:** Use across all platforms

---

## Appendix A: Complete Example

**Input XML:**
```xml
<Orders>
  <Order id="ORD-001" date="2025-10-01" type="express">
    <Customer type="VIP">
      <Name>Alice Johnson</Name>
      <Email>alice@example.com</Email>
    </Customer>
    <Items>
      <Item sku="WIDGET-001" quantity="2" price="75.00"/>
      <Item sku="GADGET-002" quantity="1" price="150.00"/>
    </Items>
  </Order>
</Orders>
```

**UTL Transformation:**
```utl
%utl 1.0
input xml
output json
---

{
  invoices: input.Orders.Order |> map(order => {
    invoiceId: "INV-" + order.@id,
    invoiceDate: now(),
    orderDate: parseDate(order.@date, "yyyy-MM-dd"),
    orderType: order.@type,
    
    customer: {
      name: order.Customer.Name,
      email: order.Customer.Email,
      tier: order.Customer.@type,
      vip: order.Customer.@type == "VIP"
    },
    
    lineItems: order.Items.Item |> map(item => {
      sku: item.@sku,
      description: lookupProduct(item.@sku).description,
      quantity: parseNumber(item.@quantity),
      unitPrice: parseNumber(item.@price),
      subtotal: parseNumber(item.@quantity) * parseNumber(item.@price)
    }),
    
    let subtotal = sum(order.Items.Item.(parseNumber(@quantity) * parseNumber(@price))),
    let discount = if (order.Customer.@type == "VIP") subtotal * 0.20 else 0,
    let tax = (subtotal - discount) * 0.08,
    
    financial: {
      subtotal: subtotal,
      discount: discount,
      tax: tax,
      total: subtotal - discount + tax
    },
    
    express: order.@type == "express",
    shippingCost: if (order.@type == "express") 15.00 else 5.00
  })
}

function lookupProduct(sku: String): Object {
  // External lookup (would be implemented in runtime)
  { description: "Product " + sku }
}
```

**Output JSON:**
```json
{
  "invoices": [
    {
      "invoiceId": "INV-ORD-001",
      "invoiceDate": "2025-10-02T14:30:00Z",
      "orderDate": "2025-10-01T00:00:00Z",
      "orderType": "express",
      "customer": {
        "name": "Alice Johnson",
        "email": "alice@example.com",
        "tier": "VIP",
        "vip": true
      },
      "lineItems": [
        {
          "sku": "WIDGET-001",
          "description": "Product WIDGET-001",
          "quantity": 2,
          "unitPrice": 75.00,
          "subtotal": 150.00
        },
        {
          "sku": "GADGET-002",
          "description": "Product GADGET-002",
          "quantity": 1,
          "unitPrice": 150.00,
          "subtotal": 150.00
        }
      ],
      "financial": {
        "subtotal": 300.00,
        "discount": 60.00,
        "tax": 19.20,
        "total": 259.20
      },
      "express": true,
      "shippingCost": 15.00
    }
  ]
}
```

---

**END OF DOCUMENT**

---

## About the Author

**Ir. Marcel A. Grauwen** is the concept originator and project driver for UTL-X (Universal Transformation Language Extended). With extensive experience in enterprise integration, data transformation, and XML/JSON ecosystems, Ir. Grauwen recognized the critical gap between proprietary solutions like DataWeave and the open-source community's needs. UTL-X represents his vision for a sustainably-funded, community-driven, format-agnostic transformation language that combines the declarative power of XSLT with modern functional programming paradigms.

---

## Project Contact

**Project Lead:** Ir. Marcel A. Grauwen  
**Project Name:** UTL-X (Universal Transformation Language Extended)  
**License:** Dual-licensed (AGPL-3.0 / Commercial)  
**Business Model:** Open Core with Commercial Extensions

For inquiries about:
- **Open Source Contributions:** GitHub repository and community forums
- **Commercial Licensing:** sales@utlx-lang.org
- **Partnership Opportunities:** partnerships@utlx-lang.org
- **General Inquiries:** info@utlx-lang.org

---

*This proposal is a starting point for community discussion. All syntax, features, and licensing terms are subject to refinement based on feedback and community input under the leadership of Ir. Marcel A. Grauwen.*


UTL-X is a format-agnostic functional transformation language written in Kotlin. It transforms data between XML, JSON, CSV, YAML and other formats using a single transformation definition. The project uses a Universal Data Model (UDM) at its core to abstract data representation.

## Build and Development Commands

### Building the Project
```bash
# Build all modules (Note: YAML module currently has a repository configuration issue)
./gradlew build

# Build specific modules
./gradlew :modules:core:build
./gradlew :modules:cli:build

# Clean build artifacts
./gradlew clean
make clean
```

### Running Tests
```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :modules:core:test
./gradlew :formats:xml:test
./gradlew :formats:json:test

# Run with detailed output
./gradlew test --info
```

### Running a Single Test
```bash
# Run specific test class
./gradlew :modules:core:test --tests "org.apache.utlx.core.TypeSystemTest"

# Run specific test method
./gradlew :modules:core:test --tests "org.apache.utlx.core.TypeSystemTest.testBasicTypes"
```

### Code Quality
```bash
# Format code (if ktlint is configured)
./gradlew ktlintFormat
make format

# Lint check
./gradlew ktlintCheck
make lint
```

### Running the CLI
```bash
# Run CLI with arguments
./gradlew :modules:cli:run --args="transform input.xml output.json"
make run ARGS="transform input.xml output.json"
```

## Architecture Overview

### Module Structure
```
utl-x/
├── modules/
│   ├── core/           # Core language implementation (parser, lexer, interpreter, UDM)
│   ├── cli/            # Command-line interface
│   └── analysis/       # Type inference, schema validation
├── formats/
│   ├── xml/            # XML parser and serializer
│   ├── json/           # JSON parser and serializer
│   ├── csv/            # CSV parser and serializer
│   └── yaml/           # YAML parser and serializer (in development)
├── stdlib/             # Standard library functions (string, date, math, etc.)
└── tools/              # Development tools and plugins
```

### Key Components

1. **Universal Data Model (UDM)** (`modules/core/src/main/kotlin/org/apache/utlx/core/udm/`)
   - Central abstraction for all data formats
   - Allows format-agnostic transformations
   - Located in `udm_core.kt`

2. **Parser Pipeline** (`modules/core/src/main/kotlin/org/apache/utlx/core/`)
   - Lexer (`lexer/lexer_impl.kt`) - Tokenization
   - Parser (`parser/parser_impl.kt`) - AST construction
   - Type System (`types/type_system.kt`) - Type checking
   - Interpreter (`interpreter/interpreter.kt`) - Execution

3. **Format Handlers** (`formats/*/src/main/kotlin/`)
   - Each format has a parser and serializer
   - Converts between native format and UDM
   - Implements common interface for pluggability

4. **Standard Library** (`stdlib/src/main/kotlin/`)
   - Rich function library organized by category
   - Functions for strings, arrays, dates, math, etc.
   - All functions work on UDM types

## Testing Approach

- Unit tests use JUnit 5 and Kotest
- Test files follow naming pattern: `*Test.kt` or `*Tests.kt`
- Test data typically embedded in test files
- Mock objects created with MockK library

## Known Issues

1. **YAML Module Build Error**: The `formats/yaml/build.gradle.kts` has a repository configuration that conflicts with the centralized repository management in `settings.gradle.kts` (line 11: `mavenCentral()`). This needs to be removed.

2. **Gradle Deprecations**: Some build files use deprecated `buildDir` instead of `layout.buildDirectory`

## Development Tips

1. **Format-Agnostic Design**: When adding features, ensure they work with the UDM rather than specific formats
2. **Type Safety**: Leverage Kotlin's type system - the project uses strong typing throughout
3. **Functional Style**: The transformation language is functional; prefer immutable data and pure functions
4. **Test Coverage**: All new functions should include comprehensive tests
5. **Documentation**: Update relevant documentation in `docs/` when adding features

## Important Files to Understand

- `modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt` - Universal Data Model
- `modules/core/src/main/kotlin/org/apache/utlx/core/ast/ast_nodes.kt` - AST structure
- `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt` - Main parser logic
- `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt` - Execution engine

## Language Syntax (UTL-X)

Basic transformation structure:
```utlx
%utlx 1.0
input json    # or xml, csv, yaml, auto
output json   # target format
---
{
  // Transformation logic using functional operators
  result: input.data |> map(item => transform(item))
}
```

Key operators:
- `|>` - Pipe operator for chaining
- `=>` - Lambda arrow
- `.` - Path navigation
- `@` - XML attribute access
- `?` - Safe navigation