= Where It Fits — Markets and Deployment

The civilian guard depends on nothing Glomidco does not control — no sponsor, no export-control clearance,
no multi-year accreditation. That is the whole point: it can be *sold, fielded and proven now*, while the
MIL edition becomes "the civilian guard + restricted packs + higher assurance".

== Markets and first customers

#table(
  columns: (auto, 1fr),
  [*Coast guard & maritime*], [a CISE adaptor that *also* enforces policy — sharing tracks and incidents between agencies and countries. CISE needs an adaptor per connected system; a guard is an adaptor with policy. Formats: CISE, AIS, IVEF, EDIFACT.],
  [*Critical infrastructure / OT*], [one-way export of sensor/SCADA-derived data out of a control network; controlled import of updates and manifests. Diodes are common here, and NIS2 pushes demonstrable control of data flows. Formats: CSV, JSON, XML, historian exports, EDIFACT.],
  [*Government data sharing*], [ministries and chain partners exchanging structured data across trust zones — a natural market for a national product assessment. Formats: JSON, XML, OData, XSD/JSON-Schema, labels.],
  [*Air-traffic / airport surveillance*], [filtering and normalising surveillance feeds between operators — a strong binary showcase for conservative buyers. Formats: ASTERIX, ADS-B.],
  [*Police / border / crisis response*], [drone metadata and situational awareness shared between agencies — the dual-use stepping stone toward defence. Formats: MISB KLV, Cursor-on-Target, C2SIM.],
)

The sensible go-to-market is *one wedge*: pick one of the first three segments for the first reference
customer, and let that customer pull the formats and the first credential forward.

== Four deployment shapes

#table(
  columns: (auto, 1fr),
  [*A — content gateway*], [an inline gateway or API proxy between two zones or organisations; carries the allow-list, schema, label and content policy. The first and simplest product.],
  [*B — diode-adjacent filter*], [content policy beside a hardware data diode (OT, low→high import); the diode gives one-way *flow* assurance, UTL-X gives *content* assurance. Partner with diode vendors.],
  [*D — split guard*], [two owners, two halves, one per zone, each with its own policy, meeting only over a one-way interlink — for chain partners who will not host each other's box.],
  [*C — hardsec transform stage*], [UTL-X transforms complex input into a simple form a separate hardware verifier checks; mostly a high-assurance / MIL play. Keep the hook; field it with a partner later.],
)

Shapes A, B and D are the civilian product; C is where civilian meets high assurance.

== Getting messages in and out

The guard decides on *content*, not on the wire a message arrived on — so the transport is a
configuration choice, not a redesign. Reusing UTL-X's Dapr component model, it connects to message buses
and brokers (Kafka, Pulsar, AMQP/RabbitMQ, MQTT, JMS, cloud queues), real-time pub/sub (OMG *DDS*), and
direct or batch endpoints (TCP/UDP, HTTP/REST, gRPC, file drops). Two properties hold throughout: the
ingress transport is *terminated, not tunnelled* (a protocol break — no session state crosses; a fresh
message is published on the receiver's independent bus), and delivery is a *fire-and-forget forward* —
consume, decide, forward a rebuilt message or route to isolation, with no synchronous reply path back
across the boundary.

== Positioning

#table(
  columns: (auto, 1fr),
  [*File CDR* (OPSWAT, Glasswall, Votiro)], [complementary — files vs messages; integrate for opaque payloads],
  [*API gateway / WAF* (Kong, Apigee, Azure APIM+WAF, F5)], [the "good enough" competitor; UTL-X is allow-list content *rebuild*, not deny-list traffic filtering],
  [*Data diodes* (Fox Crypto, Arbit, Owl, Waterfall, Advenica)], [*partners* for shape B; some already integrate third-party filters],
  [*Integration platforms* (ESB / iPaaS)], [adjacent — they transform, but are not built as fail-closed guards],
  [*Cross-domain vendors* (Infodas/Airbus, Everfox, Isode, BAE)], [mostly MIL/government high-assurance; meet them on the MIL track],
)

Naming and tagline: *UTL-X Guard* (civilian, open core) and *UTL-X MIL Guard* (restricted packs,
accredited) — *"MIL-ready by design, proven in civilian service."*
