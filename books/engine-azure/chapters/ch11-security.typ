= Security

This chapter covers the security model of a UTLXe deployment: network isolation, authentication, secrets management, and the principle of least privilege.

== Network Architecture

UTLXe uses port separation to isolate management traffic from data traffic:

#table(
  columns: (auto, auto, 1fr),
  [*Port*], [*Scope*], [*Contains*],
  [8085], [Public (via ingress)], [Data plane --- message processing only],
  [8081], [Internal (VNet only)], [Admin API, health probes, Prometheus metrics],
)

The Container App ingress exposes only port 8085 to the outside world. Port 8081 is accessible only within the VNet, which means:

- Client applications can send messages for transformation but cannot upload, delete, or modify transformations.
- Operators and CI/CD pipelines access the admin API from within the VNet (or via VPN/private endpoint).
- Prometheus scrapes metrics from port 8081 inside the VNet.
- Kubernetes probes check health on port 8081 inside the VNet.

== Admin API Authentication

The admin API is protected by an API key. Every request to `/admin/*` must include:

```
X-Admin-Key: <value of UTLXE_ADMIN_KEY>
```

If `UTLXE_ADMIN_KEY` is not set, all admin endpoints return 403. The API is locked by default --- there is no open-by-default behavior.

Store the key as a Container App secret:

```bash
az containerapp secret set \
  -n utlxe -g myResourceGroup \
  --secrets admin-key=<your-secret-key>
```

Reference it in the environment:

```bash
az containerapp update \
  -n utlxe -g myResourceGroup \
  --set-env-vars UTLXE_ADMIN_KEY=secretref:admin-key
```

Container App secrets are encrypted at rest and injected as environment variables at runtime. They are not visible in logs or in the container filesystem.

To rotate the key, update the secret and restart the container. No code change is needed.

== Non-Root Container

The UTLXe Docker image runs as a non-root user (`utlxe:utlxe`). The Dockerfile creates a dedicated user and group:

```dockerfile
RUN groupadd -r utlxe && useradd -r -g utlxe utlxe
USER utlxe
```

The container has no elevated privileges. The only writable directory is `/utlxe/data/`, where the Admin API stores uploaded transformations and schemas.

== Secrets in Transformations

Transformations sometimes need secrets --- API keys, connection strings, tokens. Never hardcode secrets in `.utlx` files. Instead, use the `env()` function to read environment variables:

```
%utlx 1.0
input json
output json
---
{
  ...$input,
  authHeader: concat("Bearer ", env("API_TOKEN"))
}
```

The `API_TOKEN` value comes from a Container App secret, injected as an environment variable. The `.utlx` source can be committed to version control without exposing the secret.

The `env()` function is restricted in the CLI for security, but available in the engine (UTLXe) where environment variables are controlled by the deployment configuration.

== VNet Integration

#block(fill: luma(240), inset: 8pt, radius: 4pt, width: 100%)[
  *This section describes a custom deployment, not the Marketplace offer.* The Marketplace
  templates provision a standard Container Apps environment with no VNet parameter, and the
  storage account they create has public network access enabled with no private endpoint.
  Everything below requires the Enterprise deployment templates --- see Chapter 8. If you
  deployed from the Marketplace, treat it as the target architecture rather than a description
  of what you have.
]

For production deployments, place the Container App Environment in a VNet:

- The admin port (8081) is accessible only from within the VNet.
- The data plane ingress (8085) can be configured as internal (VNet only) or external (public with TLS).
- Service Bus and Storage Account connections use private endpoints --- no traffic over the public internet.

A typical production network layout:

#table(
  columns: (auto, 1fr),
  [UTLXe Container App], [VNet, private subnet],
  [Admin access], [VNet only (or via Azure Bastion / VPN)],
  [Data plane ingress], [Internal or external, TLS terminated by Azure],
  [Service Bus], [Private endpoint in same VNet],
  [Storage Account], [Private endpoint in same VNet (for Azure Files)],
  [Prometheus], [In same VNet, scrapes port 8081],
)

== The Data Volume Is a Second Control Path

The admin key above protects `/admin/*`. It is not the only way to change what UTLXe runs.

When persistent storage is enabled, transformations live on an Azure Files share, and the
engine reads that share on startup. A `.utlar` bundle placed there puts the engine into
*locked mode* with that bundle as the production transformation set --- see Chapter 12. That is
the intended deployment path for CI/CD (Chapter 8), and it has a consequence worth stating
directly:

*Write access to the file share is control over what logic runs on your data.* It is more
powerful than the admin key, not less, because a bundle on the volume also causes the Admin API
to refuse changes --- an operator holding the admin key can no longer correct it through the Web
UI and must go back to the share.

So the storage account key deserves the same handling as the admin key:

- Treat it as a production credential. It is not "just storage access".
- Scope it narrowly. A CI pipeline that deploys bundles needs it; a dashboard that reads
  metrics does not.
- Rotate it when people leave, and when a pipeline that used it is retired.
- Keep it out of shared variable groups that a wider team can read.

A deployment where the admin key is carefully protected and the storage key sits in a
team-wide CI variable has secured the front door and left the back one open.

Note that the account key never appears in the Marketplace template as a literal: ARM reads it
with `listKeys()` at deployment time and hands it to the Container Apps environment. Obtaining
it requires Azure RBAC on the storage account, so the exposure is to whoever holds that
permission --- which is a question about your subscription's role assignments, not about UTLXe.

== TLS and HTTPS

UTLXe itself runs HTTP on both ports. TLS termination is handled by the Container App ingress for port 8085. Internal traffic between Dapr, UTLXe, and other services within the VNet does not need TLS --- it is already isolated by the network boundary.

If your security policy requires TLS on internal traffic, configure the Container App Environment with mTLS (mutual TLS), which encrypts all traffic between containers in the environment.
