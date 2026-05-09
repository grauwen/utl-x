# UTLXe Marketplace Offer — Ingress Posture and Access Patterns

**Document purpose:** Decide how the UTLXe Marketplace offer should
configure Azure Container Apps ingress, given that the same offer serves
two operating modes (Open for DEV/TEST, Locked for ACC/PRD) with very
different security expectations. Document the customer-side access
patterns when ingress is internal-only.

**Companion documents:**
- `UTLXe on Azure — Deployment and Operations Guide` — book; §4.1 covers two-port architecture, §11 covers security
- `utlxe-reaching-the-webadmin.md` — Container Apps ingress mechanics
- `dapr-abstract.md` — Dapr sidecar wiring
- `utlx-terraform-module-sketch.md` — Brainboard reference deployment

---

## 1. Reframing the problem — three coupled decisions

The question "should ingress be `external: true` or `false`?" is actually
three coupled decisions, not one:

1. **Open vs. Locked mode** — already determined automatically by
   whether the customer mounted a `.utlar` file at deploy time.
   Self-selecting; no UI choice needed.
2. **Ingress `external = true` vs. `false`** — needs to be set in the
   ARM template; can be a fixed value, a deploy-time choice, or driven
   from another input.
3. **How a customer reaches the Web UI when `external = false`** —
   operator access pattern; depends on the customer's network posture.

The trap is treating these as independent. They're not. **Mode dictates
who needs access, and that determines the right ingress posture.**

| Mode | Who logs in to the Web UI? | Ingress posture |
|---|---|---|
| **Open (DEV/TEST)** | Developers, integration consultants, casual operators editing transformations live | `external: true` is genuinely useful |
| **Locked (ACC/PRD)** | Almost nobody; transformations are baked in. Maybe an SRE checking the dashboard or pulling logs occasionally | `external: false` is the right default |

Ingress posture should track mode, not be a third independent decision.
That simplifies the design considerably.

---

## 2. Three architectures considered

### 2.1 Architecture A — Two ingress modes, customer chooses

Add a question to the deployment form: "Allow public access to the admin
UI?" with three explicit choices:

- **Public (default for DEV/TEST)** — `external: true`, X-Admin-Key auth, anyone with the FQDN and the key can log in
- **Private (default for ACC/PRD)** — `external: false`, reachable only from within the VNet
- **Public with IP allowlist** — `external: true` plus `ip_security_restriction` blocks limiting to specific CIDRs

The form pre-selects based on whether a `.utlar` was provided (Open mode
→ Public default; Locked mode → Private default), but the customer can
override.

**Pros:** the customer makes the call explicitly; one offer covers everyone.
**Cons:** another form question, more to explain, more places to misconfigure.

### 2.2 Architecture B — Always `external: true`, lean on auth

Ship `external: true` always. Trust the X-Admin-Key as the security
boundary. Recommend customers put a Web Application Firewall in front
for production.

**Pros:** simple, one config, customer can always reach the UI.
**Cons:** unacceptable to many enterprises. Banks, insurers, healthcare,
government, anyone with a CISO and a security questionnaire — the answer
to "is the management UI on the public internet?" cannot be "yes, but it
has an API key." This loses the enterprise market.

### 2.3 Architecture C — Always `external: false`, document access patterns

Ship `external: false` always. Provide a documented playbook for
reaching the UI: jumpbox, Bastion, VNet peering, VPN, port forwarding
via `az containerapp exec`.

**Pros:** secure default; enterprises trust it; SRE patterns are well
understood.
**Cons:** terrible developer experience. A small company evaluating
UTLXe for a DEV/TEST workload now has to provision a jumpbox VM before
they can see the dashboard. Many will give up at this hurdle.

---

## 3. Recommendation: Architecture A with smart defaults

Architecture A is the only one that serves both audiences. The
implementation is more nuanced than "add a checkbox," so the rest of
this section sketches what it actually looks like.

### 3.1 The deployment form

Add one question on a new **Network Configuration** tab, separate from
Engine Configuration:

