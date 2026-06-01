/**
 * IF06: pure restart-supervision policy.
 *
 * The decision logic for restart-with-backoff and crash-loop detection, extracted
 * from ServiceLifecycleManager so it can be unit-tested without spawning processes
 * or timers. The manager holds the mutable RestartState and performs the side
 * effects (setTimeout, respawn); these functions only compute decisions.
 */

export interface RestartPolicy {
    /** First backoff delay (ms). */
    baseMs: number;
    /** Backoff ceiling (ms). */
    maxMs: number;
    /** Max restarts allowed within the crash-loop window before giving up. */
    crashLoopMax: number;
    /** Rolling window (ms) over which restarts are counted for crash-loop detection. */
    crashLoopWindowMs: number;
}

export const DEFAULT_RESTART_POLICY: RestartPolicy = {
    baseMs: 1000,
    maxMs: 30000,
    crashLoopMax: 5,
    crashLoopWindowMs: 60000,
};

export interface RestartState {
    /** Consecutive restart attempts since the service was last healthy. */
    attempts: number;
    /** Timestamps (ms epoch) of recent restarts, for crash-loop detection. */
    crashTimes: number[];
    /** True once crash-looping has been detected and we stop retrying. */
    giveUp: boolean;
}

export function newRestartState(): RestartState {
    return { attempts: 0, crashTimes: [], giveUp: false };
}

/** Exponential backoff for the given attempt index (0-based), capped at maxMs. */
export function backoffDelay(attempt: number, policy: RestartPolicy = DEFAULT_RESTART_POLICY): number {
    return Math.min(policy.baseMs * 2 ** attempt, policy.maxMs);
}

export type RestartOutcome =
    | { action: 'restart'; delayMs: number; attempt: number }
    | { action: 'give-up'; reason: string };

/**
 * Decide what to do after an unexpected service exit, mutating [state] to record
 * the new crash time / attempt. Pure aside from updating the passed-in state, and
 * deterministic given [now] — so tests can drive it with a fixed clock.
 */
export function decideRestart(
    state: RestartState,
    now: number,
    policy: RestartPolicy = DEFAULT_RESTART_POLICY
): RestartOutcome {
    // Drop crash timestamps outside the rolling window, then record this crash.
    state.crashTimes = state.crashTimes.filter(t => now - t < policy.crashLoopWindowMs);
    state.crashTimes.push(now);

    if (state.crashTimes.length > policy.crashLoopMax) {
        state.giveUp = true;
        return {
            action: 'give-up',
            reason:
                `>${policy.crashLoopMax} restarts in ${policy.crashLoopWindowMs / 1000}s ` +
                `— crash loop, giving up`,
        };
    }

    const delayMs = backoffDelay(state.attempts, policy);
    state.attempts++;
    return { action: 'restart', delayMs, attempt: state.attempts };
}

/** Reset backoff after a service becomes healthy again. */
export function markHealthy(state: RestartState): void {
    state.attempts = 0;
}
