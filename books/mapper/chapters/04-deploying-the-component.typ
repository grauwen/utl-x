= Deploying the Mapper Component

A mapper is useful only where the data flows. The UTL-X Mapper is a *software component*, so it
drops into the places integration actually happens — a pipeline stage, a message-bus subscriber, a
service-mesh sidecar, an API filter. This chapter covers the forms it takes and the model that lets
one mapping run unchanged across all of them.

== It is software, and it already runs in production shapes

The Mapper is not new infrastructure. UTL-X already runs as a GraalVM-native binary and as a
managed cloud component — the _UTLXe on Azure_ guide runs the engine as a container with *Dapr*
pub/sub and bindings. The Mapper is that engine, configured with the format packs and the
`validate.*` contracts of this book. Everything below is a deployment *form* of one component, not a
separate product per form.

== Deployment forms

#table(
  columns: (auto, 1fr),
  [*VM image (OVA)*], [for traditional, virtualised, and air-gapped estates; the whole engine in one appliance image],
  [*OCI container / Kubernetes*], [one pod per flow, managed by an operator; packs and contracts distributed as signed artefacts; fits the Open-M `mode: component` model],
  [*Dapr sidecar / middleware*], [a mapping step beside a workload, on pub/sub or input/output bindings — every service gets format translation with no code change],
  [*API-gateway filter*], [the Mapper as a request/response transform in front of an API — inbound and outbound shaping],
  [*Embedded library / CLI*], [the native binary invoked in a batch pipeline, a build step, or a ground-station script],
)

== The sidecar model

The most cloud-native form is the Mapper as a *sidecar*: it sits beside a service and translates on
the way in and out, so a component that speaks only JSON can transparently consume AIS, ADS-B, or a
binary feed.

```
  binary feed ─▶ [ UTL-X Mapper sidecar ]  ─▶  app (speaks JSON/XML)
                     parse → UDM → validate → emit
                     invalid → dead-letter + audit (never a bad message in)
```

Via Dapr bindings or pub/sub middleware, the mapping is injected around the service without
touching its code. The honest boundary: a sidecar *shares the pod's trust domain*, so this is the
right shape for *integration and data-shaping inside one trust zone* — not for a cross-domain
security crossing, which is the Guard's job and which insists that each side run its own box.

== Transport and buses

A mapping decides on *content*, not on the wire a message arrived on, so the transport is an
*adapter layer* around it: which bus or endpoint carries messages in and out is a configuration
choice, not a change to the mapping. Reusing UTL-X's Dapr component model (pub/sub and input/output
bindings), the Mapper connects to a broad set of transports through configuration rather than code:

#table(
  columns: (auto, 1fr),
  [*Message buses / brokers*], [Apache Kafka, Apache Pulsar, AMQP (RabbitMQ), MQTT, JMS-style brokers, and managed cloud queues (Azure Service Bus, AWS SQS/SNS, Google Pub/Sub)],
  [*Real-time pub-sub*], [OMG *DDS* — the SOSA/FACE-aligned real-time data bus — and specialised feeds once they reach IP],
  [*Direct / batch*], [TCP/UDP, HTTP/REST and gRPC endpoints, file and directory drops, serial — for sensors, ground stations and batch transfer],
)

The same mapping serves both delivery styles — which is where the Mapper differs from the Guard. As a
*bus subscriber or pipeline stage* it is a *fire-and-forget forward*: consume a message, map it, and
publish the result downstream (invalid input → dead-letter + audit, never a bad message onward). As
an *API-gateway filter* it runs *request/response*, shaping a call inbound and its reply outbound.
The Guard deliberately permits only the one-way, fire-and-forget form — a reply path across a trust
boundary would be a back-channel — whereas the Mapper, an integration component inside one trust
zone, is free to do either.

== One mapping, every form — the Open-M model

The property that makes the component economics work: a mapping is written *once* and runs
*unchanged* across every form above. The deployment is a packaging and operations decision, not a
rewrite — the same leverage Solace gets by shipping one messaging product as both an appliance and
a VM/container. Under the Open-M `mode: component` model a mapping is declared with its input and
output formats and dropped into whichever runtime the deployment needs:

```
%utlx 1.1
input  ais
output cot
---
// the mapping body — identical whether this runs as a
// container, a Dapr sidecar, an API filter, or the CLI
```

== Pipelines and chaining

Because every mapping is a pure function from one format to another, mappings *compose*. A binary
feed can be decoded, enriched from a lookup pack, validated against a receiving contract, and
re-emitted in a modern format as a chain of component stages — each independently testable, each
auditable:

```
  AIS frame ─▶ [decode AIS] ─▶ [enrich: MMSI → vessel] ─▶ [validate CISE contract] ─▶ CISE XML
```

Each stage is one UTL-X mapping; the chain is a pipeline of components. A standards revision changes
one stage's definition, not the pipeline.

== Scaling and assurance

As a pure, stateless, single-pass function (Chapter 5), each mapping instance is independent — so
throughput scales *horizontally* by running more instances behind a load balancer or more pods in a
deployment, with no shared state to coordinate. Where assurance rather than throughput is the
driver, the same component climbs a hardening ladder — hardened container, dedicated VM,
confidential-computing enclave — exactly as the companion Guard does, without changing the mapping.
That assurance story, and the hardware that backs the highest tiers, is the subject of the Guard
book; the Mapper inherits it unchanged.

Part II now opens the component up — starting with the language and the model that make a mapping a
pure, composable function in the first place.