> **Admin UI access**
>
> ⚪ **Public access (recommended for DEV/TEST)** — Reachable from any
> browser at the deployed URL. Protected by your admin key. Best for
> development and quick evaluation.
>
> ⚪ **Public access with IP allowlist** — Reachable only from specific
> IP addresses you provide. Best when developers work from known office
> networks or VPN exits.
>
> ⚪ **Private access only (recommended for ACC/PRD)** — Reachable only
> from inside the Azure Container Apps environment. Best for customers
> who only need `az containerapp exec` for occasional ops access.
>
> ⚪ **VNet-integrated (recommended for enterprise / regulated)** —
> Container App deploys into a VNet that you can peer with your
> corporate network or reach via VPN/ExpressRoute/jumpbox. Required
> if you want private access from a network outside the Container
> Apps environment itself. **This choice cannot be changed after
> deployment** — see help below.
>
> *Note: Public ↔ Private can be flipped after deployment with one CLI
> command. VNet integration cannot — it must be chosen at deployment
> time.*

The default selection is driven by whether a `.utlar` was provided:

- `.utlar` mounted → "VNet-integrated" pre-selected (production posture)
- No `.utlar` → "Public access" pre-selected (developer evaluation posture)

If the customer picks "Public with IP allowlist," show a text field for
the CIDR list:

> **Allowed source IPs (CIDR format, one per line)**
> ```
> 203.0.113.0/24      # office network
> 198.51.100.42/32    # CTO's home VPN exit
> ```

If the customer picks "VNet-integrated," show fields for:

> **Subnet for the Container Apps environment**
> ```
> ⚪ Create a new VNet and subnet (provide CIDR, e.g. 10.42.0.0/23)
> ⚪ Use an existing subnet (provide subnet resource ID)
> ```

### 3.2 The ARM template wiring

Conditional on the choice, the template emits different ingress blocks.
Pseudocode:

```bicep
var isPublic = networkAccess == 'public' || networkAccess == 'publicAllowlist'
var hasIpRestriction = networkAccess == 'publicAllowlist'

ingress: {
  external: isPublic
  targetPort: 8088
  transport: 'auto'
  ipSecurityRestrictions: hasIpRestriction ? [
    for cidr in allowedCidrs: {
      name: 'allowed-${replace(cidr, '/', '-')}'
      action: 'Allow'
      ipAddressRange: cidr
      description: 'Customer-supplied allowlist'
    }
  ] : null
}
```

**Note on VNet integration — important and load-bearing:** when
`external: false`, the customer does not need a VNet immediately to
reach the app via `az containerapp exec` (Pattern 1). But to reach the
app from a jumpbox, Bastion, VPN, or ExpressRoute (Patterns 2–5), the
Container Apps environment must have been created with VNet
integration (`vnetConfiguration.infrastructureSubnetId`). This is a
**one-time decision at environment creation that cannot be changed
afterwards** — see §6.2 for the full constraint. The Network
Configuration tab in §3.1 should therefore include a fourth option for
customers who want VNet integration at deployment time, since adding it
later requires a full redeployment.

---

## 4. The five customer-side access patterns when `external = false`

When ingress is private, the customer needs an access pattern. There
are five real options, ranked by cost and complexity.

### 4.1 Pattern 1 — `az containerapp exec` (zero infra)

Customer authenticates to Azure, opens a shell inside the container,
runs `curl localhost:8088/api/info` from inside the pod. No browser GUI;
useful for ops verification but not for daily admin work.

```bash
az containerapp exec \
  --name <app> --resource-group <rg> \
  --container utlxe-ui \
  --command "/bin/sh"

# Inside the container:
curl -i http://localhost:8088/
curl -i http://localhost:8081/health
```

**Cost:** $0. **UX:** terminal-only, no UI.

### 4.2 Pattern 2 — Jumpbox VM (the "stepping server")

A small VM in the same VNet as the Container Apps environment. Customer
RDPs/SSHs into it, opens a browser inside the VM, navigates to the
internal FQDN.

**Cost:** ~$15–40/month for a B-series VM. **UX:** browser-on-VM
experience.

This is the pattern most enterprises already use for any internal-only
Azure resource — they typically already have a jumpbox VM for accessing
internal databases, internal APIs, etc. **UTLXe just becomes another
internal resource reachable from that existing jumpbox.**

