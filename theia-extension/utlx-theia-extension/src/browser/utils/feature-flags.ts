/**
 * Project-wide, RUNTIME-settable feature flags (the "semaphore"). Unlike a constant, these can
 * be changed by the user at runtime (e.g. via a menu command) and persist across reloads
 * (localStorage). Read the getter at the point of use so changes take effect immediately.
 */

export type FileDialogMode = 'theia' | 'browser';

const FILE_DIALOG_MODE_KEY = 'utlx.fileDialogMode';
const FILE_DIALOG_MODE_DEFAULT: FileDialogMode = 'theia';

/**
 * How file **Load** dialogs are presented:
 *  - `'theia'`   → Theia's open dialog. Browses the **workspace/bundle filesystem**, returns a
 *                  real URI → full path (path-on-hover + future bundle save). **Default.**
 *  - `'browser'` → the plain HTML `<input type="file">` picker. Client-side upload; basename only.
 */
export function getFileDialogMode(): FileDialogMode {
    try {
        const v = localStorage.getItem(FILE_DIALOG_MODE_KEY);
        return v === 'browser' || v === 'theia' ? v : FILE_DIALOG_MODE_DEFAULT;
    } catch {
        return FILE_DIALOG_MODE_DEFAULT;
    }
}

export function setFileDialogMode(mode: FileDialogMode): void {
    try {
        localStorage.setItem(FILE_DIALOG_MODE_KEY, mode);
    } catch {
        /* localStorage unavailable — fall back to the default on next read */
    }
}

export type NameOnLoadMode = 'inherit' | 'keep';

const NAME_ON_LOAD_MODE_KEY = 'utlx.nameOnLoadMode';
const NAME_ON_LOAD_MODE_DEFAULT: NameOnLoadMode = 'inherit';

/**
 * What happens to an input/output **name** when a file is loaded into it:
 *  - `'inherit'` → the slot adopts the loaded file's name (stem → identifier, e.g.
 *                  "00-enterprise-order.json" → "enterprise-order"). **Default** (current behavior).
 *  - `'keep'`    → the existing name is left unchanged (stays "input"/"output"). Useful when
 *                  scripts/examples reference `$input` and a load should NOT rename the slot
 *                  (see B24 — `$input` examples + the single-input default).
 *
 * Only affects auto-naming on load; a name the user typed is never clobbered in either mode.
 */
export function getNameOnLoadMode(): NameOnLoadMode {
    try {
        const v = localStorage.getItem(NAME_ON_LOAD_MODE_KEY);
        return v === 'keep' || v === 'inherit' ? v : NAME_ON_LOAD_MODE_DEFAULT;
    } catch {
        return NAME_ON_LOAD_MODE_DEFAULT;
    }
}

export function setNameOnLoadMode(mode: NameOnLoadMode): void {
    try {
        localStorage.setItem(NAME_ON_LOAD_MODE_KEY, mode);
    } catch {
        /* localStorage unavailable — fall back to the default on next read */
    }
}
