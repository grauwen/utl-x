/* eslint-disable */
/**
 * UTL-X IDE — talk demo (kiosk).  Scenario: playwright/scenarios/scenario1.md
 *
 *   "Write once → output JSON, then XML, then YAML."  Shows the language + IDE ease.
 *
 * What it does (per scenario 1):
 *   precondition: Load Theia, Name Keep  → set via localStorage so the input stays named
 *                 "input" (so $input works — see B24).
 *   1. put a JSON input in the Input panel        (examples/json/00-enterprise-order.json)
 *   2. output format defaults to json
 *   3. type a small UTL-X transform in Monaco
 *   4. Execute → show result
 *   5. switch output → xml ; 6. Execute
 *   7. switch output → yaml; 8. Execute
 *   (loop, with a pause, until you Ctrl-C)
 *
 * Run (Playwright resolves from playwright-mcp-server):
 *   cd theia-extension/playwright && npm i && node kiosk-demo.js
 *   # or: NODE_PATH=../../playwright-mcp-server/node_modules node kiosk-demo.js
 *
 * PREREQ: Theia must be running on :4000 with utlxd (run rebuild-and-start-mcp.sh first).
 *
 * NOTE (trial 1): selectors are derived from the CURRENT widget source, but VERIFY on first
 * run — every selector is a named constant in CONFIG so a mismatch is a one-line fix. The
 * two fragile spots are flagged: (a) filling the input textarea, (b) focusing Monaco.
 */
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const REPO = path.resolve(__dirname, '../..');

const CONFIG = {
  baseURL: 'http://localhost:4000',
  inputFile: path.join(REPO, 'examples/json/00-enterprise-order.json'),

  // A small, slide-legible transform. Fields verified against the enterprise-order example.
  transform: [
    '{',
    '  orderId: $input.orderId,',
    '  customer: $input.customer.companyName,',
    '  status: $input.orderStatus,',
    '  total: $input.totalAmount,',
    '  currency: $input.currency',
    '}'
  ].join('\n'),

  outputFormats: ['json', 'xml', 'yaml'],   // steps 2/5/7

  // ── selectors: prefer stable data-testid hooks; class selector is a fallback
  //    (so this works whether or not the extension has been rebuilt yet) ──────
  sel: {
    inputTextarea: '[data-testid="utlx-input"], textarea.utlx-input-editor',
    monaco: '.utlx-editor-widget .monaco-editor',                     // Monaco internal — no testid
    executeBtn: '[data-testid="utlx-execute"], .utlx-toolbar-button:has-text("Execute")',
    outputFormatSelect: '[data-testid="utlx-output-format"], .utlx-output-panel-container .utlx-panel-toolbar select',
    outputResult: '[data-testid="utlx-output"], pre.utlx-output-display',
    outputError: '[data-testid="utlx-output-error"], pre.utlx-error-message',
    // for future scenarios: modeToggle '[data-testid="utlx-mode-toggle"]', inputLoad '[data-testid="utlx-input-load"]', aiAssist '[data-testid="utlx-ai-assist"]'
  },

  // ── pacing (demo speed) ──────────────────────────────────────────────────
  slowMo: 250,            // ms per Playwright action (visible, watchable)
  typeDelay: 45,          // ms per keystroke when typing the transform
  beat: 1200,             // pause between steps (narration room)
  afterExecute: 1600,     // pause to let the audience read each result
  betweenLoops: 4000,     // pause before repeating

  loop: !process.env.DEMO_ONCE,   // `npm run demo:once` = one pass then exit (good for recording)
  recordVideoDir: path.join(__dirname, 'recordings'),  // talk-safe fallback video
};

const MOD = process.platform === 'darwin' ? 'Meta' : 'Control';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
function narrate(msg) { console.log(`\n▶ ${msg}`); }

async function setOutputFormat(page, fmt) {
  await page.locator(CONFIG.sel.outputFormatSelect).first().selectOption(fmt);
}

