# UTLXe Memory Model and Leak Analysis

**Last reviewed:** May 2026

---

## Memory Areas

| Area | Type | Bounded? | Grows with |
|---|---|---|---|
| TransformationRegistry | ConcurrentHashMap | Yes — entries removed on delete | Number of loaded transformations |
| CompiledStrategy.transformFunction | Per-instance reference | Yes — nulled on shutdown | 1 per transformation |
| ASTCompiler.compilationCache | Static ConcurrentHashMap | **No** — never evicted | Unique source versions ever compiled |
| BytecodeClassLoader | One per compilation | Held by compilationCache | Same as compilationCache |
| Error ring buffer | ConcurrentLinkedDeque, max 100 per tx | Yes | Error rate (capped) |
| LogBuffer | ConcurrentLinkedDeque, max 5000 | Yes — AtomicInteger counter | Log rate (capped) |
| SchemaStore | ConcurrentHashMap | Yes — entries removed on delete | Number of uploaded schemas |
| DaprIntegration.syncStatus | ConcurrentHashMap | Yes — entries removed on delete | Number of transformations |
| ValidationOverrideStore | ConcurrentHashMap | Yes — entries removed on delete | Number of overrides |
| HttpTransport.daprProbedBindings | ConcurrentHashSet | Yes — only grows on new OPTIONS probes | Number of Dapr bindings |

## Compilation Cache (the only unbounded structure)

### What it is

`ASTCompiler.compilationCache` is a `static ConcurrentHashMap<String, TransformFunction>` that maps SHA-256 hashes of transformation source to compiled JVM bytecode functions. It exists in the `companion object` — shared across all `CompiledStrategy` instances.

### Why it exists

Compilation is expensive (~50-100ms). The cache ensures:
- Hot-swap back to a previous version is instant (cache hit)
- Multiple strategies sharing the same source don't recompile
- Repeated `POST /admin/transformations/{name}` with the same source is idempotent

### Why it doesn't evict

Each entry holds a `TransformFunction` instance (~10-50KB including the generated class and its classloader). Evicting an entry means the next request for that source hash recompiles from scratch. Since production deployments have a fixed set of transformations, the cache is effectively bounded.

### When it grows

The cache grows only when a **new unique source** is compiled. Same source = cache hit = no growth.

| Scenario | Cache growth | Impact |
|---|---|---|
| Production (locked mode) | N entries (one per transformation in .utlar) | **None** — fixed set, never changes |
| Dev/test (upload, edit, re-upload) | Grows by 1 per unique source version | ~50KB per version. 100 versions = 5MB. Bounded by container restart. |
| Hot-swap same source | No growth (cache hit) | None |
| Hot-swap new source | +1 entry | ~50KB |

### Risk assessment

In the worst case (dev/test, rapid iteration, no restarts): 1000 unique versions × 50KB = 50MB of metaspace. This is within the JVM's default metaspace budget (256MB+) and well within the 3-6GB heap.

**Conclusion: not a production risk. Acceptable for dev/test.**

### Future improvement (if needed)

Replace `ConcurrentHashMap` with a bounded LRU cache (e.g., `LinkedHashMap` with `removeEldestEntry` or Caffeine cache). Evict entries older than N minutes or cap at 100 entries. Not needed today.

## LogBuffer Counter Drift

The `AtomicInteger` counter tracks buffer size for O(1) cap enforcement. Under extreme concurrent logging, the counter can drift slightly from the actual deque size:

```
Thread A: addFirst() → incrementAndGet() → 5001
Thread B: addFirst() → incrementAndGet() → 5002
Thread A: pollLast() → success → decrementAndGet() → 5001
Thread B: pollLast() → null (Thread A already polled) → decrementAndGet() → 5000
Actual deque size: 5001, counter: 5000
```

This means the buffer may hold slightly more than `maxEntries` (5001 instead of 5000). The drift is bounded by concurrent thread count and self-corrects over time.

**Impact: negligible.** 5001 vs 5000 entries is irrelevant.

## HTTP Connection Handling

All outbound HTTP connections to Dapr (`localhost:3500`) use `HttpURLConnection` with:
- `connectTimeout = 5000ms`
- `readTimeout = 10000ms`
- `conn.disconnect()` called after reading the response

The JVM's connection pooling (`Keep-Alive`) reuses TCP connections transparently. No connection leak.

## Recommendations

1. **Monitor metaspace in dev/test** if developers hot-swap transformations frequently. Watch `jvm_memory_metaspace_bytes` in Prometheus.
2. **No action needed for production** — locked mode has a fixed compilation cache size.
3. **If metaspace grows past 200MB**, consider adding LRU eviction to `compilationCache`.
4. **The `-XX:+AlwaysPreTouch` flag** ensures heap is allocated at startup. Metaspace is separate and grows on demand.
