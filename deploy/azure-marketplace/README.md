# Azure Marketplace — the UTLXe offering

The offering is **UTL-X Transformation Engine** by GLOMIDCO, a Marketplace **managed
application** with two plans, `starter` and `professional`. Customers never pull an image
themselves: the wizard provisions a Container App, and the Container App pulls from GHCR.

| | |
|---|---|
| Offer id | `44ab35db-fe99-48ef-b182-ffe9d0c919e3` |
| Publisher id | `glomidcobv1777133923122` |
| Plans | `starter` (8 workers), `professional` (32 workers) |
| Engine image | `ghcr.io/utlx-lang/utlxe` |
| Admin UI image | `ghcr.io/utlx-lang/utlxe-ui` |

## The images must be public

`mainTemplate.json` contains **no `registryCredentials`**. Azure therefore pulls both images
anonymously, and a private package means every new deployment of the offering fails with an
image-pull error. This is the single most important fact about the offering, and the one
least visible from the templates themselves.

To check without any credentials — a public package hands out a pull token, a private one
does not, and GHCR answers identically for a package that does not exist:

```bash
curl -s "https://ghcr.io/token?scope=repository:utlx-lang/utlxe:pull&service=ghcr.io"
# public  -> {"token":"…"}
# private -> {"errors":[{"code":"UNAUTHORIZED",…}]}
```

Visibility is set per package at
`https://github.com/orgs/utlx-lang/packages/container/<image>/settings` → Danger Zone →
Change visibility. The `verify-anonymous-pull` job in `.github/workflows/docker-publish.yml`
runs this check after every push and fails the run if either image is unreachable.

## Layout

```
starter/          mainTemplate.json + createUiDefinition.json   <- source of truth
professional/     mainTemplate.json + createUiDefinition.json   <- source of truth
zips/             the packages uploaded to Partner Center, one per plan per version

build-zips.sh         builds zips/ from the plan directories
utlxe-acceptance.sh   deploys a throwaway copy, asserts it works, destroys it
utlxe-costs.sh        what a run costs — estimated from retail rates, or actual

mainTemplate.json, createUiDefinition.json   <- SUPERSEDED, see below
```

The pair at the top level predates the two-plan split: it has no `adminKey` parameter and no
`utlxe-ui` sidecar, and it is not what any shipped package contains. Nothing builds from it.
It is kept only because the `azure-bicep/` templates still reference the same shape.

Its image pins are kept current anyway — they were bumped to `1.4.0` even though nothing
packages them. Not because the files are maintained (they are two generations behind
structurally) but because a stale pin here is a loaded gun: the version it carried,
`utlxe:1.1.0`, is an arm64 image that cannot run on Azure at all. A dead template that still
parses is far more likely to be copied than rewritten.

## Releasing a new version of the offering

Four steps, in this order. Steps 1–2 are the images, 3–4 are the offer.

1. **Tag the release.** Pushing a `v*` tag runs `docker-publish.yml`, which builds both
   images and pushes `:<version>` and `:latest`. The workflow refuses to run when the tag
   disagrees with `version` in `build.gradle.kts`, and needs a `GHCR_TOKEN` secret — a PAT
   with `write:packages` authorised for the `utlx-lang` organisation, because the default
   `GITHUB_TOKEN` is scoped to this repository and cannot write packages owned by that org.

2. **Confirm both images are public** (above). A newly created GHCR package is private by
   default, so a first push of a new image name always needs this step.

3. **Bump the plan templates and build the packages.** Each plan carries three image
   references — two in `mainTemplate.json`, one in `createUiDefinition.json`:

   ```bash
   ./build-zips.sh            # version read from the image tag in starter/mainTemplate.json
   ```

   The script refuses to package a plan whose images are not all on one version, whose JSON
   is invalid, or whose sizing is wrong.

