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

## Package archive

Partner Center **retains only the latest package**: once a new one is published, previously
published packages are no longer accessible there. So `zips/` is the only archive, and every
package that has been uploaded is committed to this repository.

Record the SHA-256 and the upload date here whenever a package is submitted, so it stays
possible to say exactly which bytes Microsoft received.

| package | sha256 | engine | UI | uploaded |
|---|---|---|---|---|
| `utlxe-starter.zip` | `2c17540a8056849e…` | `utlxe:1.1.0` | `utlxe-ui:1.0.0` | 2026-05-11 (assumed) |
| `utlxe-professional.zip` | `b2d61adfd5685931…` | `utlxe:1.1.0` | `utlxe-ui:1.0.0` | 2026-05-11 (assumed) |
| `utlxe-starter-1.2.1.zip` | `7dd1acfc2c5b413b…` | `utlxe:1.2.1` | `utlxe-ui:1.0.0` | unknown |
| `utlxe-professional-1.2.1.zip` | `aa34b6d4f7b5633a…` | `utlxe:1.2.1` | `utlxe-ui:1.0.0` | unknown |
| `utlxe-starter-1.4.0.zip` | `f195fc75825708f1…` | `utlxe:1.4.0` | `utlxe-ui:1.4.0` | *pending* |
| `utlxe-professional-1.4.0.zip` | `9f3791f1afd4118f…` | `utlxe:1.4.0` | `utlxe-ui:1.4.0` | *pending* |

Full digests:

```
2c17540a8056849e97abe5871c1cbb6ed9f7d19cdaf548c22f3c7feb73dfb455  utlxe-starter.zip
b2d61adfd5685931af021dc6203123ad5b6c8ebc7d236cab80f2b8410a7fa0d5  utlxe-professional.zip
7dd1acfc2c5b413b120b466bc1a27e28196c274fe6b99a60cb4aa9730edc9ce7  utlxe-starter-1.2.1.zip
aa34b6d4f7b5633a20948f2450e8ecd6cc73599be0cc746e362270d507bb5fd6  utlxe-professional-1.2.1.zip
f195fc75825708f166478e5f144dd713aab06f7907f9a73e7f3e1de47aa7c76e  utlxe-starter-1.4.0.zip
9f3791f1afd4118f07beadc0ccfebc82ce7ea07ab62bb055bb022a9f7a080c59  utlxe-professional-1.4.0.zip
```

Verify with `shasum -a 256 zips/<file>`.

The 11 May upload date is inferred from the Partner Center publish timestamp and the push time
of `utlxe:1.1.0`, not read back from Partner Center — by the time this was written the live
package could no longer be downloaded. That is the gap this table exists to prevent repeating.

### Neither earlier package can be rolled back to

Rolling back is not an option, and was never one:

| package | engine arch | UI arch | runs on Azure |
|---|---|---|---|
| 1.1.0 generation | arm64 | arm64 | no |
| 1.2.1 generation | amd64 | **arm64** | no |
| 1.4.0 | amd64 | amd64 | **yes** |

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
