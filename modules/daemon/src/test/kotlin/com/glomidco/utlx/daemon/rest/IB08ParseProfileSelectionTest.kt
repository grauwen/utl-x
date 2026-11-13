package com.glomidco.utlx.daemon.rest

import com.glomidco.utlx.core.format.ParseProfile
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * IB08 — the daemon must honour a deployment's parse profile.
 *
 * Every other surface could choose one: the CLI through `--parse-profile`, the engine through
 * `EngineConfig.effectiveParseProfile()`. `RestApiServer` constructed a bare
 * `TransformationService()`, so it always ran `STANDARD` — not wrong output, but a deployment
 * pinning `strict` was silently not honoured, and the IDE preview is exactly where a mapper
 * decides whether a document is acceptable. A duplicate-keyed payload previewing cleanly and
 * then being rejected in production is the preview-≠-production trap again.
 *
 * `DaemonConfig` would be the natural source, but its `load` is still a stub that returns
 * defaults and drops overrides, so this reads the env var / system property pair that
 * `BundleWorkspace.resolve` already uses for the module's other deployment setting.
 */
class IB08ParseProfileSelectionTest {

    /** Set a system property for the duration of [block], restoring whatever was there. */
    private fun <T> withProperty(key: String, value: String?, block: () -> T): T {
        val previous = System.getProperty(key)
        return try {
            if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            block()
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }

    private fun resolveWith(value: String?) =
        withProperty("utlx.parse.profile", value) { RestApiServer.resolveParseProfile() }

    @Test
    fun `each profile name resolves`() {
        assertEquals(ParseProfile.STRICT, resolveWith("strict"))
        assertEquals(ParseProfile.STANDARD, resolveWith("standard"))
        assertEquals(ParseProfile.LENIENT, resolveWith("lenient"))
    }

    @Test
    fun `the name is case and whitespace tolerant`() {
        // A value coming from a container env var or a YAML scalar often arrives padded.
        assertEquals(ParseProfile.STRICT, resolveWith("STRICT"))
        assertEquals(ParseProfile.STRICT, resolveWith("  Strict  "))
        assertEquals(ParseProfile.LENIENT, resolveWith("Lenient"))
    }

    @Test
    fun `nothing configured keeps the previous behaviour`() {
        // STANDARD is what the daemon used before it could be configured at all, so a
        // deployment that sets nothing must not shift underneath it.
        assertEquals(ParseProfile.STANDARD, resolveWith(null))
    }

    @Test
    fun `an unrecognised value falls back rather than failing startup`() {
        // A daemon that refuses to boot over a typo'd profile is worse than one that runs at
        // the documented default and logs a warning.
        assertEquals(ParseProfile.STANDARD, resolveWith("stricter"))
        assertEquals(ParseProfile.STANDARD, resolveWith(""))
    }
}
