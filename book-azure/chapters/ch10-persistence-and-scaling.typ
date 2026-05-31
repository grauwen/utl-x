= Persistence and Scaling

This chapter explains how transformations survive container restarts and how to scale UTLXe for production throughput.

== The Persistence Problem

Docker containers have an ephemeral filesystem. When a container restarts --- due to a crash, a scale-to-zero event, or a redeployment --- everything written inside the container is lost. This means the `/utlxe/data/` directory, where uploaded transformations and schemas are stored, is wiped clean.

This is a fundamental container property, not something UTLXe can fix internally. The solution is external storage.

== Azure Files Volume Mount

The recommended approach for the Azure Marketplace is an Azure File Share mounted at `/utlxe/data/`. The mount is configured by the platform at the infrastructure level --- before the container starts. UTLXe uses standard Java file I/O to read and write the directory. It has no knowledge that the directory is backed by a network file share.

*Open mode (dev/test) --- directory tree on disk:*

```
/utlxe/data/                              Azure File Share
  schemas/
    order.xsd
    invoice.json
  transformations/
    invoice-to-ubl/
      invoice-to-ubl.utlx                 transformation source
      transform.yaml                       config: strategy, validation, messaging
    order-enrichment/
      order-enrichment.utlx
      transform.yaml
```

On restart, UTLXe scans `/utlxe/data/`, finds the transformations and their `transform.yaml` configs, compiles them, and becomes ready. Zero manual intervention.

*Locked mode (acc/prd) --- single .utlar file on disk:*

```
/utlxe/data/                              Azure File Share
  orders.utlar                             deployed by CI/CD
```

On restart, UTLXe finds the `.utlar` file, unpacks it in memory, compiles all transformations, and enters locked mode. The Admin API becomes read-only. Name the `.utlar` after the business flow it serves.

If both a `.utlar` file and a directory tree exist on disk, the `.utlar` wins --- the directory is ignored. The `.utlar` is the single source of truth in locked mode.

To enable persistent storage, check the "Enable persistent transformation storage" option during Marketplace deployment. This creates an Azure Storage Account and File Share, and configures the volume mount in the Container App.

== Crash Safety: What Survives a Restart

In open mode, UTLXe writes to disk *synchronously inside every API call* --- the file is written before the HTTP 200 is returned to the caller. There is no write buffer, no deferred flush, no background writer. If you received a 200, the data is on disk.

In locked mode, UTLXe does not write to disk at all --- the `.utlar` bundle is read-only. All state comes from the archive deployed by CI/CD.

In both modes, *no graceful shutdown is needed for persistence*. An abrupt kill (`kill -9`, container crash, node failure, scale-to-zero) loses nothing that was confirmed to the caller.

What is persisted to disk (survives any restart):

#table(
  columns: (auto, auto, 1fr),
  [*State*], [*Persisted?*], [*On restart*],
  [`.utlx` source], [Yes --- written on upload], [Reloaded and recompiled from disk],
  [`transform.yaml` (messaging config)], [Yes --- written on POST .../messaging], [Reloaded, Dapr reconciled automatically],
  [Schemas], [Yes --- written on upload], [Reloaded from disk],
  [`.utlar` bundle (locked mode)], [Yes --- deployed by CI/CD], [Loaded, mode set to locked],
)

What is ephemeral (lost on restart, by design):

#table(
  columns: (auto, 1fr),
  [*State*], [*Why ephemeral*],
  [Paused state], [Operational override. On restart, transformations resume. If you need persistent pause, remove the transformation from the bundle.],
  [Validation overrides], [Emergency override. Should not survive a restart --- the fix should go into `transform.yaml` or the `.utlx` header.],
  [Error ring buffer], [Diagnostic. Recent errors are gone, but Prometheus counters and Azure Monitor logs are durable.],
  [Log buffer], [Diagnostic. Last 5000 entries in memory. Azure Monitor captures all logs durably.],
  [Sync state (draft/synced)], [Reconstructed on startup. `reconcileOnStartup()` compares disk config with Dapr components and syncs automatically.],
)

The key insight: everything the operator *configured* is on disk. Everything the operator *overrode temporarily* is in memory. This is intentional --- temporary overrides should not persist across deployments.

== CI/CD Re-Deploy Pattern

An alternative to persistent storage: let the CI/CD pipeline re-deploy transformations after every container start.

