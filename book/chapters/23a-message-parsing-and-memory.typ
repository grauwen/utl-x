= Message Parsing, Memory, and Size Boundaries

== How UTL-X Parses Messages

=== DOM-Style Parsing (What UTL-X Uses)
// UTL-X loads the ENTIRE message into memory as a UDM tree.
// This is a DOM-style approach (Document Object Model):
//
// 1. Read entire input (XML, JSON, CSV, YAML)
// 2. Parse into complete UDM tree (in-memory)
// 3. Transformation operates on the complete tree
// 4. Serialize complete output tree to target format
//
// ┌──────────┐    ┌───────────────┐    ┌─────────────┐    ┌──────────┐
// │ Raw bytes│───→│ Parser        │───→│ UDM Tree    │───→│ Serializer│
// │ (input)  │    │ (format-      │    │ (complete   │    │ (output  │
// │          │    │  specific)    │    │  in-memory) │    │  format) │
// └──────────┘    └───────────────┘    └─────────────┘    └──────────┘
//
// WHY DOM-style:
// - Random access: $input.Order.Items[3].Price (jump to any node)
// - Transformation needs the full picture (can reference any part)
// - Simpler programming model (no cursor management)
// - Required for: multi-pass, aggregations, lookups, cross-references

=== SAX-Style Parsing (Not Used by UTL-X)
// SAX (Simple API for XML) is an event-based parser:
// - Reads the document sequentially, fires events: startElement, endElement, text
// - NEVER loads the full document into memory
// - Very low memory: processes gigabyte files in constant memory
//
// Why UTL-X does NOT use SAX:
// - No random access: can't do $input.Order.Items[3].Price (items not in memory yet)
// - No backwards reference: if you're at line 500, line 10 is already gone
// - Transformations need context: "sum of ALL items" requires seeing all items
// - Extreme complexity: developer must manage state manually
//
// SAX is for: reading massive log files, simple filtering, streaming without transformation
// SAX is NOT for: data transformation with complex logic

=== StAX-Style Parsing (Not Used by UTL-X)
// StAX (Streaming API for XML) is a pull-based cursor parser:
// - Developer controls the cursor: "give me the next element"
// - Lower memory than DOM, more control than SAX
// - Still sequential: no random access
//
// Why UTL-X does NOT use StAX:
// - Same random access problem as SAX
// - Better for: simple XML-to-XML streaming, pipeline filters
// - Not suitable for: format-agnostic transformation with complex expressions

=== Summary: Parsing Approaches
//
// | Approach | Memory | Random access | Use case |
// |----------|--------|-------------|----------|
// | **DOM** (UTL-X) | Full document in memory | Yes | Complex transformation, any field access |
// | SAX | Constant (events) | No | Streaming filters, very large files |
// | StAX | Low (cursor) | No | Simple XML streaming |
// | JSON streaming | Low (token-by-token) | No | Large JSON arrays, ETL |
//
// UTL-X uses DOM because transformation REQUIRES random access.
// You cannot do "map($input.Orders, (o) -> {total: o.Total, customer: $input.CustomerName})"
// without having both Orders AND CustomerName in memory simultaneously.

== Memory Expansion Factor

=== The Problem: Why 100KB of XML Becomes 5-10MB in Memory
// Raw bytes on disk are COMPACT:
//   <Customer><Name>Alice Johnson</Name><Age>30</Age></Customer>
//   = 68 bytes
//
// In UDM (in-memory):
//   UDM.Object("Customer")
//     properties: HashMap
//       "Name" → UDM.Object
//         properties: HashMap
//           "_text" → UDM.Scalar
//             value: String("Alice Johnson")
//               char[]: 14 chars × 2 bytes = 28 bytes
//               String object header: 24 bytes
//             Scalar object header: 16 bytes
//           HashMap.Entry: 32 bytes
//         HashMap: 48 bytes + array
//         Object header: 16 bytes
//       "Age" → UDM.Object
//         ... (similar)
//     HashMap: 48 bytes + array
//     Object header: 16 bytes
//     attributes: HashMap (empty but allocated): 48 bytes
//     name: String("Customer"): 40 bytes
//
// 68 bytes of XML → ~500+ bytes in UDM
// Factor: ~7-10x for simple structures

