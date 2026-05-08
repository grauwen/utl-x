= Appendix B: Configuration Reference

== Environment Variables

#table(
  columns: (auto, auto, 1fr),
  [*Variable*], [*Default*], [*Description*],
  [`UTLXE_ADMIN_KEY`], [_(none)_], [Admin API authentication key. Required --- if not set, all admin endpoints return 403.],
  [`UTLXE_HEAP_SIZE`], [`3072m`], [JVM heap size. Starter: `3072m` (4 GB container). Professional: `6144m` (8 GB container).],
  [`JAVA_OPTS`], [_(see below)_], [Additional JVM options. Default includes G1GC, container support, AlwaysPreTouch.],
)

Default `JAVA_OPTS`:

```
-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseContainerSupport -XX:+AlwaysPreTouch
```

== Engine Configuration (engine.yaml)

The engine configuration file is optional. If present in the bundle, it overrides the defaults.

#table(
  columns: (auto, auto, 1fr),
  [*Field*], [*Default*], [*Description*],
  [`maxInputSize`], [`5MB`], [Maximum message size before rejection. Messages larger than this are rejected with 413.],
  [`workers`], [CPU cores], [Worker thread pool size.],
  [`healthPort`], [`8081`], [Health + admin API port.],
  [`dataPort`], [`8085`], [Data plane port.],
  [`grpcPort`], [`9090`], [gRPC port (when `--also-grpc` is used).],
)

== Port Map

#table(
  columns: (auto, auto, auto, 1fr),
  [*Port*], [*Container*], [*Default*], [*Purpose*],
  [8081], [utlxe], [configurable], [Admin API + health + metrics (internal to VNet)],
  [8085], [utlxe], [configurable], [Data plane --- Dapr bindings + direct HTTP],
  [9090], [utlxe], [configurable], [gRPC (optional, `--also-grpc`)],
  [8088], [utlxe-ui], [configurable via `UI_PORT`], [Admin Web UI (browser access)],
  [3500], [dapr], [fixed], [Dapr sidecar HTTP API (localhost only)],
)

All UTLXe ports are configurable via CLI flags. The UI port is configurable via the `UI_PORT` environment variable. The Dapr sidecar port is fixed at 3500 by Dapr.

== Transformation Configuration (transform.yaml)

Per-transformation configuration. Optional --- if absent, defaults apply.

#table(
  columns: (auto, auto, 1fr),
  [*Field*], [*Default*], [*Description*],
  [`strategy`], [`COMPILED`], [Execution strategy: `TEMPLATE`, `COPY`, `COMPILED`, `AUTO`.],
  [`validationPolicy`], [`strict`], [Input validation: `strict` (reject), `warn` (log), `off` (skip).],
  [`maxConcurrent`], [_(unlimited)_], [Maximum concurrent executions for this transformation.],
  [`outputBinding`], [_(none)_], [Dapr output binding name for sending transformed messages.],
)

Inputs and schemas:

```yaml
strategy: COMPILED
validationPolicy: strict
maxConcurrent: 4
inputs:
  - name: input
    schema: order.xsd
output:
  schema: invoice.xsd

# Messaging (EF10) — field name is the discriminator
input:
  queue: orders-in              # or topic: / eventhub:
  # subscription: utlxe         # required for topic input
  # consumerGroup: utlxe        # optional for eventhub (triggers pub/sub mode)
output_messaging:
  queue: orders-out             # or topic: / eventhub:
```

== JVM Tuning Reference

#table(
  columns: (auto, 1fr),
  [*Flag*], [*Purpose*],
  [`-XX:+UseG1GC`], [G1 garbage collector. Good for large heaps with pause-time targets.],
  [`-XX:MaxGCPauseMillis=200`], [Target maximum GC pause. G1 adjusts its behavior to meet this target.],
  [`-XX:+UseContainerSupport`], [Respect cgroup memory limits instead of reading host memory.],
  [`-XX:+AlwaysPreTouch`], [Allocate the entire heap at startup. Fail fast if not enough RAM.],
  [`-Xmx${UTLXE_HEAP_SIZE}`], [Maximum heap size. Set via `UTLXE_HEAP_SIZE` environment variable.],
)

== Container Plans

#table(
  columns: (auto, auto, auto, auto, auto),
  [*Plan*], [*Container RAM*], [*Heap*], [*Max message*], [*vCPU*],
  [Starter], [4 GB], [3 GB], [~100 KB], [1],
  [Professional], [8 GB], [6 GB], [~500 KB], [2],
)

The heap is set to 75% of container memory. The remaining 25% covers JVM metaspace, thread stacks, native memory, and the operating system.

== Bundle ZIP Format

```
bundle.zip
  schemas/                     (optional)
    order.xsd
    invoice.json
  transformations/
    {name}/
      {name}.utlx              (required)
      transform.yaml           (optional)
  engine.yaml                  (optional)
```

== Data Directory Layout

The on-disk state under `/utlxe/data/`. Written by the Admin API, read at startup.

```
/utlxe/data/
  schemas/
    order.xsd
    invoice.json
  transformations/
    invoice-to-ubl/
      invoice-to-ubl.utlx
    order-enrichment/
      order-enrichment.utlx
```

If Azure Files is mounted at `/utlxe/data/`, this directory survives container restarts.