+ Container starts with an empty `/utlxe/data/`.
+ Health endpoint returns `ready: false`.
+ The CI/CD pipeline detects the not-ready state and uploads the bundle.
+ UTLXe compiles the transformations.
+ Health endpoint switches to `ready: true`.
+ Kubernetes routes traffic.

The bundle lives in the CI/CD system (a git repository or artifact store). The container is truly stateless --- the pipeline is the source of truth.

== Startup Sequence

The startup sequence depends on what is found on disk:

=== Open Mode (no `.utlar` file)

+ Start the HTTP server on port 8081 (health, metrics, admin API).
+ Scan `/utlxe/data/` for existing transformations and schemas (directory structure).
  - If found (volume mount from previous session): compile and register them. Load `transform.yaml` with messaging config.
  - If empty: wait for API uploads.
+ Reconcile Dapr components (if `--dapr-components-dir` is set): sync messaging config to Dapr.
+ Start the data plane on port 8085.
+ The readiness probe checks `ready == true` before Kubernetes routes traffic.
+ Admin API: *full access* --- upload, delete, configure, sync.

=== Locked Mode (`bundle.utlar` found)

+ Start the HTTP server on port 8081 (health, metrics, admin API).
+ Detect `bundle.utlar` in `/utlxe/data/` --- enter *locked mode*.
+ Read manifest from `.utlar`: version, checksum, created timestamp.
+ Unpack `.utlar` (ZIP): load all transformations, schemas, and `transform.yaml` configs.
+ Compile all transformations. Create validators from schema references.
+ Reconcile Dapr components from messaging config in the bundle.
+ Start the data plane on port 8085.
+ Health: `ready: true`, `mode: locked`.
+ Admin API: *read-only* --- mutating endpoints return 403 `BUNDLE_LOCKED`. Operational endpoints (pause, resume, validation override, log management) remain available.

The mode is determined automatically by the presence of `bundle.utlar` on disk. No CLI flag needed --- if CI/CD placed a `.utlar`, it is production.

The admin API is available from step 1 in both modes --- health probes work before transformations are loaded.

== Memory Sizing

UTLXe runs on the JVM with a configured heap size. The heap must be large enough to hold the message being processed plus the intermediate objects created during transformation.

#table(
  columns: (auto, auto, auto, auto),
  [*Plan*], [*Container*], [*Heap*], [*Max message*],
  [Starter], [4 GB], [3 GB], [~100 KB],
  [Professional], [8 GB], [6 GB], [~500 KB],
)

The heap is set to 75% of the container memory. The remaining 25% is for the JVM itself (metaspace, thread stacks, native memory) and the operating system.

These plans run on Azure Container Apps *consumption plan* (shared infrastructure, scale to zero, max 4 vCPU / 8 GB). For larger workloads --- big SAP IDocs, high-volume streaming, or compliance requirements --- UTLXe also runs on *workload profiles* (dedicated VMs, up to 16 vCPU / 128 GB). Custom Enterprise plans are available on request.

The `-XX:+AlwaysPreTouch` JVM flag allocates the entire heap at startup. If the container does not have enough RAM, the process fails immediately with a clear error --- rather than crashing hours later under load.

== Why ZGC

UTLXe uses the Z Garbage Collector (ZGC) with generational mode. This is a deliberate choice for a message-processing engine:

- *Sub-millisecond pauses* --- regardless of heap size. No message processing stalls during garbage collection. With the older G1 collector, pauses of 50--200ms would stall all in-flight transformations simultaneously.
- *Generational mode* --- optimized for short-lived objects. Each transformation creates temporary objects (parsed input, intermediate UDM, serialized output) that become garbage immediately after the response. Generational ZGC collects these efficiently.
- *Self-tuning* --- no `MaxGCPauseMillis` or other tuning knobs needed. ZGC adapts to the workload automatically.
- *Requires JDK 21+* --- UTLXe targets JDK 21. The Marketplace container image includes a compatible JDK.

The practical impact: a customer processing 1,000 messages per second sees consistent 1--5ms latency per message, with no periodic spikes from GC pauses.

== Choosing an Execution Strategy

Each transformation runs under an execution strategy, set with `strategy:` in `transform.yaml` (or left to the default). The strategy is purely a performance choice --- it never changes the output, only how fast the engine produces it. Four values are selectable: `TEMPLATE`, `COPY`, `COMPILED`, and `AUTO`. The fifth row below, `COPY` + `COMPILED`, is not a separate value --- it is what `COPY` does automatically when it can also compile the fill logic to bytecode.

