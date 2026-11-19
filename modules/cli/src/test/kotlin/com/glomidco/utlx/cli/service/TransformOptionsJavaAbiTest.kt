// modules/cli/src/test/kotlin/com/glomidco/utlx/cli/service/TransformOptionsJavaAbiTest.kt
package com.glomidco.utlx.cli.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * `TransformOptions` is consumed from Java by an embedding integration, and that makes its
 * constructor set an ABI rather than an implementation detail.
 *
 * **A Kotlin call site cannot catch a regression here.** Kotlin has default arguments, so
 * `TransformOptions(verbose = true)` compiles whether or not `@JvmOverloads` is present — every
 * ordinary test in this module would stay green while Java callers broke. Kotlin default arguments
 * do not exist on the JVM: without `@JvmOverloads` the class exposes exactly ONE real constructor
 * taking every parameter, so a Java `new TransformOptions(a, b, c, d)` stops compiling the moment a
 * field is added.
 *
 * That has happened twice. `parseProfile` and `maxLanguageVersion` turned a 4-argument Java call
 * into a 6-argument one, and the integration had to be edited to pass two values it did not care
 * about. So this test asserts the JVM-visible surface directly, because that is the thing being
 * promised.
 *
 * The faithful alternative is a Java test source set, where removing `@JvmOverloads` would be a
 * compile error rather than an assertion failure. That is stronger and also a build-file change and
 * a new source set for one class; reflection buys the same guarantee at today's cost.
 */
class TransformOptionsJavaAbiTest {

    /** Real constructors, excluding the synthetic `DefaultConstructorMarker` overload Kotlin emits. */
    private fun realConstructorArities(): Set<Int> =
        TransformationService.TransformOptions::class.java.constructors
            .filterNot { c -> c.parameterTypes.any { it.name.endsWith("DefaultConstructorMarker") } }
            .map { it.parameterCount }
            .toSet()

    @Test
    fun `every trailing-parameter prefix is reachable from Java`() {
        val arities = realConstructorArities()
        val fieldCount = TransformationService.TransformOptions::class.java
            .declaredFields.count { !it.isSynthetic && it.name != "Companion" }

        // @JvmOverloads emits one constructor per prefix: the no-arg one through the full one.
        val expected = (0..fieldCount).toSet()
        assertEquals(
            expected, arities,
            "expected a constructor for every arity 0..$fieldCount; @JvmOverloads is probably missing " +
                "from TransformOptions. Kotlin callers would not notice — Java callers break."
        )
    }

    @Test
    fun `the four-argument call used by the embedding integration still binds`() {
        // The exact shape that broke twice: verbose, pretty, strictTypes, overrideOutputFormat.
        // Named rather than derived, so a future reshuffle of the parameter ORDER fails here too —
        // appending a field is safe, inserting one silently changes what an existing Java call means.
        val four = TransformationService.TransformOptions::class.java.constructors
            .singleOrNull { it.parameterCount == 4 }
        assertNotNull(four, "the 4-argument constructor is part of the published surface")

        assertArrayEquals(
            arrayOf(
                java.lang.Boolean.TYPE,   // verbose
                java.lang.Boolean.TYPE,   // pretty
                java.lang.Boolean.TYPE,   // strictTypes
                String::class.java        // overrideOutputFormat
            ),
            four!!.parameterTypes,
            "the first four parameters must stay (verbose, pretty, strictTypes, overrideOutputFormat) " +
                "— new fields are APPENDED, never inserted"
        )
    }

    @Test
    fun `the defaults are the values the integration can now omit`() {
        // If these were not the defaults, telling the integration to drop the two arguments would
        // change behaviour rather than just shorten the call.
        val defaults = TransformationService.TransformOptions()
        assertEquals(com.glomidco.utlx.core.format.ParseProfile.STANDARD, defaults.parseProfile)
        assertEquals(com.glomidco.utlx.core.semantic.LanguageVersions.LATEST, defaults.maxLanguageVersion)
    }
}
