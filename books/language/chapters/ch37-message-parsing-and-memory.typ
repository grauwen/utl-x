= Message Parsing, Memory, and Size Boundaries

Every UTL-X transformation loads the entire message into memory as a UDM tree. This chapter explains why, what it costs, and how to plan for it. Understanding the memory model is essential for production sizing — the difference between a container that handles 86K messages per second and one that crashes with out-of-memory.

== How UTL-X Parses Messages

=== DOM-Style Parsing

UTL-X uses a DOM-style (Document Object Model) approach: the entire input is parsed into a complete in-memory tree before transformation begins.

```
Raw bytes → Parser → UDM Tree (complete, in memory) → Transformation → Serializer → Output
```

Why DOM-style? Because transformation requires *random access*. Consider:

```utlx
map($input.Orders.Order, (order) -> {
  orderId: order.@id,
  customer: $input.CustomerInfo.Name,   // ← reference to a DIFFERENT subtree
  total: sum(map(order.Lines.Line, (l) -> toNumber(l.Price) * toNumber(l.Qty)))
})
```

This transformation accesses `$input.CustomerInfo.Name` while iterating over `$input.Orders.Order` — two different branches of the tree. The `sum()` needs all line items. These operations require the full document in memory simultaneously.

=== Why Not Streaming?

Two alternative parsing approaches exist but are not suitable for UTL-X:

*SAX (event-based):* reads sequentially, fires events (startElement, text, endElement). Never loads the full document. Very low memory — can process gigabyte files. But no random access: when you're at line 500, line 10 is already gone. You cannot do `$input.Orders.Order[3].Price` because items 0-2 were discarded.

*StAX (cursor-based):* developer pulls the next element on demand. Lower memory than DOM, more control than SAX. Still sequential — no random access.

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Approach*], [*Memory*], [*Random access*], [*Use case*],
  [DOM (UTL-X)], [Full document], [Yes], [Complex transformation, any field access],
  [SAX], [Constant (events)], [No], [Streaming filters, very large files],
  [StAX], [Low (cursor)], [No], [Simple XML streaming],
  [JSON streaming], [Low (token)], [No], [Large JSON arrays, ETL pipelines],
)

UTL-X uses DOM because transformation *requires* random access. This is a fundamental architectural decision, not a limitation — it's the only approach that supports the full UTL-X language (aggregation, cross-references, sorting, grouping).

== Memory Expansion Factor

=== Why 100KB of XML Becomes 5MB in Memory

Raw bytes on disk are compact. In-memory objects have overhead:

```
XML on disk (68 bytes):
  <Customer><Name>Alice Johnson</Name><Age>30</Age></Customer>

UDM in memory (~500+ bytes):
  UDM.Object("Customer")
    properties: HashMap (48 bytes + array)
      "Name" → UDM.Object
        properties: HashMap (48 bytes + array)
          "_text" → UDM.Scalar
            String("Alice Johnson"): 14 chars × 2 bytes + 24 bytes header
            Scalar header: 16 bytes
          HashMap.Entry: 32 bytes
      "Age" → UDM.Object (similar)
    attributes: HashMap (48 bytes — empty but allocated)
    name: String("Customer"): 40 bytes
```

68 bytes of XML become ~500 bytes in UDM. That's a 7-10x expansion for simple structures. Complex XML with attributes, namespaces, and deep nesting can be worse.

=== Expansion by Format

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Format*], [*Typical expansion*], [*Why*],
  [XML], [10-50x], [Verbose: every element becomes Object + HashMap + attributes HashMap],
  [JSON], [5-20x], [Object overhead, string copies, HashMap entries],
  [CSV], [3-10x], [Compact on disk, each row becomes Object with header-keyed properties],
  [YAML], [5-20x], [Same as JSON (parses to same UDM structure)],
  [OData JSON], [5-20x], [Same as JSON, plus metadata attribute objects],
)