#table(
  columns: (auto, auto, 1fr),
  [*Strategy*], [*Throughput (per container)*], [*When to use*],
  [`TEMPLATE`], [1,000--5,000 msg/s], [Development and low volume. Interprets the transformation directly --- instant to load, slowest to run.],
  [`COPY`], [5,000--20,000 msg/s], [Schema-driven flows with a predictable output shape. Pre-builds an output skeleton and fills it per message.],
  [`COMPILED`], [20,000--86,000 msg/s], [High volume and complex logic. Generates JVM bytecode for the transformation.],
  [`COPY` + `COMPILED`], [50,000--86,000+ msg/s], [Maximum throughput: pre-built skeleton for structure plus compiled fill logic.],
  [`AUTO`], [Varies], [Safe default. Picks `COPY` when an output schema is present, otherwise `TEMPLATE`.],
)

Throughput figures are for a single container (8 workers, 1 vCPU) on ~1KB JSON-to-JSON messages; larger or XML payloads scale the numbers down proportionally. For the engine mechanics behind these strategies, see Chapter 32 (Engine Lifecycle) in _UTL-X: One Language, All Formats_.

=== Compilation Is Eager --- No First-Message Penalty

A common worry with `COMPILED` is a latency spike on the first message while the engine generates bytecode. *That does not happen.* UTLXe compiles eagerly: every transformation is compiled when the bundle loads (or at upload time), and the engine reports `ready` only after all transformations are compiled. The readiness probe holds traffic until then, so the first message always hits already-compiled code.

What this means operationally on Azure Container Apps:

- *Startup and scale-out, not runtime, pay the cost.* The compile time (seconds for `COMPILED`, milliseconds for `TEMPLATE`/`COPY`) is added to how long a *new replica* takes to become ready --- not to any message's latency. When KEDA scales out, a fresh replica compiles its bundle before the readiness probe passes and traffic is routed to it. Brief slower scale-out, never errors or latency spikes on live traffic.
- *Bundle (locked) mode keeps restarts fast.* In production bundle mode, a restart compares the bundle manifest version to what was last loaded and skips recompilation if it is unchanged --- so a routine restart does not re-pay the compile cost.
- *Time-to-ready is the metric to watch*, not first-message latency. If fast scale-out matters more than peak throughput for a given flow (for example, bursty low-volume traffic), `AUTO` or `TEMPLATE` becomes ready almost instantly.

The admin upload response reports the compile time directly, e.g. `"compiled_in_ms": 48`, so you can see exactly what each transformation adds to startup.

== Horizontal Scaling

For higher throughput, deploy multiple container replicas behind the Azure load balancer. Each replica runs an independent UTLXe instance.

If using persistent storage, all replicas share the same Azure File Share. Upload transformations once --- all replicas see the same files.

If using CI/CD re-deploy, the pipeline must deploy to each replica (or use a shared storage backend for the bundle).

Azure Service Bus distributes messages across consumers automatically. Event Hub uses consumer groups to distribute partitions across replicas.

== Never Use Swap

Swap and garbage collection do not mix. When the JVM's garbage collector scans the heap, it touches every page. If those pages have been swapped to disk, each page access triggers a page fault --- a disk I/O operation that takes milliseconds instead of nanoseconds. A GC pause that normally takes 20 milliseconds can take 20 _seconds_ when pages are swapped.

The rule: *never run UTLXe with swap enabled.*

In Azure Container Apps, set the memory request equal to the memory limit:

```yaml
resources:
  requests:
    memory: "4Gi"
  limits:
    memory: "4Gi"
```

This ensures the container gets exactly the memory it requests, with no swap. If the container exceeds its memory limit, Kubernetes kills it (OOM) --- which is faster to recover from than swap thrashing.

== Poison Messages

A poison message is an input that is too large or too malformed to process. Without protection, a single 2 GB message can exhaust the JVM heap and crash the container.

The `maxInputSize` configuration rejects oversized messages before parsing:

```yaml
maxInputSize: 5MB
```

Messages larger than this limit are rejected immediately with a 413 status code. The Dapr sidecar receives the error, and Service Bus moves the message to the dead-letter queue after max retries.

The default is 5 MB. Increase it only if you know your messages are legitimately larger, and verify that the heap has enough room.
