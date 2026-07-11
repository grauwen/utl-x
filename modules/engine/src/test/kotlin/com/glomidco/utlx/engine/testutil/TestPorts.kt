package com.glomidco.utlx.engine.testutil

import java.net.ServerSocket

/**
 * Resolve a port for a test server, in priority order:
 *
 *  1. [envVar] if set — an explicit override (a firewalled CI sandbox that only allows certain
 *     ports, or attaching a debugger to a known port). Used as-is, trusting the operator.
 *  2. [preferred] if given AND currently free — a deterministic port (handy in logs / for a
 *     debugger), but only when it's actually available.
 *  3. Otherwise an OS-assigned free port.
 *
 * Steps 2–3 keep it collision-safe: a preferred port is a *preference*, never a hard requirement,
 * so a taken (or unset) preferred port falls back to an ephemeral one instead of failing a run.
 */
internal fun freeOrEnvPort(envVar: String, preferred: Int? = null): Int {
    System.getenv(envVar)?.toIntOrNull()?.let { return it }
    if (preferred != null && isPortAvailable(preferred)) return preferred
    return ServerSocket(0).use { it.localPort }
}

/** True if [port] can be bound right now (best-effort; a tiny TOCTOU window remains, fine for tests). */
private fun isPortAvailable(port: Int): Boolean =
    runCatching { ServerSocket(port).use { } }.isSuccess
