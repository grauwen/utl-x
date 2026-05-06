# Do the SAP Documents Imply Proto Changes?

**Document purpose:** Walk every demand from `utlxe-sap-cpi-embedding.md`
and `utlxe-cpi-correlation.md` against the current `utlxe.proto` (v1) and
decide what — if anything — needs to change in the wire protocol.

**Headline:** Almost everything is already covered. **One real gap exists**
(rule-emitted business metadata as a typed shape, not a flat map), and it
is not CPI-specific — it's a generic interface improvement that benefits
every host (CPI, BizTalk, Logic Apps, Dapr). Two smaller refinements are
worth considering.

**Companion documents:**
- `utlxe.proto` (v1) — current wire definition
- `utlxe-sap-cpi-embedding.md` — the four CPI embedding options
- `utlxe-cpi-correlation.md` — CPI correlation model
- `utlxe-biztalk-replacement.md` — .NET embedding via existing C# SDK

---

## 1. Demand-by-demand walkthrough

The two SAP documents collectively impose nine distinct demands on the
wire protocol. Eight are already met. One is not.

| # | Demand from SAP docs | Proto field today | Verdict |
|---|---|---|---|
| 1 | Bundle loadable inline (CPI iFlow resource) | `LoadBundleRequest.bundle_data` (bytes) | ✅ |
| 2 | Bundle loadable from URI (HTTP / blob) | `LoadBundleRequest.bundle_uri` | ✅ |
| 3 | Format-agnostic input (XML/JSON/CSV/EDI/IDoc/OData/Avro/Proto) | `ExecuteRequest.content_type` | ✅ |
| 4 | Pass iFlow exchange data into rule as parameters | `ExecuteRequest.parameters` map | ✅ |
| 5 | Pass W3C trace context | `ExecuteRequest.traceparent` | ✅ |
| 6 | Carry CPI Correlation ID through transformation | `ExecuteRequest.correlation_id` echoed in `ExecuteResponse.correlation_id` | ✅ |
| 7 | Carry CPI Message ID equivalent | `ExecuteRequest.message_id` + `causation_id` | ✅ (and richer than CPI's MPL ID alone) |
| 8 | Pass-through custom properties (forward unchanged) | `ExecuteRequest.metadata` ↔ `ExecuteResponse.metadata` | ✅ |
| 9 | **Rule emits structured business identifiers** the adapter maps to `SAP_ApplicationID`, `SAP_MessageType`, `customStatus`, and custom MPL header properties | Only flat `metadata` map exists, and its semantics today is "forward custom properties" | ❌ **Gap** |

The first eight are good news — the proto was designed with this kind of
embedding in mind. Several fields (`correlation_id`, `causation_id`,
`message_id`, `traceparent`) are already exactly what CPI's correlation
model needs.

The ninth demand is the substantive one and the rest of this document is
about it.

---

## 2. The one real gap — rule-emitted output metadata

### 2.1 What the SAP docs require

`utlxe-cpi-correlation.md` §3 specifies that rules should emit:

```
meta.applicationId    → SAP_ApplicationID  (header)
meta.messageType      → SAP_MessageType    (header)
meta.customStatus     → SAP_MessageProcessingLogCustomStatus  (property)
meta.custom.*         → MPL.addCustomHeaderProperty(...)      (each entry)
```

The adapter then knows how to route each value to the right CPI exchange
scope. This is the contract that turns "the rule extracted the IDoc
number" into "the operator can search for it in the MPL monitor."

### 2.2 What the proto exposes today

`ExecuteResponse.metadata` is a flat `map<string, string>` documented as
"Forwarded custom properties (if metadataForwarding != none)." Its purpose
is **pass-through**: take properties off the input, propagate them to the
output. That's a different problem from "let the rule emit new
business-meaningful fields with typed semantics."

The two could be conflated by convention — "if a key starts with
`out.applicationId`, the adapter treats it as application ID, otherwise
it's pass-through" — but stringly-typed conventions are exactly what proto
exists to avoid. They're brittle, they're un-discoverable, and every host
adapter has to reimplement the same parsing logic.

### 2.3 The substantive nature of the gap

This is not a CPI peculiarity. The same gap exists for:

- **BizTalk** — `IBaseMessage.Context` properties need to be set with namespace-qualified names (e.g., `ns0#OrderNumber`). The shim needs to know which output values are promoted properties versus diagnostic-only.
- **Logic Apps Standard** — the `[WorkflowActionTrigger]` return type is serialized into the workflow designer, which can pick up named output fields. A typed `BusinessIdentifiers` shape projects naturally; a flat `metadata` map projects as one opaque blob.
- **Dapr** — pub/sub publishes via CloudEvents. Business identifiers belong in `ce-*` extension attributes, not in the data payload.
- **Kafka / Pulsar** — message headers (Pulsar properties, Kafka headers) are the natural carrier. Same need: rule emits, wrapper routes.

So this is a **generic interface evolution**, not "what SAP needs." CPI is
just the host that surfaced it most clearly because its correlation model
is documented and prescriptive.

---

## 3. Proposed proto changes

Three additive changes. All three are backward-compatible — they add new
fields with new tags, and the existing `metadata` map keeps its current
semantics for pass-through.

### 3.1 New message: `OutputMetadata`

```protobuf
// Rule-emitted output metadata. Distinct from request/response `metadata`
// (which is flat pass-through of arbitrary key/value pairs). This shape
// carries business-meaningful identifiers that hosts map to host-specific
// scopes (CPI MPL fields, BizTalk promoted properties, CloudEvents
// extension attributes, etc.).
message OutputMetadata {
  // Business-meaningful application/document ID — the value an operator
  // searches for. Examples: PO number, IDoc number, sales order ID.
  // Maps to: CPI SAP_ApplicationID, BizTalk promoted property, CloudEvents
  //          ce-id (or a custom extension), Kafka header.
  string application_id = 1;

  // Logical message type the rule produced. Examples: "CanonicalOrder.v3",
  // "InvoiceCreated", "DEBMAS05".
  // Maps to: CPI SAP_MessageType, CloudEvents ce-type, BizTalk MessageType.
  string message_type = 2;

  // Optional non-fatal status the rule wants to surface. Examples:
  // "FALLBACK_RULE_USED", "PARTIAL_MATCH", "DEPRECATED_FORMAT".
  // Maps to: CPI SAP_MessageProcessingLogCustomStatus (property scope),
  //          BizTalk message context, log/trace attribute elsewhere.
  string custom_status = 3;

  // Searchable business identifiers beyond application_id. Examples:
  // {"DeliveryNumber": "80012346", "CustomerNumber": "1000123"}.
  // Maps to: CPI MessageLogFactory.addCustomHeaderProperty per entry,
  //          BizTalk per-property promotion, log attributes elsewhere.
  map<string, string> custom_identifiers = 4;

  // Optional logical sender / receiver labels the rule resolved.
  // Most useful in B2B / EDI flows where the sender/receiver can only be
  // determined after parsing the payload.
  // Maps to: CPI SAP_Sender / SAP_Receiver headers.
  string sender = 5;
  string receiver = 6;
}
```

### 3.2 Add `output_metadata` to response messages

Three responses gain a new field:

```protobuf
message ExecuteResponse {
  // ... existing fields ...
  OutputMetadata output_metadata = 14;     // NEW — rule-emitted business metadata
}

message ExecutePipelineResponse {
  // ... existing fields ...
  OutputMetadata output_metadata = 15;    // NEW — emitted by the LAST stage of the pipeline
}

// ExecuteBatch is covered automatically because it returns repeated ExecuteResponse.
```

The existing `metadata` field stays exactly as it is, with the same
pass-through semantics. Adapters that don't care about typed metadata
ignore `output_metadata` — backward compatibility is total.

### 3.3 Bundle authoring convention (no proto change)

This is the corresponding `.utlx` convention, documented but not encoded
in the proto:

```utlx
%output {
  body: transformed,
  meta: {
    applicationId: $idoc.E1EDK01.BELNR,
    messageType:   "CanonicalOrder.v3",
    customStatus:  $fallbackUsed ? "FALLBACK_RULE_USED" : null,
    custom: {
      IDOCNUM:        $idoc._control.DOCNUM,
      DeliveryNumber: $delivery.id
    }
  }
}
```

The engine takes whatever `meta` block the rule produces and serializes it
into the proto's `OutputMetadata` shape. Rules that don't emit `meta`
produce an empty `OutputMetadata`, and the host adapter sees nothing
unusual.

---

## 4. Two smaller refinements worth considering

These are quality-of-life fixes the SAP work surfaces. Neither is required
for CPI integration to work — both improve clarity.

### 4.1 Document `metadata` semantics explicitly

The existing comment on `ExecuteRequest.metadata` is "Custom properties to
forward (e.g., Service Bus custom properties)." The comment on
`ExecuteResponse.metadata` is "Forwarded custom properties (if
metadataForwarding != none)." This implies a `metadataForwarding`
configuration knob that isn't visible anywhere in the proto.