async function execute(page) {
  await page.locator(CONFIG.sel.executeBtn).first().click();
  // Wait for a fresh result (or an error) to render.
  await page.locator(`${CONFIG.sel.outputResult}, ${CONFIG.sel.outputError}`).first()
    .waitFor({ state: 'visible', timeout: 15000 }).catch(() => {});
  await sleep(CONFIG.afterExecute);
}

async function runOnce(page, inputJson) {
  // 1. Load the JSON input. Name Keep ⇒ the slot stays "input" (so $input resolves).
  //    (trial 1) We fill the input textarea directly — robust + same visible result as a
  //    file Load. To show the actual "Load from file" gesture, replace this with a click on
  //    button[title="Load from file"] and drive the Theia open dialog.
  narrate('Step 1 — put a JSON order in the Input panel');
  const ta = page.locator(CONFIG.sel.inputTextarea).first();
  await ta.waitFor({ state: 'visible', timeout: 15000 });
  await ta.click();
  await page.keyboard.press(`${MOD}+A`);
  await page.keyboard.press('Delete');
  await ta.fill(inputJson);
  await ta.blur();
  await sleep(CONFIG.beat);

  // 2. Output format defaults to json.
  narrate('Step 2 — output is JSON by default');
  await setOutputFormat(page, 'json');
  await sleep(CONFIG.beat);

  // 3. Type a small UTL-X transform in Monaco.
  narrate('Step 3 — write a small UTL-X transform');
  const editor = page.locator(CONFIG.sel.monaco).first();
  await editor.click();                       // focus Monaco  (VERIFY: right editor)
  await page.keyboard.press(`${MOD}+A`);
  await page.keyboard.press('Delete');
  await page.keyboard.type(CONFIG.transform, { delay: CONFIG.typeDelay });
  await sleep(CONFIG.beat);

  // 4. Execute → JSON result.
  narrate('Step 4 — Execute → JSON output');
  await execute(page);

  // 5/6. Switch output to XML → Execute.
  narrate('Step 5/6 — same transform, output XML');
  await setOutputFormat(page, 'xml');
  await sleep(CONFIG.beat);
  await execute(page);

  // 7/8. Switch output to YAML → Execute.
  narrate('Step 7/8 — same transform, output YAML');
  await setOutputFormat(page, 'yaml');
  await sleep(CONFIG.beat);
  await execute(page);

  narrate('One transform, three formats — done.');
}

(async () => {
  const inputJson = fs.readFileSync(CONFIG.inputFile, 'utf-8');
  fs.mkdirSync(CONFIG.recordVideoDir, { recursive: true });

  const browser = await chromium.launch({
    headless: false,
    slowMo: CONFIG.slowMo,
    args: ['--kiosk', '--start-fullscreen', '--disable-infobars'],
  });
  const context = await browser.newContext({
    viewport: null,
    recordVideo: { dir: CONFIG.recordVideoDir },
  });

  // Precondition (semaphores): Load Theia + Name Keep — set before any page script runs.
  await context.addInitScript(() => {
    localStorage.setItem('utlx.fileDialogMode', 'theia');
    localStorage.setItem('utlx.nameOnLoadMode', 'keep');
  });

  const page = await context.newPage();
  await page.goto(CONFIG.baseURL, { waitUntil: 'domcontentloaded' });
  // Let Theia's shell + the UTL-X panels mount.
  await page.locator(CONFIG.sel.executeBtn).first().waitFor({ state: 'visible', timeout: 60000 });

  let n = 0;
  do {
    n++;
    narrate(`══ Demo pass #${n} ══`);
    try {
      await runOnce(page, inputJson);
    } catch (e) {
      console.error('  pass failed, resetting:', e.message);
      await page.reload({ waitUntil: 'domcontentloaded' }).catch(() => {});
      await page.locator(CONFIG.sel.executeBtn).first().waitFor({ state: 'visible', timeout: 60000 }).catch(() => {});
    }
    if (CONFIG.loop) await sleep(CONFIG.betweenLoops);
  } while (CONFIG.loop);

  await context.close();   // flushes the recording to recordings/
  await browser.close();
  console.log(`\n✓ video saved under ${CONFIG.recordVideoDir}`);
})();