=== Expansion Factors by Format
//
// | Format | Typical expansion | Why |
// |--------|------------------|-----|
// | XML | 10-50x | Verbose: tags + attributes → objects + hashmaps |
// | JSON | 5-20x | Object overhead, string copies, hashmap entries |
// | CSV | 3-10x | Compact on disk, but each row becomes an object |
// | YAML | 5-20x | Same as JSON (YAML parses to same UDM) |
// | OData JSON | 5-20x | Same as JSON plus @odata metadata objects |
//
// XML is the worst case because:
// - Every element becomes a UDM.Object (with properties HashMap, attributes HashMap)
// - Even leaf text elements: <Name>Alice</Name> → Object + properties + _text + Scalar
// - Attributes: separate HashMap (even if empty — pre-allocated)
// - Namespace metadata: additional HashMap per element

=== Real-World Examples
//
// | Message size | Format | UDM memory | Workers | Container memory needed |
// |-------------|--------|-----------|---------|------------------------|
// | 1 KB | JSON | ~10 KB | 8 | 256 MB (comfortable) |
// | 10 KB | XML | ~200 KB | 8 | 256 MB (comfortable) |
// | 100 KB | XML | ~5 MB | 8 | 512 MB (8 × 5MB = 40MB peak) |
// | 500 KB | XML | ~25 MB | 8 | 1 GB (8 × 25MB = 200MB peak) |
// | 1 MB | XML | ~50 MB | 8 | 2 GB (8 × 50MB = 400MB peak) |
// | 5 MB | XML | ~250 MB | 8 | 4 GB (8 × 250MB = 2GB peak) |
// | 10 MB | XML | ~500 MB | 4 | 8 GB (4 × 500MB = 2GB peak, reduced workers) |
// | 50 MB | XML | ~2.5 GB | 1 | 8 GB (single worker, near limit) |
//
// Note: "peak" assumes all workers process large messages simultaneously.
// In practice, messages vary in size — peak is rare.

== Practical Message Size Boundaries

=== Sweet Spot: 1 KB - 1 MB
// - This is where UTL-X excels
// - 8 workers can process messages concurrently without memory pressure
// - 86K+ msg/s throughput at 1 KB
// - ~5K msg/s throughput at 100 KB
// - Typical integration messages: API responses, events, orders, invoices
// - Standard cloud container: 2-4 GB memory is sufficient

=== Feasible: 1 MB - 10 MB
// - Works but needs memory planning
// - Reduce workers (4 instead of 8) to control peak memory
// - Increase container memory (4-8 GB)
// - Typical: large XML documents (UBL invoices with 1000+ lines, FHIR bundles)
// - Latency: 50-500ms per message (acceptable for batch-like workloads)
// - The UTLXe HTTP transport has a 10 MB default request body limit
//   (configurable — but raising it is a signal you may need a different approach)

=== Not Recommended: 10 MB - 50 MB
// - Technically possible with sufficient memory (8 GB container)
// - Only 1-2 workers (most memory goes to the document)
// - GC pressure: large object allocation triggers garbage collection pauses
// - Latency: seconds per message
// - Better approach: split the document before transformation
//   (e.g., split a 50 MB XML file into 1000 × 50 KB messages)

=== Not Feasible: 50 MB+
// - UDM expansion would require 4+ GB for a SINGLE message
// - No room for workers, JVM overhead, or other messages
// - This is where SAX/StAX streaming parsers are needed (not UTL-X)
// - Alternative: use Apache Spark, Dataflow, or custom streaming code
// - UTL-X is a TRANSFORMATION tool, not a BIG DATA tool