Two options:

**A.** Make the forwarding policy explicit in the request:

```protobuf
message ExecuteRequest {
  // ... existing fields ...
  MetadataForwarding metadata_forwarding = 11;  // NEW
}

enum MetadataForwarding {
  METADATA_FORWARDING_UNSPECIFIED = 0;  // engine default (today: forward)
  METADATA_FORWARD_ALL = 1;
  METADATA_FORWARD_NONE = 2;
  METADATA_FORWARD_LISTED = 3;          // see metadata_forward_keys
}
```

**B.** Document the engine-side default and accept that today's behavior
("always forward") is the only behavior. Skip the field.

Option B is fine if forwarding semantics are truly fixed. Option A is
better if a host ever needs "drop the upstream noise but keep the
correlation IDs" — which CPI customers will ask for.

### 4.2 The `traceparent` field should be paired with `tracestate`

Today only `traceparent` is on `ExecuteRequest`. W3C Trace Context has two
headers: `traceparent` (required) and `tracestate` (optional vendor
context). Most observability backends are fine without `tracestate`, but
for completeness:

```protobuf
message ExecuteRequest {
  // ... existing fields ...
  string traceparent = 9;
  string tracestate = 12;  // NEW — W3C Trace Context vendor extensions
}
```

Tag 12 is free; tag 11 was used by `parameters`. Tiny addition; might as
well do it now.