**Worth knowing:** customers running ACC/PRD UTLXe in production almost
always already have a jumpbox or Bastion in their landing zone. They
are not provisioning new infrastructure to access UTLXe; they're just
adding UTLXe to the list of things their existing operations VM can
reach. This dramatically reduces the perceived friction.

### 4.3 Pattern 3 — Azure Bastion

Better security than a jumpbox with a public IP. Bastion terminates
RDP/SSH at the Azure edge, you tunnel through it. Combine with Bastion's
tunnel feature (`az network bastion tunnel`) to forward arbitrary TCP
ports — including HTTPS to the internal Container App.

**Cost:** ~$140/month base + per-hour usage. **UX:** good, but heavy.

Worth mentioning to enterprise customers; not something to bake into
the offer. They'll already have it or know they want it.

### 4.4 Pattern 4 — VNet integration with on-prem network (VPN / ExpressRoute)

For enterprises that already have site-to-site VPN or ExpressRoute
connecting their on-prem network to Azure, the Container Apps
environment can be deployed into a VNet that's reachable from the
corporate LAN. Customer's laptop, on the corporate network or VPN'd in,
reaches the internal FQDN directly.

**Cost:** existing VPN/ExpressRoute infrastructure (already paid for).
**UX:** seamless — laptop reaches Azure-hosted internal services as if
they were on-prem.

**This is what large enterprises actually use.** The customer doesn't
perceive any access friction because their corporate network is
logically extended into Azure.

**Hard prerequisite:** the Container Apps environment must have been
created with `infrastructureSubnetId` set. This is a one-time
deployment-time decision and **cannot be changed afterwards** — see §6.2
for the full caveat. If the offer creates a non-VNet-integrated
environment by default, customers cannot adopt this pattern without
redeploying into a new environment.

### 4.5 Pattern 5 — Application Gateway / Front Door with private backend

Deploy an Azure Application Gateway or Front Door with WAF in front of
the Container App. The gateway is internet-facing; the Container App
is internal-only. The gateway can enforce additional auth (Entra ID,
client certs), WAF rules, geo-blocking, rate limits.

**Cost:** ~$250+/month for App Gateway, ~$35/month + per-request for
Front Door. **UX:** identical to public ingress for the user, but with
enterprise-grade auth and WAF in front.

**This is the pattern enterprises asking about "stepping server"
actually want.** Not a single VM; a managed reverse proxy with auth and
WAF. Worth documenting as a recommended production pattern.

---

## 5. What to put in the offer vs. the documentation

A clean split:

**In the offer's ARM template (deployment-time, pick one):**
- Public
- Public + IP allowlist
- Private (requires customer to handle access via patterns 1–5)

**In the documentation (post-deployment, customer chooses):**
- For dev/test: Public is fine; rotate the admin key regularly
- For prod with existing jumpbox/Bastion: Private + use existing infra (Pattern 2 or 3)
- For prod with VPN/ExpressRoute: Private + VNet integration (Pattern 4)
- For prod with internet-facing access required: Private + App Gateway with WAF (Pattern 5)

The offer doesn't try to be all five patterns. It exposes the ingress
choice and documents how to layer the access pattern the customer
actually wants.

---

## 6. The mutability point — what is and isn't reversible

### 6.1 What IS mutable after deployment

The ingress configuration on a Container App is mutable; flipping
`external` from `false` to `true` (or vice versa) is a single `az
containerapp ingress update` call:

```bash
# Make it public after the fact
az containerapp ingress update \
  --name <app> --resource-group <rg> \
  --type external

# Make it private
az containerapp ingress update \
  --name <app> --resource-group <rg> \
  --type internal
```

You can also enable previously-disabled ingress, change `targetPort`,
change `transport` (`auto`, `http`, `http2`, `tcp`), and update CORS
settings the same way:

```bash
# Enable ingress that was previously disabled
az containerapp ingress enable \
  --name <app> --resource-group <rg> \
  --type external \
  --target-port 8088 \
  --transport auto \
  --allow-insecure false

# Disable ingress entirely
az containerapp ingress disable \
  --name <app> --resource-group <rg>
```

