# IF01: IDE — Adopt F02 Newline Separator Support

**Status:** Open  
**Priority:** Medium  
**Created:** May 2026  
**Depends on:** F02 (newline separators — implemented May 2026)

---

## Classification: Feature (not bug)

The IDE (utlxd + TypeScript frontend) currently expects commas between properties, let bindings, and match cases inside `{ }`. After F02, newlines are sufficient. The IDE needs to be updated to match the parser's new behavior.

This is a feature adaptation, not a bug — the IDE was correct before F02. It needs to catch up with the language change.

## What Changed in F02

The UTL-X parser (`parser_impl.kt`) now accepts newlines as separators in 3 additional contexts:

| Context | Before F02 | After F02 |
|---------|-----------|-----------|
| Properties inside `{ }` | Comma required | Newline or comma |
| Let bindings inside `{ }` | Comma required | Newline or comma |
| Match cases inside `match { }` | Comma required | Newline or comma |
| `[` after expression on new line | Index access | Array literal |

## IDE Impact

### 1. Syntax Highlighting / Error Reporting

The IDE's TypeScript parser (or its connection to utlxd) may flag valid code as errors:

```utlx
// IDE may show error on line 3 — expects comma after "Alice"
{
  name: "Alice"
  age: 30
}
```

**Fix:** The IDE should use utlxd's parser (which shares the same Kotlin code as utlx CLI). If utlxd is recompiled with F02 changes, the parser errors will disappear. Verify that utlxd uses the updated parser, not a separate TypeScript parser.

### 2. Code Generation / Scaffolding

The IDE generates code in several places:
- Schema-to-schema scaffolding (Design Time mode)
- Function builder "Apply to editor" button
- Autocompletion inserting property templates

These may still generate comma-separated properties. While this is valid (backward compatible), the **recommended style** is now newlines for multi-line objects.

**Fix:** Update code generation templates to use newlines between properties for multi-line output. Keep commas for single-line output (`{a: 1, b: 2}`).

### 3. Autocompletion

When the user types a property name and presses Enter, the IDE may auto-insert a comma at the end of the previous line. This is no longer necessary for multi-line objects.

**Fix:** Do not auto-insert trailing commas when the next token is on a new line.

### 4. Formatting / Pretty-Print

If the IDE has a "Format Document" command, it may add commas between properties. The formatter should be updated to follow the new style:
- Multi-line objects: newlines only (no commas)
- Single-line objects: commas required

### 5. Match Expression Support

Match cases without commas should be accepted:

```utlx
match ($input.status) {
  "active" => "green"
  "pending" => "yellow"
  _ => "gray"
}
```

## Verification Steps

1. Recompile utlxd with the F02 parser changes: `./gradlew :modules:daemon:build`
2. Open VS Code with the UTL-X extension
3. Verify: multi-line object without commas shows no errors
4. Verify: let bindings inside `{ }` without commas shows no errors
5. Verify: match cases without commas shows no errors
6. Verify: function builder generates newline-separated properties
7. Verify: autocompletion does not insert unnecessary commas

## Relationship to IB01

IB01 (IDE — `let` bindings outside object body fail) is a separate issue. IF01 is about the IDE accepting the new F02 separator rules. IB01 is about `$input` scoping in the IDE execution engine.

## Effort Estimate

| Task | Effort |
|------|--------|
| Recompile utlxd (picks up F02 parser changes automatically) | 5 minutes |
| Test IDE error reporting | 0.5 day |
| Update code generation templates (if needed) | 0.5 day |
| Update formatter (if exists) | 0.5 day |
| **Total** | **1-2 days** |

---

*IDE feature IF01. May 2026.*
*Adapts the IDE to F02's newline separator changes. The parser change propagates automatically via utlxd recompilation; the TypeScript frontend may need template/formatter updates.*
