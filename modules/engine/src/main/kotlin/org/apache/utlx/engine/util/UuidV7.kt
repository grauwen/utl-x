package org.apache.utlx.engine.util

import com.fasterxml.uuid.Generators

/**
 * UUIDv7 generator for the UTLXe engine (RFC 9562).
 *
 * Delegates to FasterXML JUG (java-uuid-generator) — the de facto
 * standard UUID library for Java. Apache 2.0 licensed.
 *
 * Used for MessageId generation in EF04 (message correlation and tracing).
 */
object UuidV7 {

    private val generator = Generators.timeBasedEpochGenerator()

    /** Generate a new UUIDv7 string. Time-ordered, sortable, timestamp-embedded. */
    fun generate(): String = generator.generate().toString()
}