4. **Upload in Partner Center**, one zip per plan, then Review and publish. The pipeline is
   automated validation → preview → publisher signoff → certification → publish; the 1.2.1
   round took two days end to end.

5. **Verify the published offer actually deploys.** `./utlxe-acceptance.sh --template
   ./starter/mainTemplate.json` creates a throwaway resource group, deploys, asserts, and
   deletes it again. This is the step that had never existed, and its absence is why three
   broken generations shipped: the packages validated, the images pulled, and nothing ever
   checked that a container started.

   `./utlxe-costs.sh` sizes what such a run costs before you make it — a ten-minute Starter
   run is a few cents at retail and normally inside the monthly free grant. Run it with
   `--hours 24` before using the acceptance script's `--keep`.

## Testing a deployment — `utlxe-acceptance.sh`

The offer was published on 2026-05-11 and first actually deployed on 2026-11-17. That gap is
where six defects lived, none of them findable by code review, unit tests or the conformance
suite. This script is what closes it: it deploys the real template into a throwaway resource
group, asserts the result, and destroys it.

`./utlxe-acceptance.sh --help` prints the full header. The summary:

### Modes

```bash
./utlxe-acceptance.sh --status                 # can a run start? is anything still billing?
./utlxe-acceptance.sh --template ./starter/mainTemplate.json
./utlxe-acceptance.sh --template ./starter/mainTemplate.json --keep
./utlxe-acceptance.sh --teardown --dry-run     # what cleanup would remove
./utlxe-acceptance.sh --teardown               # remove it
./utlxe-acceptance.sh --image ghcr.io/utlx-lang/utlxe:1.4.3   # engine only, NOT the offer
```

`--template` is the one that tests what a customer receives. `--image` deploys a bare container
app and exercises the engine, so a green run there says nothing about the Marketplace artifacts.

`--status` and `--teardown` need no template. Groups are found by the tag the script applies
(`purpose=utlxe-acceptance`), never by name pattern.

### What it asserts

18 assertions across the deployment, the public surface and the engine:

| | |
|---|---|
| deployment | ARM deployment succeeded · ingress FQDN published |
| reachability | `/health` reachable within `HEALTH_TIMEOUT` |
| empty state | fresh container reports 0 transformations · reports `ready=false` |
| security | admin API rejects an unauthenticated request |
| function | transformation uploads and compiles · test endpoint reports ok · output is correct |
| loaded state | container becomes ready after upload · health reports 1 transformation |
| durability | container recovers after restart · (restart-survival is SKIPped without storage) |
| cleanup | resource group deletes cleanly |

Results are PASS / FAIL / SKIP / WARN. Exit 0 all passed, 1 at least one FAIL, 2 fatal
(bad arguments, missing tool, not logged in).

### Reading a failure

On an unhealthy container the script collects, from the same deployment, what would otherwise
cost another 30-minute cycle to gather:

| block | what it settles |
|---|---|
| `--- ingress ---` | whether `targetPort` and `external` are what the template intended |
| `--- public surface ---` | `/`, `/health`, `/metrics`, `/admin/info` with HTTP status and curl exit |
| `--- utlxe ---` / `--- utlxe-ui ---` | each container's logs separately |
| `--- system ---` | platform events: pull failures, OOM kills, **probe failures** |
| `--- replicas ---` | per-container `ready`, `started`, `restartCount` |
| `--- from inside utlxe-ui ---` | listening sockets, the rendered nginx config, loopback reach to the engine |

Two readings worth knowing, both learned the hard way:

- **Every path timing out, including `/`, with both containers healthy** means a container is
  not *ready*. Container Apps routes nothing to a replica with any not-ready container, so a
  working proxy looks dead. Check `--- replicas ---` and the probe failures in `--- system ---`
  before suspecting the sidecar. That is [EB18](../../docs/bugs-fixed/EB18-readiness-requires-a-transformation-so-no-deployment-can-serve.md).
