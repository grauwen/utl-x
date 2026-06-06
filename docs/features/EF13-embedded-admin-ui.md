# EF13: Admin Web UI

> **See also:** **[IF19](IF19-shared-bundle-api-and-management-ui.md)** — this admin UI is the
> **reuse candidate (Option A)** for in-IDE bundle management (embedded in Theia's freed terminal
> slot, driven by a shared bundle API on utlxd).

**Status:** Design  
**Priority:** High (Marketplace customer experience)  
**Created:** May 2026  
**Depends on:** EF03 (Admin API), EF10 (messaging/sync), EF12 (log management)

---

## Summary

The Admin API is powerful but curl-based. Enterprise customers deploying from the Azure Marketplace expect a browser-based interface. EF13 adds a lightweight web UI as a **separate container** in the same pod — calling the existing REST API. Zero changes to UTLXe.

## Design Principle: Separate Container, Communication via API

The web UI runs as its own container alongside UTLXe in the same Container App. It serves static HTML/CSS/JS and calls UTLXe's Admin API on `localhost:8081` (same pod, no network hop).

```
Azure Container Apps Pod
  ├── UTLXe container        (port 8081 admin API, port 8085 data)
  ├── Dapr sidecar           (port 3500, localhost only)
  └── utlxe-ui container     (port 8088 default, external ingress → browser)
           │
           └── fetch() → http://localhost:8081/admin/*
```

All ports are configurable via environment variables (`UI_PORT`, `ADMIN_PORT`). No hardcoded ports.

This means:
- **Zero changes to UTLXe** — the engine stays unchanged
- UI updates don't require rebuilding or redeploying UTLXe
- The UI container is optional — don't deploy it if you don't need it
- All communication goes through the REST API — no internal coupling
- The UI can be built with any technology (vanilla JS now, React/Vue later)
- Separate CI/CD pipeline for the UI
- ~5MB nginx or caddy image serving static files

## Pages

### 1. Dashboard (home page)

```
┌──────────────────────────────────────────────────────────┐
│ UTLXe                                   mode: open  v1.0.1│
│                                                           │
│  Transformations: 3    Schemas: 2    Uptime: 4h 32m      │
│  Messages: 45,230      Errors: 7     Error rate: 0.02%   │
│  Dapr: dynamic         Log level: INFO                    │
│                                                           │
│  ┌─────────┬────────┬────────┬────────┬─────────────────┐│
│  │ Name    │ Status │ Msgs   │ Errors │ Messaging       ││
│  ├─────────┼────────┼────────┼────────┼─────────────────┤│
│  │orders-in│ ● ready│ 34,210 │      2 │ queue:orders-in ││
│  │invoices │ ● ready│ 10,891 │      5 │ topic:raw-inv   ││
│  │returns  │ ○ pause│      0 │      0 │ queue:returns-in││
│  └─────────┴────────┴────────┴────────┴─────────────────┘│
│                                                           │
│  [Upload Transformation]  [Upload Bundle]  [Refresh]      │
└──────────────────────────────────────────────────────────┘
```

Data source: `GET /admin/transformations` + `GET /admin/info`

### 2. Transformation Detail

Click a transformation name to see details:

```
┌──────────────────────────────────────────────────────────┐
│ ← Back                              orders-in            │
│                                                           │
│  Status: ● ready          Strategy: COMPILED              │
│  Messages: 34,210         Errors: 2                       │
│  Deployed: 2026-05-08     Sync: synced                    │
│                                                           │
│  ┌─ Messaging ──────────────────────────────────────────┐│
│  │ Input:  queue: orders-in     [Edit]                  ││
│  │ Output: queue: orders-out                            ││
│  │ Dapr status: active          [Sync]                  ││
│  └──────────────────────────────────────────────────────┘│
│                                                           │
│  ┌─ Test ───────────────────────────────────────────────┐│
│  │ Input:  [                                    ]       ││
│  │         [  {"orderId": "ORD-001", "amount": 100}  ]  ││
│  │         [                                    ]       ││
│  │ [Run Test]                                           ││
│  │                                                       ││
│  │ Output: {"processedOrderId": "PROC-ORD-001", ...}    ││
│  │ Duration: 2ms                                        ││
│  └──────────────────────────────────────────────────────┘│
│                                                           │
│  ┌─ Recent Errors ──────────────────────────────────────┐│
│  │ 2026-05-08 10:15  Null reference: $input.address     ││
│  │ 2026-05-07 14:32  Schema validation: 'amount' req.  ││
│  └──────────────────────────────────────────────────────┘│
│                                                           │
│  ┌─ Source ─────────────────────────────────────────────┐│
│  │ %utlx 1.0                                           ││
│  │ input json                                           ││
│  │ output json                                          ││
│  │ ---                                                  ││
│  │ {                                                    ││
│  │   processedOrderId: concat("PROC-", $input.orderId),││
│  │   ...                                                ││
│  │ }                                                    ││
│  └──────────────────────────────────────────────────────┘│
│                                                           │
│  [Pause] [Resume] [Delete] [Validation Override]          │
└──────────────────────────────────────────────────────────┘
```

