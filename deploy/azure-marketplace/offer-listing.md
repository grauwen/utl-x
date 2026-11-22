# Azure Marketplace — offer listing text

The description shown on the **UTL-X Transformation Engine** offer listing, kept here so changes
are diffable alongside the templates that have to support them.

Reviewed 2026-11-20 against `starter/mainTemplate.json`, `starter/createUiDefinition.json`,
`deploy/docker/Dockerfile.engine` and the engine sources. §2 records the basis for every claim and
why four were removed.

---

## 1. Ready to paste

UTL-X is a modern, format-agnostic data transformation engine. Write your transformation logic once
in a single declarative language and apply it to any supported format — XML, JSON, OData, YAML, or
CSV. Both instance documents and schemas are first-class.

### Two ways in, no code to write

**HTTP** — applications POST a document and get the transformed result back in the response. The
body is the document itself, in whatever format it already is; `Content-Type` says which. No
envelope to build, no SDK to install, no base64. Protected by its own API key, separate from the
administration key, so an application can run your transformations but cannot deploy, delete, or
list them.

**Azure Service Bus** — UTLXe consumes from a queue you own and publishes transformed messages to a
topic you own, or to an **Azure Event Hub** if you would rather fan results into a stream. Lock renewal, retry and dead-letter behaviour are handled by the Dapr sidecar, and
auto-scaling follows queue depth. Enable it at deployment and supply your connection string; the
HTTP path keeps working alongside it.

A browser-based admin UI ships in the same deployment: upload transformations, test them against
sample payloads, inspect logs and metrics.

### Runs anywhere, not just here

Messaging integration runs through Dapr's component model, so the same UTL-X container also runs on
AWS, GCP, on-premises Kubernetes, or self-hosted — a different broker is a configuration change,
not a code change. Your transformation logic is plain text in a documented language, so the
transformation IP stays portable even if the platform does not.

### Key features

- Open source under AGPL-3.0, with a commercial licence available
- 650+ built-in standard library functions
- Strong type system with optional compile-time checking
- Instance document transformation: XML, JSON, OData, YAML, CSV
- Schema transformation: XSD, JSCH (JSON Schema), Avro, Protobuf, OSCH (OData EDMX), TSCH
  (Frictionless Table Schema)
- Semantic validation — assert facts about data, not just its shape, with the verdict available to
  the caller
- Observability built in: Prometheus metrics endpoint, OpenTelemetry tracing, and an Azure
  Application Insights agent in the engine image that activates when you supply a connection string
- Persistent storage option — transformations survive restarts on an Azure Files share; or ship a
  fixed bundle for an immutable deployment

### Compare

- **vs. XSLT** — modern syntax, works with all formats, not just XML
- **vs. DataWeave** — fully open source, no MuleSoft dependency
- **vs. custom code** — declarative, maintainable, type-safe, version-controlled
- **vs. proprietary iPaaS transformation** — runs anywhere Dapr runs; transformation IP is portable

Full documentation, including a deployment guide for this offer, is on GitHub:
https://github.com/grauwen/utl-x/blob/main/books/UTLXe%20on%20Azure.pdf

---

## 2. What each claim rests on

### Verified against the shipped artefacts

| claim | basis |
|---|---|
| formats, instance and schema | the `formats/` tree: xml json csv yaml xsd jsch avro protobuf odata osch tsch |
| 650+ stdlib functions | 692 functions, 705 catalogue entries — the listing figure is conservative |
| AGPL-3.0 + commercial | dual-licensed, owned by GLOMIDCO BV |
| optional compile-time checking | `--strict` / `strictTypes`, `modules/analysis` type inference |
| Prometheus metrics | `GET /metrics`, `text/plain; version=0.0.4` — the Prometheus exposition content type |
| OpenTelemetry tracing | `io.opentelemetry:opentelemetry-api:1.40.0`, `engine/telemetry/Tracing.kt` |
| Application Insights | `applicationinsights-agent.jar` baked into `Dockerfile.engine`; the entrypoint adds `-javaagent` only when `APPLICATIONINSIGHTS_CONNECTION_STRING` is set |
| HTTP data path | `POST /api/transformations/{id}/run`, published by nginx behind `X-Api-Key`; new in 1.4.4 |
| two separate keys | `adminKey` and `dataPlaneKey`, distinct Container App secrets; the acceptance suite asserts the admin key is **rejected** on the data path |
| Service Bus queue in / topic out | `bindings.azure.servicebus.queues` + `bindings.azure.servicebus.topics` |
| Event Hub **output** | `bindings.azure.eventhubs`, direction output, behind `enableEventHubs`. Input is *not* offered — see EF25 Part 3 |
| persistent storage / fixed bundle | `enablePersistentStorage`, Azure Files at `/utlxe/data`; a `.utlar` starts the engine in locked mode |
| semantic validation | `%utlx 1.1` and the `validate.*` namespace |
| Dapr portability | components are configuration; the engine never handles a broker credential |

