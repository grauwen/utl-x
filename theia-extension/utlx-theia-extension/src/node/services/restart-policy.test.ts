/**
 * IF06: unit tests for the restart-supervision policy (pure decision logic).
 * Uses a fixed clock (the `now` arg) so backoff and crash-loop behavior are
 * deterministic — no real timers, no spawned processes.
 */
import {
    backoffDelay,
    decideRestart,
    markHealthy,
    newRestartState,
    DEFAULT_RESTART_POLICY,
} from './restart-policy';

describe('backoffDelay', () => {
    it('doubles from base and caps at maxMs', () => {
        expect(backoffDelay(0)).toBe(1000);   // base
        expect(backoffDelay(1)).toBe(2000);
        expect(backoffDelay(2)).toBe(4000);
        expect(backoffDelay(3)).toBe(8000);
        expect(backoffDelay(4)).toBe(16000);
        expect(backoffDelay(5)).toBe(30000);  // 32000 -> capped
        expect(backoffDelay(10)).toBe(30000); // stays capped
    });
});

describe('decideRestart', () => {
    it('returns increasing, capped backoff and increments attempt', () => {
        const s = newRestartState();
        let now = 0;
        const delays: number[] = [];
        for (let i = 0; i < 5; i++) {
            const o = decideRestart(s, now);
            expect(o.action).toBe('restart');
            if (o.action === 'restart') {
                delays.push(o.delayMs);
                expect(o.attempt).toBe(i + 1);
            }
            now += 1; // crashes within the window
        }
        expect(delays).toEqual([1000, 2000, 4000, 8000, 16000]);
    });

    it('gives up after exceeding crashLoopMax within the window', () => {
        const s = newRestartState();
        // crashLoopMax (5) restarts allowed; the 6th in-window crash trips give-up.
        for (let i = 0; i < DEFAULT_RESTART_POLICY.crashLoopMax; i++) {
            expect(decideRestart(s, i).action).toBe('restart');
        }
        const sixth = decideRestart(s, DEFAULT_RESTART_POLICY.crashLoopMax);
        expect(sixth.action).toBe('give-up');
        expect(s.giveUp).toBe(true);
    });

    it('does NOT give up when crashes are spread beyond the window', () => {
        const s = newRestartState();
        const win = DEFAULT_RESTART_POLICY.crashLoopWindowMs;
        // Each crash is > one window after the previous → old ones get pruned,
        // so the in-window count never exceeds the cap.
        for (let i = 0; i < 10; i++) {
            const o = decideRestart(s, i * (win + 1));
            expect(o.action).toBe('restart');
        }
        expect(s.giveUp).toBe(false);
    });

    it('markHealthy resets the backoff attempt counter', () => {
        const s = newRestartState();
        decideRestart(s, 0); // attempt -> 1
        decideRestart(s, 1); // attempt -> 2
        expect(s.attempts).toBe(2);
        markHealthy(s);
        expect(s.attempts).toBe(0);
        // Next restart starts from base delay again.
        const o = decideRestart(s, 2);
        expect(o.action).toBe('restart');
        if (o.action === 'restart') {
            expect(o.delayMs).toBe(1000);
        }
    });
});
