package org.apache.utlx.daemon

import kotlin.system.exitProcess

/**
 * Die-with-parent watchdog (IF06).
 *
 * When the IDE process (Theia / Electron backend) that spawned this daemon dies
 * — including a hard `kill -9` where no graceful shutdown runs — the daemon must
 * exit on its own. Otherwise it orphans and keeps its ports (LSP/API) bound,
 * causing the next launch to hit EADDRINUSE or talk to a stale process.
 *
 * Mechanism: poll the parent PID via `ProcessHandle` (JDK 9+). This is portable
 * (no platform-specific code) and does not consume stdin — important because the
 * daemon may use stdin for the STDIO LSP transport.
 *
 * Opt-in: only started when the parent passes `--parent-pid`. Script-managed or
 * standalone daemons (which intentionally outlive their launching shell) pass no
 * PID and therefore run without a watchdog.
 *
 * Testability: the policy (`checkOnce`) and the liveness probe (`isParentAlive`)
 * are separated from the thread/`exitProcess` plumbing in `start`, so the
 * decision logic can be unit-tested without spawning a thread or killing the JVM.
 *
 * PID-reuse note: if the parent dies and the OS recycles its exact PID within one
 * poll window, the watchdog could briefly see "alive" and delay exit. This is
 * highly unlikely on modern PID spaces and only delays cleanup; it never causes a
 * wrong early exit.
 */
object ParentWatchdog {

    /** Real liveness probe for [pid] using ProcessHandle (JDK 9+). */
    fun isParentAlive(pid: Long): Boolean =
        ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    /**
     * One watchdog step: if the parent is gone, invoke [onParentDeath] and return
     * true (the loop should stop). Otherwise return false. Pure decision logic —
     * both the liveness check and the death action are injected, so this is
     * testable without threads or process exit.
     */
    fun checkOnce(
        parentPid: Long,
        isAlive: (Long) -> Boolean = ::isParentAlive,
        onParentDeath: () -> Unit
    ): Boolean {
        if (!isAlive(parentPid)) {
            onParentDeath()
            return true
        }
        return false
    }

    /**
     * Start the watchdog on a daemon thread. Polls every [pollMillis]; on parent
     * death, [onParentDeath] runs (default: exit the JVM cleanly).
     */
    fun start(
        parentPid: Long,
        pollMillis: Long = 2000,
        onParentDeath: () -> Unit = {
            System.err.println(
                "[utlxd] Parent process $parentPid is gone — exiting (die-with-parent watchdog)"
            )
            exitProcess(0)
        }
    ) {
        val thread = Thread({
            while (true) {
                if (checkOnce(parentPid, onParentDeath = onParentDeath)) return@Thread
                try {
                    Thread.sleep(pollMillis)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
        }, "parent-watchdog")
        thread.isDaemon = true
        thread.start()
        System.err.println("[utlxd] Parent-watchdog active (parent PID $parentPid, poll ${pollMillis}ms)")
    }
}