Data source: `GET /admin/transformations/{name}` + `GET /admin/transformations/{name}/messaging` + `GET /admin/transformations/{name}/errors`

### 3. Upload Page

```
┌──────────────────────────────────────────────────────────┐
│ Upload Transformation                                     │
│                                                           │
│  Name: [orders-in          ]                              │
│                                                           │
│  Source (.utlx):                                          │
│  [Choose file] or drag & drop    invoice-to-ubl.utlx     │
│                                                           │
│  ── or paste source directly ──                           │
│  ┌──────────────────────────────────────────────────────┐│
│  │ %utlx 1.0                                           ││
│  │ input json                                           ││
│  │ output json                                          ││
│  │ ---                                                  ││
│  │                                                      ││
│  └──────────────────────────────────────────────────────┘│
│                                                           │
│  [Upload]  [Upload & Test]                                │
│                                                           │
│  Status: ✓ Deployed successfully (48ms compile)           │
└──────────────────────────────────────────────────────────┘
```

Action: `POST /admin/transformations/{name}`

### 4. Messaging Configuration

```
┌──────────────────────────────────────────────────────────┐
│ Messaging: orders-in                    sync: draft       │
│                                                           │
│  Input:                                                   │
│    ○ Queue  ○ Topic  ○ Event Hub  ○ None                 │
│    Queue name: [orders-in         ]                       │
│                                                           │
│  Output:                                                  │
│    ○ Queue  ○ Topic  ○ Event Hub  ○ None                 │
│    Topic name: [processed-orders  ]                       │
│                                                           │
│  [Save (draft)]  [Save & Sync]                            │
│                                                           │
│  Sync status: draft — 2 pending changes                   │
│  [Sync Now]                                               │
└──────────────────────────────────────────────────────────┘
```

Actions: `POST /admin/transformations/{name}/messaging` + `POST /admin/transformations/{name}/sync`

### 5. Logs Page

```
┌──────────────────────────────────────────────────────────┐
│ Logs                          Level: [INFO ▼] [Apply]     │
│                                Auto-revert: [30] min      │
│                                                           │
│  Filter: [____________] Level: [ALL ▼] [Search]           │
│                                                           │
│  ┌────────────────────────────────────────────────────── ┐│
│  │ 13:15:30 INFO  HttpTransport  [orders-in] MessageId= ││
│  │ 13:15:30 DEBUG HttpTransport  Dapr input headers: ... ││
│  │ 13:15:30 DEBUG HttpTransport  Output → POST http://...││
│  │ 13:15:28 INFO  HttpTransport  [invoices] MessageId=.. ││
│  │ 13:15:25 WARN  HttpTransport  Dapr output returned 500││
│  │ ...                                                   ││
│  └───────────────────────────────────────────────────────┘│
│                                                           │
│  Showing 100 of 2,847 buffered    [Load more] [Clear]     │
└──────────────────────────────────────────────────────────┘
```

Data source: `GET /admin/logs` + `GET /admin/log/level` + `POST /admin/log/level`

### 6. Schemas Page

```
┌──────────────────────────────────────────────────────────┐
│ Schemas (2)                          [Upload Schema]      │
│                                                           │
│  ┌──────────────┬──────────┬─────────────────────────── ┐│
│  │ Filename     │ Size     │ Uploaded                    ││
│  ├──────────────┼──────────┼─────────────────────────── ┤│
│  │ order.xsd    │ 4.8 KB   │ 2026-05-08 10:00           ││
│  │ invoice.json │ 2.3 KB   │ 2026-05-08 10:01           ││
│  └──────────────┴──────────┴─────────────────────────── ┘│
└──────────────────────────────────────────────────────────┘
```

Data source: `GET /admin/schemas`

### 7. Sync Overview