- **`/` returning 200 while `/health` returns 502** is the opposite: nginx is fine and the proxy
  hop to the engine is broken.

### Tunables

All are environment variables with defaults:

| variable | default | |
|---|---|---|
| `LOCATION` | `westeurope` | quota is per region — use another region to run in parallel |
| `RG_PREFIX` | `rg-utlxe-acceptance` | |
| `DEPLOYMENT_NAME` | `utlxe-acceptance` | fixed so the deployment can be asserted on regardless of template filename |
| `SUBSCRIPTION` | current `az` context | |
| `HEALTH_TIMEOUT` | `300` | seconds to wait for the first healthy response |
| `ENV_WAIT_TIMEOUT` | `1500` | seconds to wait for the one-per-region environment slot |
| `P_APP_NAME` / `P_ADMIN_KEY` | `namePrefix` / `adminKey` | the only offer-specific parameter names |

### Cost and cadence

A full run is a few cents and normally inside the monthly free grant — `./utlxe-costs.sh`
sizes it. The real cost is time: roughly 30 minutes per iteration on a subscription with a
one-environment quota, because teardown is asynchronous and the next run waits for the slot.

Two ways to cut it: `--location northeurope` runs while West Europe is still cleaning up, and
`--keep` leaves a deployment alive so the next hypothesis can be tested in seconds instead of
half an hour. A kept deployment bills about $0.30/hour and holds the quota slot, so `--status`
will remind you it exists.

## Subscription prerequisites, and the environment quota

Both of these surfaced on 2026-11-17, during the first real deployment this offer has ever
had — six months after it went Live. Neither is visible from the templates, and neither is
stated in the offer listing.

### Resource providers must be registered

`Microsoft.App` and `Microsoft.OperationalInsights` were `NotRegistered` on a subscription
that had never run Container Apps. Azure usually auto-registers on first use, but it is not
guaranteed, and when it does not the failure (`MissingSubscriptionRegistration`) reads like a
template defect:

```bash
az provider register --namespace Microsoft.App --wait
az provider register --namespace Microsoft.OperationalInsights --wait
```

### The managed-environment quota — and why it is partly our doing

```
MaxNumberOfRegionalEnvironmentsInSubExceeded: cannot have more than 1
Container App Environments in West Europe
```

Three things are easy to conflate here, and the first reading of this error got it wrong:

1. **It is a quota on `Microsoft.App/managedEnvironments`, not a limit on Marketplace
   deployments.** Microsoft does not cap how many times an offer may be deployed.
2. **The limit depends on subscription type.** The MPN benefit subscription used for testing
   is capped at **1**; standard pay-as-you-go and EA subscriptions get a substantially higher
   default. So most customers will not meet this, but we always will.
3. **It only bites because this template creates its own environment every time.** One
   environment can host many Container Apps. A template that accepted an existing
   environment would not consume a quota slot at all.

Check the real number for any subscription:

```bash
az rest --method get --url "https://management.azure.com/subscriptions/$(az account show --query id -o tsv)/providers/Microsoft.App/locations/westeurope/usages?api-version=2024-03-01" \
  --query "value[?name.value=='ManagedEnvironmentCount'].{used:currentValue,limit:limit}" -o table
```

**This contradicts the DTAP guidance in the book.** `books/engine-azure` ch08 says *"For DTAP
(Dev/Test/Acc/Prd), deploy UTLXe from the Marketplace once per environment."* Four
deployments in one subscription and region means four managed environments. On a
limit-of-1 subscription that fails at the second; on a limit-of-15 subscription it works but
spends four slots on something environments exist to share. Either the book changes or the
template does — see **Open: environment reuse** below.

### What it means for testing here

The quota is per region, and teardown is asynchronous, so on this subscription acceptance
runs are strictly serial with a slow turnaround:

```
wait for slot   10-20 min   previous run's environment in ScheduledForDelete
deploy           8-18 min   managed-environment provisioning dominates
assert            ~2 min
teardown issue    instant   but the next run pays for it
```

