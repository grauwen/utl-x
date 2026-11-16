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
build-zips.sh     builds zips/ from the plan directories
mainTemplate.json, createUiDefinition.json   <- SUPERSEDED, see below
```

The pair at the top level predates the two-plan split: it has no `adminKey` parameter and no
`utlxe-ui` sidecar, and it is not what any shipped package contains. Nothing builds from it.
It is kept only because the `azure-bicep/` templates still reference the same shape.

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
