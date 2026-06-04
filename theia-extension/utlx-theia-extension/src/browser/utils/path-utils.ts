/**
 * Path display helpers shared by the panels.
 */

/**
 * Shorten a full path for display: if it runs through a `*.utlxp` project directory, show it
 * from that project root onward (project-relative, portable) — e.g.
 * "/Users/…/sales.utlxp/schemas/x.xsd" → "sales.utlxp/schemas/x.xsd". Otherwise the full path.
 * This mirrors how the engine resolves refs relative to the bundle (`.utlxp`/`.utlar`) root.
 */
export function toProjectRelativePath(fullPath: string): string {
    const segments = fullPath.split('/');
    for (let i = segments.length - 1; i >= 0; i--) {
        if (segments[i].endsWith('.utlxp')) {
            return segments.slice(i).join('/');
        }
    }
    return fullPath;
}
