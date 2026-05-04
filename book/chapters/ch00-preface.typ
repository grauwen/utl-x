#heading(numbering: none)[Preface]

#show heading: set heading(numbering: none)

== Why This Book

Integration mapping hasn't meaningfully advanced in a decade. This book exists because that needs to change.

For twenty-five years, the industry answer to "how do I transform data between systems?" has been XSLT. It works — for XML. The moment your source or target is JSON, CSV, or YAML, you are on your own. You reach for jq, or a Python script, or a Node.js function, and you start maintaining a second tool for what is conceptually the same job.

Then DataWeave arrived and did something genuinely right: it abstracted away the format. Write your transformation logic once, and it worked on XML, JSON, or CSV without rewriting anything. The catch was that DataWeave was owned by MuleSoft, which is owned by Salesforce. You could not use it outside the Anypoint Platform without paying for it. There was no fork, no escape hatch, no community that could take it somewhere the vendor chose not to go.

So most teams fell back to the status quo: XSLT for XML, jq for JSON, custom scripts for everything else. A growing pile of tools, each doing the same thing differently, each with its own syntax, its own edge cases, its own maintenance burden. A Dutch wholesale distributor receiving purchase orders from twelve European partners — XML over SOAP from one, JSON to a REST API from another, CSV on SFTP from a third, YAML webhooks from a fourth — needs not one transformation layer but four. When SAP shows up with OData, it becomes five.

This is the quiet tax every integration project pays. It compounds. It is paid in onboarding time when a new developer has to learn which tool handles which format. It is paid in bugs that appear at format boundaries. It is paid every time a business requirement changes and the same logic has to be updated in four different scripts instead of one.

UTL-X was built to eliminate that tax. One transformation language, all formats, open source.

This book teaches you UTL-X from the ground up: the language, the runtime, the IDE tooling, the production engine, and the Azure deployment model. It is not a reference manual — the reference documentation is online. It is a book about how to think in UTL-X: how the Universal Data Model works, why format-agnostic transformation changes what is possible, and how to build real integration pipelines that are maintainable, testable, and scale.

By the end, you will understand not just how to write UTL-X transformations, but why the approach works — and why it was worth building in the first place.

== Who This Book Is For

This book is written for developers and architects who work with data integration, ETL pipelines, API transformation, or enterprise messaging — and who are tired of solving the same problem in five different languages.

You do not need prior experience with UTL-X. You do need to be comfortable reading code. The examples use XML, JSON, CSV, and YAML as input and output formats; a working knowledge of at least two of these will help you follow along without friction.

*This book is for you if:*

You are a *backend developer* who has written custom parsing and transformation code more times than you can count, and you sense there should be a better abstraction than a new script for every new format.

You are an *integration architect* who has evaluated MuleSoft, Boomi, or Talend, found the licensing costs prohibitive for what you actually need, and wants a genuinely open alternative that does not lock you into a platform.

You are a *data engineer* building ETL pipelines who currently uses a mix of Python, SQL, and format-specific tools, and wants a single declarative layer that handles the transformation step across all formats.

You are a *DevOps or platform engineer* deploying transformation workloads on Azure or Kubernetes who needs a lightweight, containerised tool with Prometheus metrics, health probes, and Kafka integration out of the box.

You are a *technical lead* evaluating UTL-X for your team and want to understand the full stack — language, runtime, IDE support, and production deployment — before committing.

*This book is probably not for you* if you are building something that does not involve transforming data between formats. UTL-X is a specialised tool; it does one thing and does it well. If your problem is something else — workflow orchestration, data storage, API gateway routing — other tools will serve you better.

== How to Read This Book

This book is organised in eight parts. You do not need to read it linearly — but knowing the structure will help you find what you need.

*Part I: Foundation* — Start here. Covers the core concepts: the UTL-X header, the Universal Data Model, basic expressions, and your first transformations. Everyone reads this.

*Part II: Language Mastery* — Read sequentially. Deepens your understanding of the language: functions, operators, control flow, error handling, data restructuring, private functions, and advanced patterns. By the end you will be fluent.

*Part III: Formats Deep Dive* — Pick what you need. Each chapter covers one format family in depth: XML, JSON, CSV, YAML, OData, schema formats. Read the chapters relevant to your integration landscape.

*Part IV: Real-World Applications* — When you are ready for production. Enterprise integration patterns, the UTLXe engine lifecycle, cloud deployment on Azure, SDKs, migration guides, performance tuning, logging, and quality assurance.

*Part V: Future Outlook* — Where UTL-X is heading. Semantic validation, API contracts, formats not yet covered, the competitive landscape, AI integration, and architectural decisions.

*Part VI: Case Studies* — Real-world walkthroughs. End-to-end examples showing UTL-X solving actual integration problems.

*Part VII: Reference* — Grammar specification, appendices, and cross-format patterns.

*Part VIII: Standard Library Encyclopedia* — All 652 functions documented individually with examples. Use as a lookup reference.

== A Note on Versions

This book covers UTL-X v1.1.0. The CLI tool (`utlx`) and its 650+ standard library functions are stable. The production engine (`utlxe`) and IDE daemon (`utlxd`) are in active development; chapters covering those components reflect their state at time of writing and may evolve. The language itself — the transformation syntax, the UDM, the type system — is stable and the core focus of this book.

All examples in this book can be run with the free, open source UTL-X binaries available at:

#align(center)[`https://github.com/grauwen/utl-x/releases`]
