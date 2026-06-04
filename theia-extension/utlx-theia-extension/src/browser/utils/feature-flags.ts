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