XML is the worst case because every element — even a leaf like `<Name>Alice</Name>` — becomes a UDM Object with a properties HashMap, an attributes HashMap (even if empty), and a name String. JSON is more efficient because it has no attributes, no namespaces, and no element names to store.

=== Worked Example: A 50 KB UBL Invoice

To make the expansion factor concrete, consider a typical Peppol UBL invoice — 50 KB of XML with 20 line items:

```
On disk (50 KB XML):
  <?xml version="1.0" encoding="UTF-8"?>
  <Invoice xmlns="urn:oasis:names:...">       20 bytes namespace URI
    <cbc:ID>INV-2026-001</cbc:ID>             stored as: name + text + namespace
    <cbc:IssueDate>2026-05-04</cbc:IssueDate>
    ...
    <cac:InvoiceLine> × 20 lines              each line: ~10 elements
      <cbc:ID>1</cbc:ID>                      each element → UDM.Object + HashMap
      <cbc:InvoicedQuantity>2</cbc:InvoicedQuantity>
      <cbc:LineExtensionAmount currencyID="EUR">50.00</cbc:LineExtensionAmount>
      ...                                     ← attributes stored separately
    </cac:InvoiceLine>
  </Invoice>

In memory (UDM tree — estimated 1.5 MB):
  Root UDM.Object                              48 bytes (object header + references)
    properties HashMap                         48 bytes + 16 entries × 32 bytes
      "Invoice" → UDM.Object                   48 bytes
        attributes: {"xmlns": "urn:..."}       48 bytes HashMap + 80 bytes entry
        properties HashMap                     48 bytes + entries
          "cbc:ID" → UDM.Object                48 + 48 + 80 bytes (text unwrapped at access)
          "cbc:IssueDate" → UDM.Object         similar
          "cac:InvoiceLine" → UDM.Array        48 bytes + array of 20 Objects
            [0] → UDM.Object                   48 + HashMap + 10 child properties
              "cbc:LineExtensionAmount"         48 + attributes HashMap (currencyID)
                attributes: {"currencyID":"EUR"}  48 + 32 + 40 bytes
                _text: "50.00"                 48 + 40 bytes
            [1] → UDM.Object ...               × 20 items

  Total: ~1.5 MB (30x expansion from 50 KB)
```

The overhead comes from: HashMap per element (~96 bytes minimum even when empty), String headers (40+ bytes per string), Object headers (16 bytes each), and attribute maps allocated even when elements have no attributes.

For this invoice, one UTLXe worker processes it in ~5ms. Eight workers handle 1,600 invoices/second. Memory per worker: ~3 MB (input UDM + output UDM + intermediate values). Total container need: well under 512 MB.

=== Real-World Memory Examples

#table(
  columns: (auto, auto, auto, auto, auto),
  align: (left, left, left, left, left),
  [*Message size*], [*Format*], [*UDM memory*], [*Workers*], [*Container memory*],
  [1 KB], [JSON], [~10 KB], [8], [256 MB],
  [10 KB], [XML], [~200 KB], [8], [256 MB],
  [100 KB], [XML], [~5 MB], [8], [512 MB],
  [500 KB], [XML], [~25 MB], [8], [1 GB],
  [1 MB], [XML], [~50 MB], [8], [2 GB],
  [5 MB], [XML], [~250 MB], [4], [4 GB],
  [10 MB], [XML], [~500 MB], [2], [8 GB],
  [50 MB], [XML], [~2.5 GB], [1], [8 GB (near limit)],
)

Container memory accounts for: all workers processing simultaneously, JVM base overhead, GC headroom. In practice, not all workers hit peak simultaneously — the table shows worst case.

== Message Size Boundaries

=== Sweet Spot: Under 1 MB

This is where UTL-X excels. Typical integration messages — API responses, events, orders, invoices — fall in this range:

- 8+ concurrent workers, no memory pressure
- 86K+ msg/s at 1 KB, ~5K msg/s at 100 KB
- Standard 2-4 GB container is more than sufficient
- Sub-millisecond to single-digit millisecond latency

=== Feasible: 1 MB to 10 MB