```
┌──────────────────────────────────────────────────────────┐
│ Dapr Sync                          mode: dynamic          │
│                                                           │
│  Sidecar: ● reachable (v1.17.5)                          │
│  Components dir: /dapr/components                         │
│                                                           │
│  ┌─────────┬────────┬────────────────┬──────────────────┐│
│  │ Name    │ Status │ Last synced    │ Actions          ││
│  ├─────────┼────────┼────────────────┼──────────────────┤│
│  │orders-in│ synced │ 10:05 today    │                  ││
│  │invoices │ draft  │ —              │ [Sync]           ││
│  │returns  │ no_dapr│ —              │                  ││
│  └─────────┴────────┴────────────────┴──────────────────┘│
│                                                           │
│  [Sync All Drafts]                                        │
└──────────────────────────────────────────────────────────┘
```

Data source: `GET /admin/sync` + `GET /admin/dapr`

## Locked Mode Behavior

When UTLXe is in locked mode, the UI reflects this:

- Banner at the top: "Production mode — read-only. Changes deploy via CI/CD."
- Upload buttons: disabled / hidden
- Delete buttons: disabled / hidden
- Messaging edit: disabled
- Operational buttons still active: Pause, Resume, Validation Override, Log Level, Test
- All read views work normally

The UI reads `mode` from `GET /admin/info` and adjusts accordingly.

## Technology

| Aspect | Choice | Rationale |
|---|---|---|
| Framework | None — vanilla HTML/CSS/JS | No build step, no npm, no node_modules, smallest footprint |
| CSS | Minimal custom CSS | Clean, professional, no framework bloat |
| JS | Vanilla `fetch()` + DOM manipulation | No React, no Vue — the UI is simple enough |
| Web server | nginx or caddy (~5MB image) | Serves static files, reverse-proxies `/admin/*` to UTLXe |
| Authentication | Uses existing `X-Admin-Key` | JS stores key in `sessionStorage` after login prompt |

### Why nginx, not a programming language?

The UI container has no server-side application code. The browser does all logic via `fetch()` to the REST API. The container only serves static files and reverse-proxies API calls. That's nginx — no runtime needed.

| Option | Image size | Server-side code? | Verdict |
|---|---|---|---|
| **nginx** | 5MB | No — just config | **Best fit** |
| caddy | 15MB | No — just config | Also good, simpler HTTPS |
| Go | 10MB | Unnecessary | Overkill for static files |
| Kotlin/Ktor | 200MB+ | Unnecessary | Way overkill — JVM for serving HTML |
| Node/Express | 100MB+ | Unnecessary | node_modules for serving HTML |

If the UI ever needs server-side logic (session management, server-side rendering), pick a language then. Today the REST API is the backend — the browser is the application.

### Why no framework?

The UI has ~7 pages with tables, forms, and text areas. No complex state management. No real-time updates. No component reuse across dozens of pages. A framework would add:
- Build pipeline (webpack/vite)
- Node.js dependency
- 500KB+ of JavaScript framework code
- Maintenance burden (framework version upgrades)

For ~20KB of vanilla JS serving 7 pages, a framework is overhead without benefit. If the UI grows significantly (e.g., for UTLXc), a framework can be introduced later — the API contract doesn't change.

## File Structure

```
utlxe-ui/
  Dockerfile              ← FROM nginx:alpine, COPY static files
  nginx.conf              ← Reverse proxy /admin/* to localhost:8081
  static/
    index.html            ← SPA entry point (all pages)
    style.css             ← Minimal styling
    app.js                ← All application logic
    utlxe-logo.svg        ← Logo for header
```

One HTML file, one CSS file, one JS file. The JS uses hash-based routing (`#/transformations`, `#/logs`, etc.) for navigation without page reloads.

## Container Configuration

### Dockerfile

```dockerfile
FROM nginx:alpine
ENV UI_PORT=8088
ENV ADMIN_PORT=8081
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY static/ /usr/share/nginx/html/
EXPOSE ${UI_PORT}
```

The nginx `templates/` directory uses `envsubst` on startup — `${UI_PORT}` and `${ADMIN_PORT}` are replaced from environment variables. Override at deploy time:

```bash
docker run -e UI_PORT=9090 -e ADMIN_PORT=18081 -p 9090:9090 utlxe-ui
```

### nginx.conf

```nginx
server {
    listen ${UI_PORT};

    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /admin/ {
        proxy_pass http://localhost:${ADMIN_PORT};
        proxy_set_header Host $host;
    }

    location /health {
        proxy_pass http://localhost:${ADMIN_PORT};
    }
}
```

