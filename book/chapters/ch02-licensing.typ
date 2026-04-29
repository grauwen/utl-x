= Licensing: Open Source and Commercial

== UTL-X Is Open Source (AGPL-3.0)

UTL-X is licensed under the *GNU Affero General Public License v3.0* (AGPL-3.0). This is one of the strongest open-source licenses, designed to keep software free and open — even when deployed as a cloud service.

== What AGPL-3.0 Means in Practice

=== You CAN (without paying anything):
// - Use UTL-X for any purpose — personal, educational, commercial
// - Run UTL-X internally within your organization
// - Modify the source code
// - Distribute UTL-X (as long as you include the source code)
// - Deploy UTL-X as a service — IF you share your modifications

=== The Key AGPL Condition:
// If you MODIFY UTL-X and make it available over a network
// (as a web service, API, cloud service, SaaS),
// you MUST make your modified source code available to users.
//
// This is the "network use" clause (Section 13) — the key difference
// between AGPL and regular GPL:
//   GPL: only triggers when you DISTRIBUTE the software
//   AGPL: also triggers when you provide access over a NETWORK

=== You CANNOT (without a commercial license):
// - Embed UTL-X in a proprietary product and sell it without sharing source
// - Offer UTL-X as part of a closed-source SaaS platform
// - Remove the AGPL license notices
// - Sublicense under a different license

== Usage Scenarios and License Requirements

// | Scenario | License needed | Cost | Why |
// |----------|---------------|------|-----|
// | **Personal use** | AGPL (free) | Free | Any use is permitted |
// | **Learning / education** | AGPL (free) | Free | Any use is permitted |
// | **Internal company use** (no modification) | AGPL (free) | Free | No distribution, no network clause triggered |
// | **Internal company use** (with modifications) | AGPL (free) | Free | Internal use is not "distribution" — modifications stay private |
// | **Open-source project** (using UTL-X) | AGPL (free) | Free | Your project must also be AGPL-compatible (copyleft) |
// | **Consulting** (using UTL-X for clients) | AGPL (free) | Free | Transformations (.utlx files) are DATA, not software — AGPL doesn't apply to your .utlx files |
// | **Azure Marketplace deployment** | Commercial | $35-105/month | Marketplace billing through Azure — commercial license included |
// | **Embedding in proprietary SaaS** | Commercial | Contact sales | AGPL requires source sharing; commercial license exempts this |
// | **OEM / white-label** | Commercial | Contact sales | Distributing UTL-X as part of your product |
// | **Embedding in proprietary desktop app** | Commercial | Contact sales | Distribution of modified AGPL code in closed-source |

== Important Clarification: Your .utlx Files Are YOURS

// The AGPL covers the UTL-X SOFTWARE (parser, interpreter, engine, stdlib).
// It does NOT cover the DATA you process or the TRANSFORMATIONS you write.
//
// Your .utlx transformation files are YOUR intellectual property.
// They are input to the program, not part of the program.
// You can keep them proprietary, sell them, or do anything you want with them.
//
// Analogy:
//   AGPL covers the UTL-X compiler (like gcc is GPL).
//   Your .utlx files are like your C source code compiled with gcc.
//   gcc's GPL does not infect your C code. UTL-X's AGPL does not infect your .utlx files.
//
// This means:
//   ✅ Your transformation logic is private
//   ✅ Your schemas are private
//   ✅ Your pipeline configurations are private
//   ✅ Your test data is private
//   Only the UTL-X engine itself is AGPL

== The Commercial License Model

=== Why a Commercial License Exists
// - AGPL is designed to keep open-source software open
// - Some organizations cannot use AGPL (legal/compliance restrictions)
// - Some use cases require embedding UTL-X without source sharing
// - The commercial license provides an alternative for these cases

=== Commercial License Options

==== Azure Marketplace (Managed Application)
// - Simplest path: deploy from Azure Marketplace
// - Pricing: Starter ($35/month), Professional ($105/month)
// - Billing: through Azure — appears on customer's Azure invoice
// - License: commercial license included in the Marketplace offering
// - No AGPL obligations — Microsoft handles the licensing

==== GCP / AWS Marketplace (Coming)
// - Same model: deploy from Marketplace, commercial license included
// - GCP: Cloud Run, $44/month
// - AWS: ECS/Fargate, $44/month

==== Direct Commercial License
// - For: companies embedding UTL-X in proprietary products
// - For: OEM / white-label distribution
// - For: organizations with AGPL-incompatible policies
// - Pricing: per-deployment or per-organization, negotiated
// - Contact: licensing@utlx-lang.org

=== Dual Licensing Summary
//
// ┌─────────────────────────────────────────────┐
// │              UTL-X Source Code               │
// │                                              │
// │  License A: AGPL-3.0 (free, open source)    │
// │    → Use, modify, deploy freely              │
// │    → Share modifications if network service  │
// │    → Your .utlx files are NOT affected       │
// │                                              │
// │  License B: Commercial (paid)                │
// │    → No source-sharing obligation            │
// │    → Embed in proprietary products           │
// │    → Azure/GCP/AWS Marketplace includes this │
// │    → Direct license for OEM/white-label      │
// └─────────────────────────────────────────────┘
//
// You choose which license applies to your use case.
// Most users use AGPL (free). Marketplace users get commercial automatically.

== Frequently Asked Questions

=== "Can I use UTL-X in my company for free?"
// Yes. Internal use is always free under AGPL. You can run UTL-X on your
// servers, write any transformations, process any data. No payment needed.
// No source-sharing needed (internal use is not distribution).

=== "Do I need to open-source my .utlx files?"
// No. Your transformation files are data/input, not part of the UTL-X
// software. AGPL does not apply to them. They are your intellectual property.

=== "Can I use UTL-X in my SaaS product?"
// If you DON'T modify UTL-X: yes, under AGPL — but you must make clear
// to users that UTL-X (AGPL) is used, and provide access to its source.
// If you DO modify UTL-X: you must share your modifications under AGPL.
// If you want NO obligations: buy a commercial license.

=== "What if I deploy UTL-X from Azure Marketplace?"
// Commercial license is included in the Marketplace offering.
// No AGPL obligations apply. You pay $35-105/month through Azure billing.

=== "Can I redistribute UTL-X?"
// Under AGPL: yes, but you must include the source code and AGPL license.
// Under commercial license: as agreed in the license terms.

=== "Does AGPL affect my entire application?"
// Only if you LINK or EMBED UTL-X in your application code.
// If you call UTL-X via HTTP API, subprocess (stdio-proto), or Dapr sidecar,
// there is no linking — your application's license is not affected.
// The Go and C# wrappers use subprocess communication, not linking.
