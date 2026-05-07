# EF11: UTLXc — Control Plane for UTLXe Fleet Management

**Status:** Design  
**Priority:** Post Azure Marketplace go-live  
**Created:** May 2026  
**Depends on:** EF03 (Admin API), EF09 (production bundle mode), EF10 (dynamic Dapr bindings)

---

## Summary

When a customer runs multiple UTLXe instances (one per flow or flow group), they need centralized management. UTLXc is a separate component — the **control plane** — that manages a fleet of UTLXe **data planes**. It provides a single dashboard, centralized deployment, aggregate monitoring, and coordinated rollout.

UTLXe works perfectly standalone. UTLXc is the enterprise upsell for customers with 5+ instances.

## The Problem

A customer with 12 UTLXe instances (orders, invoices, returns, shipping, notifications, etc.) today must:

- Deploy bundles to 12 instances separately (12 CI/CD targets or 12 curl calls)
- Check health of 12 instances separately (12 URLs to monitor)
- View errors across 12 instances separately (12 error endpoints)
- Answer "which instance handles invoices?" by checking each one
- Coordinate rollouts manually ("deploy new invoice transform, verify, then deploy to next")
- Manage access control per instance (12 admin keys)

This doesn't scale. Every enterprise middleware solves this with a control plane:

| Product | Data Plane | Control Plane |
|---|---|---|
| Kubernetes | kubelet + pods | API server + etcd |
| Istio | Envoy sidecar | istiod |
| MuleSoft | Mule Runtime | Anypoint Platform |
| Kong | Kong Gateway | Kong Manager |
| Azure APIM | Gateway | Management API |
| Dapr | Sidecar | Operator + Placement |
| **UTL-X** | **UTLXe** | **UTLXc** |

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│ Azure Container Apps Environment (VNet)                   │
│                                                          │
│  ┌─────────────┐     ┌─────────────┐                    │
│  │ UTLXc        │     │ Management  │  ← Azure AD login  │
│  │ Control Plane│◄────│ Web UI      │  ← External ingress │
│  │ (internal)   │     └─────────────┘                    │
│  └──────┬───────┘                                        │
│         │                                                │
│         │  http://utlxe-orders:8081/admin/*               │
│         │  http://utlxe-invoices:8081/admin/*             │
│         │  http://utlxe-returns:8081/admin/*              │
│         ▼                                                │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐     │
│  │ UTLXe-orders │ │UTLXe-invoices│ │ UTLXe-returns│     │
│  │ + Dapr       │ │ + Dapr       │ │ + Dapr       │     │
│  │ port 8085    │ │ port 8085    │ │ port 8085    │     │
│  │ port 8081    │ │ port 8081    │ │ port 8081    │     │
│  └──────────────┘ └──────────────┘ └──────────────┘     │
│         ▲                ▲                ▲              │
│         │                │                │              │
│     Service Bus      Service Bus      Service Bus        │
│     (orders)         (invoices)       (returns)          │
└──────────────────────────────────────────────────────────┘
```

## Component Naming

| Component | Full Name | Role |
|---|---|---|
| **UTLXe** | UTL-X Engine (data plane) | Transforms messages. One per flow or flow group. |
| **UTLXc** | UTL-X Control Plane | Manages fleet of UTLXe instances. One per environment. |
| **utlxd** | UTL-X Daemon | IDE integration (development time). Already exists. |
| **utlx** | UTL-X CLI | Command line tool. Already exists. |

The "e" already means "engine." Calling it "data plane" in documentation and marketing is natural — no renaming needed.

## Phased Implementation

### Phase 1: Agentless (v1)

UTLXc discovers UTLXe instances and calls their existing Admin APIs. Zero changes to UTLXe.

```
UTLXc ──GET──→ http://utlxe-orders:8081/admin/info
UTLXc ──GET──→ http://utlxe-orders:8081/admin/transformations
UTLXc ──GET──→ http://utlxe-orders:8081/admin/transformations/*/errors
UTLXc ──POST─→ http://utlxe-orders:8081/admin/bundle
UTLXc ──POST─→ http://utlxe-orders:8081/admin/sync
```

**Instance discovery:** UTLXc reads a configuration file or environment variable listing UTLXe instances:

```yaml
# utlxc-config.yaml
instances:
  - name: utlxe-orders
    address: http://utlxe-orders:8081
    adminKey: "${UTLXE_ORDERS_ADMIN_KEY}"
    tags: [orders, inbound]
    environment: production

  - name: utlxe-invoices
    address: http://utlxe-invoices:8081
    adminKey: "${UTLXE_INVOICES_ADMIN_KEY}"
    tags: [invoices, outbound]
    environment: production

  - name: utlxe-returns
    address: http://utlxe-returns:8081
    adminKey: "${UTLXE_RETURNS_ADMIN_KEY}"
    tags: [returns, inbound]
    environment: production