Works but needs memory planning:

- Reduce workers to 2-4 to control peak memory
- Increase container memory to 4-8 GB
- Typical: large UBL invoices with 1,000+ lines, FHIR bundles, complex IDoc messages
- Latency: 50-500ms per message (acceptable for batch-like workloads)

=== Not Recommended: 10 MB to 50 MB

Technically possible with sufficient memory but problematic:

- Only 1-2 workers (most memory consumed by one document)
- GC pressure: large object allocation triggers garbage collection pauses
- Latency: seconds per message
- Better approach: split the document before transformation

=== Not Feasible: Over 50 MB

UDM expansion would require 4+ GB for a single message — no room for workers, JVM overhead, or concurrent messages. This is where streaming parsers (SAX, StAX) or big data tools (Spark, Dataflow) are appropriate.

UTL-X is a *transformation tool*, not a *big data tool*. Different scales need different tools.

=== "Why Not Just Use 1 GB Messages?"

A common question: "My server has 64 GB of RAM — can't I just process a 1 GB XML file?"

The answer is no, and it is not just about memory — it is about *three walls* you hit simultaneously:

*Wall 1: Memory expansion.* A 1 GB XML file expands to 30-50 GB in UDM. No single JVM can hold this. Even with 64 GB physical RAM, the JVM's garbage collector cannot efficiently manage a 50 GB heap — GC pauses become seconds to minutes.

*Wall 2: Parse time.* Parsing is O(n) — linear in message size. A 1 KB message parses in microseconds. A 1 GB message takes minutes just to build the UDM tree, before any transformation logic runs.

*Wall 3: GC pressure.* The JVM's garbage collector must track every object in the heap. A 50 KB message creates ~1,000 objects. A 50 MB message creates ~1,000,000 objects. A 1 GB message creates ~20,000,000 objects. GC pause duration grows with object count — not linearly, but with increasing overhead as the heap grows.

=== CPU Time: Linear, Not Exponential

Good news: transformation time scales *linearly* with message size, not exponentially. Processing a message twice as large takes roughly twice as long — not four times:

#table(
  columns: (auto, auto, auto, auto, auto),
  align: (left, left, left, left, left),
  [*Message size*], [*Parse time*], [*Transform time*], [*Total*], [*Scaling*],
  [1 KB], [< 1ms], [< 1ms], [~1ms], [baseline],
  [10 KB], [~1ms], [~1ms], [~2ms], [2x size → 2x time],
  [100 KB], [~5ms], [~5ms], [~10ms], [10x size → 10x time],
  [1 MB], [~30ms], [~20ms], [~50ms], [linear],
  [10 MB], [~300ms], [~200ms], [~500ms], [linear],
  [50 MB], [~2s], [~1s], [~3s], [linear],
  [100 MB], [~5s], [~3s], [~8s], [linear — but GC pauses add unpredictability],
)

The transformation itself (evaluating the UTL-X body) is O(n) — it visits each element once. Functions like `map`, `filter`, `groupBy` are linear. Even `sortBy` is O(n log n). There are no O(n²) operations in normal transformations.

*However:* at large sizes, the bottleneck shifts from CPU to memory management. GC pauses become unpredictable — a 50 MB message might process in 3 seconds or 8 seconds depending on when GC triggers. This is why the "Not Recommended" boundary exists: not because CPU time is exponential, but because GC behavior becomes non-deterministic.

=== The Real Boundaries

#table(
  columns: (auto, auto, auto, auto, auto, auto),
  align: (left, left, left, left, left, left),
  [*Message size*], [*UDM memory*], [*Objects created*], [*Feasibility*], [*Workers (4GB)*], [*Approach*],
  [Under 10 KB], [< 200 KB], [~500], [Excellent], [8-32], [Standard UTL-X],
  [10-100 KB], [0.2-5 MB], [500-50K], [Excellent], [8-16], [Standard],
  [100 KB-1 MB], [5-50 MB], [50K-500K], [Good], [4-8], [Monitor memory],
  [1-10 MB], [50-500 MB], [500K-5M], [Feasible], [2-4], [More memory, fewer workers],
  [10-50 MB], [0.5-2.5 GB], [5M-25M], [Marginal], [1], [Split first if possible],
  [50-100 MB], [2.5-5 GB], [25M-50M], [Risky], [1], [Dedicated 8GB+ container],
  [Over 100 MB], [5+ GB], [50M+], [Not feasible], [--], [Streaming or big data tools],
)

