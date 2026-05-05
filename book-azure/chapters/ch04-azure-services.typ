= Connecting to Azure Services

_UTLXe integrates with Azure messaging services via Dapr sidecar. This chapter covers Service Bus, Event Hub, and Event Grid._

== How Dapr Works with UTLXe

// Dapr sidecar runs alongside UTLXe in the same Container App
// Dapr delivers messages to UTLXe via HTTP (POST /api/dapr/input/{bindingName})
// UTLXe sends output to Dapr via HTTP (POST localhost:3500/v1.0/bindings/{outputBinding})
// Diagram: Service Bus → Dapr → UTLXe → Dapr → Output queue

== Azure Service Bus

=== Input: Receiving from a Queue or Topic

// Dapr component configuration (YAML)
// Binding name maps to transformation name
// Content-type detection (JSON, XML, CSV)

=== Output: Sending to a Queue or Topic

// Output binding in transform config or X-UTLXe-Output-Binding header
// Dapr routes the transformed message to the output queue

=== Dead-Letter Queue

// Failed transformations → Dapr returns 500 → Service Bus retries
// After max retries → message moves to DLQ
// UTLXe error ring buffer shows what went wrong

== Azure Event Hub

=== Input: Consuming from Partitions

// Similar to Service Bus but partition-aware
// Consumer group configuration
// Checkpoint management via Dapr

=== Output: Publishing Events

// Output binding to Event Hub topic

== Event Grid (Webhook)

// UTLXe as Event Grid subscriber
// Receives CloudEvents via HTTP data plane
// No Dapr needed — direct HTTP

== Worked Example: Dynamics 365 → Peppol

// End-to-end: D365 Business Central → Service Bus → UTLXe → Service Bus → Peppol Access Point
// Three chained transformations: JSON→UBL, validate, route-by-country
// Sequence diagram from the architecture docs

== Dapr Component Configuration Reference

// Service Bus input binding YAML
// Service Bus output binding YAML
// Event Hub input binding YAML
// Event Hub output binding YAML
