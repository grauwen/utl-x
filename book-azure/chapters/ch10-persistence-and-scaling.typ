= Persistence and Scaling

This chapter explains how transformations survive container restarts and how to scale UTLXe for production throughput.

== The Persistence Problem

Docker containers have an ephemeral filesystem. When a container restarts --- due to a crash, a scale-to-zero event, or a redeployment --- everything written inside the container is lost. This means the `/utlxe/data/` directory, where uploaded transformations and schemas are stored, is wiped clean.

This is a fundamental container property, not something UTLXe can fix internally. The solution is external storage.

== Azure Files Volume Mount

The recommended approach for the Azure Marketplace is an Azure File Share mounted at `/utlxe/data/`. The mount is configured by the platform at the infrastructure level --- before the container starts. UTLXe uses standard Java file I/O to read and write the directory. It has no knowledge that the directory is backed by a network file share.

```
Container filesystem (ephemeral):
  /utlxe/utlxe.jar             from Docker image, rebuilt on restart

Mount point (persistent):
  /utlxe/data/                  Azure File Share — survives restarts
    schemas/
      order.xsd
    transformations/
      invoice-to-ubl/
        invoice-to-ubl.utlx
      order-enrichment/
        order-enrichment.utlx
```

On restart, UTLXe scans `/utlxe/data/`, finds the transformations from the previous session, compiles them, and becomes ready. Zero manual intervention.

To enable persistent storage, check the "Enable persistent transformation storage" option during Marketplace deployment. This creates an Azure Storage Account and File Share, and configures the volume mount in the Container App.

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
  [Starter], [2 GB], [1536 MB], [~50 KB],
  [Professional], [4 GB], [3072 MB], [~200 KB],
)

The heap is set to 75% of the container memory. The remaining 25% is for the JVM itself (metaspace, thread stacks, native memory) and the operating system.

The `-XX:+AlwaysPreTouch` JVM flag allocates the entire heap at startup. If the container does not have enough RAM, the process fails immediately with a clear error --- rather than crashing hours later under load.

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
    memory: "2Gi"
  limits:
    memory: "2Gi"
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
