# Frequently Asked Questions (FAQ)

Common questions about UTL-X answered.

---

## General Questions

### What is UTL-X?

UTL-X (Universal Transformation Language Extended) is an open-source, format-agnostic transformation language that converts data between XML, JSON, CSV, YAML, and other formats using a unified syntax.

Think of it as:
- **XSLT** for modern formats (not just XML)
- **DataWeave** but open source
- A **functional programming language** for data transformation

### Why use UTL-X instead of custom code?

**Benefits over custom code:**
- ✅ **Faster development** - Write transformations, not boilerplate
- ✅ **Less error-prone** - Declarative = fewer bugs
- ✅ **Easier maintenance** - Clear, concise transformations
- ✅ **Format flexibility** - Change formats without rewriting logic
- ✅ **Type safety** - Catch errors at compile time

**Example:** A transformation that takes 100+ lines of Java can be 10-20 lines of UTL-X.

### Is UTL-X production-ready?

**Current status:** Alpha (v0.1.0)

- ✅ Core language features work
- ✅ XML and JSON support
- ⚠️ Missing features: CSV, YAML, some stdlib functions
- ⚠️ Not yet optimized for production workloads

**Recommendation:** 
- Use for **prototypes** and **internal tools**
- Wait for v1.0 for **production systems**
- Follow the [roadmap](roadmap.md) for timeline

### Is UTL-X free?

**Yes!** UTL-X is open source under AGPL-3.0.

**Two licensing options:**

1. **AGPL-3.0 (Free)** - Open source use
   - Perfect for open source projects
   - Internal company tools
   - Academic research
   - Must open source modifications if offering as SaaS

2. **Commercial License** (Paid) - For proprietary use
   - Use in closed-source products
   - SaaS without open sourcing
   - Removes AGPL obligations
   - Includes support

See [LICENSE.md](../../LICENSE.md) for details.

---

## Getting Started

### How do I install UTL-X?

See the complete [Installation Guide](../getting-started/installation.md).

**Quick start:**
```bash
# Clone repository
git clone https://github.com/grauwen/utl-x.git
cd utl-x

# Build
./gradlew build

# Verify
./gradlew test
```

### What are the system requirements?

**Minimum:**
- Java 11 or higher
- 256 MB RAM
- 50 MB disk space

**Recommended:**
- Java 17 (LTS)
- 512 MB RAM
- 100 MB disk space

**Supported platforms:**
- Linux (any distribution)
- macOS (10.14+)
- Windows (10+)

### Where do I start learning?

**Recommended learning path:**

1. [Installation](../getting-started/installation.md) - Set up UTL-X
2. [Your First Transformation](../getting-started/your-first-transformation.md) - 10-minute tutorial
3. [Basic Concepts](../getting-started/basic-concepts.md) - Core concepts
4. [Examples](../examples/xml-to-json.md) - Practical examples
5. [Language Guide](../language-guide/overview.md) - Deep dive

**Estimated time:** 1-2 hours to be productive.

---

## Language Questions

### What formats does UTL-X support?

**Currently (v0.1.0):**
- ✅ XML (input and output)
- ✅ JSON (input and output)

**Coming soon:**
- 🚧 CSV (in development)
- 🚧 YAML (planned)
- 🔌 Custom formats (plugin API)

### Can I transform XML to JSON?

Yes! That's one of UTL-X's primary use cases.

**Example:**
```utlx
%utlx 1.0
input xml
output json
---
{
  orderId: $input.Order.@id,
  customer: $input.Order.Customer.Name
}
```

See [XML to JSON examples](../examples/xml-to-json.md).

### Can I transform JSON to XML?

Yes!

**Example:**
```utlx
%utlx 1.0
input json
output xml
---
{
  Order: {
    @id: $input.orderId,
    Customer: {
      Name: $input.customer.name
    }
  }
}
```

### Does UTL-X support namespaces?

Yes, for XML:

