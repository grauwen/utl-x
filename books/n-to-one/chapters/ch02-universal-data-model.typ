= The Universal Data Model

A theory of mapping needs a common ground for the things it maps. If a "mapping" had to be defined separately for XML-to-JSON, CSV-to-XML, YAML-to-OData, and every other pairing, there would be no theory — only a catalogue of special cases. The Universal Data Model (UDM) is that common ground. This chapter develops it as a formal object, because the rest of the book reasons over UDM and nothing else.

== The Combinatorial Argument for a Common Model

Begin with the problem UDM solves. Suppose a system must transform between $F$ formats directly, format to format. Each ordered pair needs its own conversion logic, with its own handling of that pair's quirks — attributes and namespaces for XML, headers and type inference for CSV, anchors and tags for YAML. The number of such paths grows as $F times (F - 1)$: quadratic in the number of formats. Every new format multiplies the work.

Interpose a single neutral model and the quadratic collapses to linear. Each format needs one *parser* into the model and one *serializer* out of it — $2F$ pieces of logic, never $F^2$. More importantly for us, the transformation itself is written once, against the model, and is *format-blind*: it cannot tell, and need not care, whether a value arrived as a JSON number or an XML text node.

#figure(
  rect(width: 100%, inset: 14pt, stroke: 0.5pt + luma(160), radius: 4pt)[
    #align(center)[
      #text(size: 10pt)[XML · JSON · CSV · YAML · OData]
      #v(3pt)
      #text(size: 14pt)[$arrow.b$ parse $#h(2.2cm)$ serialize $arrow.t$]
      #v(3pt)
      #text(size: 12pt, weight: "bold")[Universal Data Model]
      #v(2pt)
      #text(size: 9pt, fill: luma(110))[the transformation operates here, and only here]
    ]
  ],
  caption: [One model in the middle turns $F^2$ format-to-format paths into $2F$ parse/serialize pieces. The transformation never touches a raw format.],
)

This is not a UTLX invention; it is the *canonical data model* pattern from enterprise integration, and before that the pivot representation of any multi-format compiler. What matters for the theory is the consequence: a mapping is a function from UDM to UDM. Formats live only at the edges.

== UDM as a Value Algebra

Formally, UDM is a recursively defined set of values. A UDM value is one of:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Constructor*], [*Holds*],
  [Scalar], [a single atomic value: string, number, boolean, or null],
  [Object], [a finite map from names to UDM values, with optional attributes and metadata],
  [Array], [an ordered sequence of UDM values],
  [DateTime, Date, LocalDateTime, Time], [first-class temporal values],
  [Binary], [a raw byte sequence],
)

The first three constructors carry the entire structural story; the temporal and binary types are conveniences that spare every transformation from re-parsing dates and re-decoding bytes. There is also an internal Lambda value for function arguments to higher-order operations, but it never appears in data.

Two design choices in the Object constructor repay attention, because they are where the messy realities of formats are quarantined rather than leaked.

#strong[Attributes are separate from properties.] An XML element's attributes are kept in their own map, distinct from its child elements. This preserves XML's element/attribute distinction without forcing it on formats that have no such notion — JSON simply never populates the attribute map. A transformation can treat attributes and properties uniformly when it wants to, or distinguish them when it must.

#strong[Metadata is a reserved, format-fidelity channel.] Each Object may carry a small metadata map holding things a faithful round-trip needs but the data itself does not — an XML element's namespace context, or the detected XSD design pattern. This channel is dropped by the ordinary JSON, XML, and YAML serializers (the data is what survives) but *preserved* by the dedicated UDM-language serializer, so a UDM value can be written out and read back without losing format-specific fidelity. The lesson generalises: a clean data model needs a side-channel for the inconvenient truths of real formats, kept strictly apart from the data.

== Shape and Data: One Model, Two Readings

Here the theory makes a distinction that the rest of the book leans on constantly. The same UDM tree can be read in two ways.

Read for its *values*, a UDM tree is an *instance* — concrete data, the parsed content of one document. Read for its *shape* — the names, the nesting, the types, the cardinalities, with the particular values abstracted away — it describes a *schema*.