About **30 minutes per iteration**. Two ways to cut it:

- **Alternate regions** — the quota is per region, so `--location northeurope` runs while
  West Europe is still cleaning up.
- **`--keep` on a failing run** — leaves the environment up to inspect, skipping a full
  provision/delete cycle. Roughly $0.30/hour; `./utlxe-costs.sh --hours N` gives the number.

`utlxe-acceptance.sh` waits for the slot in preflight (`ENV_WAIT_TIMEOUT`, default 1500s)
rather than failing with an error that looks like a template defect.

### Open: environment reuse

Adding an optional `existingEnvironmentId` parameter — use the customer's environment when
supplied, create one only when it is not — would remove the quota collision, make the DTAP
story work as documented, and cut the slowest step out of every test run. It is the normal
shape for a Container Apps marketplace offer. Not in 1.4.1; it is a template change that
needs its own deployment test.

## Certification: apiVersion recency

The 1.4.2 submission was rejected on 2026-11-18 under **policy 300.4.1.1 — Best Practice
Tests**, which is Microsoft running the [ARM template test toolkit](https://aka.ms/arm-ttk)
against `mainTemplate.json`.

The toolkit's *apiVersions Should Be Recent* test fails any apiVersion older than **730 days**
when a newer GA version exists. Eight of nine resource declarations were over, the worst by
more than five years:

```
Microsoft.Resources/deployments            2021-04-01   2057d
Microsoft.OperationalInsights/workspaces   2022-10-01   1509d
Microsoft.Storage/storageAccounts          2023-05-01   1297d
Microsoft.App/*                            2024-03-01    992d
```

These predate all of the 2026-11 work — the versions were inherited from the existing template
and carried forward, including into the storage resources added for persistence. The same
submission would have been rejected as 1.4.0 or 1.4.1.

**`reference()` and `listKeys()` carry their own apiVersion**, separate from the resource
declaration. Three such expressions kept 2022 and 2024 versions alive after every declaration
was current — the Log Analytics `customerId`/`sharedKey` lookups and the container app's FQDN
lookup. Grep the whole file for date strings, not just `"apiVersion"` keys.

**The `2019-04-01` in `$schema` is not an apiVersion** and must not be bumped. It is the
current deploymentTemplate schema URI and arm-ttk requires it.

### Checking before you submit

Current GA versions come from the subscription, not from memory:

```bash
az provider show --namespace Microsoft.App \
  --query "resourceTypes[?resourceType=='containerApps'].apiVersions[0]" -o tsv
```

Two child types are **not listed** in the provider manifest —
`Microsoft.App/managedEnvironments/storages` and
`Microsoft.Storage/storageAccounts/fileServices/shares` — so their versions are taken from the
parent family, whose listed siblings all move together. That is reasoned rather than verified,
which is why an acceptance run has to follow any apiVersion change: ARM rejects an unsupported
version at deployment, and it is better to learn that in fifteen minutes than in a two-day
certification cycle.

## Package archive

Partner Center **retains only the latest package**: once a new one is published, previously
published packages are no longer accessible there. So `zips/` is the only archive, and every
package that has been uploaded is committed to this repository.

Record the SHA-256 and the upload date here whenever a package is submitted, so it stays
possible to say exactly which bytes Microsoft received.

| package | sha256 | engine | UI | submitted | deployable |
|---|---|---|---|---|---|
| `utlxe-starter.zip` | `2c17540a8056849e…` | `utlxe:1.1.0` | `utlxe-ui:1.0.0` | 2026-05-11 (assumed) | no — adminKey, arm64 |
| `utlxe-professional.zip` | `b2d61adfd5685931…` | `utlxe:1.1.0` | `utlxe-ui:1.0.0` | 2026-05-11 (assumed) | no — adminKey, arm64 |
| `utlxe-starter-1.2.1.zip` | `7dd1acfc2c5b413b…` | `utlxe:1.2.1` | `utlxe-ui:1.0.0` | unknown | no — adminKey, UI arm64 |
| `utlxe-professional-1.2.1.zip` | `aa34b6d4f7b5633a…` | `utlxe:1.2.1` | `utlxe-ui:1.0.0` | unknown | no — adminKey, UI arm64 |
| `utlxe-starter-1.4.0.zip` | `f195fc75825708f1…` | `utlxe:1.4.0` | `utlxe-ui:1.4.0` | **2026-11-16** | **no — adminKey** |
| `utlxe-professional-1.4.0.zip` | `9f3791f1afd4118f…` | `utlxe:1.4.0` | `utlxe-ui:1.4.0` | **2026-11-16** | **no — adminKey** |
| `utlxe-starter-1.4.1.zip` | `5b8522b72384d7f2…` | `utlxe:1.4.1` | `utlxe-ui:1.4.1` | superseded before submission | yes |
| `utlxe-professional-1.4.1.zip` | `a8bb00865df6ce76…` | `utlxe:1.4.1` | `utlxe-ui:1.4.1` | superseded before submission | yes |
| `utlxe-starter-1.4.2.zip` | `1bc9c769eab3c07c…` | `utlxe:1.4.2` | `utlxe-ui:1.4.2` | **2026-11-18 — rejected, stale apiVersions** | yes |
| `utlxe-professional-1.4.2.zip` | `23dc175915e14a90…` | `utlxe:1.4.2` | `utlxe-ui:1.4.2` | **2026-11-18 — rejected, stale apiVersions** | yes |
| `utlxe-starter-1.4.3.zip` | `8d97202384f89175…` | `utlxe:1.4.3` | `utlxe-ui:1.4.3` | *pending* | yes |
| `utlxe-professional-1.4.3.zip` | `cbc4884d805dd442…` | `utlxe:1.4.3` | `utlxe-ui:1.4.3` | *pending* | yes |

Full digests:

```
2c17540a8056849e97abe5871c1cbb6ed9f7d19cdaf548c22f3c7feb73dfb455  utlxe-starter.zip
b2d61adfd5685931af021dc6203123ad5b6c8ebc7d236cab80f2b8410a7fa0d5  utlxe-professional.zip
7dd1acfc2c5b413b120b466bc1a27e28196c274fe6b99a60cb4aa9730edc9ce7  utlxe-starter-1.2.1.zip
aa34b6d4f7b5633a20948f2450e8ecd6cc73599be0cc746e362270d507bb5fd6  utlxe-professional-1.2.1.zip
f195fc75825708f166478e5f144dd713aab06f7907f9a73e7f3e1de47aa7c76e  utlxe-starter-1.4.0.zip
9f3791f1afd4118f07beadc0ccfebc82ce7ea07ab62bb055bb022a9f7a080c59  utlxe-professional-1.4.0.zip
5b8522b72384d7f2e91604613687125eda2063887ea57024c8048ee4f5d970f4  utlxe-starter-1.4.1.zip
a8bb00865df6ce7693101d3e30260c8615b7239e8d8c671817d0b1dfd40ea2a7  utlxe-professional-1.4.1.zip
1bc9c769eab3c07c1fed0c8f8573b565a28c8da96aab2c097426c6180a1c2dce  utlxe-starter-1.4.2.zip
23dc175915e14a90ee57690d4dc6f7f3c64276702268a3161e975e22273976bf  utlxe-professional-1.4.2.zip
8d97202384f891755498af5a48af17237579e2ca819e8a7fda606d598ecc179f  utlxe-starter-1.4.3.zip
cbc4884d805dd442ef652e1d359a6da6bb8bee1c5efd02973cc6d09f8f9cdf94  utlxe-professional-1.4.3.zip
```

Verify with `shasum -a 256 zips/<file>`.

The 11 May upload date is inferred from the Partner Center publish timestamp and the push time
of `utlxe:1.1.0`, not read back from Partner Center — by the time this was written the live
package could no longer be downloaded. That is the gap this table exists to prevent repeating.

### The templates could not deploy at all before 1.4.0

Found while tracing the Service Bus path, and it outranks everything else here: the
`container-app` nested deployment runs with `expressionEvaluationOptions.scope = "inner"`
and referenced `parameters('adminKey')`, which was never declared in that nested template
nor passed to it. Under inner scope a nested template sees only its own parameters, so that
reference resolves to nothing and ARM rejects the deployment with `InvalidTemplate` before
creating a single resource.

It is present in the 1.1.0 and 1.2.1 packages as well — so no generation of this offer has
ever been deployable, independently of the image architecture problem below. Both failures
had to be fixed before a customer could get a running container.

Fixed in 1.4.0 by declaring `adminKey` as a `secureString` on the nested template and
passing it through from the parent.

**This is a static analysis, not an observed failure** — it was found by comparing parameter
references against declarations, not by deploying. Confirm it the cheap way before any
upload:

```bash
az deployment group validate \
  --resource-group <a throwaway rg> \
  --template-file starter/mainTemplate.json \
  --parameters adminKey=<any-8-chars>
```

### Neither earlier package can be rolled back to

Rolling back is not an option, and was never one:

| package | engine arch | UI arch | runs on Azure |
|---|---|---|---|
| 1.1.0 generation | arm64 | arm64 | no |
| 1.2.1 generation | amd64 | **arm64** | no |
| 1.4.0 | amd64 | amd64 | **yes** |

The arm64 images are **deliberately kept** on ghcr.io rather than deleted — `utlxe:1.1.0`,
`utlxe:1.2.0`, `utlxe-ui:1.0.0`. Nothing in this repository references them and no one is
expected to pull them; they are retained as a record of what was built and when. Do not
propose removing them again without a reason beyond tidiness.

Azure Container Apps runs linux/amd64 only. The 1.1.0 images were built by hand on an Apple
Silicon machine before any CI existed, and the 28 May rebuild fixed the engine but left the UI
sidecar on the arm64 `utlxe-ui:1.0.0` — and one unrunnable container fails the whole revision.
**1.4.0 is the first package whose images can actually start on Azure.** This is why the
publish workflow pins `platforms: linux/amd64` rather than relying on whoever runs the build.

## Why `build-zips.sh` exists

The 1.1.0 → 1.2.1 bump was made by hand and landed in `zips/` but not in the plan
directories. By 1.4.0 the directories had drifted to the point where `professional/`
described a Professional plan with the **Starter** worker count — 8 instead of 32 — and
nothing caught it, because nothing rebuilt from the directories. A package built from that
state would have billed as Professional and run at a quarter of the advertised concurrency.

The 1.4.0 directories were regenerated from the shipped 1.2.1 zips, and the script now fails
the build on each of those failure modes rather than producing a wrong package.

## Known inconsistency — wizard defaults vs template defaults

`mainTemplate.json` parameter defaults are a fallback. On the Marketplace path the
`createUiDefinition.json` outputs always supply a value, so the **wizard dropdown default is
what a customer actually gets**, and the two disagree:

| plan | wizard default | mainTemplate default |
|---|---|---|
| starter | 1.0 core, 2 GB | 2.0 cores, 4 GB |
| professional | 2.0 cores, 4 GB | 4.0 cores, 8 GB |

Worker counts agree (8 and 32). This has been shipped behaviour since 1.2.1 and is recorded
here rather than changed, because it determines what customers are billed for and what the
offer listing promises. `books/engine-azure` ch02 documents a third combination again —
Starter *(1 vCPU, 4 GB)*, Professional *(2 vCPU, 8 GB)* — taking vCPU from the wizard and
memory from the template.