All ports configurable via environment variables. Default: UI on 8088, admin proxy to 8081.

The browser calls the UI container on port 8080. API calls to `/admin/*` are reverse-proxied to UTLXe on `localhost:8081`. The browser never calls UTLXe directly — no CORS issues.

### Container App Configuration (Bicep)

```bicep
// Add as a second container in the UTLXe Container App
containers: [
  {
    name: 'utlxe'
    image: utlxeImage
    // ... existing UTLXe config
  }
  {
    name: 'utlxe-ui'
    image: 'ghcr.io/grauwen/utlxe-ui:latest'
    resources: { cpu: json('0.25'), memory: '0.5Gi' }
  }
]

// Ingress points to the UI container
ingress: {
  external: true
  targetPort: 8088    // UI container (configurable via UI_PORT env var)
}

// Admin port stays internal (additional port mapping)
additionalPortMappings: [
  { targetPort: 8081, exposedPort: 8081, external: false }
  { targetPort: 8085, exposedPort: 8085, external: true }
]
```

### Optional Deployment

The UI container is optional. Customers who manage UTLXe via CI/CD and curl don't need it:

```bicep
// With UI (Marketplace default)
containers: [ utlxeContainer, uiContainer ]

// Without UI (DevOps teams)
containers: [ utlxeContainer ]
```

## Authentication Flow

1. User opens `http://utlxe:8081/`
2. JS checks `sessionStorage` for admin key
3. If not found: show a login prompt (single text field for the API key)
4. JS calls `GET /admin/info` with the key to verify
5. If 403: show error, stay on login
6. If 200: store key in `sessionStorage`, show dashboard
7. All subsequent `fetch()` calls include `X-Admin-Key` header

The key is stored in `sessionStorage` (cleared when the tab closes), not `localStorage` (persists across sessions). This is intentional: the admin key should not persist in the browser.

## Effort Estimate

| Task | Effort |
|---|---|
| HTML structure + CSS styling | 1 day |
| Dashboard page (list + info) | 0.5 day |
| Transformation detail page (source, errors, test) | 1 day |
| Upload page (file + paste) | 0.5 day |
| Messaging configuration page | 0.5 day |
| Logs page (filter, level change) | 0.5 day |
| Schemas + sync overview pages | 0.5 day |
| Locked mode behavior | 0.5 day |
| Auth flow (login prompt) | 0.5 day |
| Dockerfile + nginx.conf | 0.5 day |
| Bicep/Terraform for optional UI container | 0.5 day |
| Testing across browsers | 0.5 day |
| **Total** | **~7 days** |

## Relationship to Other Features

- **EF03** (Admin API): the UI is a consumer of the REST API — no API changes needed, no UTLXe changes
- **EF09** (locked mode): UI disables mutating actions when locked
- **EF10** (messaging/sync): messaging config page and sync overview
- **EF11** (control plane): UTLXc will have its own, richer fleet-management UI. This UI is per-instance only.
- **EF12** (log management): logs page with level change and filtering

## When to Build

The UI is a strong differentiator for the Marketplace offering. Customers evaluating UTLXe expect a browser interface, not curl documentation. Build it before or shortly after Marketplace go-live.

However, the REST API works today. Early adopters (DevOps teams) don't need a UI. The UI is a polish feature — important for broad adoption, not blocking for first customers.

## Why Separate Container, Not Embedded

| Concern | Embedded in JAR | Separate container |
|---|---|---|
| Changes to UTLXe | Ktor config + static files in JAR | **Zero** |
| UI update cycle | Rebuild + redeploy UTLXe JAR | Rebuild only UI image |
| Separation of concerns | Frontend mixed into JVM codebase | Clean separation |
| Optional | Hard to disable | Don't deploy the container |
| Size impact on UTLXe | +20KB (small but principle) | None |
| CI/CD | Single pipeline | UI has own pipeline |
| Future technology | Locked to JAR resources | Can evolve independently (React, Vue, SSR) |

The principle: UTLXe is a data plane engine. It exposes an API. The UI is a separate concern that consumes that API. Mixing them creates coupling that makes both harder to evolve.

---

*Feature EF13. May 2026. Design document.*
*Key insight: the REST API IS the backend. The UI is a ~5MB nginx container in the same pod, reverse-proxying to UTLXe. Zero UTLXe changes. The UI container is optional — skip it for DevOps-managed deployments.*