This is why UDM can represent both the data flowing through a transformation and the schemas a mapping is reasoned about. A node is the same kind of node in both readings; what differs is whether you care about the value at a leaf or only its type and obligations (required, optional, repeating). In the UTLX tooling this appears as a single node type for both: the schema-field node is literally an extension of the data-field node, adding only schema-specific annotations such as "required" and "constraint." Shape is data with the values forgotten and the obligations remembered.

Keeping this distinction explicit pays off immediately. A *schema graph* — the object Part II reasons over — is the shape reading of UDM, enriched with keys and foreign keys. A *data UDM* — the object a transformation actually produces — is the value reading. They share a coordinate system (the same paths address both), which is what lets a tool align an instance onto its schema without either holding a pointer to the other. We will use that alignment in Chapter 3 and again when we visualise mappings.

It helps to fix one term before it sprawls. The shape reading of a UDM tree is its *schema*, and this book meets it at three granularities — the same object seen at increasing scope:

#block(fill: luma(245), inset: 10pt, radius: 4pt, width: 100%)[
  - A *schema-field node* — a data node plus its obligations (`required`, a constraint, a cardinality). The building block.
  - A *schema graph* — those nodes joined by key and foreign-key edges into a navigable whole. The object the matchers reason over; built in full in Chapter 4.
  - A *schema document* — the entire schema as one envelope, which also records facts about the schema itself, such as the input's role and type. Developed in Chapter 8.
]

Node, graph, document: not three things but one, addressed at the level of a field, of the connected structure, and of the whole. Where this book says "schema" without qualification, the granularity is whichever the context needs.

== Schema Is Metadata

The shape reading has a more familiar name: *metadata* — data about data. A schema is exactly that: a formal description of a *class* of data instances — their names, types, cardinalities, and constraints — with no particular instance's values. To say UDM has a shape reading and a data reading is to say it can be read as metadata or as data: the schema graph of Part II is metadata; the instance a transformation produces is data; and they share one node model because metadata is, in the end, data with the values abstracted and the obligations retained.

This invites a ladder. Data is described by metadata — a schema. Metadata is in turn described by *meta-metadata* — a schema of schemas, a registry. The `meta-schema` input type of Chapter 8 and the schema-document envelope that records a schema's own type both sit one level up: they are data about the metadata.

One caution, because "metadata" is as overloaded as "transformation." The sense above — a schema is metadata about a *class* of data — is distinct from the small format-fidelity *metadata channel* a UDM object carries (a namespace, a detected XSD pattern), which is metadata about *one value's representation*, a narrower thing; and from the enterprise `metadata` data class of Chapter 8 (catalogues, lineage), which is data about an organisation's data assets. All three are honestly "data about data," but at different scopes — class, value, and asset. When this book calls a schema metadata, it means the first.

== Why the Model Must Carry Its Own Meaning

A subtle but load-bearing principle governs UDM's design: *the model should carry everything a downstream consumer needs, so that no consumer has to reach back to the original format.*

When a schema is parsed, the format-specific facts that matter — an element is a key, this reference is a foreign key, that group repeats — must be lifted into the UDM representation as first-class annotations. If they are left behind in the XSD or JSON-Schema text, then every later stage (matching, strategy selection, code generation) must re-open the source format and re-derive them, and the model has failed at its one job. The matchers and strategies in Part II are therefore defined over UDM schema graphs, never over raw XSD or JSON-Schema syntax trees. There must be one schema graph, not four format-specific ones.

This principle also tells us where *not* to put things. Schema-level facts — what kind of input this is, how it is classified — belong to the schema reading, attached to the schema object as a whole, never smeared across the data nodes of an instance. The data model represents values; it does not represent a value's role in some larger pipeline. We return to this boundary in Chapter 8, where inputs are typed: the type is a property of the *schema document*, not of any datum inside it.

== What UDM Buys the Theory

With UDM in place, the central objects of the book can be stated cleanly:

- An *input* is a UDM value (its data reading) described by a UDM schema graph (its shape reading).
- The *output contract* is a UDM schema graph — pure shape, fixed in advance.
- A *transformation* is a function from a tuple of input UDM values to one output UDM value.
- A *mapping* — the thing we analyse — is a relationship between the input schema graphs and the output schema graph, independent of any particular data.

That last line is the quiet payoff. Because UDM separates shape from data, a mapping can be reasoned about entirely in the shape reading — before any instance exists. Whether to do so, and what becomes possible when we do, is the subject of the next chapter.
