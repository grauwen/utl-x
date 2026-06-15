#heading(numbering: none)[Introduction: What Is Mapping?]

#show heading: set heading(numbering: none)

Before a theory of N:1 mapping, a plainer question deserves an answer: what is *mapping* in the first place? The word is used loosely across the industry — for data conversion, for field assignment, for integration glue of every kind. This book needs it sharp. This introduction supplies the definition, the reasons mapping is harder than it looks, and a short account of how the discipline arrived where it is. Readers fluent in data transformation can skim it; everyone else should start here, because every later chapter assumes this vocabulary.

== Mapping, Defined

A *mapping* relates two data structures: a *source* and a *target*. It is the specification of how each part of the target is obtained from the source — which target field copies which source field, which is computed, which is looked up, which is left to a default. A *transformation* is the executable realisation of a mapping: a function that, given a concrete source, produces a concrete target satisfying the mapping.

Three words carry weight in that definition. *Source* and *target* name the two ends; in this book the target is privileged — it is a fixed contract — and the source is, in general, several inputs at once. *Specification* signals that a mapping is a description, not yet a program: it says *what* must hold between source and target, and a transformation is one way to make it hold. Keeping the mapping (the *what*) distinct from the transformation (the *how*) is the move that lets a mapping be analysed before it is executed — the whole premise of Message Contract mode in Chapter 3.

The smallest mapping relates one source to one target: the *1:1* case. It is where the field's theory was developed, and it is the right place to build intuition. This book is about the larger case — many sources, one target, the *N:1* mapping of Chapter 1 — but everything in it rests on the 1:1 foundation, which Chapter 12 documents in full.

== A Word on "Transformation"

The phrase "data transformation" means different things to different communities, and it is worth saying plainly which one this book is about. To a data scientist, transforming data means *feature engineering* — scaling, normalising, reshaping a table so a model can learn from it. To a business leader, "data transformation" may mean an organisational programme: new governance, new platforms, a change in how a company treats data as an asset. Neither is the subject here.

This book is about *structural and semantic data mapping*: taking data in one representation — a format, a schema, a contract — and producing it faithfully in another. It is the transformation of integration and interchange: an order becomes an invoice, an XML message becomes a JSON document, several inputs converge into one output that satisfies a fixed contract. The values are not being analysed or modelled; they are being *moved and reshaped* from one shape to another, correctly. When this book says "transformation," it means that — and only that.

== Why Mapping Is Hard

If mapping were merely copying fields with matching names, there would be no theory and no book. It is hard because source and target disagree along three independent axes, and a real mapping must reconcile all three at once.

*Format heterogeneity.* The source may be XML and the target JSON; one has elements, attributes, and namespaces, the other objects, arrays, and a fixed set of scalar types. The same information wears different syntactic clothes. This is the most visible difficulty and, as it turns out, the most superficial — a common data model dissolves it, which is why Chapter 2 begins there.

*Structural heterogeneity.* Even within one format, source and target organise the same information differently: a flat list on one side, a nested header-and-detail tree on the other; a repeating group here, a joined reference table there. Reconciling structure is not syntactic translation; it is reshaping, and it is where the interesting mapping shapes — joins, groupings, flattenings — live.

*Semantic heterogeneity.* Hardest of all, the two sides may mean the same thing by different names, or different things by the same name. `customerId` and `clientRef`; two unrelated fields both called `id`. No amount of format normalisation or structural analysis closes a semantic gap; it requires knowledge of what the data *means*. This is the axis where deterministic methods reach their limit and human or machine judgement must enter — a boundary this book draws as sharply as it can.

A mapping is the act of reconciling all three. Much of the craft, and all of the theory, is about handling each with the right tool: a common model for format, a schema graph for structure, and a careful, explicit treatment of semantics that never pretends to more certainty than it has.

== A Short History

Mapping has two lineages — one in practice, one in research — that ran in parallel for decades and only recently began to converge.

The *practitioner* lineage is a story of format-specific tools. XSLT, standardised in 1999, gave the XML world a powerful, declarative transformation language, and for a generation "data mapping" and "XSLT" were nearly synonyms — as long as the data was XML. As JSON rose, jq filled the same niche for it; YAML, CSV, and the rest each acquired their own utilities. Visual mappers — IBM's BizTalk Mapper, Altova MapForce, the graphical tools inside every ETL suite — made mapping approachable by drawing lines between source and target trees, with "functoids" in the middle for the computations, an idiom this book's tooling deliberately echoes. DataWeave, arriving with MuleSoft around the middle of the 2010s, did something genuinely new for the mainstream: it abstracted the format away, so one transformation worked across XML, JSON, and CSV alike. Its limitation was ownership, not idea: it lived inside a single vendor's platform.

The *research* lineage ran quietly alongside, in the database community, and it is the one this book draws on. Schema matching — automatically finding the correspondences between two schemas — was surveyed definitively by Rahm and Bernstein in 2001. Schema mapping — turning those correspondences into an executable relationship — was pioneered by the Clio project at IBM through the 2000s, which established that joins and nestings are derived from keys and foreign keys, not guessed from names. Data exchange gave the whole enterprise a formal object: a mapping as a set of source-to-target dependencies. And the provenance and data-classification communities supplied the vocabulary for *how* an output value is produced and *what kind* of data each input is. Chapter 12 is an annotated tour of this canon.

The two lineages converge here. The practitioner's visual mapper and the researcher's schema graph are the same object drawn differently; the functoid and the derived field are the same idea named differently. What has been missing is a treatment that takes the research seriously and lands it on a working, open, format-agnostic language — which is precisely the gap this book sets out to fill.

== How to Think About Mapping

If there is one habit of mind to carry into the rest of the book, it is this: *a mapping is an object to be reasoned about, not merely a script to be written.* The practitioner tradition, for all its tools, has largely treated mapping as authorship — you sit down and write the assignments. The research tradition treats it as something with structure, completeness, and provenance — something you can analyse, score, and check. This book sides firmly with the second view, while keeping the first view's hard-won practicality. The reward is the ability to say, precisely, what a mapping *does* and whether it is *right* — before a single byte of real data has flowed.

With that, the subject proper can begin. Chapter 1 states the N:1 problem; Chapter 2 builds the common model that makes "all formats" mean something; Chapter 3 draws the line between reasoning about schemas and transforming data. Everything after is consequence.