Note: `ExecutePipelineRequest` and `ExecuteBatchRequest`/`BatchItem`
should mirror whatever decision is made here, so the four request messages
stay consistent.

### 4.3 Consistency tidy-up — `BatchItem` lacks `traceparent`

Looking at the current proto, `BatchItem` has `correlation_id`,
`message_id`, `causation_id`, `metadata`, `parameters` — but **no
`traceparent`**. That means batched executions can't propagate trace
context per item. If batching is ever used in a CPI Splitter step (which
is common for multi-item IDocs and array payloads), each item should be
its own span.

```protobuf
message BatchItem {
  // ... existing fields ...
  string traceparent = 9;   // NEW
  string tracestate = 10;   // NEW (matching §4.2)
}
```

---

## 5. What does *not* need to change

Worth stating explicitly because these all looked like candidates and
turned out not to be:

- **No new RPC methods.** `Execute`, `ExecuteBatch`, `ExecutePipeline` cover everything CPI needs. Adding a `TransformWithCpiContext` would be wrong — host-specific RPCs balkanize the API.
- **No CPI-specific message types.** `OutputMetadata` is host-neutral; the adapter does the host-specific mapping. Same shape works for BizTalk, Logic Apps, Dapr.
- **No new error codes for SAP/MPL conditions.** `TRANSFORMATION_FAILED` plus `ErrorPhase.TRANSFORMATION` is sufficient. The CPI adapter translates to MPL custom status / failure markers itself.
- **No streaming RPCs.** Despite earlier discussions about gRPC `Watch`-style health, CPI's per-message integration is request/reply. If streaming is added later it should be for log/metrics export, not for transforms.
- **No changes to bundle ops.** `LoadBundle` / `UnloadBundle` already cover inline + URI. CPI's iFlow-resource case is the inline path with the iFlow's local resource read into `bundle_data` by the adapter.
- **No changes to `LoadTransformation` / `UnloadTransformation`.** Single-rule loading is orthogonal to CPI's bundle-oriented model.

