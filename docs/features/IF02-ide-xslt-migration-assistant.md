# IF02: IDE XSLT Migration Assistant

**Status:** Parked — depends on F12 (XSLT transpiler) and EF08 (.NET SDK/BizTalk)  
**Priority:** High (after F12 is implemented)  
**Created:** May 2026  
**Related:** [F12: XSLT-to-UTL-X Migration Tool](F12-xslt-to-utlx-migration.md)

---

## Summary

IDE integration for F12's XSLT-to-UTL-X transpiler. Provides a guided migration experience in VS Code / Theia where developers can convert XSLT stylesheets to UTL-X transformations with visual feedback, inline TODO markers, and AI-assisted completion for unconvertible constructs.

## Relationship to F12

F12 is the **engine** — the `utlx migrate` CLI command and the XSLT-to-UTL-X compiler logic.

IF02 is the **experience** — the IDE integration that makes F12 accessible and productive.

```
F12 (CLI/compiler)  ←── core transpiler logic
     ↑
IF02 (IDE)          ←── UX layer on top of F12
     ↑
utlxd daemon        ←── LSP/MCP bridge between IDE and F12
```

IF02 does not implement its own transpiler — it calls F12 through the `utlxd` design daemon.

## Features

### 1. Right-click "Convert to UTL-X"

In the VS Code / Theia file explorer:
- Right-click on `.xsl` or `.xslt` file
- Select "Convert to UTL-X"
- Generated `.utlx` file opens in a new editor tab

### 2. Side-by-side preview

Split view:
- Left: original XSLT (read-only, syntax highlighted)
- Right: generated UTL-X (editable)
- Matching elements highlighted in both panels (click `xsl:for-each` on left → `map()` highlights on right)

### 3. Inline TODO markers

Unconvertible XSLT constructs get `// TODO: ...` comments in the generated UTL-X with:
- The original XSLT snippet as context
- Explanation of why it couldn't be auto-converted
- Suggested manual approach

```utlx
// TODO: xsl:apply-templates with mode="detail" — UTL-X doesn't have template matching.
// Original XSLT: <xsl:apply-templates select="items/item" mode="detail"/>
// Suggestion: Use map($input.items.item, (item) -> { ... }) with explicit field mapping.
```

### 4. AI-assisted TODO completion

For each `// TODO` marker:
- Code action "Complete with AI" sends the XSLT context + UTL-X skeleton to Claude/Copilot
- AI generates the UTL-X equivalent
- Developer reviews and accepts/edits

This bridges the 10-15% gap that the deterministic transpiler can't cover.

### 5. Migration report

After conversion, generate a summary:
- Lines converted automatically vs TODO markers
- Coverage percentage
- List of unsupported XSLT features encountered
- Estimated manual effort for remaining TODOs

## Implementation

### utlxd design commands

```
utlxd migrate/convert     — convert .xsl to .utlx (calls F12)
utlxd migrate/preview     — side-by-side diff without writing file
utlxd migrate/report      — migration feasibility report
utlxd migrate/batch       — convert entire directory of .xsl files
```

### VS Code extension actions

| Action | Trigger | Command |
|---|---|---|
| Convert file | Right-click .xsl → "Convert to UTL-X" | `utlx.migrate.convert` |
| Preview conversion | Right-click .xsl → "Preview UTL-X conversion" | `utlx.migrate.preview` |
| Migration report | Right-click folder → "Migration report" | `utlx.migrate.report` |
| Complete TODO | Code action on `// TODO` line | `utlx.migrate.completeTodo` |

## Effort

| Task | Effort | Depends on |
|---|---|---|
| utlxd design commands | 1 day | F12 |
| VS Code extension: convert action | 1 day | utlxd commands |
| Side-by-side preview panel | 2 days | VS Code extension API |
| TODO marker generation | Part of F12 | F12 |
| AI-assisted completion | 1-2 days | Claude/Copilot API |
| Migration report | 0.5 day | F12 |
| **Total** | **5-7 days** | After F12 |

## Sequence

1. **EF08** — .NET SDK + BizTalk shim (gives customers a runtime path)
2. **F12** — XSLT transpiler CLI (`utlx migrate`) 
3. **IF02** — IDE integration (this feature)

---

*Feature IF02. May 2026. IDE integration for F12 XSLT migration. Depends on F12 and EF08.*