```

Alternative: discover via Azure Container Apps Management API (list all Container Apps with label `utlxe=true`).

**Pro:** Works today. No UTLXe changes. Simple.
**Con:** Pull-based (polling). Manual instance list. Admin keys per instance.

### Phase 2: Agent Registration (v2)

Each UTLXe instance has a lightweight registration client. When `--control-plane` is set, UTLXe:

1. **On startup:** registers with the control plane
2. **Every 30s:** sends heartbeat with health summary
3. **Accepts:** push commands from control plane (via existing Admin API callback)

```bash
utlxe --mode http \
      --control-plane https://utlxc.internal.myenv.azurecontainerapps.io \
      --instance-name orders-processing \
      --instance-tags "orders,inbound,production"
```

**Registration request (UTLXe → UTLXc on startup):**

```json
POST /api/register

{
  "instance_name": "orders-processing",
  "admin_url": "http://utlxe-orders:8081",
  "tags": ["orders", "inbound", "production"],
  "version": "1.0.1",
  "transformations": 3,
  "mode": "locked",
  "bundle_version": "v3.2.1",
  "dapr_mode": "dynamic"
}
```

**Heartbeat (UTLXe → UTLXc every 30s):**

```json
POST /api/heartbeat

{
  "instance_name": "orders-processing",
  "state": "RUNNING",
  "transformations": 3,
  "ready": true,
  "messages_processed": 45230,
  "errors_last_minute": 0,
  "uptime_seconds": 86400
}
```

**Push deployment (UTLXc → UTLXe via Admin API callback):**

UTLXc calls the registered `admin_url`:
```
POST http://utlxe-orders:8081/admin/bundle
```

The agent is minimal — a background HTTP client in UTLXe, not a full framework. It's optional: if `--control-plane` is not set, UTLXe works standalone as today.

**Pro:** Self-discovering. Push-based. Real-time fleet view.
**Con:** Requires UTLXe change (lightweight). Control plane must be available at startup (graceful degradation if not).

### Phase 3: Advanced Fleet Management (v3)

- **Coordinated rollout:** deploy bundle to one instance, verify health for N minutes, then roll to next batch
- **Canary deployment:** deploy to 10% of instances, compare error rates, auto-promote or rollback
- **Cross-instance pipeline view:** visualize message flow across multiple UTLXe instances (orders → enrichment → notification)
- **Schema registry:** centralized schema management shared across all instances
- **Secret rotation:** coordinate admin key rotation across all instances

## UTLXc Capabilities

### Fleet Dashboard

```
┌──────────────────────────────────────────────────────────┐
│ UTLXc Fleet Dashboard                                    │
│                                                          │
│ Instances: 12 healthy, 0 unhealthy, 0 unreachable       │
│ Transformations: 34 total across fleet                   │
│ Messages/sec: 1,247                                      │
│ Error rate: 0.02%                                        │
│                                                          │
│ ┌─────────────────┬────────┬──────┬────────┬──────────┐ │
│ │ Instance        │ Status │ TX   │ msg/s  │ Errors   │ │
│ ├─────────────────┼────────┼──────┼────────┼──────────┤ │
│ │ utlxe-orders    │ ● OK   │  3   │  342   │ 0        │ │
│ │ utlxe-invoices  │ ● OK   │  4   │  128   │ 2        │ │
│ │ utlxe-returns   │ ● OK   │  2   │   45   │ 0        │ │
│ │ utlxe-shipping  │ ● OK   │  5   │  412   │ 0        │ │
│ │ utlxe-notif     │ ⚠ WARN │  3   │   87   │ 14       │ │
│ │ ...             │        │      │        │          │ │
│ └─────────────────┴────────┴──────┴────────┴──────────┘ │
│                                                          │
│ [Deploy Bundle] [Pause All] [View Errors]                │
└──────────────────────────────────────────────────────────┘
```

### UTLXc API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/instances` | List all registered UTLXe instances |
| `GET` | `/api/instances/{name}` | Instance details (health, transformations, errors) |
| `GET` | `/api/instances/{name}/transformations` | Transformations on a specific instance |
| `GET` | `/api/instances/{name}/errors` | Errors from a specific instance |
| `POST` | `/api/instances/{name}/deploy` | Deploy bundle to a specific instance |
| `POST` | `/api/instances/{name}/pause` | Pause all transformations on an instance |
| `POST` | `/api/instances/{name}/resume` | Resume all transformations on an instance |
| `POST` | `/api/deploy` | Deploy bundle to instances by tag |
| `GET` | `/api/fleet/health` | Aggregate fleet health |
| `GET` | `/api/fleet/errors` | Aggregate errors across all instances |
| `GET` | `/api/fleet/transformations` | All transformations across fleet |
| `POST` | `/api/register` | Instance self-registration (Phase 2) |
| `POST` | `/api/heartbeat` | Instance heartbeat (Phase 2) |

**Deploy by tag:**

```json
POST /api/deploy
{
  "tags": ["invoices"],
  "bundle_url": "https://storage.blob.core.windows.net/bundles/invoice-v3.2.1.utlar",
  "strategy": "rolling",
  "batch_size": 1,
  "health_check_seconds": 60
}
```

