/**
 * Serves the built VitePress docs site SAME-ORIGIN through Theia's own backend (:4000) at /utlx-docs/,
 * so the in-IDE **Help → UTLX Language** panel iframes a relative `/utlx-docs/` URL. Same-origin =
 * cloud/remote-safe (the browser already talks to :4000) and satisfies a strict `frame-src 'self'` CSP;
 * our route sets no X-Frame-Options, so the iframe isn't refused.
 *
 * Because it's served under the sub-path /utlx-docs/, the site must be built with VitePress
 * `base: '/utlx-docs/'` (so its absolute `/assets/…` references resolve here). That's the `dist-ide`
 * build. Single source of truth: books/language/*.typ → (pandoc) website/guide/*.md → (vitepress) dist.
 *
 * BUILD FIRST:  cd website && npm run content && npm run build:ide
 */

import { injectable } from 'inversify';
import express = require('@theia/core/shared/express');
import * as path from 'path';
import * as fs from 'fs';
import { BackendApplicationContribution } from '@theia/core/lib/node';

/** Repo path to the IDE docs build (base '/utlx-docs/'). Hardcoded like the demo scripts; promote to
 *  a preference/env later. */
const DOCS_DIST = '/Users/magr/data/mapping/github-git/utl-x/website/.vitepress/dist-ide';

/** Same-origin mount path. Keep in sync with the iframe src in browser/docs/utlx-docs-widget.tsx AND
 *  the VitePress `base` used by `npm run build:ide`. */
const DOCS_PATH = '/utlx-docs';

@injectable()
export class DocsStaticContribution implements BackendApplicationContribution {
    configure(app: express.Application): void {
        // Always mount the route — even before `dist-ide` is built — so /utlx-docs/ never returns a
        // bare "Cannot GET". express.static reads the filesystem per request, so once you run
        // `npm run build:ide` the docs appear WITHOUT a backend restart.
        app.use(DOCS_PATH, express.static(DOCS_DIST, { index: 'index.html' }));
        // Deep-link / not-yet-built fallback.
        app.get(`${DOCS_PATH}/*`, (_req: express.Request, res: express.Response) => {
            const index = path.join(DOCS_DIST, 'index.html');
            if (fs.existsSync(index)) {
                res.sendFile(index);
            } else {
                res.status(200).type('html').send(
                    '<!doctype html><meta charset="utf-8">' +
                    '<body style="font-family:system-ui;padding:2rem;color:var(--foreground,#444)">' +
                    '<h2>UTL-X docs not built yet</h2>' +
                    '<p>Run <code>cd website &amp;&amp; npm run content &amp;&amp; npm run build:ide</code> ' +
                    'and reopen this panel.</p></body>');
            }
        });
        console.log(`[UTLX docs] /utlx-docs/ → ${DOCS_DIST}`);
    }
}
