# B26: CLI error handling — errors printed twice, parse failures dump a stack trace, and `transform <script>` with no input hangs

**Status:** Root cause **CONFIRMED** by source analysis (not yet reproduced on the released native binary; fixes **proposed, not yet implemented**).
**Priority:** Medium — no incorrect transformation output; this is UX / input-validation quality. Bug 3 (hang) is the most user-hostile.
**Created:** June 2026
**Reported:** GitHub issue #2 (@skin27 / Raymond Meester) — Windows 11, UTL-X CLI v1.2.1 (Oracle GraalVM native, Java 25.0.3 LTS).
**Related:** B24/B25 (same reporter, same v1.2.1 native binary, CLI surface).

> **Classification: these are BUGS, not features.** All three are unintended behavior — duplicated
> output, an internal stack trace leaking to the user, and an indefinite block with no prompt. None
> is a deliberate, documented behavior.

---

## TL;DR

Three CLI input-validation / error-handling defects, two of which share **one root cause**: errors
are emitted on **two channels at once** — printed at the `throw` site via `System.err.println`, then
printed *again* by `Main.kt` when it renders the returned `CommandResult.Failure`. The third is an
unguarded blocking `readLine()` on stdin.

| # | Symptom | Root cause | File:line |
|---|---|---|---|
| 1 | `Error: Script file not found: …` printed **twice** | double output channel (throw-site print + `Main` print) | `TransformCommand.kt:631` |
| 2a | Parse failure prints a full **Java stack trace** | parser logs an *expected* `ParseException` at `error` level **with the throwable** | `parser_impl.kt:61` |
| 2b | `Parse errors:` block printed **twice** | double output channel (same as #1) | `TransformationService.kt:175-177` |
| 3 | `utlx transform <script>` (no input) **hangs** | unguarded `readLine()` blocks on an interactive TTY | `TransformCommand.kt:255`, `:448` |

The unifying fix is **one error-output channel**: throw the message, let `Main.kt` render it once.

---

## The shared root cause (Bugs 1 and 2b)

`Main.kt` renders every failed command's message:

```kotlin
// modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt:118
when (result) {
    is CommandResult.Success -> exitProcess(0)
    is CommandResult.Failure -> {
        if (result.message.isNotEmpty()) {
            System.err.println("Error: ${result.message}")   // ← the single, intended channel
        }
        exitProcess(result.exitCode)
    }
}
```

But several validation sites **also** print the same text before throwing, and the thrown message
then flows back into `result.message` → printed a second time.

---

## Bug 1 — "Script file not found" printed twice

### Reproduction (from the issue)
```
D:\test\utlx> utlx-windows-x64.exe transform .\transformations\order-to-invoice.utl .\examples\order.xml
Error: Script file not found: D:\test\utlx\.\transformations\order-to-invoice.utl
Error: Script file not found: D:\test\utlx\.\transformations\order-to-invoice.utl
```
(Note: the file has a `.utl` extension, not `.utlx` — a secondary UX point; the validation message is correct, just doubled.)

### Root cause
```kotlin
// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt:630
if (!scriptFile.exists()) {
    System.err.println("Error: Script file not found: ${scriptFile.absolutePath}")   // print #1
    throw IllegalArgumentException("Script file not found: ${scriptFile.absolutePath}")
}
```
`execute` catches and returns the message (`TransformCommand.kt:172`):
```kotlin
} catch (e: IllegalArgumentException) {
    // Argument parsing errors (already printed to stderr)
    return CommandResult.Failure(e.message ?: "Invalid arguments", 1)
}
```
…and `Main.kt:122` prints `Error: <message>` — **print #2**.

The same double-print pattern affects:
- *"Script file is required"* — `TransformCommand.kt:625`
- *"Input file not found"* — `TransformCommand.kt:638`
- *"Unknown option: …"* — `TransformCommand.kt:570`

It is also **inconsistent**: some sites are throw-only (e.g. *"Expression cannot be empty"* at `:582`,
*"Cannot use -e with a script file"* at `:585`) and rely on `Main` to print — so the codebase mixes
both conventions.

### Proposed fix
Adopt a single channel. Remove the `System.err.println("Error: …")` lines in `parseOptions`
(keep `printUsage()` where helpful) and let the thrown message be printed once by `Main.kt`. This
also makes the throw-only cases consistent.

---

## Bug 2 — empty / invalid script dumps a stack trace and duplicates "Parse errors"

### Reproduction (from the issue — an empty script file)
```
19:17:37.237 ERROR o.a.utlx.core.parser.Parser_impl - Parse exception: Expected '---' separator after header (not found in script)
org.apache.utlx.core.parser.ParseException: Expected '---' separator after header (not found in script)
        at org.apache.utlx.core.parser.Parser.error(parser_impl.kt:1634)
        at org.apache.utlx.core.parser.Parser.parseProgram(parser_impl.kt:85)
        ... (full stack trace) ...
Parse errors:
  Expected '---' separator after header (not found in script) at Location(line=1, column=1)
Error: Parse errors:
  Expected '---' separator after header (not found in script) at Location(line=1, column=1)
```

### Root cause 2a — parser logs an expected error with the throwable
```kotlin
// modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt:60
} catch (e: ParseException) {
    logger.error(e) { "Parse exception: ${e.message}" }   // ← passes the throwable → logback prints the stack trace
    val enhancedError = ParseErrorEnhancer.enhance(e, source, tokens, current)
    ...
    ParseResult.Failure(...)   // the failure is ALREADY handled structurally
}
```
A parse failure is an **expected user error** — `parse()` converts it into a `ParseResult.Failure`
that the caller handles cleanly. Logging it at `error` level *with the exception object* is what emits
the `org.apache.utlx.core.parser.ParseException: … at Parser.error(parser_impl.kt:1634) …` dump to the
console.

### Root cause 2b — "Parse errors:" printed twice (double channel)
```kotlin
// modules/cli/src/main/kotlin/org/apache/utlx/cli/service/TransformationService.kt:171
is ParseResult.Failure -> {
    val errorMessages = parseResult.errors.joinToString("\n") { "  ${it.message} at ${it.location}" }
    System.err.println("Parse errors:")              // print #1
    System.err.println(errorMessages)
    throw IllegalStateException("Parse errors:\n$errorMessages")   // → Main prints #2: "Error: Parse errors:\n  …"
}
```

### Proposed fix
- **2a:** downgrade `logger.error(e) { … }` → `logger.debug(e) { … }` (or `logger.warn { e.message }`
  with no throwable). Keeps the stack trace available under `--debug`, off the default console.
- **2b:** remove the two `System.err.println` lines; the thrown message already carries the text and
  `Main.kt` prints it once.
- **2c (nice-to-have):** add a friendly pre-check for an empty script in `TransformCommand`
  (around `:309`, before compile): `if (scriptContent.isBlank()) → "Error: Script file is empty: <path>"`,
  rather than surfacing the parser-internal *"Expected '---' separator"*.

---

## Bug 3 — `utlx transform <script>` with no input file hangs

### Reproduction (from the issue)
```
utlx-windows-x64.exe transform .\transformations\order-to-invoice.utlx
(hangs indefinitely)
```

### Root cause
With a script but **no** input file, `namedInputs` is empty, so `execute` falls into the stdin branch:
```kotlin
// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt:255
val inputData = readStdin()
// :448
private fun readStdin(): String =
    generateSequence { readLine() }.joinToString("\n")
```
`readLine()` **blocks waiting for terminal input** when stdin is an interactive console (nothing piped).
On Windows, `utlx transform script.utlx` from a normal prompt therefore appears to hang — it is silently
waiting for typed input + EOF (Ctrl+Z).

### Proposed fix
Guard the stdin read with the **same TTY heuristic `Main.kt` already uses** for identity mode
(`System.console() == null` ⇒ stdin is piped):
```kotlin
if (options.namedInputs.isEmpty() && System.console() != null) {
    return CommandResult.Failure(
        "No input provided. Pass an input file (utlx transform <script> <input>) " +
        "or pipe data via stdin.", 1)
}
```
`System.console()` returns null when stdin *or* stdout is redirected — the same imperfection already
relied upon in `Main.kt:23`, so this stays consistent with existing behavior.

---

## Suggested fix order

1. **Bug 3** (hang) — highest user impact; small, self-contained guard.
2. **Bug 2a** (stack trace) — one-line logger change in core.
3. **Bugs 1 + 2b** (double print) — adopt the single-channel convention across `parseOptions` and
   `compileScript`; verify no message is lost for the throw-only validation cases.
4. **Bug 2c** (empty-script message) — optional polish.

All changes are localized to `TransformCommand.kt`, `TransformationService.kt`, and one line in
`parser_impl.kt`. No transformation-semantics impact; recommend a few CLI-level regression tests
(missing script, empty script, no input on a TTY) to lock the behavior.
