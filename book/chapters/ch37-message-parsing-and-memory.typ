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

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Message size*], [*Feasibility*], [*Workers (2GB)*], [*Approach*],
  [Under 100 KB], [Excellent], [8-32], [Standard UTL-X],
  [100 KB - 1 MB], [Good], [8], [Standard, monitor memory],
  [1 MB - 10 MB], [Feasible], [2-4], [More memory, fewer workers],
  [10 MB - 50 MB], [Marginal], [1], [Consider splitting first],
  [Over 50 MB], [Not feasible], [--], [Use streaming/big data tools],
)

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
