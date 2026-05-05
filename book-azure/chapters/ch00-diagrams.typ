// Landscape pages with UML sequence diagrams for Chapter 1 scenarios
// Included between ch00-why-utlxe.typ and ch01-quick-start.typ in main.typ

#import "@preview/chronos:0.3.0"

#set page(flipped: true, margin: (top: 1.5cm, bottom: 1.5cm, left: 1.5cm, right: 1.5cm), header: none)
#set text(size: 9pt)

// ── Scenario 1: Service Bus via Dapr ──

#figure(
  chronos.diagram({
    import chronos: *
    _par("SB-In", display-name: "Service Bus (input queue)")
    _par("Dapr", display-name: "Dapr Sidecar — localhost:3500")
    _par("UTLXe", display-name: "UTLXe — localhost:8085")
    _par("SB-Out", display-name: "Service Bus (output queue)")

    _seq("SB-In", "Dapr", comment: "1. AMQP 1.0 / TLS :5671 — message from queue")
    _seq("Dapr", "UTLXe", comment: "2. HTTP POST localhost:8085/api/dapr/input/orders-in  {JSON}")
    _seq("UTLXe", "UTLXe", comment: "3. Parse → Transform → Serialize")
    _seq("UTLXe", "Dapr", comment: "4. HTTP POST localhost:3500/v1.0/bindings/orders-out  (new connection)")
    _seq("Dapr", "SB-Out", comment: "5. AMQP 1.0 / TLS :5671 — forward to output queue")
    _seq("Dapr", "UTLXe", comment: "6. HTTP 200 OK (reply to step 4)", dashed: true)
    _seq("UTLXe", "Dapr", comment: "7. HTTP 200 OK (reply to step 2)", dashed: true)
    _seq("Dapr", "SB-In", comment: "8. AMQP 1.0 — complete message")
  }),
  caption: [Scenario 1: Azure Service Bus queue-to-queue via Dapr sidecar],
)

#v(0.5cm)

// ── Scenario 2: Event Hub via Dapr ──

#figure(
  chronos.diagram({
    import chronos: *
    _par("EH-In", display-name: "Event Hub (partition)")
    _par("Dapr", display-name: "Dapr Sidecar — localhost:3500")
    _par("UTLXe", display-name: "UTLXe — localhost:8085")
    _par("EH-Out", display-name: "Event Hub (output)")

    _seq("EH-In", "Dapr", comment: "1. AMQP 1.0 / TLS :5671 — event from partition")
    _seq("Dapr", "UTLXe", comment: "2. HTTP POST localhost:8085/api/dapr/input/events-in  {JSON}")
    _seq("UTLXe", "UTLXe", comment: "3. Parse → Transform → Serialize")
    _seq("UTLXe", "Dapr", comment: "4. HTTP POST localhost:3500/v1.0/bindings/events-out  (new connection)")
    _seq("Dapr", "EH-Out", comment: "5. AMQP 1.0 / TLS :5671 — publish to output Event Hub")
    _seq("Dapr", "UTLXe", comment: "6. HTTP 200 OK (reply to step 4)", dashed: true)
    _seq("UTLXe", "Dapr", comment: "7. HTTP 200 OK (reply to step 2)", dashed: true)
    _seq("Dapr", "EH-In", comment: "8. AMQP 1.0 — checkpoint offset")
  }),
  caption: [Scenario 2: Azure Event Hub via Dapr sidecar],
)

#v(0.3cm)
#align(center)[
  #text(size: 8pt, fill: gray)[
    Solid arrows: requests. Dashed arrows: responses. Two protocols in each flow:\
    *AMQP 1.0 / TLS (port 5671)* — Dapr ↔ Service Bus / Event Hub (managed by Dapr, encapsulated from user).\
    *HTTP (localhost:8085, localhost:3500)* — Dapr ↔ UTLXe (pod-internal, no network traversal).\
    Steps 2→7: Dapr's HTTP request to UTLXe (open during processing). Steps 4→6: UTLXe's HTTP request to Dapr (output delivery).
  ]
]

#pagebreak()

// ── Scenario 3: Direct HTTP (no Dapr) ──

#figure(
  chronos.diagram({
    import chronos: *
    _par("Client", display-name: "HTTP Client (external)")
    _par("Ingress", display-name: "Azure Ingress (HTTPS :443)")
    _par("UTLXe", display-name: "UTLXe (HTTP localhost:8085)")

    _seq("Client", "Ingress", comment: "1. HTTPS POST :443 /api/transform/invoice-to-ubl  {input JSON}")
    _seq("Ingress", "UTLXe", comment: "2. HTTP POST localhost:8085/api/transform/invoice-to-ubl  (TLS terminated at ingress)")
    _seq("UTLXe", "UTLXe", comment: "3. Parse → Transform → Serialize")
    _seq("UTLXe", "Ingress", comment: "4. HTTP 200 {transformed output}", dashed: true)
    _seq("Ingress", "Client", comment: "5. HTTPS 200 {transformed output}", dashed: true)
  }),
  caption: [Scenario 3: Direct HTTP --- client calls UTLXe via Azure ingress, no Dapr sidecar involved],
)

#v(0.5cm)
#align(center)[
  #text(size: 8pt, fill: gray)[
    No Dapr, no message queue, no AMQP. HTTPS from the client terminates at the Azure ingress (port 443).\
    The ingress forwards as plain HTTP to UTLXe on localhost:8085. The response follows the same path back.
  ]
]

// Restore portrait for subsequent chapters
#set page(flipped: false, margin: (top: 3cm, bottom: 3cm, left: 2cm, right: 2cm))