The Container App's running revisions stay alive; the platform
reconfigures the load balancer routing, and within 30–120 seconds the
ingress posture has flipped. The FQDN may briefly resolve to nowhere
during the transition.

To verify a flip took effect:

```bash
az containerapp show \
  --name <app> --resource-group <rg> \
  --query "properties.configuration.ingress" \
  -o json
```

This means the customer is not locked into their initial choice. A team
that starts with Public for dev/test and later wants to lock it down for
production can do so without redeploying. Worth highlighting in the
form's help text — reduces the perceived stakes of the choice.

### 6.2 What is NOT mutable after deployment — the VNet caveat

There is one ingress-adjacent property that is **NOT mutable** after
deployment: the **Container Apps environment's VNet configuration**.

Specifically:

- The Container Apps environment can be deployed with `infrastructureSubnetId` (VNet integration) or without it
- Once created, **this cannot be changed**
- To add or remove VNet integration, the environment must be deleted and recreated
- All Container Apps inside the environment are deleted with it

This matters because **`internal` and `external` ingress mean different
things depending on whether the environment has VNet integration**:

| Environment VNet config | `external: true` means | `external: false` means |
|---|---|---|
| **No VNet (default)** | Public internet HTTPS URL | Reachable only inside the Container Apps environment — not from any customer VNet, not from any jumpbox in another VNet, not from VPN/ExpressRoute |
| **VNet-integrated** | Public internet HTTPS URL (load balancer in customer VNet) | Reachable from inside the customer's VNet (and peered networks, VPN, ExpressRoute) |

So the customer's ability to "make it private later and reach it from
my corporate network via VPN" depends on the environment having VNet
integration — which is decided at environment creation time and cannot
be added later.

### 6.3 Three deployment scenarios for the Marketplace offer

**Scenario 1 — offer creates a fresh environment without VNet integration** (simplest, cheapest, current default Marketplace pattern):
- Customer can flip `external: true ↔ false` freely
- But `external: false` means the app is reachable only from inside the same Container Apps environment — not from the customer's broader Azure network
- **Practically: only Pattern 1 (`az containerapp exec`) works as an access pattern.** Patterns 2–5 from §4 need VNet integration to reach the app
- This is a thin operational story for "private mode"

**Scenario 2 — offer creates a fresh environment with VNet integration:**
- Customer can flip `external: true ↔ false` freely
- `external: false` is meaningfully reachable from the customer's broader Azure network, so Patterns 2–5 work
- Better posture for enterprise customers
- Requires the offer to take a position on VNet provisioning at deploy time (CIDR ranges, subnet sizing)

**Scenario 3 — customer wants to add VNet integration to a deployment that wasn't created with it:**
- **Cannot be done in place.** The customer has to:
  1. Create a new Container Apps environment with VNet integration
  2. Redeploy the Container App into it
  3. Copy over secrets, ingress config, custom domains, certificates
  4. Cut over messaging configuration to point at the new app
  5. Delete the old environment
- Operationally painful; effectively a re-deployment exercise
- Worth warning customers about explicitly so they don't pick "no VNet" by default and regret it 6 months later

### 6.4 What to tell customers in the form's help text

A short, honest paragraph:

> Network access can be changed at any time after deployment by running
> `az containerapp ingress update --type external|internal`. However,
> if you choose **Private access only** and later want it reachable
> from your corporate network (via VPN, ExpressRoute, or VNet peering),
> you also need VNet integration — and that is set at deployment time
> and cannot be added later. If you anticipate needing private network
> access in the future, choose **VNet-integrated** at deployment time
> even if you start with public ingress today.

---

## 7. Concrete recommendations for the offer

Seven things, in order of effort:

1. **Implement Architecture A as a four-option Network Configuration tab** — Public, Public + IP allowlist, Private, VNet-integrated. The fourth option is essential because VNet integration cannot be added after deployment.
2. **Default selection follows mode** — `.utlar` mounted → VNet-integrated; no `.utlar` → Public. Customer can override.
3. **Tell the customer what's reversible and what isn't** — in the form's help text and in the deployment outputs page. Public ↔ Private flips freely; VNet integration is permanent. Do not let customers discover this six months later.
4. **Show the CLI command to flip ingress** — `az containerapp ingress update --type external|internal` should appear in the deployment outputs page, with a one-line note. Customers will appreciate not having to look it up.
5. **Document the five access patterns** — in the book, add a section to Chapter 11 (Security) covering patterns 1–5 from §4 with cost, complexity, when each fits, and which require VNet integration.
6. **Ship a reference Bicep snippet for Pattern 5** — App Gateway + private Container App in a VNet-integrated environment. This is the production-grade pattern enterprises want; copy-paste template makes UTLXe land in regulated industries far more easily.
7. **Consider a "Private Plus" tier in the future** — a higher-priced Marketplace plan that includes the App Gateway + WAF + VNet preconfigured. Enterprise customers happily pay for "secure-by-default with audit trail" as a turnkey package.

---

## 8. Phased rollout plan

You have a publish in progress. Don't stop it for this — but plan the
change for the next revision.

### 8.1 For the version going through certification now

- Pick whichever single posture fits the offer's initial market positioning. Given Marketplace launches typically attract DEV/TEST evaluators first, **`external: true` is the right initial default** — customers who can't reach their own admin UI will give up and write a one-star review.
- Document in the listing description that the offer currently defaults to public ingress + admin key auth, and that customers needing private-only access should contact you for guidance or follow Pattern 1 (`az containerapp exec`).
- Mention in the description that VNet integration and IP allowlisting are on the near-term roadmap.

### 8.2 For the next revision (within a few weeks of go-live)

- Add the Network Configuration tab with the three options (Architecture A).
- Add the documentation section in Chapter 11 (Security) for the five access patterns.
- Provide a reference Bicep template for Pattern 5 (App Gateway + private Container App).
- Optionally add a Private Plus plan with App Gateway + WAF baked in.

This sequencing lets the offer ship now without overengineering, and
addresses the enterprise-customer feedback predictably arriving within
the first month of the listing being live.

---

## 9. The strategic frame

The "stepping server" instinct is a valid signal that the team is
already thinking about enterprise-customer constraints. The next step is
to formalize it into an offer-level choice with documented access
patterns, rather than treating it as a runtime improvisation.

Two things worth absorbing as design principles for future Marketplace
revisions:

**One offer, multiple postures.** A single Marketplace SKU should
configure itself for either dev/test or production at deploy time, not
require two separate listings. Customers move between modes; the offer
should accommodate that.

**The customer brings their network.** UTLXe should not try to provide
a complete enterprise networking stack (jumpboxes, Bastion, App
Gateway). It should expose the right hooks and document how a customer's
existing network stack plugs into them. Enterprises have opinions about
their network; honor them rather than fighting them.

---

## 10. Open questions for the team

The original open question about VNet integration ("should we offer it
at all?") is **now resolved by §6.2**: yes, it must be a deployment-time
option, because it cannot be added afterwards without a full
redeployment. That decision is no longer open.

Two questions remain genuinely open:

1. **Should the Container Apps environment be customer-supplied or offer-provisioned?** Offer-provisioned is simpler for the customer; customer-supplied lets them deploy into an existing VNet they already have peered/configured. The four-option tab in §3.1 leans toward offer-provisioned with a "use existing subnet" advanced field for the VNet-integrated option. Validate this with one or two enterprise design partners before locking it in — they may prefer to bring their own environment entirely.
2. **Should there be a separate "DEV/TEST" SKU and "PROD" SKU in the Marketplace?** Probably no — the mode is already determined by `.utlar` presence, and a single SKU with a Network Configuration tab is more flexible. But worth considering if the Marketplace UX favors multi-SKU listings for discoverability.

A new question that emerges from §6.2:

3. **What's the migration playbook for customers who chose "Private (no VNet)" and now need VNet integration?** The §6.3 Scenario 3 process is operationally painful. Worth documenting it explicitly in the book and possibly providing a migration script that automates the secret/ingress/custom-domain copy. This becomes the support burden if the four-option tab isn't shipped.

---

*Document maintainer: UTLX platform team. Update when the Network
Configuration tab ships in the offer, when VNet integration becomes a
deployment option, or when Container Apps changes its ingress model
materially (e.g., when private endpoints reach GA).*
