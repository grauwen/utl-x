= Schemas as Graphs

The strategy of N:1 mapping rests on one move, and this chapter is about making it: stop reading a schema as text and start reading it as a graph. A schema written in XSD or JSON Schema is a document with names, and names are the most seductive — and least reliable — signal a mapper can use. The real information is in the topology: which nodes are entities, which fields identify them, which references reach from one input into another. That topology is a graph, and the whole of Part II reasons over it.

== Why Names Are the Wrong Substrate

A field called `customerId` in one schema and `clientRef` in another almost certainly mean the same thing; two fields both called `id`, in different entities, almost certainly do not. Name similarity is noisy in exactly the places that matter most: foreign-key joins, flattened repeating groups, and fields that share a generic name across unrelated entities. A mapper that works on names as surface syntax produces its worst errors precisely at the junctions where an integration's value lives.

The constraint-based signal is different in kind. A primary key, a foreign key, a cardinality, a containment — these are *structural facts*, declared in the schema and not subject to the vagaries of naming convention, abbreviation, or language. Following the schema-matching literature, the theory elevates the constraint-based signal above the linguistic one. Names become a tie-breaker among structurally plausible candidates, never a way to promote a structurally impossible one. To do that, the structure has to be a first-class object. That object is the schema graph.

== One Graph, Built on the Universal Data Model

There is an immediate temptation to build a graph per format — an XSD graph, a JSON-Schema graph, an EDMX graph — each with its own notion of "key" and "reference." That way lies four diverging implementations and four sets of bugs. The model of Chapter 2 forbids it. Schema parsing lifts every format into the *shape* reading of UDM, and the schema graph is built on that single representation. `xs:key`, JSON-Schema `$ref` chains, and EDMX navigation properties are all read once, into one vocabulary of nodes and edges. The matchers of the next chapters are defined over UDM schema graphs, never over raw format syntax trees.

This is the principle from Chapter 2 — the model must carry its own meaning — applied to structure. Whatever a format declares about identity and reference is lifted into the graph at parse time as first-class annotations, so that no later stage must re-open the original XSD to recover them.

== The Node Typology

Every node in a schema graph carries a structural role, derived from the graph and its constraints rather than from the node's name:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Node role*], [*Meaning*],
  [entity / collection], [a structure, possibly repeating; cardinality distinguishes array from object],
  [key], [an identifying field of an entity],
  [reference (foreign key)], [a field whose value identifies a node in another entity — a join edge],
  [containment], [a parent–child nesting relationship],
  [value], [a leaf scalar],
)

This typology is what replaces the older, weaker vocabulary of "driver," "lookup," and "enrichment" inputs. Those roles are not primitive; they fall out of the graph. A lookup table is simply an entity that the primary data *references by foreign key*. The driver is the entity that the output's top-level collection iterates. Roles are derived from the schema graph plus its foreign-key edges — deterministic, and far stronger than any name heuristic.

== Keys and Foreign Keys as Edges

Two kinds of edge carry most of the signal.

A *containment* edge is the parent–child relationship of nesting: an order contains its line items. Containment is cardinal — one-to-one for a singleton child, one-to-many for a repeating group — and the cardinality is itself a structural fact, read from `maxOccurs`, from a JSON array, or from a collection-typed navigation property.

A *reference* edge is a foreign key: a field whose *value* is the identity of a node elsewhere. A line item's `productCode` is not a description; it is a pointer into the product catalogue. The reference edge is the formal counterpart of the intuition that "the second input is a lookup." Where a reference edge crosses from one input's graph into another's, it is exactly a join; where it stays inside one graph, it ties an entity to its parent.

== Cardinality and Weak Entities

Cardinality turns the graph from a static picture into a predictor of mapping shape. The pattern that recurs in almost every business message is *header–detail*: a parent entity and a repeating child collection bound to it. An order and its lines; an invoice and its line items; a shipment and its parcels.

In entity-relationship terms, the child of a header–detail pair is a *weak entity* under an identifying relationship: an order line has no independent existence: it is identified only in the context of its order. This is not a curiosity of terminology. The header–detail shape is exactly what predicts a `map` over the collection, and a `groupBy` when the detail must be rolled up. Reading cardinality off the graph is reading the strategy off the graph.

== Intra-Document Structure: The IDOC

It is easy to assume that foreign keys only matter *between* inputs — the payload referencing a lookup. They matter just as much *within* one input, and missing this is missing most of the granularity.

Consider an SAP IDOC carried as XML. It contains a header segment with an order identifier and a set of line-item segments, each with a line identifier and a back-reference to its header. Orders and order-lines that point to each other are a node-level foreign-key relationship *inside the payload graph* — a header–detail pattern, not a new kind of input. It is exactly this relationship that predicts the mapping shape: a parent with a referenced child collection becomes a `map`, and a roll-up over that collection becomes a `groupBy`.

This is the level at which the granular "kind of message" lives. The input as a whole has a *role* — it is the payload. Its internal richness — entities, keys, the header–detail edges between them — is the *graph*, and the graph is where strategy is decided. A theory that types only whole inputs and never looks inside them cannot tell an order from an order-with-lines, and so cannot tell a copy from a `map`.

== Reachability: Clio's Observation

The graph earns its keep through a single, powerful idea, due to the Clio line of work on schema mapping: *a mapping is graph reachability under integrity constraints.* Candidate correspondences between a source and a target are not guessed from names; they are *derived* from the paths the schema graph permits. A foreign-key-reachable path from a source node to a target node corresponds directly to a join, or a lookup, in the generated transformation. Joins and nestings are consequences of the keys and foreign keys in the graph, not independently authored decisions.

This reframes the mapper's job. It is not inventing a mapping and hoping it is consistent with the schemas; it is *reading off* the mappings the schema topology already supports, and scoring them. The schema graph is not a hint to a matching algorithm — it is the space the algorithm searches. The next chapter is about how that search is scored.