```utlx
input xml {
  namespaces: {
    "soap": "http://schemas.xmlsoap.org/soap/envelope/",
    "app": "http://example.com/app"
  }
}
---
{
  body: $input.{"soap:Envelope"}.{"soap:Body"}.{"app:Data"}
}
```

### How do I handle missing data?

Use the `||` operator for default values:

```utlx
{
  name: $input.customer.name || "Unknown",
  quantity: $input.quantity || 0,
  active: $input.active || false
}
```

### Can I define my own functions?

Yes!

```utlx
function calculateTax(amount: Number, rate: Number): Number {
  amount * rate
}

function formatCurrency(value: Number): String {
  "$" + value.toFixed(2)
}

// Use them:
{
  tax: calculateTax(100, 0.08),
  display: formatCurrency(108.00)
}
```

---

## Comparison Questions

### How is UTL-X different from XSLT?

| Feature | XSLT | UTL-X |
|---------|------|-------|
| Syntax | XML-based | Modern, concise |
| Formats | XML only | XML, JSON, CSV, YAML |
| Learning Curve | Steep | Moderate |
| Age | 25+ years | New |
| Community | Large, mature | Growing |

**Use XSLT when:**
- You only need XML transformations
- You have existing XSLT expertise
- You need maximum compatibility

**Use UTL-X when:**
- You work with multiple formats
- You want modern syntax
- You need format flexibility

See [UTL-X vs XSLT](../comparison/vs-xslt.md) for details.

### How is UTL-X different from DataWeave?

| Feature | DataWeave | UTL-X |
|---------|-----------|-------|
| License | Proprietary | Open Source (AGPL-3.0) |
| Vendor | Salesforce/MuleSoft | Community/Glomidco |
| Cost | MuleSoft license | Free (or commercial) |
| Templates | Limited | XSLT-style |
| Lock-in | Yes | No |

**Use DataWeave when:**
- You already use MuleSoft
- You need MuleSoft support
- You're invested in the ecosystem

**Use UTL-X when:**
- You want open source
- You want no vendor lock-in
- You need XSLT-style templates
- You want community-driven development

See [UTL-X vs DataWeave](../comparison/vs-dataweave.md) for details.

### How is UTL-X different from JSONata?

| Feature | JSONata | UTL-X |
|---------|---------|-------|
| Formats | JSON only | Multiple formats |
| Templates | No | Yes |
| Compilation | Interpreted | Compiled |
| Type System | Dynamic | Static with inference |

**Use JSONata when:**
- You only work with JSON
- You need a lightweight library
- You want JavaScript integration

**Use UTL-X when:**
- You work with XML, CSV, YAML too
- You want template matching
- You need strong typing
- You want better performance (compiled)

### Should I use UTL-X or write custom code?

**Use custom code when:**
- Complex business logic beyond data transformation
- Integration with specific frameworks
- Need absolute maximum performance
- Very simple one-time transformations

**Use UTL-X when:**
- Primarily data transformation
- Multiple format conversions
- Need maintainable transformations
- Want to avoid boilerplate code
- Team needs to understand transformations quickly

**Rule of thumb:** If 80%+ of your code is data manipulation, use UTL-X.

---

## Technical Questions

### What programming language is UTL-X written in?

**Kotlin** (JVM)

Why Kotlin:
- Modern, expressive language
- Excellent type system
- Great tooling
- JVM interoperability
- Null safety

### Can I use UTL-X as a library?

Yes! UTL-X can be used as:

1. **Command-line tool**
   ```bash
   utlx transform $input.xml script.utlx
   ```

2. **Library (Java/Kotlin)**
   ```kotlin
   val engine = UTLXEngine.compile("script.utlx")
   val output = engine.transform(inputData, Format.JSON)
   ```

3. **Maven/Gradle dependency**
   ```kotlin
   dependencies {
       implementation("com.glomidco.utlx:utlx-core:0.1.0")
   }
   ```

See [API Reference](../reference/api-reference.md) for details.

### What JVM version is required?

**Minimum:** Java 11  
**Recommended:** Java 17 (LTS)  
**Maximum tested:** Java 21