This deploys the bundle to all instances tagged `invoices`, one at a time, waiting 60 seconds between each to verify health.

### UTLXc Web UI

A single-page web application served by UTLXc:

- **Fleet overview:** instance list with status indicators
- **Instance drill-down:** transformations, errors, messaging config, Dapr status
- **Bundle management:** upload, deploy to instances/tags, view deployment history
- **Error explorer:** search errors across all instances, filter by transformation/time/severity
- **Audit log:** who deployed what, when, to which instance
- **Settings:** instance tags, alert thresholds, notification channels

Authentication: Azure AD via Easy Auth on the Container App ingress. RBAC:

| Role | Capabilities |
|---|---|
| **Viewer** | See dashboard, errors, health. No deployments. |
| **Deployer** | Deploy bundles to dev/test instances. |
| **Admin** | Deploy to all instances. Pause/resume. Manage config. |

### UTLXc Technology

| Aspect | Choice | Rationale |
|---|---|---|
| Language | Kotlin (same as UTLXe) or Go | Shared codebase / operational simplicity |
| Web framework | Ktor (Kotlin) or Gin (Go) | Lightweight, same as UTLXe |
| Web UI | React or Vue SPA | Standard, team familiarity |
| Persistence | SQLite or PostgreSQL | Instance registry, audit log, deployment history |
| Auth | Azure AD + Easy Auth | Enterprise standard, zero custom auth code |
| Deployment | Azure Container Apps | Same platform as UTLXe |

For v1: a single Kotlin/Ktor application with embedded SQLite. The web UI can be a simple server-rendered HTML dashboard (no SPA framework needed for v1).

## Azure Marketplace Offering

Two offerings:

| Offering | Includes | Target | Price model |
|---|---|---|---|
| **UTLXe** (standalone) | One UTLXe instance | Small teams, 1-3 flows | Per-instance |
| **UTLXe + UTLXc** (fleet) | UTLXc + N UTLXe instances | Enterprise, 5+ flows | Base + per-instance |

UTLXc is the upsell. Customers start with standalone UTLXe, then upgrade to fleet management when they outgrow manual deployment.

## Container Apps Networking

```yaml
# UTLXe Container App (each instance)
ingress:
  external: true
  targetPort: 8085              # data plane — receives Dapr/HTTP messages
  additionalPortMappings:
    - targetPort: 8081          # admin API — internal only
      exposedPort: 8081
      external: false           # only reachable from within VNet

# UTLXc Container App
ingress:
  external: true
  targetPort: 8080              # web UI + API — Azure AD protected via Easy Auth
```

UTLXc calls each UTLXe's admin port via internal DNS: `http://utlxe-orders:8081/admin/*`. Traffic stays within the Container Apps environment. No public exposure of admin APIs.

## What Changes in UTLXe (Phase 2 only)

| Change | Description | Effort |
|---|---|---|
| `--control-plane` CLI flag | URL of UTLXc instance | 0.5 day |
| `--instance-name` CLI flag | Name for registration | 0.5 day |
| `--instance-tags` CLI flag | Comma-separated tags | 0.5 day |
| Registration client | POST to control plane on startup | 1 day |
| Heartbeat sender | Background thread, POST every 30s | 0.5 day |
| Graceful degradation | If control plane unreachable, log warning and continue | 0.5 day |

**Phase 1 requires zero UTLXe changes.** UTLXc v1 calls existing Admin APIs.

## Effort Estimate

| Phase | Scope | Effort |
|---|---|---|
| **Phase 1 (v1)** | Agentless: config-based discovery, aggregate dashboard, deploy-by-tag, basic web UI | 3-4 weeks |
| **Phase 2 (v2)** | Agent registration in UTLXe, push deployment, real-time fleet view | 2 weeks |
| **Phase 3 (v3)** | Coordinated rollout, canary, schema registry, audit log | 4-6 weeks |

## Relationship to Other Features

- **EF03** (Admin API): UTLXc is a consumer of the Admin API — no changes needed
- **EF09** (locked mode): UTLXc deploys .utlar bundles to locked instances via Admin API
- **EF10** (dynamic Dapr): UTLXc can trigger sync on instances after bundle deployment
- **EF08** (.NET SDK): UTLXc could also manage .NET-hosted UTLXe instances (same Admin API)

## When to Build

1. **Ship UTLXe to Azure Marketplace** (EF07, EF09)
2. **First customers** — learn how they actually deploy (1 instance? 5? 20?)
3. **UTLXc v1** — based on real customer patterns, not speculation
4. **UTLXc v2** — agent registration, based on v1 feedback

Do not build UTLXc before having real customers. The Admin API is sufficient for early adopters. UTLXc is the product that emerges from watching how customers manage multiple UTLXe instances.

---

*Feature EF11. May 2026. Design document.*
*Key insight: UTLXe is the data plane. UTLXc is the control plane. Phase 1 requires zero UTLXe changes — UTLXc just calls the existing Admin API. The agent (Phase 2) is a lightweight registration client, not a framework. Build it after real customers validate the need.*
