#heading(numbering: none)[Appendix A · The Classical Theory of 1:1 Mapping]

#show heading: set heading(numbering: none)

This book has been about *many* inputs becoming one output. The literature it draws on was, almost without exception, written about *one* source and one target. That is not a defect to apologise for; it is the foundation to build on. The classical theory of 1:1 mapping — how a single source schema relates to a single target schema — is mature, peer-reviewed, and, as Chapter 6 showed, generalises to N:1 by the simple act of letting the source be a union. This back-matter study narrates that foundation and annotates its canon, so that the references scattered through the preceding chapters stand together as a reading list.

Each entry below gives a citation, a short abstract, and a one-line _Brings_ — the single thing the work contributes to this book's argument.

== The 1:1 Assumption, and Its Quiet Generalisation

Read the schema-matching and schema-mapping literature and you will find an implicit picture: a source schema on the left, a target schema on the right, correspondences drawn between them. The matchers assume a source/target *pair*. The mapping generators discover a query from one schema to another. The picture is so consistent that the 1:1-ness of it goes unstated.

Yet, as Chapter 6 observed, the data-exchange formalism never required it. A schema mapping $M = (S, T, Sigma)$ places no constraint that $S$ be a single schema; in the relational setting $S$ is already a set of relations, and the dependencies in $Sigma$ range over all of them. The N:1 problem of this book is therefore not an extension of the theory but a reading of it that the diagrams happened to suppress. Everything below was developed for the 1:1 case and applies to N:1 once the source is understood as the union of the inputs — with the one caveat, also from Chapter 6, that name–value inputs are constants, not schemas, and sit outside the matching machinery entirely.

== Schema Matching

The matching problem is: given two schemas, find the correspondences between their elements. It is the input to mapping, and its literature gives us the taxonomy, the signals, and the discipline of combining them.

#emph[Rahm, E. & Bernstein, P. A. (2001).] "A Survey of Approaches to Automatic Schema Matching." _VLDB Journal_ 10(4). — The definitive taxonomy. Classifies every matcher along orthogonal axes: schema-only versus instance-based, element-level versus structure-level, linguistic versus constraint-based, individual versus combining. \
#emph[Brings —] the taxonomy that becomes the book's architectural skeleton: the two-modes split, graph-before-names, and keys-over-names are its axes.

#emph[Madhavan, J., Bernstein, P. A. & Rahm, E. (2001).] "Generic Schema Matching with Cupid." _VLDB_. — A structure-aware matcher that computes element similarity and propagates it up and down the schema tree, so agreement among children reinforces their parent and vice versa. \
#emph[Brings —] similarity propagation through structure — the case for matching with structural context rather than isolated names.

#emph[Do, H. H. & Rahm, E. (2002).] "COMA — A System for Flexible Combination of Schema Matching Approaches." _VLDB_. — A composite matcher: a library of individual matchers whose scores are combined under configurable strategies and weights. \
#emph[Brings —] composite, weighted multi-signal scoring — the direct model for the sub-scored correspondence set of Chapter 7.

#emph[Melnik, S., Garcia-Molina, H. & Rahm, E. (2002).] "Similarity Flooding: A Versatile Graph Matching Algorithm and its Application to Schema Matching." _ICDE_. — Casts matching as a fixpoint computation: similarity flows along the edges of a combined graph until it settles. \
#emph[Brings —] graph-propagation matching — the algorithmic view that maps onto UDM schema-graph alignment.

== Schema Mapping and Data Exchange

Matching finds correspondences; *mapping* turns them into an executable relationship, and *data exchange* makes that relationship a formal object. This is the theory that lets us say what a mapping *is*.

#emph[Miller, R. J., Haas, L. M. & Hernández, M. A. (2000).] "Schema Mapping as Query Discovery." _VLDB_. — The foundation of the Clio line. Reframes mapping as discovering a query from source to target, driven by value correspondences and schema structure rather than authored by hand. \
#emph[Brings —] mapping-as-discovery: a mapping is derived from structure, not hand-written.

#emph[Popa, L., Velegrakis, Y., Miller, R. J., Hernández, M. A. & Fagin, R. (2002).] "Translating Web Data." _VLDB_. — Clio's mapping generation using the schemas' referential constraints to produce nested mappings, inventing target values via Skolem functions where the source supplies none. \
#emph[Brings —] joins and nesting from keys (not names), and value invention by Skolem function — the formal root of the lookup join and the derivation gap.

#emph[Haas, L. M., Hernández, M. A., Ho, H., Popa, L. & Roth, M. (2005).] "Clio Grows Up: From Research Prototype to Industrial Tool." _SIGMOD_. — The account of Clio's maturation into a usable tool. \
#emph[Brings —] evidence that schema-mapping theory is engineering-ready, not a paper exercise.

#emph[Fagin, R., Haas, L. M., Hernández, M., Miller, R. J., Popa, L. & Velegrakis, Y. (2009).] "Clio: Schema Mapping Creation and Data Exchange." In _Conceptual Modeling: Foundations and Applications_, Springer. — A retrospective tying mapping generation to data-exchange semantics. \
#emph[Brings —] the consolidated bridge between "how mappings are generated" and "what a mapping formally is."

#emph[Fagin, R., Kolaitis, P. G., Miller, R. J. & Popa, L. (2005).] "Data Exchange: Semantics and Query Answering." _Theoretical Computer Science_ 336(1) (originally ICDT 2003). — Defines a schema mapping formally as a set of source-to-target tuple-generating dependencies, and develops universal solutions, the chase, and certain answers. \
#emph[Brings —] the formal object itself: tgds, $M=(S,T,Sigma)$, and the boundary between structural reshaping and computation.