### Does UTL-X work with GraalVM?

**Planned:** Native image support is planned for v1.0.

This will enable:
- Standalone executables (no JVM required)
- Faster startup times
- Lower memory usage
- Better for serverless/Lambda

### Can I extend UTL-X with custom formats?

**Yes, via plugin API** (coming in v0.2.0)

**Example:**
```kotlin
class ProtobufFormat : FormatParser {
    override fun parse(input: InputStream): UDM {
        // Your parsing logic
    }
    
    override fun serialize(udm: UDM): OutputStream {
        // Your serialization logic
    }
}
```

See [Custom Formats](../formats/custom-formats.md) for details.

### What about performance?

**Current (v0.1.0):**
- Functional but not optimized
- Suitable for development/testing
- ~10-50ms per transformation

**Future (v1.0.0):**
- Compile-time optimization
- Lazy evaluation
- Streaming where possible
- Target: ~1-5ms per transformation

See [Performance](../architecture/performance.md) for details.

---

## Practical Usage

### How do I debug transformations?

**1. Print intermediate values:**
```utlx
{
  _debug_input: input,
  _debug_items: $input.items,
  result: transform(input)
}
```

**2. Break down complex expressions:**
```utlx
{
  let step1 = filter($input.items, i => i.active),
  let step2 = map(step1, i => i.price),
  let step3 = sum(step2),
  result: step3
}
```

**3. Test predicates:**
```utlx
{
  _count_before: count($input.items),
  _count_after: count($input.items |> filter(i => i.price > 100)),
  result: filter($input.items, i => i.price > 100)
}
```

### How do I handle errors?

**Use default values:**
```utlx
{
  name: $input.customer.name || "Unknown",
  quantity: parseNumber($input.quantity) || 0
}
```

**Check existence:**
```utlx
{
  hasCustomer: $input.customer != null,
  customerName: if ($input.customer != null) 
                  $input.customer.name 
                else 
                  "N/A"
}
```

### Can I use UTL-X in production?

**Current recommendation (v0.1.0 Alpha):**
- ✅ Internal tools
- ✅ Prototypes
- ✅ Development environments
- ⚠️ NOT for critical production systems yet

**Wait for v1.0 (Stable) for:**
- Production APIs
- Mission-critical systems
- High-volume workloads

Track progress: [Roadmap](roadmap.md)

### How do I contribute?

We welcome contributions!

**Ways to contribute:**
- 🐛 Report bugs
- 📝 Improve documentation
- ✨ Add features
- 💡 Suggest improvements
- 🧪 Write tests

See [Contributing Guide](../../CONTRIBUTING.md) for details.

### Where can I get help?