---

## 6. Compatibility analysis

| Change | Tag(s) used | Backward compat | Forward compat |
|---|---|---|---|
| Add `OutputMetadata` message | new type | ✅ Old clients ignore | ✅ Old engines never emit |
| Add `output_metadata` to `ExecuteResponse` | tag 14 | ✅ Optional field | ✅ Defaults to empty |
| Add `output_metadata` to `ExecutePipelineResponse` | tag 15 | ✅ Optional field | ✅ Defaults to empty |
| Add `metadata_forwarding` to `ExecuteRequest` (Option A) | tag 11 | ⚠️ Tag 11 currently unused — verify | ✅ Defaults to UNSPECIFIED → engine default |
| Add `tracestate` to `ExecuteRequest` | tag 12 | ✅ Optional field | ✅ Empty default |
| Add `traceparent` + `tracestate` to `BatchItem` | tags 9 + 10 | ✅ Optional fields | ✅ Empty default |

**Wait — `parameters` is tag 10 in `ExecuteRequest`.** Let me re-check the
current tag map for `ExecuteRequest`:

```
1  transformation_id
2  payload
3  content_type
4  named_inputs
5  correlation_id
6  message_id
7  causation_id
8  metadata
9  traceparent
10 parameters       ← already taken
```

So tag 11 is the next free one. The proposed allocation becomes:

```
11 metadata_forwarding   (if §4.1 Option A is taken)
12 tracestate
13 output_metadata       (response side — different message, fresh tag space)
```

For `ExecuteResponse`, current allocations through tag 13:
```
1  success
2  output
3  output_content_type
4  error
5  error_class
6  validation_errors
7  metrics
8  error_phase
9  correlation_id
10 message_id
11 causation_id
12 metadata
13 error_code
```

Tag 14 is free for `output_metadata`. ✅

For `ExecutePipelineResponse`, current tags through 14:
```
1  success
2  output
3  output_content_type
4  error
5  error_class
6  error_phase
7  validation_errors
8  correlation_id
9  stages_completed
10 total_duration_us
11 message_id
12 causation_id
13 metadata
14 error_code
```

Tag 15 is free for `output_metadata`. ✅

All proposed additions are clean.

---

## 7. Recommendation summary

**Required for SAP CPI work:**

1. **Add `OutputMetadata` message.** This is the one real gap. The custom adapter can technically work without it by parsing `metadata` keys by convention, but encoding the contract in the proto is the right call before the BizTalk shim and CPI adapter both ship and force the same convention to be reinvented twice.

2. **Add `output_metadata` field to `ExecuteResponse` (tag 14) and `ExecutePipelineResponse` (tag 15).**

**Recommended quality-of-life:**

3. **Add `tracestate` to `ExecuteRequest` (tag 12), `ExecutePipelineRequest`, and `BatchItem`.** Trivial cost, completes W3C Trace Context support.

4. **Add `traceparent` to `BatchItem` (tag 9).** Currently missing — batched items can't carry per-item trace context.

5. **Decide on `metadata_forwarding` (tag 11).** Either add the explicit knob, or document the implicit "always forward" semantics in the proto comments. Don't leave the comment referring to a `metadataForwarding` setting that isn't visible.

