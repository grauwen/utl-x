# UTLXe ↔ Open-M Alignment

**Last reviewed:** May 2026

---

## UTLXe Runs in Both Worlds

UTLXe is the transformation engine for two deployment models:

| Deployment | Transport | Envelope | Broker | Who manages |
|---|---|---|---|---|
| **Open-M** | stdio-proto (pipe) | MPPM (`PipelineEnvelope`, protobuf) | Pulsar/Kafka | Go wrapper |
| **Azure (standalone)** | HTTP (Dapr) | JSON envelope or single message | Service Bus/Event Hub | Dapr sidecar |

The engine code is identical. The transport and envelope differ. UTLXe doesn't know which world it's running in — it receives `ExecuteRequest` via the transport interface and returns `ExecuteResponse`.

## Three Message IDs — Identical Semantics

Both Open-M and UTLXe use the same three-ID model:

| ID | Open-M (MPPM proto) | UTLXe (engine proto) | Purpose |
|---|---|---|---|
| `correlation_id` | `PipelineEnvelope.correlation_id` | `ExecuteRequest.correlation_id` (field 5) | Groups all messages in one business transaction |
| `message_id` | `PipelineEnvelope.message_id` | `ExecuteRequest.message_id` (field 6) | Identifies this specific message (UUIDv7) |
| `causation_id` | `PipelineEnvelope.causation_id` | `ExecuteRequest.causation_id` (field 7) | Links to the parent message that caused this one |

The semantics are identical:
- `correlation_id` is set once by the originator, preserved through the entire chain
- `message_id` is new per hop (UUIDv7, time-ordered)
- `causation_id` = parent's `message_id`

### Who Sets What

| Context | correlation_id | message_id | causation_id |
|---|---|---|---|
| **Open-M**: ingress component | New UUID (once) | New UUID | Empty (first in chain) |
| **Open-M**: wrapper at each hop | Preserved | New UUID | Parent's message_id |
| **Azure**: originator (webshop, ERP) | Business ID (e.g., order number) | New UUID | Empty |
| **Azure**: UTLXe at output | Preserved | New UUID (UUIDv7) | Input's message_id |

The only difference: Open-M generates `correlation_id` as a UUID at the ingress. Azure recommends a business ID (e.g., `ORD-2026-100`) set by the originator. Both work — the semantics (grouping) are the same.

## OpenTelemetry Mapping — Identical

Both Open-M and UTLXe map the three IDs to the same OpenTelemetry attributes:

| Open-M | UTLXe | OpenTelemetry | W3C Trace Context |
|---|---|---|---|
| `correlation_id` | `correlation_id` | `trace_id` (or span attribute) | `traceparent` field 2 |
| `message_id` | `message_id` | `span_id` (or span attribute) | `traceparent` field 3 |
| `causation_id` | `causation_id` | `parent_span_id` | Propagated via context |

Open-M sets `trace_id = correlation_id` at the ingress so the OTel trace and the business correlation are the same UUID. UTLXe preserves `traceparent` from Dapr and adds the three IDs as span attributes (`utlxe.correlation_id`, `utlxe.message_id`).

## Proto Field Mapping

The Open-M engine protocol and the UTLXe proto share the same field semantics:

```
Open-M EngineProcessRequest:          UTLXe ExecuteRequest:
  correlation_id  ─────────────────→  correlation_id  (field 5)
  message_id      ─────────────────→  message_id      (field 6)
  causation_id    ─────────────────→  causation_id    (field 7)
  payload         ─────────────────→  payload          (field 2)
  content_type    ─────────────────→  content_type     (field 3)
  trace_context   ─────────────────→  traceparent      (field 9)
                                      tracestate       (field 12)
```

When Open-M's Go wrapper calls UTLXe via stdio-proto, it maps MPPM envelope fields to `ExecuteRequest` fields. The engine processes the transformation. The wrapper takes the `ExecuteResponse` and creates the next MPPM envelope with updated IDs.

## Multi-Input: Different Mechanisms

| Aspect | Open-M (MPPM) | UTLXe (Azure/Dapr) |
|---|---|---|
| Wire format | `map<string, bytes>` in proto | JSON object keys |
| Per-input format | Raw bytes — engine parses per declared format | All JSON (mixed format not yet supported) |
| Step history | Sliding window of last N upstream outputs | Not available |
| Who constructs | Go wrapper from MPPM step window | Sender (envelope) or Durable Functions (aggregator) |

MPPM is richer: the step window gives any component access to upstream outputs without explicit forwarding. The JSON envelope is simpler: just current inputs, no history.

Mixed-format multi-input (e.g., JSON + XML in one envelope) works in Open-M (raw bytes per input) but not yet in UTLXe's JSON envelope (planned).

## What This Means for Customers

A UTL-X transformation works in both worlds without modification:

```
%utlx 1.0
input json
output xml
---
{
  Invoice: {
    ID: concat("INV-", $input.orderId),
    Total: round($input.amount * 1.21, 2)
  }
}
```

- Deploy in Open-M → wrapper manages MPPM, Pulsar topics, Go runtime
- Deploy in Azure → Dapr manages Service Bus, HTTP delivery, Container Apps
- Same `.utlx` file, same output, same three-ID tracing

The transformation is portable. The deployment model is a choice, not a constraint.
