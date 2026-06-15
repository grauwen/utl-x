#heading(numbering: none)[Preface]

#show heading: set heading(numbering: none)

== Why This Book

There is a companion to this volume, _UTL-X: One Language, All Formats_, that teaches you how to write transformations. This book is about something quieter and, in the long run, more important: how to *reason* about them.

The two books answer different questions. The first asks, "How do I transform this XML into that JSON?" This one asks, "What *is* a mapping — as an object I can analyse, score, and check for correctness — when several inputs must converge into one fixed output contract?"

That convergence — many inputs, one output — is the shape almost every real integration eventually takes. A purchase-order message arrives. It is not enough on its own: the line items carry product codes that must be expanded from a catalogue; the totals are not present in the source and must be computed; a handful of environment values — a tenant region, a correlation id — must be stamped in; and the whole thing must satisfy an invoice schema that someone else owns and will not change for your convenience. That is an *N:1 mapping*, and writing it by hand is a craft. Reasoning about it — knowing it is complete, knowing which fields are derived and which are copied, knowing where a human must still decide — is a theory.

The good news is that the theory already exists, scattered across four decades of database research: schema matching, schema mapping, data exchange, data provenance. None of it was written with UTL-X in mind. All of it applies. This book gathers that material, translates it out of its native dialect, and lands it on UTL-X's two concrete foundations — the *Universal Data Model* and the distinction between *Execution mode* and *Message Contract mode*.

== What This Book Is, and Is Not

This is a *theory* book. It contains very little UTL-X syntax. Where the companion volume shows you `concat`, `map`, and `groupBy`, this book shows you *why* a given output field demands an aggregation, *why* a second input is a lookup rather than a co-equal source, and *why* the mapping you wrote is — or is not — sound.

It is not a reference manual, and it is not a tutorial. It is the book you read when you want to understand the machinery behind Message Contract mode: the schema graph, the correspondence set, the strategy pipeline, and the line — drawn as sharply as we can draw it — between what a deterministic algorithm can decide and what genuinely needs a human or an LLM.

== Who Should Read It

Read this if you are an *integration architect* who has built a hundred mappings by feel and wants the vocabulary to say precisely what they do; a *platform engineer* designing tooling that must analyse mappings rather than merely run them; or a *researcher or student* who wants to see classical schema-mapping theory applied to a working, open transformation language rather than a paper prototype.

You do not need a database-theory background — every borrowed idea is introduced from first principles — but you should be comfortable with the notion of a schema, a tree, and a function.

== How to Read It

The book is in three parts.

*Part I — Foundations* builds the ground the rest stands on: the N:1 problem itself, the Universal Data Model as a formal object, and the two-modes distinction (Execution versus Message Contract). Read it first; everything else assumes it.

*Part II — The Theory of Mapping* is the heart of the book. It treats schemas as graphs, mappings as sets of source-to-target dependencies, and the analysis of a mapping as a scored correspondence set. This is where the classical literature enters.

*Part III — From Theory to Transformation* turns the theory into a working pipeline: typing the inputs, analysing the outputs, inferring functions, and generating idiomatic UTL-X strategy-first.

A note on cross-references: throughout, design decisions are tagged with their origin in the UTL-X feature record (IF11 coverage, IF20 strategy, IF21 the visual mapper). You do not need those documents to read the book; the tags simply mark where theory met implementation.