For the vast majority of integration workloads — API messages, events, orders, invoices, FHIR bundles, IDoc segments — messages are under 1 MB. UTL-X handles these effortlessly. The 50+ MB range is for bulk data exports, full database dumps, or log files — these belong in streaming pipelines (Kafka Streams, Spark, Dataflow), not in a transformation tool.

=== When to Split Instead of Grow

If your messages are large because they contain arrays of independent records, split before transforming:

```utlx
// Instead of transforming a 50 MB file with 10,000 orders:
// Split into individual orders (each ~5 KB), transform each separately.
// UTLXe's pipeline chaining handles this natively.
```

A 50 MB file with 10,000 orders → 10,000 × 5 KB messages → each processes in ~2ms → total: 20 seconds with 8 workers. Same data, no memory problem, predictable latency per message.

== The Poison Message Problem

A single oversized message can crash the entire JVM — taking down all workers and all in-flight transformations. This is the *poison message* scenario:

```
Normal operation:
  Worker 1: processing 50 KB invoice     ✓ (3 MB UDM)
  Worker 2: processing 80 KB order       ✓ (4 MB UDM)
  Worker 3: processing 20 KB event       ✓ (1 MB UDM)
  Free heap: 1.5 GB

Poison message arrives (2 GB XML file):
  Worker 4: starts parsing 2 GB XML...
    → UDM expansion: 2 GB × 30 = 60 GB needed
    → JVM heap limit: 2 GB
    → java.lang.OutOfMemoryError
    → JVM CRASHES
    → Workers 1, 2, 3 also die (shared JVM)
    → All in-flight messages lost
```

This is not theoretical. In enterprise integration, a partner accidentally sends a database dump instead of a single order. A batch system sends an entire day's data as one message. A test system sends production data without pagination.

=== Defense: Input Size Limits

UTLXe should reject messages above a configurable size limit *before* parsing:

```yaml
# TransformConfig
limits:
  maxInputSize: 10485760    # 10 MB — reject anything larger
  maxInputSizeAction: "reject"  # or "dead-letter"
```

The check happens on raw bytes — before UDM expansion, before memory allocation. A 2 GB message is rejected in microseconds, not after consuming all heap trying to parse it.

Without this check, a single malformed or oversized message can take down a production engine that normally handles thousands of messages per second.

=== Defense: Worker Isolation (Future)

A more robust approach: run each worker in a memory-limited sandbox. If one worker exceeds its allocation, only that worker dies — not the entire JVM. This requires either:
- Separate JVM processes per worker (overhead but safe)
- JVM memory regions per thread (not supported by standard JVMs)
- Container-level isolation (one transformation per container — Kubernetes pod pattern)

For now, input size limits are the practical defense.

== Swap: Never Use It

Operating system swap (paging memory to disk) is catastrophic for UTL-X workloads. Never enable swap on a UTLXe host.

*Why swap destroys performance:*

The JVM's garbage collector must scan the entire heap to find live objects. When part of the heap is on disk (swapped out), every GC scan triggers disk I/O — turning a 10ms GC pause into a 10-second pause. The GC doesn't know which pages are swapped; it touches them all.

```
Without swap (heap fits in RAM):
  GC pause: 10-50ms
  Transformation: 5ms
  Total: 15-55ms per message

With swap (heap partially on disk):
  GC pause: 5-30 SECONDS (disk I/O on every scan)
  Transformation: 5ms (if data is in RAM) or 500ms+ (if swapped)
  Total: 5-30 SECONDS per message — 1000x slower
```

