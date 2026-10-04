= Message Contracts and the Mapping Workflow

A mapping does not exist in isolation; it exists between two *contracts* — what the sender produces and
what the receiver accepts. This chapter is about making those contracts explicit, and about the
workflow of authoring, validating, and proving a mapping against them.

== What a message contract is

A message contract is the agreed shape of a message at a boundary: its format, its schema (which
fields, of which types), its value constraints, and its labelling rules. Integration goes wrong when
contracts are *implicit* — living in a sender's head and a receiver's code — and it goes right when
they are *explicit artefacts* a mapping can be written and tested against.

The Mapper treats both ends as first-class:

#table(
  columns: (auto, 1fr),
  [*Input contract*], [the format and schema the Mapper reads — a form-class for binary (Chapter 6), or an XML/JSON schema for text],
  [*Output contract*], [the format, schema, value ranges, code lists, and labelling the receiver will accept — enforced by `validate.*` before emit (Chapter 7)],
)

== Schema-driven mapping

When both contracts are declared as schemas, authoring a mapping becomes a guided, checkable task
rather than guesswork. The Mapper's authoring environment works from the two schemas directly:

#table(
  columns: (auto, 1fr),
  [*Field discovery*], [both contracts present as navigable trees — the input UDM shape and the output's required shape side by side],
  [*Coverage*], [every required output field is tracked; an unmapped required field is flagged before the mapping ships, not after a message is rejected],
  [*Type awareness*], [the mapping knows a field is a scaled number, an enum, or a date, and the matching `validate.*` check is suggested],
  [*Label mapping*], [the `^` metadata channel is mapped explicitly, so a classification or releasability caveat is never dropped or silently invented],
)

== Instance and schema together

A contract has two faces: the *schema* (the rule) and an *instance* (an example message). The Mapper's
workflow uses both — the schema to check coverage and types, a real instance to see the mapping run
end to end. A binary input instance decodes through its form-class to a UDM tree; the mapping produces
an output instance; the output is validated against the output contract. The author sees, on one
screen, the input, the result, and the verdict.

A subtlety worth stating, because it is easy to get wrong: a *schema* format and its *instance* format
are not the same thing. A JSON-schema contract describes an instance that is JSON — or YAML, which
shares the model. The authoring environment keeps that mapping explicit (a JSON schema admits JSON and
YAML instances; an XML schema admits XML) so the example a mapping is tested against is always a
*valid instance of the declared contract*, never a schema document mistaken for data.

== The authoring workflow

Authoring a Mapper mapping follows one loop:

#table(
  columns: (auto, 1fr),
  [*1. Declare contracts*], [name input and output formats; attach the form-class / schema for each],
  [*2. Map*], [write the body — navigate the input tree, build the output tree; coverage tracks the required fields],
  [*3. Validate*], [add the `validate.*` gate for the output contract — required fields, ranges, code lists, labels],
  [*4. Prove*], [run real instances through the mapping; confirm valid messages pass and malformed ones land in dead-letter with a `ValidationResult`],
  [*5. Ship*], [the mapping is a versioned artefact; a standards revision re-enters the loop at step 1 as a definition/schema edit],
)

Because a mapping is pure and deterministic (Chapter 5), step 4 is *exhaustive and repeatable*: a
corpus of instances — valid, boundary, and deliberately malformed — forms a regression suite that
proves the mapping's behaviour and re-proves it after every change. That test corpus is itself an
accreditation asset.

== One rule, every format pair

The payoff of the canonical model returns here. A validation rule — "latitude in [−90, 90]", "release
caveat in the permitted set" — is written *once* against the UDM and applies no matter which format the
data arrived in or which it is leaving as. The contract library is not per-format-pair; it is a set of
reusable checks over one model, composed per output contract. That is what keeps a growing matrix of
format pairs maintainable instead of combinatorial.

With the workflow established, Chapter 9 grounds all of it in the actual standards — walking the
open formats the Mapper bridges, and what each demands of a form-class and a contract.