**Community Support:**
- 💬 [GitHub Discussions](https://github.com/grauwen/utl-x/discussions) - Ask questions
- 🐛 [GitHub Issues](https://github.com/grauwen/utl-x/issues) - Report bugs
- 📧 [Email](mailto:community@glomidco.com) - General inquiries

**Commercial Support:**
- 📞 Priority support with commercial license
- 🎓 Training and consulting available
- 🔧 Custom integration assistance

Contact: sales@glomidco.com

---

## Licensing Questions

### What does AGPL-3.0 mean?

**AGPL-3.0 (GNU Affero General Public License):**

**You CAN:**
- ✅ Use UTL-X freely
- ✅ Modify the source code
- ✅ Distribute your modifications
- ✅ Use commercially

**You MUST:**
- ⚠️ Keep source code open (copyleft)
- ⚠️ License derivatives under AGPL-3.0
- ⚠️ If you modify and offer as SaaS, you must open source your changes

**Key difference from MIT/Apache:**
- AGPL requires open sourcing even for network use (SaaS)
- MIT/Apache don't require open sourcing

### When do I need a commercial license?

You need a commercial license if:

- ❌ You want to use UTL-X in proprietary software without open sourcing
- ❌ You offer UTL-X as part of a SaaS product without releasing your code
- ❌ You distribute UTL-X in closed-source products
- ❌ You cannot comply with AGPL-3.0 copyleft requirements

**Commercial license benefits:**
- ✅ No copyleft obligations
- ✅ Use in proprietary software
- ✅ Priority support
- ✅ Legal indemnification

Contact: licensing@glomidco.com

### Can I use UTL-X in my company's internal tools?

**Yes, under AGPL-3.0!**

If you're using UTL-X purely internally (not offering it as a service to external users), AGPL-3.0 is fine.

**Internal use = OK under AGPL:**
- ETL pipelines
- Data migration scripts
- Internal APIs
- Development tools

**Commercial license needed for:**
- Offering transformations as a SaaS to customers
- Embedding in software you sell
- Distributing to external users

### How much does a commercial license cost?

**Pricing tiers:**

- **Developer:** €499/developer/year
- **Team:** €2,999/5 developers/year
- **Enterprise:** €25,000/unlimited/year
- **OEM:** Custom pricing

See [Commercial Licensing](https://utl-x.com/commercial) for details.

---

## Future Plans

### What's the roadmap?

See [Roadmap](roadmap.md) for detailed timeline.

**Summary:**
- **v0.2.0** (Q2 2026) - CSV/YAML support, IDE plugins
- **v1.0.0** (Q4 2026) - Production-ready, stable API
- **v2.0.0** (2027) - Native compilation, advanced features

### Will UTL-X always be open source?

**Yes!** The open source version will always exist.

**Commitment:**
- Core language will always be AGPL-3.0
- No "bait and switch" - we won't remove features to proprietary
- If Glomidco is ever sold, open source version continues

See [License](../../LICENSE.md) for legal guarantee.

### Can I request features?

**Absolutely!**

1. **Check roadmap** first: [Roadmap](roadmap.md)
2. **Open discussion:** [GitHub Discussions](https://github.com/grauwen/utl-x/discussions)
3. **Explain use case** and why it's valuable
4. **Community votes** on features

Popular features get prioritized!

---

## Troubleshooting

### I get "command not found: utlx"

**Problem:** UTL-X CLI not in PATH.

**Solution:**
```bash
# Option 1: Add to PATH
export PATH=$PATH:/path/to/utl-x/build/install/utlx/bin

# Option 2: Create symlink
sudo ln -s /path/to/utl-x/build/install/utlx/bin/utlx /usr/local/bin/utlx

# Option 3: Use full path
/path/to/utl-x/build/install/utlx/bin/utlx transform $input.xml script.utlx
```

### Build fails with "OutOfMemoryError"

**Problem:** Gradle runs out of memory.

**Solution:**
```bash
# Increase Gradle memory
echo "org.gradle.jvmargs=-Xmx2g" >> gradle.properties

# Rebuild
./gradlew clean build
```

### Tests fail during build

**Problem:** Test failures block build.

**Quick fix (not recommended for development):**
```bash
./gradlew build -x test
```

**Better solution:**
```bash
# See which tests fail
./gradlew test --info

# Fix the tests or report a bug
```

### Transformation produces wrong output

**Debug steps:**

1. **Verify input format:**
   ```bash
   cat $input.xml  # Is it valid XML?
   ```

2. **Test selector paths:**
   ```utlx
   {
     _debug: $input.Order  // See what data exists
   }
   ```

3. **Break down transformation:**
   ```utlx
   {
     let step1 = $input.Order,
     let step2 = step1.Customer,
     _debug: step2
   }
   ```

4. **Check types:**
   ```utlx
   {
     _type: getType($input.value),
     _parsed: parseNumber($input.value)
   }
   ```

Still stuck? Ask in [Discussions](https://github.com/grauwen/utl-x/discussions)!

---

## More Questions?

Didn't find your question here?

- 💬 **Ask in Discussions:** https://github.com/grauwen/utl-x/discussions
- 📧 **Email us:** community@glomidco.com
- 📖 **Check docs:** [Documentation Index](../README.md)

We'll add popular questions to this FAQ!