#emph[Bellahsene, Z., Bonifati, A. & Rahm, E. (eds.) (2011).] "Schema Matching and Mapping." Springer, _Data-Centric Systems and Applications_. — A single volume surveying the whole field above. \
#emph[Brings —] the one-stop survey: the recommended entry point for an implementer.

== Data Classification and Modeling

Mapping theory says how schemas relate; data-management theory says what *kinds* of data there are. This is the grounding for typing the inputs (Chapter 8) and naming the structural archetypes.

#emph[Chen, P. P. (1976).] "The Entity-Relationship Model — Toward a Unified View of Data." _ACM TODS_ 1(1). — The origin of entities, relationships, and cardinality, and of the *weak entity* under an identifying relationship. \
#emph[Brings —] the vocabulary for header–detail: an order line is a weak entity, identified only within its order.

#emph[Kimball, R. & Ross, M.] "The Data Warehouse Toolkit." Wiley. — Dimensional modelling: the fact-versus-dimension distinction and the star and snowflake schemas. \
#emph[Brings —] the structural-archetype axis: a lookup is a dimension, a payload-with-lines is a fact.

#emph[Kimball, R. & Caserta, J.] "The Data Warehouse ETL Toolkit." Wiley. — Classifies target columns into direct, derived-computed, surrogate-key, lookup-decode, and default. \
#emph[Brings —] a target-field-derivation taxonomy in all but name — the practitioner root of the output analysis of Chapter 9.

#emph[DAMA International.] "DAMA-DMBOK: Data Management Body of Knowledge." Technics Publications. — The enterprise taxonomy of master, transactional, reference, metadata, and configuration data. \
#emph[Brings —] near-exact external validation of the input role taxonomy of Chapter 8.

#emph[Loshin, D. (2008).] "Master Data Management." Morgan Kaufmann. — The standard treatment of master versus transactional versus reference data. \
#emph[Brings —] the grounding for `lookup`-as-master/reference and `payload`-as-transactional.

#emph[Dreibelbis, A., et al. (2008).] "Enterprise Master Data Management: An SOA Approach to Managing Core Information." IBM Press. — An architectural view of the same classification in an integration context. \
#emph[Brings —] the integration-architecture reading of the data-class taxonomy.

== Integration and Messaging

The N:1 shape is, before it is a theory, an integration reality. This is the literature of messages and the patterns that move them.

#emph[Hohpe, G. & Woolf, B. (2003).] "Enterprise Integration Patterns." Addison-Wesley. — The pattern language of messaging, including the *message types* (Document, Command, Event) and the *Canonical Data Model*. \
#emph[Brings —] the integration-level statement of "one model in the middle" that UDM realises, plus a message-kind classification.

#emph[UN/EDIFACT; ANSI ASC X12; SAP IDoc.] — The dominant electronic-data-interchange and enterprise-messaging structures, with their control/header/detail/status segment hierarchies. \
#emph[Brings —] real, standardised header–detail messages: the IDOC intra-document foreign-key example, and proof the predicted structure is ubiquitous.

== Data Provenance and Lineage

The output analysis of Chapter 9 asks "how is this value produced?" That is the provenance question, and it has its own deep literature.

#emph[Green, T. J., Karvounarakis, G. & Tannen, V. (2007).] "Provenance Semirings." _PODS_. — How-provenance: an algebra (commutative semirings) describing *how* an output value is computed from inputs, not merely whether it depends on them. \
#emph[Brings —] the formal algebra of copy versus derive versus aggregate.

#emph[Cui, Y., Widom, J. & Wiener, J. L. (2000).] "Tracing the Lineage of View Data in a Warehousing Environment." _ACM TODS_ 25(2). — Lineage tracing: identifying the source tuples responsible for a derived result. \
#emph[Brings —] the warehousing root of target-field provenance — which sources produced a value.

#emph[Cui, Y. & Widom, J. (2003).] "Lineage Tracing for General Data Warehouse Transformations." _VLDB Journal_ 12(1). — Classifies transformations into dispatchers, aggregators, and black boxes by their output-to-input relationship. \
#emph[Brings —] a transformation-kind classification the target-field taxonomy mirrors.

#emph[Buneman, P., Khanna, S. & Tan, W. C. (2001).] "Why and Where: A Characterization of Data Provenance." _ICDT_. — Distinguishes *why*-provenance (which inputs justified an output) from *where*-provenance (which input a value was copied from). \
#emph[Brings —] the conceptual scaffolding for asking, of each output field, where it came from.

== Program Synthesis

Once a field is typed as calculated, the remaining task is to synthesise its expression. This is the literature of generating programs from examples.

#emph[Gulwani, S. (2011).] "Automating String Processing in Spreadsheets Using Input-Output Examples." _POPL_. — The FlashFill work: synthesising a string transformation from a few input–output pairs. \
#emph[Brings —] programming-by-example — the natural successor to a `derivation-gap`, where analysis names the need and synthesis supplies the formula.

== Reading Order

For a newcomer to the theory, a path through the canon: begin with Rahm and Bernstein (2001) for the map of the territory; read Fagin et al. (2005) for the formal object; read the Clio trilogy (Miller et al. 2000; Popa et al. 2002; Fagin et al. 2009) for how mappings are generated from structure; and consult Bellahsene, Bonifati and Rahm (2011) when a single reference is wanted. The data-management works (Chen; Kimball; DAMA-DMBOK) supply the typing vocabulary; the provenance works (Green et al.; Cui and Widom) supply the output vocabulary. The rest of this book is, in a sense, an extended argument that these 1:1 foundations, taken together and read without the 1:1 assumption, are exactly what an N:1 mapping needs.
