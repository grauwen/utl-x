# B17: Native Binary — Apache XML Security Resource Bundle Missing

**Status:** Closed — 2026-11-13, verified no longer reproducing (see below)
**Priority:** Critical (native binary unusable)  
**Created:** May 2026  
**Affects:** v1.1.0 native binaries (macOS, Linux, Windows)

---

## Problem

The GraalVM native binary crashes on first function call with:

```
java.util.MissingResourceException: Can't find bundle for base name 
org/apache/xml/security/resource/xmlsecurity, locale en_US
```

The error occurs in `XMLCanonicalizationFunctions.<clinit>` when the stdlib initializes C14N functions. This blocks ALL transformations — not just XML canonicalization — because the stdlib initialization fails entirely.

The JVM version (`java -jar cli-1.1.0.jar`) works correctly. Only the native binary is affected.

## Root Cause

GraalVM native-image strips resources not explicitly listed in the resource configuration. The Apache XML Security library (`xmlsec`) loads a resource bundle at class init time:

```java
ResourceBundle.getBundle("org/apache/xml/security/resource/xmlsecurity")
```

This resource bundle is not included in the native image because it's not detected by GraalVM's static analysis (loaded dynamically via `ResourceBundle.getBundle()`).

## Fix

Add the resource bundle to the GraalVM native-image configuration.

### Option A: resource-config.json

Add to `src/main/resources/META-INF/native-image/resource-config.json`:

```json
{
  "resources": {
    "includes": [
      {"pattern": "org/apache/xml/security/resource/.*"}
    ]
  },
  "bundles": [
    {"name": "org.apache.xml.security.resource.xmlsecurity"}
  ]
}
```

### Option B: Build argument

Add to the native-image build command (in `build.gradle.kts` or GitHub Actions workflow):

```
-H:IncludeResourceBundles=org.apache.xml.security.resource.xmlsecurity
```

### Option C: GraalVM configuration in build.gradle.kts

```kotlin
graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("-H:IncludeResourceBundles=org.apache.xml.security.resource.xmlsecurity")
        }
    }
}
```

## Files to Check

| File | What to look for |
|------|-----------------|
| `modules/cli/build.gradle.kts` | GraalVM native-image configuration section |
| `modules/cli/src/main/resources/META-INF/native-image/` | Existing resource/reflect config |
| `.github/workflows/release.yml` | Native-image build step arguments |

## Verification

After fix:
```bash
echo '{"name": "Alice"}' | ./utlx-macos-arm64 -e 'concat("Hello, ", $input.name)'
# Should output: "Hello, Alice"

echo '<doc/>' | ./utlx-macos-arm64 -f xml -e 'c14n($input)'
# Should output canonical XML (tests C14N specifically)
```

## Impact

ALL native binary users are affected — the binary is unusable. The JVM version works fine as a workaround:

```bash
java -jar modules/cli/build/libs/cli-1.1.0.jar transform script.utlx < input.json
```

---

*Bug B17. May 2026. Critical — native binary broken since at least v1.0.2.*

---

## Closed 2026-11-13 — verified no longer reproducing

Found during a backlog sweep: this report still said **Open**, but the native binary runs the Apache XML Security paths without a missing-bundle error.

```bash
# modules/cli/build/native/nativeCompile/utlx, rebuilt 2026-11-13
c14n($input)      => "<a><b>1</b></a>"
c14nHash($input)  => "23551dcb..."
```

The full 577-fixture conformance corpus also passes against the native binary, so the
reflection and resource configuration covers these paths now.

A stale `Open` is as misleading as a stale `Fixed` — it hides real work behind noise. The
behaviour is now pinned by `conformance-suite/utlx/tests/edge-cases/stale_open_bug_regressions.yaml`,
so closing it cannot be undone silently.
