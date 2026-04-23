# UTL-X Azure — Commercial Repository Strategy

**Version 1.0 — April 2026**

---

## 1. Separation Principle: Open Core + Commercial Wrapper

The UTL-X engine and proto contract stay open source. The Azure-specific integration, packaging, and deployment tooling lives in a separate private repository under commercial license.

### Value Stack

| Layer | Repository | License | Rationale |
|-------|-----------|---------|-----------|
| UTL-X engine (Kotlin/JVM) | `grauwen/utl-x` (public) | AGPL-3.0 | Drives adoption, community, credibility |
| Proto contract (`utlxe.proto`) | `grauwen/utl-x` (public) | AGPL-3.0 | It's the API contract — must be public for any wrapper |
| VS Code extension for UTL-X | `grauwen/utl-x` (public) | AGPL-3.0 | Drives adoption into Azure dev workflow |
| C# client library (UtlxClient) | Private/commercial | Commercial | Packaged, tested, supported .NET integration |
| Azure Function template + Bicep | Private/commercial | Commercial | This is the product |
| Logic Apps built-in connector | Private/commercial | Commercial | This is the premium product |
| SaaS API + Marketplace listing | Private/commercial | Commercial | This is the revenue engine |

The thin C# wrapper (~300 lines) has minimal IP value — anyone could write it from the public proto file. The real commercial value is the **packaged, tested, supported Azure integration**: the Function App template, the connector NuGet, the Marketplace offer, the deployment automation, and the SLA.

---

## 2. Repository Structure

### Public (existing)
```
github.com/grauwen/utl-x              (AGPL-3.0)
├── modules/engine/                     UTLXe engine (all transport modes)
├── proto/utlxe/v1/utlxe.proto         Proto contract (source of truth)
├── ...                                 Everything else (CLI, daemon, formats, stdlib)
```

### Private (new)
```
github.com/glomidco/utlx-azure         (Commercial license)
├── src/
│   ├── UtlxClient/                     C# wrapper library (spawns UTLXe, stdio-proto)
│   ├── UtlxClient.AzureFunction/       Azure Function package (HTTP → UTLXe)
│   └── UtlxClient.LogicApps/           Logic Apps built-in connector (IServiceOperationsProvider)
├── tests/
│   └── UtlxClient.Tests/               Integration tests (spawn real UTLXe)
├── samples/
│   └── AzureFunctionSample/            Working Azure Function example
├── deploy/
│   ├── bicep/                          ARM/Bicep one-click deployment templates
│   └── docker/                         Dockerfile for containerized deployment
├── marketplace/
│   ├── saas-fulfillment/               SaaS Fulfillment API implementation
│   ├── landing-page/                   Marketplace landing page (Entra SSO)
│   └── metering/                       Usage metering → Marketplace billing
├── .github/workflows/                  CI/CD (build, test, pack, publish)
├── LICENSE                             Commercial license
└── README.md
```

### How the private repo references the public one

- **Proto file**: Copy `proto/utlxe/v1/utlxe.proto` into the private repo (snapshot at each release). Alternatively, use a git submodule pointing to `grauwen/utl-x` — but a versioned copy is simpler and avoids submodule friction.
- **UTLXe JAR**: Download from the public repo's GitHub Releases as a build dependency. The CI workflow pulls the matching version.
- **No source dependency**: The private repo never compiles Kotlin. It only consumes the JAR artifact and proto file.

---

## 3. Why This Model Works Commercially

**AGPL on the engine** means anyone using UTLXe as a network service must open-source their modifications. But calling UTLXe via the proto pipe is normal use, not a modification — customers don't trigger AGPL obligations by using the C# wrapper.

**The commercial trigger is convenience.** Customers can technically:
1. Read the public proto file
2. Write their own C# wrapper
3. Build their own Azure Function
4. Package their own Logic Apps connector
5. Handle their own deployment, monitoring, and support

Or they can buy `utlx-azure` and get all of that tested, packaged, documented, and supported. Enterprises pay for the latter every time.

**This is the proven open-core model** used by Redis (open engine, commercial Redis Cloud), MongoDB (open server, commercial Atlas), Elastic (open engine, commercial Cloud/Enterprise), and others.

---

## 4. GitHub Organization Issue

### Problem

The `glomidco` GitHub organization is tied to old administrative accounts. Access may need to be recovered.

### Options

| Option | Effort | Result |
|--------|--------|--------|
| **A. Recover glomidco org** | Set up email forwarding for the old admin addresses → reset GitHub credentials → regain org admin access | `github.com/glomidco/utlx-azure` — proper corporate branding |
| **B. Create new org** | Create `glomidco-dev` or `utlx-commercial` org with your current GitHub account | Immediate access, but different org name |
| **C. Use personal account** | Create `grauwen/utlx-azure` as private repo | Fastest, but mixes personal and commercial |
| **D. Transfer later** | Start under personal account → transfer to glomidco org once recovered | No blocker now, clean up later |

### Recommended: Option D (start now, transfer later)

1. Create `grauwen/utlx-azure` as a private repo now — don't let the org issue block progress
2. In parallel, recover the `glomidco` GitHub org:
   - Set up email forwarding on your mail server for the old admin addresses
   - Use GitHub's "Forgot password" flow with the forwarded email
   - Once in, add your current GitHub account as org owner
   - Transfer the repo from `grauwen/utlx-azure` to `glomidco/utlx-azure`
3. GitHub preserves all redirects after transfer — no broken links

The repo transfer is a 30-second operation once you have org admin access. Don't wait for it.

---

## 5. Licensing for the Commercial Repo

The private repo should have a simple commercial license. Suggested terms:

```
UTL-X Azure Integration — Commercial License

Copyright (c) 2026 Glomidco B.V. All rights reserved.

This software is licensed, not sold. Use requires a valid commercial
license from Glomidco B.V. See https://utlx.io/licensing for terms.

Unauthorized copying, distribution, or use is prohibited.
```

The NuGet package, Azure Function package, and Logic Apps connector are all distributed under this license. Customers get access via:
- Marketplace purchase (SaaS offer — Microsoft handles billing)
- Direct license agreement (enterprise customers like Workato)
- Trial/evaluation license (time-limited key)

---

## 6. Revenue Streams

| Stream | Route | When |
|--------|-------|------|
| Azure Function package (BYOL) | Route 4 | Phase 1 (weeks) |
| SaaS API subscriptions | Route 2 | Phase 2 (months 1-3) |
| Metered transformation overage | Route 2 | Phase 2 |
| Power Platform connector usage | Route 3 | Phase 3 (months 3-6) |
| Logic Apps built-in connector license | Route 1 | Phase 4 (months 6-12) |
| Platform license (Workato, etc.) | Direct | Ongoing |

All revenue flows through the commercial repo's artifacts. The open-source repo generates zero direct revenue — its job is adoption and credibility.

---

*Strategy document for Glomidco. April 2026.*
