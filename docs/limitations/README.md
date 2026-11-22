# Limitations — permanent boundaries, written down once

A limitation is not a bug and not a feature. It is a boundary that **changing requires changing a
decision, not writing code** — and that someone will otherwise rediscover, re-report and
re-investigate every few months.

## The admission test

One sentence, and it is strict on purpose:

> Changing this requires changing a decision, not writing code.

That admits a representation that cannot hold a distinction, or a design choice whose cost is
permanent. It **excludes** anything merely unbuilt: a `Status: Design`, `Proposed` or
`Future Enhancement` entry is backlog, and backlog already has a home in
[`../features`](../features). A directory that admits backlog becomes a second backlog and stops
being read.

## Numbering

The **prefix keeps its meaning from [`../bugs-fixed/README.md`](../bugs-fixed/README.md): it names
the component**, not the kind. `B` is the shared language stack, `EB` the engine, and so on. The
*directory* is what says "limitation".

The number line is **shared with `bugs-fixed/`** — deliberately. One sequence per component means
a reader who greps `B65` finds it without first having to know which kind of record it is.

## What a limitation record must carry

Four things, and the last two are what distinguish it from a bug report:

1. **What you observe** — the smallest reproduction, with real output.
2. **The mechanism** — *why* the boundary is where it is, in terms of the representation or the
   decision that puts it there. Not "this is not supported".
3. **The cost to change** — what it would actually take. This is the sentence that stops the
   limitation being re-litigated.
4. **A test that pins it.** A bug test proves a fix; a limitation test proves the boundary **has
   not silently moved**. Without one the record decays into a claim, and a claim about behaviour
   is worth less than nothing once the behaviour changes.

## Records

| record | component | boundary |
|---|---|---|
| [B65](B65-numeric-and-lexical-fidelity-on-a-round-trip.md) | `:core`, `formats/*` | a number's written form is not always recoverable from its value |