**Not required, not recommended:**

- No CPI-specific RPCs.
- No CPI-specific error codes.
- No restructuring of bundle ops, parameters, named_inputs, or pipeline.

**Overall:** the proto is in good shape for SAP integration. **The single
substantive change is adding the typed `OutputMetadata` shape.** Without
it, every host adapter — CPI, BizTalk, Logic Apps, Dapr — invents its own
string-key convention for the same purpose. With it, the rule author
writes one `meta` block and every host does the right thing.

The rule-authoring convention (`%output { body, meta { ... } }`) is then
the matching change on the bundle side, documented in the rule-authoring
guide. That is not a proto concern — it's a UTLX language convention that
serializes into `OutputMetadata`.

---

## 8. Concrete proto diff

For convenience, here is the full proposed diff:

```diff
 message ExecuteRequest {
   string transformation_id = 1;
   bytes payload = 2;
   string content_type = 3;
   map<string, bytes> named_inputs = 4;
   string correlation_id = 5;
   string message_id = 6;
   string causation_id = 7;
   map<string, string> metadata = 8;
   string traceparent = 9;
   map<string, string> parameters = 10;
+  MetadataForwarding metadata_forwarding = 11;  // optional; see §4.1
+  string tracestate = 12;
 }

+enum MetadataForwarding {
+  METADATA_FORWARDING_UNSPECIFIED = 0;
+  METADATA_FORWARD_ALL = 1;
+  METADATA_FORWARD_NONE = 2;
+  METADATA_FORWARD_LISTED = 3;
+}

 message ExecuteResponse {
   bool success = 1;
   bytes output = 2;
   string output_content_type = 3;
   string error = 4;
   ErrorClass error_class = 5;
   repeated ValidationError validation_errors = 6;
   ExecuteMetrics metrics = 7;
   ErrorPhase error_phase = 8;
   string correlation_id = 9;
   string message_id = 10;
   string causation_id = 11;
   map<string, string> metadata = 12;
   ErrorCode error_code = 13;
+  OutputMetadata output_metadata = 14;
 }

+message OutputMetadata {
+  string application_id = 1;
+  string message_type = 2;
+  string custom_status = 3;
+  map<string, string> custom_identifiers = 4;
+  string sender = 5;
+  string receiver = 6;
+}

 message BatchItem {
   bytes payload = 1;
   string content_type = 2;
   map<string, bytes> named_inputs = 3;
   string correlation_id = 4;
   string message_id = 5;
   string causation_id = 6;
   map<string, string> metadata = 7;
   map<string, string> parameters = 8;
+  string traceparent = 9;
+  string tracestate = 10;
 }

 message ExecutePipelineRequest {
   repeated string transformation_ids = 1;
   bytes payload = 2;
   string content_type = 3;
   string correlation_id = 4;
   string message_id = 5;
   string causation_id = 6;
   map<string, string> metadata = 7;
   string traceparent = 8;
   map<string, PipelineStageInputs> stage_inputs = 9;
   map<string, string> parameters = 10;
+  string tracestate = 11;
 }

 message ExecutePipelineResponse {
   bool success = 1;
   bytes output = 2;
   string output_content_type = 3;
   string error = 4;
   ErrorClass error_class = 5;
   ErrorPhase error_phase = 6;
   repeated ValidationError validation_errors = 7;
   string correlation_id = 8;
   int32 stages_completed = 9;
   int64 total_duration_us = 10;
   string message_id = 11;
   string causation_id = 12;
   map<string, string> metadata = 13;
   ErrorCode error_code = 14;
+  OutputMetadata output_metadata = 15;
 }
```

That's the entire wire-protocol change footprint for SAP support — and
not just SAP support, but a measurable improvement for every host the
engine will ever embed in.

---

*Document maintainer: UTLX platform team. Revisit when the SAP CPI custom
adapter prototype is built and any host-side mapping that doesn't fit
`OutputMetadata` is discovered.*
