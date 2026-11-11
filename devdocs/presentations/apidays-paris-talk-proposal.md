# API Days Paris — Talk Proposal

*Submitted by Ir. Marcel A. Grauwen, Glomidco B.V. · proposal text as submitted*

## Title

**No Pass-Through: Rebuilding Every API Message at the Trust Boundary**

*(67 characters)*

## Abstract

API gateways route traffic and WAFs block known-bad patterns, but what happens when an approved payload carries something nobody anticipated? Cross-domain guards solve this with a stricter rule: never forward a message as received. Parse it, inspect it, and rebuild it clean.

This talk shows how UTL-X, an open-source, format-agnostic transformation language, becomes such a guard. Every incoming message, whether JSON, XML, CSV, YAML or OData, is parsed into one Universal Data Model, and its schema can be checked against an approved contract (XSD, JSON Schema and more). UTL-X 1.1's pure, deterministic validation layer then inspects that tree: field allow-lists, value constraints and cross-field checks. What passes is re-serialized into a strict canonical form, in the same format or a different one. Comments, duplicate keys, smuggled bytes and unexpected fields simply do not survive. What fails is rejected with an audit record.

We'll walk through a concrete example: a partner's JSON order enters, only approved fields are kept, and canonical XML leaves for the internal system. You'll see why this "parse, inspect, rebuild" pattern matches NIST and NCSC cross-domain guidance, and how to apply it to your own APIs.

*(193 words)*

## Notes to self

- **Promises made in the abstract:**
  - UTL-X 1.1 (`validate.*`) finished before the talk;
  - a demo of partner JSON order → approved fields only → canonical XML;
  - the mapping to NIST SP 800-53 AC-4 and the NCSC cross-domain guidance.
- **Deliberately left out:** AIS and other binary formats (BINF not yet realised), EDIFACT (planned), military and hardware topics.
- **Possible alternative titles considered:**
  - Never Forward, Always Rebuild: Zero-Trust Payloads for APIs
  - Beyond the WAF: Allow-List Content Guards for Cross-Domain APIs