=== Size Boundary Summary
//
// | Message size | Feasibility | Workers (2GB container) | Approach |
// |-------------|-------------|------------------------|----------|
// | < 100 KB | Excellent | 8-32 | Standard UTL-X |
// | 100 KB - 1 MB | Good | 8 | Standard, monitor memory |
// | 1 MB - 10 MB | Feasible | 2-4 | Increase memory, reduce workers |
// | 10 MB - 50 MB | Marginal | 1 | Consider splitting |
// | 50 MB+ | Not feasible | - | Use streaming tools |

== Memory Sizing Guide for Production

=== Formula
// Container memory needed (minimum):
//   = JVM base (200 MB)
//   + (max_message_size × expansion_factor × workers × 2)
//   + GC overhead (25%)
//
// The ×2 accounts for: input UDM + output UDM (both in memory during transform)
//
// Example: 100 KB XML messages, 8 workers
//   = 200 MB + (100 KB × 30 × 8 × 2) + 25%
//   = 200 MB + 48 MB + 62 MB
//   = 310 MB → round up to 512 MB (safe)
//
// Example: 1 MB XML messages, 8 workers
//   = 200 MB + (1 MB × 30 × 8 × 2) + 25%
//   = 200 MB + 480 MB + 170 MB
//   = 850 MB → round up to 1 GB or use 2 GB

=== The 75% Rule
// UTL-X Bicep/Terraform templates set JVM heap to 75% of container memory:
//   1 GB container → 768 MB heap (-Xmx768m)
//   2 GB container → 1536 MB heap (-Xmx1536m)
//   4 GB container → 3072 MB heap (-Xmx3072m)
//
// The remaining 25% is for:
//   - JVM metaspace, code cache, thread stacks
//   - Native memory (Netty buffers for HTTP transport)
//   - OS overhead inside the container
//
// DO NOT set -Xmx to 100% of container memory — the container will be OOM-killed.

== Future: Streaming Support?

=== Could UTL-X Support Streaming?
// In theory, a SUBSET of transformations could work in streaming mode:
//   - Simple field mappings: $input.Customer.Name → always sequential
//   - Element-by-element array processing: map() where each element is independent
//   - Filtering: where() on arrays without cross-references
//
// But most real transformations CANNOT stream:
//   - Aggregations: sum(), count(), avg() — need all elements
//   - Cross-references: $input.Header.Currency used inside $input.Lines
//   - Sorting: sort() — need all elements in memory
//   - Grouping: groupBy() — need all elements
//   - Conditional logic: if ($input.Type == "A") ... — need to see the whole document
//
// Verdict: streaming is incompatible with UTL-X's transformation model.
// For large files, split before transform (not during).

=== Split-Before-Transform Pattern
// For messages >10 MB:
// 1. Use a pre-processor (Python script, streaming XML parser) to split
// 2. Feed individual items to UTL-X (1 message = 1 order/invoice/record)
// 3. UTL-X transforms each item at full speed
// 4. Post-processor reassembles if needed
//
// Example: 50 MB XML with 10,000 orders
// → Split into 10,000 × 5 KB messages
// → UTL-X processes at 86K msg/s = done in 0.1 seconds
// → Faster AND uses less memory than loading 50 MB at once

== Comparison: UTL-X vs Streaming Tools
//
// | Aspect | UTL-X (DOM) | Apache Spark | SAX/StAX |
// |--------|------------|-------------|----------|
// | Message size | < 10 MB | Unlimited | Unlimited |
// | Random access | Yes | Via RDD/DF | No |
// | Complex transforms | Yes | Yes (code) | Very limited |
// | Latency | 1-50ms | Seconds-minutes | Milliseconds |
// | Use case | Integration | Big data | Simple streaming |
// | Format support | 11 formats | JSON/CSV/Parquet | XML only |
//
// UTL-X is not competing with Spark or streaming parsers.
// Different tools for different scales:
//   < 10 MB, complex transforms → UTL-X
//   > 10 MB, simple transforms → streaming
//   > 1 GB, batch analytics → Spark/Dataflow