Swap makes the problem worse, not better. The system appears to have enough memory (no OutOfMemoryError) but performance degrades by orders of magnitude. This is harder to diagnose than a clean crash — the engine appears to be "running" but latency is catastrophic.

=== Container Configuration

For Kubernetes / Azure Container Apps:

```yaml
# Disable swap in container spec
resources:
  limits:
    memory: "4Gi"      # hard limit — container killed if exceeded
  requests:
    memory: "4Gi"      # guaranteed — no overcommit

# JVM configuration (in Dockerfile or env)
JAVA_OPTS: "-Xmx3g -XX:+UseContainerSupport -XX:+AlwaysPreTouch"
```

`-XX:+AlwaysPreTouch` forces the JVM to allocate all heap pages at startup — this ensures the OS commits real RAM, not virtual memory. If the container doesn't have enough physical memory, it fails immediately at startup instead of degrading slowly under load.

`-XX:+UseContainerSupport` tells the JVM to respect container memory limits (cgroup) rather than looking at host memory. Without this, a JVM in a 4 GB container on a 64 GB host might try to use 16 GB of heap — and get killed by the OOM killer.

== Memory Sizing Formula

For production container sizing:

```
Container memory = JVM base (200 MB)
                 + (max_message_size × expansion_factor × workers × 2)
                 + 25% GC overhead
```

The ×2 accounts for input UDM and output UDM both in memory during transformation.

*Example: 100 KB XML, 8 workers:*

```
= 200 MB + (100 KB × 30 × 8 × 2) + 25%
= 200 MB + 48 MB + 62 MB
= 310 MB → round up to 512 MB
```

*Example: 1 MB XML, 8 workers:*

```
= 200 MB + (1 MB × 30 × 8 × 2) + 25%
= 200 MB + 480 MB + 170 MB
= 850 MB → use 1 GB or 2 GB
```

=== The 75% Rule

UTL-X deployment templates set JVM heap to 75% of container memory:

- 1 GB container → 768 MB heap (`-Xmx768m`)
- 2 GB container → 1536 MB heap (`-Xmx1536m`)
- 4 GB container → 3072 MB heap (`-Xmx3072m`)

The remaining 25% is for JVM metaspace, code cache, thread stacks, native memory (HTTP transport buffers), and OS overhead. Do NOT set `-Xmx` to 100% of container memory — the container will be OOM-killed by the orchestrator.

== The Split-Before-Transform Pattern

For messages over 10 MB, split before transforming:

```
50 MB XML (10,000 orders)
  → Pre-processor splits into 10,000 × 5 KB messages
  → UTL-X processes at 86K msg/s = done in 0.1 seconds
  → Post-processor reassembles if needed
```

This is *faster* than loading 50 MB at once — and uses far less memory. The pre-processor can be a simple Python script, a streaming XML parser (`xmlstarlet`, `xml_split`), or a shell script with `csplit`.

The pattern applies to any large batch file: a CSV with 100,000 rows, an XML with thousands of records, a JSON array with thousands of elements. Split by record, transform each record independently, collect results.

== UTL-X vs Streaming and Big Data Tools

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Aspect*], [*UTL-X (DOM)*], [*Apache Spark*], [*SAX/StAX*],
  [Message size], [Under 10 MB], [Unlimited], [Unlimited],
  [Random access], [Yes], [Via RDD/DataFrame], [No],
  [Complex transforms], [Yes (full language)], [Yes (Scala/Python code)], [Very limited],
  [Latency], [1-50ms], [Seconds to minutes], [Milliseconds],
  [Format support], [11 formats], [JSON, CSV, Parquet], [XML only],
  [Use case], [Integration], [Big data analytics], [Simple streaming],
  [Deployment], [Container], [Cluster], [Library],
)

These tools serve different scales:
- *Under 10 MB, complex transforms:* UTL-X
- *Over 10 MB, simple transforms:* split + UTL-X, or streaming parser
- *Over 1 GB, batch analytics:* Apache Spark, Google Dataflow, AWS Glue