### Removed, and why

**"Azure Key Vault — credentials resolved at runtime via secret references; no plaintext secrets in
your deployment."** No Key Vault integration exists — `KeyVault`, `keyVaultUrl` and `vaultUri` are
all absent from both templates. Credentials are Container App secrets, pasted into the wizard.
Tracked as EF25 Part 2.

**"Microsoft Entra ID — managed identity / workload identity authentication to brokers, no
connection strings required."** The opposite of what ships: there is no `identity` block on the
Container App, and the wizard *requires* a connection string, validated as
`^Endpoint=sb://.{12,}$`. This is the claim a customer meets on day one and the first that had to
go. Tracked as EF25 Part 1 — which also establishes that it needs a **user-assigned** identity the
customer pre-grants, so even when it ships the honest wording is "no connection strings stored",
not "nothing to configure".

**"Azure Event Hubs — ingest streaming events… partition keys, consumer groups and Azure Storage
checkpointing configured declaratively."** Publishing *to* an Event Hub now works and is claimed
above. *Ingesting from* one does not: the Dapr input binding needs a consumer group and a checkpoint
store, and partition keys are not passed by the engine at all. The
engine can generate them for a self-hosted Dapr deployment (`DaprIntegration.generateEventHubBinding`),
but the Marketplace offering wires only Service Bus. Tracked as EF25 Part 3.

**"Under 100 ms cold start, 15 MB footprint."** Neither matches what Azure runs. The engine image is
`eclipse-temurin:21-jre-jammy` running `java -jar utlxe.jar` — a JVM, not the GraalVM binary — so
cold start is JVM boot plus eager bundle compilation, measured in seconds. And the artefacts are
85 MB (native binary) and 36 MB (CLI fat jar); nothing in the deployment is 15 MB. If a performance
claim is wanted, `books/engine-azure` ch10 documents 20,000–86,000 msg/s for the COMPILED strategy
on ~1 KB JSON-to-JSON at 1 vCPU — attributable, and worth confirming before it is quoted.

### Softened

**"Ships as a container wrapped with Dapr as its sidecar, so it plugs into Azure messaging the
moment you deploy it."** Dapr is opt-in: `enableDapr` is true only when the messaging mode is
`servicebus`. Choose HTTP and there is no Dapr sidecar at all.

**"Consume from queues *and topics*, publish to queues, topics, *or dead-letter destinations*."**
One queue input and one topic output. No topic-subscription input, no queue output, no DLQ
configuration — though Service Bus's own lock/retry/DLQ behaviour does apply to the queue, which is
what the rewritten text claims.

**"mTLS between sidecars (Dapr Sentry, 24-hour rotating workload certificates)."** Real as a Dapr
platform property, but there is one `appId` and no service invocation in this offering, so there is
no sidecar-to-sidecar traffic for it to protect. Dropped rather than explained.

### One thing to fix outside the text

The listing links to `books/UTLXe on Azure.pdf` on `main`. **That file was last committed
31 May 2026** — nearly six months stale. It predates 1.4.0 through 1.4.4 and therefore describes an
offering that could not actually deploy: the `adminKey` secureString blocker and the arm64 images
were not fixed until 1.4.0–1.4.3. A prospect who follows the link from the listing today gets a
guide to a broken deployment.

Note the layout difference, because it is where the staleness comes from. On `development` each
book keeps its PDF beside its sources — `books/engine-azure/UTLXe on Azure.pdf`,
`books/language/UTL-X One Language All Formats.pdf`, and so on for the five book projects. On
`main` the PDFs are **flattened** into `books/`. That flattening is a deliberate manual publish
step, not something a build script should do, so rebuilding the book does not and should not update
what customers download.

So this is a release-checklist item rather than a tooling one: **publish the rebuilt PDF to `main`
whenever the offering changes.** The current `books/engine-azure/UTLXe on Azure.pdf` is 146 pages
and current as of 2026-11-20.
