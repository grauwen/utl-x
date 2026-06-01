/**
 * Session persistence helper (IF09).
 *
 * Persists IDE work product across a browser REFRESH using `sessionStorage`:
 *   - per-tab (two tabs / two browsers can't clobber each other — see IF09),
 *   - survives refresh, cleared on tab close (durable file-backing is IF03/IF04).
 *
 * Every snapshot is wrapped in a versioned envelope so an incompatible older
 * snapshot is discarded (defaults) instead of being force-fed into a changed shape.
 * A size guard keeps us well under the ~5 MB sessionStorage budget; large input
 * sample data is skipped (see capContent) rather than silently truncated.
 */

/** Bump when any persisted snapshot shape changes — old snapshots are then discarded. */
export const PERSIST_SCHEMA_VERSION = 1;

/** Per-field cap for bulky content (instance/schema data). Above this, skip it. */
export const PER_FIELD_CAP_BYTES = 1024 * 1024; // 1 MB

/** Total per-key cap. A snapshot bigger than this is not persisted at all. */
export const TOTAL_SNAPSHOT_CAP_BYTES = 4 * 1024 * 1024; // 4 MB (budget is ~5 MB)

/** sessionStorage keys, one per widget. */
export const SESSION_KEYS = {
    inputPanel: 'utlx.session.input-panel',
    outputPanel: 'utlx.session.output-panel',
    editor: 'utlx.session.editor',
} as const;

interface Envelope<T> {
    v: number;
    data: T;
}

/**
 * Read a versioned snapshot. Returns undefined when absent, unparseable, or from an
 * incompatible schema version (caller then falls back to defaults).
 */
export function loadSession<T>(key: string): T | undefined {
    try {
        const raw = sessionStorage.getItem(key);
        if (!raw) {
            return undefined;
        }
        const env = JSON.parse(raw) as Envelope<T>;
        if (!env || typeof env !== 'object' || env.v !== PERSIST_SCHEMA_VERSION) {
            return undefined; // incompatible / corrupt → discard cleanly
        }
        return env.data;
    } catch {
        return undefined;
    }
}

/**
 * Write a versioned snapshot. Never throws (persistence must not break the IDE).
 * A snapshot exceeding the total cap is dropped with a console note rather than
 * risking a sessionStorage quota error.
 */
export function saveSession<T>(key: string, data: T): void {
    try {
        const raw = JSON.stringify({ v: PERSIST_SCHEMA_VERSION, data } as Envelope<T>);
        if (raw.length > TOTAL_SNAPSHOT_CAP_BYTES) {
            console.warn(
                `[session-persistence] ${key}: snapshot ${raw.length}B exceeds cap ` +
                `${TOTAL_SNAPSHOT_CAP_BYTES}B — not persisted`
            );
            return;
        }
        sessionStorage.setItem(key, raw);
    } catch (e) {
        // QuotaExceededError or serialization issue — degrade silently.
        console.warn(`[session-persistence] failed to persist ${key}`, e);
    }
}

/** Remove a snapshot (e.g. on an explicit reset). */
export function clearSession(key: string): void {
    try {
        sessionStorage.removeItem(key);
    } catch {
        /* ignore */
    }
}

/**
 * Decide whether a bulky content field is small enough to persist. Above the
 * per-field cap we skip the data and signal `dropped: true`, so the panel can show
 * "content not restored — reload the file" instead of silently truncating it.
 */
export function capContent(content: string | undefined | null): { content?: string; dropped: boolean } {
    if (content == null || content === '') {
        return { dropped: false };
    }
    if (content.length > PER_FIELD_CAP_BYTES) {
        return { dropped: true };
    }
    return { content, dropped: false };
}
