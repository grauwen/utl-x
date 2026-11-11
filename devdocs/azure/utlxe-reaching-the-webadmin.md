# Reaching the UTLXe Web UI from Your Laptop

**Document purpose:** Explain how Azure Container Apps networking actually
works, why the UTLXe Web UI is (or is not) reachable from a browser on
your laptop, and what to verify or change in the Marketplace offer's
ingress configuration.

**Companion documents:**
- `UTLXe on Azure — Deployment and Operations Guide` — the canonical book; §4.2 covers the Web UI architecture
- `dapr-abstract.md` — Dapr sidecar wiring
- `utlx-terraform-module-sketch.md` — Brainboard / Terraform reference deployment

---

## 1. The core insight

**Azure Container Apps gives you a public HTTPS URL automatically — but
only if you ask for it.**

There is no port-forwarding, no VPN, no jumpbox required. The Container
Apps platform itself handles internet-to-pod routing. But this only
happens when ingress is configured as `external = true`. If ingress is
`external = false` (or disabled), the container is reachable from inside
the Container Apps environment only, and your laptop cannot reach it
without a tunnel.

Most of the confusion comes from this: people see "container in Azure"
and think "Kubernetes pod, I need `kubectl port-forward`." Container Apps
is different — it's a managed service where ingress is a checkbox.

---

## 2. The UTLXe pod layout (recap from book §4.2)

A deployed UTLXe Container App has three containers in one pod:

| Container | Port | Purpose | Internet-reachable? |
|---|---|---|---|
| `utlxe` | 8081 | Admin API + health + metrics | ❌ Internal only |
| `utlxe` | 8085 | Data plane (message processing) | ✅ via Dapr sidecar |
| `dapr` | 3500 | Dapr sidecar | ❌ localhost only |
| `utlxe-ui` | 8088 | Web UI (nginx, proxies `/admin/*` to `utlxe:8081`) | ✅ This is what you open in a browser |

The Web UI container (`utlxe-ui`) is the operator surface. It's the only
container that should accept browser traffic from the public internet.

---

## 3. What happens when ingress is external

When `external_enabled = true` on the Container App, Azure does the
following automatically:

1. Allocates a public DNS name like `utlxe-abc123.kindplant-1234.westeurope.azurecontainerapps.io`
2. Provisions a TLS certificate for that hostname
3. Stands up a managed Layer 7 load balancer (Envoy under the hood)
4. Routes incoming HTTPS traffic from the public internet → load balancer → your Container App's `targetPort` inside the pod

You do nothing for any of this. No NSG rules, no Application Gateway, no
VNet, no public IP — the platform handles it.

So to reach the Web UI from your laptop:

1. The Container App must have ingress enabled with `external = true`
2. The `targetPort` must point at the Web UI container's port (`8088`)
3. You type `https://<fqdn>` in your browser

That's it. The "tunnel from laptop to Azure" people imagine isn't needed
because the URL is already public.

---

## 4. Verify your offer's ingress configuration

Run this against your deployed Container App:

```bash
az containerapp show \
  --name <utlxe-app-name> \
  --resource-group <rg> \
  --query "properties.configuration.ingress" \
  -o json
```

Expected output for a Marketplace-deployed UTLXe offer:

```json
{
  "external": true,
  "fqdn": "utlxe-abc123.kindplant-1234.westeurope.azurecontainerapps.io",
  "targetPort": 8088,
  "transport": "auto",
  "allowInsecure": false
}
```

The three things that matter:

- **`external: true`** — public internet can reach this URL
- **`fqdn: <something>.azurecontainerapps.io`** — this is the URL for the browser
- **`targetPort: 8088`** — traffic lands on `utlxe-ui` (the Web UI), not on the engine admin (`8081`) or the data plane (`8085`)

If `external` is `false`, you cannot reach it from your laptop without a
tunnel (see §6). If `targetPort` is `8085`, the data plane is exposed
instead of the UI, which is wrong for the operator experience.

---

## 5. Diagnosing what you see

| Symptom | Likely cause | Fix |
|---|---|---|
| Browser hangs / times out | `external: false`, or NSG / firewall blocking outbound | Check ingress; check corporate firewall doesn't block `*.azurecontainerapps.io` |
| Browser shows TLS error | Should not happen with default Container Apps cert; suggests a custom domain misconfiguration | Check `customDomains` block; revert to default FQDN if testing |
| Browser shows nginx default page or 404 | `targetPort` points at wrong container (e.g. 8085 data plane), or `utlxe-ui` container not in the deployment | Verify pod composition; fix `targetPort` to `8088` |
| Browser shows login screen but admin key rejected | `UTLXE_ADMIN_KEY` env var not set or doesn't match what you typed | Reset the secret with `az containerapp secret set`, restart the app |
| Browser shows login screen, login works, dashboard loads but transformations missing | Container restarted, persistence not configured | Check Azure Files volume mount per book §10.2 |

---

## 6. If you genuinely need internal-only access

Some customers want the Web UI on a private network only — air-gapped
deployments, regulated industries, internal-tooling-only scenarios. Four
working options when `external: false`:

### 6.1 `az containerapp exec` — internal curl

Runs commands inside the container. Useful for "is the engine alive"
verification. Cannot drive a browser GUI.

```bash
az containerapp exec \
  --name <app> --resource-group <rg> \
  --container utlxe-ui \
  --command "/bin/sh"

# Inside the container:
curl -i http://localhost:8088/
curl -i http://localhost:8081/health
```

**Cost:** $0. **Limit:** terminal only, no browser.

### 6.2 Jumpbox VM in the same VNet

Deploy a small VM in the same VNet as the Container Apps environment.
RDP/SSH into the VM, open a browser there, navigate to the internal FQDN.

**Cost:** ~$20–40/month for a B-series VM. **Limit:** awkward UX, separate
machine to maintain.

### 6.3 Azure Bastion

More secure than a jumpbox with a public IP. Bastion terminates RDP/SSH
at the Azure edge; you tunnel through it from your laptop. Combine with
Bastion's tunnel feature (`az network bastion tunnel`) to forward arbitrary
TCP ports — including HTTPS to the internal Container App.

**Cost:** ~$140/month base + per-hour usage. **Use:** appropriate for
production ops access; overkill for testing.

### 6.4 Tailscale / Cloudflare Tunnel / WireGuard

Run a tunnel client as a small container in the VNet (e.g. Tailscale
sidecar) that exposes the internal FQDN through a managed mesh back to
your laptop.

**Cost:** free tier on most. **Limit:** adds an external dependency; only
appropriate if your team already uses one of these tools.

---

## 7. The recommended default for a Marketplace offer

For UTLXe specifically, the right defaults in the Marketplace ARM /
Bicep template are:

| Port | `external` | Reason |
|---|---|---|
| **8088 (`utlxe-ui`)** | **`true`** | Web UI is the operator surface; first-time customers must reach it from a browser to see the dashboard |
| **8085 (data plane)** | `false` | Reached only via Dapr sidecar; never directly from the internet |
| **8081 (admin API)** | `false` | Proxied via the Web UI; never directly internet-reachable |

The X-Admin-Key (book §4.3, §16.1) is the auth boundary — the Web UI
prompts for it on login and includes it in every proxied `/admin/*`
call. So `external: true` on port 8088 doesn't mean "anyone on the
internet can change transformations"; it means "anyone who knows the
admin key can." That's the correct posture for a Marketplace offer.

Customers who want network isolation should be able to choose it
explicitly — a checkbox in the deployment form ("Restrict admin UI to
VNet only — requires VPN access"), advanced/optional. Default to
internet-reachable + key-protected; let the paranoid customer opt in to
the locked-down posture.

---

## 8. Mental model

The model that helps:

- **Container Apps with `external: true`** = "managed Cloudflare Worker"
  — the platform gives you an HTTPS URL, you do nothing for networking.
- **Container Apps with `external: false`** = "Kubernetes pod with
  ClusterIP service" — only reachable from inside the cluster's network
  boundary.
- **The "VPN to Azure" scenario** = only relevant when `external: false`,
  and exists because Azure correctly refuses to expose internal-only
  resources to the public internet.

Your laptop reaches a normal Container App the same way it reaches any
public website — DNS resolves a name, your browser opens an HTTPS
connection, the platform routes it to the right pod. Nothing magical, no
infrastructure to set up on your side.

---

## 9. Concrete next steps

If you're about to deploy via the Marketplace preview link:

1. **Before clicking Create**, look at the offer's ARM template (search
   for `"external"` and `"targetPort"` in `mainTemplate.json`). Confirm
   the values match the table in §7.
2. **At deploy time**, set a strong `UTLXE_ADMIN_KEY` and save it
   somewhere durable (password manager).
3. **After deploy completes**, capture the FQDN from the deployment
   outputs blade or via `az containerapp show ... --query
   properties.configuration.ingress.fqdn`.
4. **Open `https://<fqdn>` in your browser**, paste the admin key, log
   in. The dashboard from book §4.2.1 (Figure 1) loads.

If the FQDN is unreachable, the troubleshooting table in §5 covers the
common causes.

---

## 10. Action item for the offer itself

The Marketplace deployment form should give the customer the FQDN and a
"Open the admin UI" link in its outputs. Currently (per our earlier
discussion) the Engine Configuration page asks for CPU / memory / min
replicas but doesn't tell the customer how to reach the deployed app
afterward. That's a `createUiDefinition.json` outputs improvement worth
doing alongside the cost-visibility one — a customer who finishes
deployment should land on a screen that says:

> ✅ Deployment complete
>
> Your UTLXe admin UI is at:
> `https://utlxe-abc123.kindplant-1234.westeurope.azurecontainerapps.io`
>
> Log in with the admin key you set during deployment.
>
> [Open admin UI →]

That single change removes the "I deployed it, now what?" gap that
prompted this document.

---

*Document maintainer: UTLX platform team. Update if Container Apps
ingress model changes (e.g., when private endpoints reach GA for ACA),
or if the offer's default ingress posture changes between releases.*
