# Audit: parameters that are accepted and then ignored

**Written:** 2026-11-14, during the compiler-warning cleanup that followed the 1.4.0 release.
**Status:** inventory only — none of these is verified as a user-visible defect except where
a bug report is named.

Clearing 256 compiler warnings turned up a recurring shape: a function takes an argument,
the caller passes a real value, and the body never reads it. Unlike an unused local, this is
not noise — it is either a redundant parameter (harmless, remove it) or behaviour that was
planned and never wired (a defect, and removing the parameter would hide it).

The cleanup pass removed only the ones that were provably redundant. Everything below was
left in place **on purpose**, so the parameter remains as the marker. Each needs someone to
decide which of the two it is.

## Verified and reported

| Site | Report |
|---|---|
| `PrettyprintFunctions.kt` — `indent`, `flowStyle`, `preserveWhitespace`, `indentStr` (6) | [B60](../../docs/bugs-fixed/B60-prettyprint-xml-and-yaml-ignore-their-indent-argument.md) |
| `FunctionsCommand.kt:250` — `functions` | [CB05](../../docs/bugs-fixed/CB05-functions-list-ignores-the-category-filter-in-text-output.md) |

## Acknowledged stubs — leave alone

These carry an explicit `TODO` or print "Implementation in progress". The unused parameter
is the honest signature of work not yet done.

- `LintCommand.kt:435` `autoFix(source, fixableWarnings)` — `// TODO: Implement auto-fixing logic`
- `LintCommand.kt:463` `DeadCodeAnalyzer.analyze(program)` — `// TODO: Implement dead code detection`
- `StatusCommand.kt:10`, `StopCommand.kt:10` — `execute(args)`, both print "Implementation in progress..."

## Uniform signatures — leave alone

`GraphvizASTVisualizer` has a `visitX(node, options)` family; six members do not consult
`options` (and `visitNullLiteral` uses neither parameter) because their output is constant.
The uniform shape is what makes the dispatch readable, so these are deliberate.

## Unverified — someone should decide

Schema and format paths, where an ignored parameter plausibly means dropped schema fidelity:

- `AvroSchemaSerializer.kt:250, 399` — `defaultNamespace` ignored by `transformField` and
  `transformArray`. Does a namespaced Avro schema round-trip?
- `TableSchemaParser.kt:267` — `fieldToUSDL(field, primaryKey)` ignores `primaryKey`. Is the
  primary key preserved into USDL?
- `EDMXSerializer.kt:304` — `serializeEntityContainer(sb, container, namespace)` ignores
  `namespace`.
- `JSONSchemaParser.kt:258` — `enhanceProperty(key, value)` ignores `key`.

Engine, daemon and interpreter:

- `CopyStrategy.kt:309` — `returnSkeleton(skeleton)` ignores its skeleton, which reads oddly
  for a function with that name.
- `StdioProtoTransport.kt:155` — `dispatch(envelope, registry)` ignores `registry`.
- `PathCompleter.kt:282` — `rankCompletion(name, propType)` ignores `propType`, so completion
  ranking cannot be type-aware.
- `DaemonConfig.kt:28` — `configPath` ignored; a caller passing a config path may expect it
  to be read.
- `StartCommand.kt:394, 395` — `config` and `options` ignored.
- `interpreter.kt:1341` — `payload` ignored in the `every` quantified-validation helper
  (F24). Worth checking against the rule semantics.

Dead public API (zero callers anywhere in the repo, so the unused parameter is moot):

- `InterpreterErrorEnhancer.kt:257, 276` — `enhanceTypeMismatch(...)` and
  `enhanceAssignmentOperator(...)` both ignore `location` and are themselves never called.
  Either wire them into the enhancer or delete them.
- `PluralizationFunctions.kt:553` — `matchCase(original, result, originalCase)` ignores
  `original`; `originalCase` appears to carry what it needed.

## Related: unfinished implementation marked by unused locals

- `YAMLFunctions.kt:1183-1201` — a flat YAML parser records `currentKey` / `currentIndent`
  when it meets a key with an empty value (the start of a nested block) and then never reads
  them, so nested structure is silently dropped. The variables are the only remaining trace
  of the intent, which is why the cleanup pass left them.

## Reproducing this list

```bash
./gradlew compileKotlin --rerun-tasks 2>&1 | grep "is never used" | sort -u
```

Note that a `Parameter 'args' is never used` on a `fun f(args: List<UDM>)` is **not** in this
category: the stdlib dispatch table requires that signature, so a zero-argument stdlib
function necessarily ignores it. There are 18 of those, and they are correct as written.
