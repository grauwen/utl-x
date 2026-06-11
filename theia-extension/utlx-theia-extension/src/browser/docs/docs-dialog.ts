/**
 * Help → UTLX Language: a MODAL dialog (like the Function Builder) that iframes the locally-served
 * VitePress docs site (backend DocsStaticContribution at /utlx-docs/, same-origin). Users browse the
 * book/reference inside it and close again — it never touches the main editor layout.
 */

import { AbstractDialog } from '@theia/core/lib/browser';

/** Same-origin mount path served by the backend DocsStaticContribution. */
const DOCS_PATH = '/utlx-docs/';

export class DocsDialog extends AbstractDialog<void> {
    constructor() {
        super({ title: 'UTL-X Language' });
        this.addClass('utlx-docs-dialog');

        const frame = document.createElement('iframe');
        frame.src = DOCS_PATH;
        frame.title = 'UTL-X Language';
        frame.style.border = 'none';
        frame.style.display = 'block';
        frame.style.width = '82vw';
        frame.style.height = '78vh';
        this.contentNode.appendChild(frame);

        this.appendAcceptButton('Close');
    }

    get value(): void {
        return undefined;
    }
}
