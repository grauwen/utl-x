= Licensing: Open Source and Commercial

Before diving into the technical details of UTL-X, it's important to understand what you can and cannot do with it. Licensing matters — especially in enterprise environments where legal and compliance teams review every dependency.

The short version: UTL-X is free and open source for almost every use case. The only exception is embedding it inside a proprietary product you distribute — and even then, a commercial license is available.

== UTL-X Is Open Source (AGPL-3.0)

UTL-X is licensed under the *GNU Affero General Public License v3.0* (AGPL-3.0). This is one of the strongest open-source licenses, designed to keep software free and open — even when deployed as a cloud service.

If you've worked with GPL-licensed software before, AGPL is similar with one important addition: the _network use_ clause. Under regular GPL, sharing obligations only trigger when you _distribute_ the software. Under AGPL, they also trigger when you make the software available over a _network_ — for example, as a SaaS offering.

This matters for UTL-X because transformation engines are typically deployed as services, not distributed as installable software.

== What You Can Do (Without Paying Anything)

Under the AGPL-3.0 license, you are free to:

- *Use UTL-X for any purpose* — personal, educational, or commercial
- *Run UTL-X internally* within your organization, on any number of servers
- *Modify the source code* to fit your needs
- *Deploy UTL-X as a service* within your organization
- *Write any number of `.utlx` transformations* — they are yours (more on this below)

There is no usage limit, no trial period, no feature restriction. The full UTL-X engine with all 652 standard library functions, all 11 format parsers, and all execution strategies is available under AGPL-3.0.

== The Key AGPL Condition

If you *modify* UTL-X and make your modified version available to users over a network (as a web service, API, or cloud service), you must make your modified source code available to those users under the same AGPL-3.0 license.

This is the _quid pro quo_ of AGPL: you benefit from the open-source community's work, and in return, your improvements flow back to the community.

Note the emphasis on _modified_. If you use UTL-X as-is (without changing the engine source code), there is nothing to share — the original source is already public on GitHub.

== Your Transformations Are Yours

This is the most important clarification in this chapter:

*The AGPL license covers the UTL-X software — the parser, interpreter, engine, and standard library. It does NOT cover the transformations you write or the data you process.*

Your `.utlx` files are _input_ to the program, not part of the program. They are your intellectual property. You can keep them proprietary, sell them, or do anything you want with them.

The analogy is precise: GCC (the GNU C compiler) is licensed under GPL. But the C programs you compile with GCC are _yours_ — GCC's license does not "infect" your code. Similarly, UTL-X's AGPL license does not affect your `.utlx` transformation files.

This means:

- Your transformation logic is private
- Your schemas are private
- Your pipeline configurations are private
- Your test data is private
- Only the UTL-X engine itself is AGPL

== Usage Scenarios

The following table covers every common scenario:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Scenario*], [*License*], [*Cost*], [*Why*],
  [Personal use / learning], [AGPL (free)], [Free], [Any use is permitted],
  [Internal company use (unmodified)], [AGPL (free)], [Free], [No distribution, no network clause],
  [Internal company use (modified)], [AGPL (free)], [Free], [Internal use is not distribution],
  [Open-source project using UTL-X], [AGPL (free)], [Free], [Your project must be AGPL-compatible],
  [Consulting (writing .utlx for clients)], [AGPL (free)], [Free], [.utlx files are data, not software],
  [Azure Marketplace deployment], [Commercial], [\$35--105/mo], [Commercial license included],
  [GCP / AWS Marketplace], [Commercial], [\$44--131/mo], [Commercial license included],
  [Embedding in proprietary SaaS], [Commercial], [Contact], [AGPL requires source sharing],
  [OEM / white-label distribution], [Commercial], [Contact], [Distributing modified AGPL code],
)

The vast majority of users fall into the first five rows — all free, no obligations.

== The Subprocess Exception

A technical but important detail: if your application communicates with UTL-X via *HTTP API*, *subprocess* (stdio-proto), or *Dapr sidecar*, there is no _linking_ between your code and UTL-X. Your application's license is not affected by AGPL.

The C\# and Go wrappers use subprocess communication (protobuf over stdin/stdout). They spawn UTL-X as a separate process. This is not linking — it's inter-process communication. Your C\#, Go, or Python application remains under whatever license you choose.

This is the same principle that allows proprietary applications to call GPL-licensed command-line tools (like `grep` or `git`) without becoming GPL themselves.

== The Commercial License

=== Why It Exists

Some organizations have policies that prohibit AGPL dependencies — regardless of how they're used. Others want to embed UTL-X inside a proprietary product without source-sharing obligations. The commercial license serves these cases.

=== Cloud Marketplace (Simplest Path)

When you deploy UTL-X from the Azure Marketplace (or GCP/AWS when available), a commercial license is included in the subscription. No AGPL obligations apply. The licensing is handled by Microsoft/Google/Amazon through the Marketplace billing.

- *Azure*: Starter \$35/month, Professional \$105/month
- *GCP*: Starter \$44/month, Professional \$131/month
- *AWS*: Starter \$44/month, Professional \$131/month

=== Direct Commercial License

For companies embedding UTL-X in proprietary products, OEM distribution, or organizations with AGPL-incompatible compliance policies, a direct commercial license is available. Pricing is per-deployment or per-organization, negotiated based on use case.

Contact: licensing\@utlx-lang.org

== Dual Licensing: How It Works

UTL-X uses a _dual licensing_ model — the same source code is available under two licenses. You choose which one applies to your use:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*License A: AGPL-3.0 (free)*], [*License B: Commercial (paid)*],
  [Use, modify, deploy freely], [No source-sharing obligation],
  [Share modifications if network service], [Embed in proprietary products],
  [Your .utlx files are NOT affected], [Azure/GCP/AWS Marketplace includes this],
  [Community-driven improvements], [Direct support available],
)

Most users never need the commercial license. The AGPL is free, permissive for internal use, and doesn't touch your transformation files. The commercial license exists as a safety net for the minority of use cases where AGPL is a blocker.

== Frequently Asked Questions

*"Can I use UTL-X in my company for free?"*

Yes. Internal use is always free under AGPL. You can run UTL-X on your servers, write any transformations, process any data. No payment needed. No source-sharing needed — internal use is not distribution.

*"Do I need to open-source my .utlx files?"*

No. Your transformation files are data, not part of the UTL-X software. AGPL does not apply to them. They are your intellectual property.

*"Can I use UTL-X in my SaaS product?"*

If you use UTL-X _unmodified_: yes, under AGPL — but you must make clear to users that UTL-X (AGPL) is used, and provide access to its source code. If you _modify_ UTL-X: you must share your modifications under AGPL. If you want no obligations: buy a commercial license.

*"What if I deploy from Azure Marketplace?"*

Commercial license is included. No AGPL obligations. You pay \$35--105/month through your Azure bill.

*"Does AGPL affect my entire application?"*

Only if you _link_ or _embed_ UTL-X directly in your application code (e.g., importing UTL-X as a Java library). If you communicate via HTTP, subprocess, or Dapr — your application is not affected. The Go and C\# wrappers use subprocess communication, specifically to avoid this.

With licensing clarified, let's move on to what matters most: using UTL-X to transform data.
