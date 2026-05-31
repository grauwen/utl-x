package org.apache.utlx.daemon

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for ParentWatchdog (IF06 die-with-parent).
 *
 * We test the injectable decision logic (checkOnce) and the real liveness probe
 * (isParentAlive). We never let the watchdog call the real exitProcess — the
 * death action is injected so it cannot kill the test JVM.
 */
class ParentWatchdogTest {

    @Test
    fun `checkOnce triggers death action and stops when parent is gone`() {
        val died = AtomicInteger(0)
        val stop = ParentWatchdog.checkOnce(
            parentPid = 4242L,
            isAlive = { false },          // simulate a dead parent
            onParentDeath = { died.incrementAndGet() }
        )
        assertTrue(stop, "checkOnce should signal the loop to stop when parent is gone")
        assertEquals(1, died.get(), "death action should run exactly once")
    }

    @Test
    fun `checkOnce does nothing and continues while parent is alive`() {
        val died = AtomicInteger(0)
        val stop = ParentWatchdog.checkOnce(
            parentPid = 4242L,
            isAlive = { true },           // simulate a live parent
            onParentDeath = { died.incrementAndGet() }
        )
        assertFalse(stop, "checkOnce should not stop the loop while parent is alive")
        assertEquals(0, died.get(), "death action must NOT run while parent is alive")
    }

    @Test
    fun `isParentAlive is true for the current process and false for an unused pid`() {
        val selfPid = ProcessHandle.current().pid()
        assertTrue(ParentWatchdog.isParentAlive(selfPid), "current process should be alive")

        // A very high PID is overwhelmingly unlikely to exist.
        assertFalse(ParentWatchdog.isParentAlive(999_999_999L), "nonexistent pid should be dead")
    }

    @Test
    fun `start exits on its own thread when parent dies, without killing the JVM`() {
        val latch = CountDownLatch(1)
        // Inject a death action that signals instead of exiting the process.
        ParentWatchdog.start(
            parentPid = 4242L,
            pollMillis = 20,
            onParentDeath = { latch.countDown() }
        )
        assertTrue(
            latch.await(2, TimeUnit.SECONDS),
            "watchdog thread should detect the dead parent and run the death action"
        )
    }
}
