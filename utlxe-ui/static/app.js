// UTLXe Admin UI — vanilla JS SPA
// All communication via REST API (fetch)
//
// API base URL: in production (nginx proxy), calls go to /admin/* on the same origin.
// For local dev, set ?api=http://localhost:8081 in the URL to point directly at UTLXe.

const app = document.getElementById('app');
let adminKey = sessionStorage.getItem('utlxe-admin-key') || '';
let engineInfo = {};

// Detect API base from URL parameter or default to same-origin proxy
const urlParams = new URLSearchParams(window.location.search);
const API_BASE = urlParams.get('api') || '';  // e.g., "http://localhost:8081" or "" for proxy

// ── API helpers ──

async function api(method, path, body) {
  const opts = {
    method,
    headers: { 'X-Admin-Key': adminKey }
  };
  if (body !== undefined) {
    if (typeof body === 'string') {
      opts.headers['Content-Type'] = 'text/plain';
      opts.body = body;
    } else {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
  }
  const resp = await fetch(API_BASE + '/admin' + path, opts);
  const text = await resp.text();
  try { return { status: resp.status, data: JSON.parse(text) }; }
  catch { return { status: resp.status, data: text }; }
}

async function apiGet(path) { return api('GET', path); }
async function apiPost(path, body) { return api('POST', path, body); }
async function apiDelete(path) { return api('DELETE', path); }

// ── Router ──

function navigate(hash) {
  window.location.hash = hash;
}

window.addEventListener('hashchange', route);
window.addEventListener('load', async () => {
  if (!adminKey) { renderLogin(); return; }
  const check = await apiGet('/info');
  if (check.status === 403) { renderLogin('Invalid key'); return; }
  engineInfo = check.data;
  route();
});

function route() {
  const hash = window.location.hash || '#/';
  const parts = hash.replace('#', '').split('/').filter(Boolean);

  if (!adminKey) { renderLogin(); return; }

  if (parts[0] === 'transformations' && parts[1]) {
    renderTransformationDetail(parts[1]);
  } else if (parts[0] === 'upload') {
    renderUpload();
  } else if (parts[0] === 'messaging' && parts[1]) {
    renderMessaging(parts[1]);
  } else if (parts[0] === 'logs') {
    renderLogs();
  } else if (parts[0] === 'schemas') {
    renderSchemas();
  } else if (parts[0] === 'sync') {
    renderSync();
  } else if (parts[0] === 'config') {
    renderConfig();
  } else {
    renderDashboard();
  }
}

// ── Login ──

function renderLogin(error) {
  app.innerHTML = `
    <div class="login-container">
      <h1>UTL<span style="color:#cc0000">X</span><span style="color:#0078d4;font-size:0.7em">e</span> Admin</h1>
      ${error ? `<div class="banner banner-error">${error}</div>` : ''}
      <label>Admin API Key</label>
      <input type="password" id="key-input" placeholder="Enter X-Admin-Key value" autofocus>
      <button class="btn btn-primary" style="width:100%;margin-top:8px" onclick="doLogin()">Connect</button>
    </div>`;
  document.getElementById('key-input').addEventListener('keydown', e => {
    if (e.key === 'Enter') doLogin();
  });
}

async function doLogin() {
  adminKey = document.getElementById('key-input').value;
  const check = await apiGet('/info');
  if (check.status === 403) {
    adminKey = '';
    renderLogin('Invalid API key');
    return;
  }
  sessionStorage.setItem('utlxe-admin-key', adminKey);
  engineInfo = check.data;
  navigate('#/');
}

function logout() {
  adminKey = '';
  sessionStorage.removeItem('utlxe-admin-key');
  renderLogin();
}

// ── Layout ──

function layout(activeNav, content) {
  const mode = engineInfo.mode || 'open';
  const locked = mode === 'locked';
  const version = engineInfo.version || '';
  const dapr = engineInfo.dapr_mode || 'http-only';
  const level = engineInfo.log_level || 'INFO';

  app.innerHTML = `
    <header>
      <div class="logo">UTL<span>X</span><span class="e">e</span> Admin</div>
      <div class="status">
        <span class="badge ${locked ? 'badge-locked' : 'badge-open'}">${mode}</span>
        <span>dapr: ${dapr}</span>
        <span>log: ${level}</span>
        <span>v${version}</span>
        <a href="#" onclick="logout()" style="color:#aab;text-decoration:underline">logout</a>
      </div>
    </header>
    <nav>
      <a href="#/" class="${activeNav === 'dashboard' ? 'active' : ''}">Dashboard</a>
      <a href="#/upload" class="${activeNav === 'upload' ? 'active' : ''}">Upload</a>
      <a href="#/schemas" class="${activeNav === 'schemas' ? 'active' : ''}">Schemas</a>
      <a href="#/sync" class="${activeNav === 'sync' ? 'active' : ''}">Sync</a>
      <a href="#/logs" class="${activeNav === 'logs' ? 'active' : ''}">Logs</a>
      <a href="#/config" class="${activeNav === 'config' ? 'active' : ''}">Config</a>
    </nav>
    <main>
      ${locked ? '<div class="banner banner-locked">Production mode (locked) — transformations deployed via CI/CD. Operational endpoints available.</div>' : ''}
      ${content}
    </main>`;
}

// ── Dashboard ──

async function renderDashboard() {
  layout('dashboard', '<div class="card"><h2>Loading...</h2></div>');

  const [infoResp, txResp] = await Promise.all([apiGet('/info'), apiGet('/transformations')]);
  engineInfo = infoResp.data;
  const txs = txResp.data.transformations || [];

  const totalMsgs = txs.reduce((s, t) => s + (t.messages_processed || 0), 0);
  const totalErrors = txs.reduce((s, t) => s + (t.errors || 0), 0);
  const errorPct = totalMsgs > 0 ? (totalErrors / totalMsgs * 100).toFixed(2) : '0.00';

  const rows = txs.map(t => {
    const dot = t.status === 'paused' ? 'dot-paused' : 'dot-ready';
    const statusCls = t.status === 'paused' ? 'status-paused' : 'status-ready';
    const msg = t.messaging;
    const msgText = msg ? describeMessaging(msg) : '<span style="color:#aaa">none</span>';
    const syncBadge = t.sync_status ? `<span class="status-${t.sync_status}">${t.sync_status}</span>` : '';
    return `<tr onclick="navigate('#/transformations/${t.name}')" style="cursor:pointer">
      <td><span class="dot ${dot}"></span><strong>${t.name}</strong></td>
      <td class="${statusCls}">${t.status}</td>
      <td>${(t.messages_processed || 0).toLocaleString()}</td>
      <td>${t.errors || 0}</td>
      <td>${msgText}</td>
      <td>${syncBadge}</td>
    </tr>`;
  }).join('');

  layout('dashboard', `
    <div class="metrics-row">
      <div class="metric-card"><div class="value">${txs.length}</div><div class="label">Transformations</div></div>
      <div class="metric-card"><div class="value">${totalMsgs.toLocaleString()}</div><div class="label">Messages</div></div>
      <div class="metric-card"><div class="value">${totalErrors}</div><div class="label">Errors</div></div>
      <div class="metric-card"><div class="value">${errorPct}%</div><div class="label">Error Rate</div></div>
    </div>
    <div class="card">
      <h2>Transformations</h2>
      <table>
        <tr><th>Name</th><th>Status</th><th>Messages</th><th>Errors</th><th>Messaging</th><th>Sync</th></tr>
        ${rows || '<tr><td colspan="6" style="color:#aaa;text-align:center">No transformations loaded</td></tr>'}
      </table>
    </div>
  `);
}

function describeMessaging(msg) {
  const parts = [];
  if (msg.input) {
    const ep = msg.input;
    if (ep.queue) parts.push('in: queue:' + ep.queue);
    else if (ep.topic) parts.push('in: topic:' + ep.topic);
    else if (ep.eventhub) parts.push('in: eh:' + ep.eventhub);
  }
  if (msg.output) {
    const ep = msg.output;
    if (ep.queue) parts.push('out: queue:' + ep.queue);
    else if (ep.topic) parts.push('out: topic:' + ep.topic);
    else if (ep.eventhub) parts.push('out: eh:' + ep.eventhub);
  }
  return parts.join(' | ') || '<span style="color:#aaa">none</span>';
}

// ── Transformation Detail ──

async function renderTransformationDetail(name) {
  layout('dashboard', '<div class="card"><h2>Loading...</h2></div>');

  const [detailResp, errorsResp, msgResp, valResp, cfgResp, schemasResp] = await Promise.all([
    apiGet(`/transformations/${name}`),
    apiGet(`/transformations/${name}/errors?limit=10`),
    apiGet(`/transformations/${name}/messaging`),
    apiGet(`/transformations/${name}/validation`),
    apiGet(`/transformations/${name}/config`),
    apiGet('/schemas')
  ]);

  if (detailResp.status === 404) {
    layout('dashboard', `<div class="banner banner-error">Transformation '${name}' not found</div>`);
    return;
  }

  const tx = detailResp.data;
  const errors = errorsResp.data.errors || [];
  const msg = msgResp.data;
  const val = valResp.data || {};
  const cfg = cfgResp.data || {};
  const availableSchemas = (schemasResp.data.schemas || []).map(s => s.filename);
  const locked = engineInfo.mode === 'locked';

  const errorRows = errors.map(e => `
    <tr>
      <td>${new Date(e.timestamp).toLocaleString()}</td>
      <td>${e.phase || ''}</td>
      <td>${escapeHtml(e.message)}</td>
      <td style="font-size:11px;color:#888">${e.message_id || ''}</td>
    </tr>`).join('');

  layout('dashboard', `
    <div style="margin-bottom:16px">
      <a href="#/" class="btn btn-sm">&larr; Back</a>
      <strong style="margin-left:12px;font-size:18px">${name}</strong>
    </div>

    <div class="card">
      <h2>Details</h2>
      <table>
        <tr><td style="width:150px"><strong>Status</strong></td><td><span class="dot ${tx.status === 'paused' ? 'dot-paused' : 'dot-ready'}"></span>${tx.status}</td></tr>
        <tr><td><strong>Strategy</strong></td><td>${tx.strategy || 'COMPILED'}</td></tr>
        <tr><td><strong>Messages</strong></td><td>${(tx.messages_processed || 0).toLocaleString()}</td></tr>
        <tr><td><strong>Errors</strong></td><td>${tx.errors || 0}</td></tr>
        <tr><td><strong>Deployed</strong></td><td>${tx.deployed_at || ''}</td></tr>
      </table>
      <div class="btn-group">
        ${tx.status === 'paused'
          ? `<button class="btn btn-primary btn-sm" onclick="doResume('${name}')">Resume</button>`
          : `<button class="btn btn-sm" onclick="doPause('${name}')">Pause</button>`}
        <a href="#/messaging/${name}" class="btn btn-sm" ${locked ? 'disabled' : ''}>Messaging</a>
        ${!locked ? `<button class="btn btn-danger btn-sm" onclick="doDelete('${name}')">Delete</button>` : ''}
      </div>
    </div>

    <div class="card">
      <h2>Messaging</h2>
      ${msg.input || msg.output
        ? `<table>
            ${msg.input ? `<tr><td style="width:80px"><strong>Input</strong></td><td>${describeEndpoint(msg.input)}</td></tr>` : ''}
            ${msg.output ? `<tr><td><strong>Output</strong></td><td>${describeEndpoint(msg.output)}</td></tr>` : ''}
            <tr><td><strong>Sync</strong></td><td class="status-${msg.sync_status || 'no_dapr'}">${msg.sync_status || 'no_dapr'}</td></tr>
           </table>`
        : '<p style="color:#aaa">No messaging configured</p>'}
    </div>

    <div class="card">
      <h2>Configuration</h2>
      ${locked ? `
      <table>
        <tr><td style="width:150px"><strong>Strategy</strong></td><td>${cfg.strategy || 'COMPILED'}</td></tr>
        <tr><td><strong>Validation</strong></td><td>${cfg.validationPolicy || 'SKIP'}</td></tr>
        <tr><td><strong>Max concurrent</strong></td><td>${cfg.maxConcurrent || 1}</td></tr>
        <tr><td><strong>Input schema</strong></td><td>${(cfg.inputs || []).map(i => i.schema || 'none').join(', ')}</td></tr>
        <tr><td><strong>Output schema</strong></td><td>${cfg.output_schema || 'none'}</td></tr>
      </table>
      <p style="color:#856404;margin-top:8px">Read-only in locked mode</p>
      ` : `
      <label>Strategy</label>
      <select id="cfg-strategy" style="width:auto">
        ${['COMPILED','TEMPLATE','COPY','AUTO'].map(s => `<option ${s === cfg.strategy ? 'selected' : ''}>${s}</option>`).join('')}
      </select>

      <label>Validation policy</label>
      <select id="cfg-validation" style="width:auto">
        ${['strict','warn','SKIP'].map(s => `<option ${s === cfg.validationPolicy ? 'selected' : ''}>${s}</option>`).join('')}
      </select>

      <label>Max concurrent</label>
      <input type="text" id="cfg-maxconcurrent" value="${cfg.maxConcurrent || 1}" style="width:80px">

      <label>Input schema</label>
      <select id="cfg-input-schema" style="width:auto">
        <option value="">none</option>
        ${availableSchemas.map(s => `<option ${(cfg.inputs || []).some(i => i.schema === s) ? 'selected' : ''}>${s}</option>`).join('')}
      </select>

      <label>Output schema</label>
      <select id="cfg-output-schema" style="width:auto">
        <option value="">none</option>
        ${availableSchemas.map(s => `<option ${s === cfg.output_schema ? 'selected' : ''}>${s}</option>`).join('')}
      </select>

      <div class="btn-group">
        <button class="btn btn-primary btn-sm" onclick="doSaveConfig('${name}')">Save Config</button>
      </div>
      <div id="cfg-result"></div>
      `}
    </div>

    <div class="card">
      <h2>Validation</h2>
      <table>
        <tr><td style="width:150px"><strong>Effective policy</strong></td><td>${val.effective_policy || 'n/a'}</td></tr>
        <tr><td><strong>Source</strong></td><td>${val.source || 'default'}</td></tr>
        <tr><td><strong>Config policy</strong></td><td>${val.config_policy || 'n/a'}</td></tr>
        ${val.override_policy ? `<tr><td><strong>Override</strong></td><td>${val.override_policy} <button class="btn btn-danger btn-sm" onclick="doRemoveValidationOverride('${name}')">Remove</button></td></tr>` : ''}
      </table>
      <div style="margin-top:8px;display:flex;gap:8px;align-items:center">
        <select id="val-policy" style="width:auto">
          <option value="strict" ${val.effective_policy === 'strict' ? 'selected' : ''}>strict</option>
          <option value="warn" ${val.effective_policy === 'warn' ? 'selected' : ''}>warn</option>
          <option value="off" ${val.effective_policy === 'off' ? 'selected' : ''}>off</option>
        </select>
        <button class="btn btn-sm" onclick="doSetValidationOverride('${name}')">Set Override</button>
      </div>
      <div id="val-result"></div>
    </div>

    <div class="card">
      <h2>Test</h2>
      <label>Input (JSON)</label>
      <textarea id="test-input" rows="4" placeholder='{"key": "value"}'></textarea>
      <div class="btn-group">
        <button class="btn btn-primary btn-sm" onclick="doTest('${name}')">Run Test</button>
      </div>
      <div id="test-result"></div>
    </div>

    <div class="card">
      <h2>Recent Errors (${errorsResp.data.total_errors || 0} total)</h2>
      ${errors.length > 0
        ? `<table>
            <tr><th>Time</th><th>Phase</th><th>Message</th><th>MessageId</th></tr>
            ${errorRows}
           </table>`
        : '<p style="color:#aaa">No recent errors</p>'}
    </div>

    <div class="card">
      <h2>Source</h2>
      <pre>${escapeHtml(tx.source || '')}</pre>
    </div>
  `);
}

function describeEndpoint(ep) {
  if (!ep) return '';
  const type = ep.queue ? 'queue' : ep.topic ? 'topic' : ep.eventhub ? 'eventhub' : '?';
  const name = ep.queue || ep.topic || ep.eventhub || '';
  const extra = ep.subscription ? ` (sub: ${ep.subscription})` : ep.consumerGroup ? ` (cg: ${ep.consumerGroup})` : '';
  const status = ep.dapr_status ? ` <span class="status-${ep.dapr_status === 'active' ? 'synced' : ep.dapr_status}">[${ep.dapr_status}]</span>` : '';
  return `${type}: <strong>${name}</strong>${extra}${status}`;
}

// ── Actions ──

async function doPause(name) {
  await apiPost(`/transformations/${name}/pause`, '');
  renderTransformationDetail(name);
}

async function doResume(name) {
  await apiPost(`/transformations/${name}/resume`, '');
  renderTransformationDetail(name);
}

async function doDelete(name) {
  if (!confirm(`Delete transformation '${name}'?`)) return;
  await apiDelete(`/transformations/${name}`);
  navigate('#/');
}

async function doSaveConfig(name) {
  const strategy = document.getElementById('cfg-strategy').value;
  const validationPolicy = document.getElementById('cfg-validation').value;
  const maxConcurrent = parseInt(document.getElementById('cfg-maxconcurrent').value) || 1;
  const inputSchema = document.getElementById('cfg-input-schema').value || null;
  const outputSchema = document.getElementById('cfg-output-schema').value || null;

  const body = {
    strategy,
    validationPolicy,
    maxConcurrent,
    inputs: inputSchema ? [{ name: 'input', schema: inputSchema }] : [],
    output_schema: outputSchema
  };

  const resp = await apiPost(`/transformations/${name}/config`, body);
  const div = document.getElementById('cfg-result');
  if (resp.status === 200) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">Config saved (strategy=${strategy}, validation=${validationPolicy})</div>`;
  } else {
    div.innerHTML = `<div class="banner banner-error" style="margin-top:8px">${escapeHtml(resp.data.error || 'Failed')}</div>`;
  }
}

async function doSetValidationOverride(name) {
  const policy = document.getElementById('val-policy').value;
  const resp = await apiPost(`/transformations/${name}/validation`, { policy });
  const div = document.getElementById('val-result');
  if (resp.status === 200) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">Override set to '${policy}'</div>`;
    setTimeout(() => renderTransformationDetail(name), 1000);
  } else {
    div.innerHTML = `<div class="banner banner-error" style="margin-top:8px">${escapeHtml(resp.data.error || 'Failed')}</div>`;
  }
}

async function doRemoveValidationOverride(name) {
  await apiDelete(`/transformations/${name}/validation`);
  renderTransformationDetail(name);
}

async function doTest(name) {
  const input = document.getElementById('test-input').value;
  const resultDiv = document.getElementById('test-result');
  resultDiv.innerHTML = '<p style="color:#888">Running...</p>';
  const resp = await apiPost(`/transformations/${name}/test`, input);
  if (resp.data.status === 'ok') {
    resultDiv.innerHTML = `
      <div class="banner banner-success" style="margin-top:8px">
        OK (${resp.data.duration_ms || 0}ms)
      </div>
      <pre>${escapeHtml(typeof resp.data.output === 'string' ? resp.data.output : JSON.stringify(resp.data.output, null, 2))}</pre>`;
  } else {
    resultDiv.innerHTML = `
      <div class="banner banner-error" style="margin-top:8px">
        ${escapeHtml(resp.data.error || resp.data.error_code || 'Error')}
      </div>`;
  }
}

// ── Upload ──

function renderUpload() {
  const locked = engineInfo.mode === 'locked';
  layout('upload', `
    ${locked ? '' : `
    <div class="card">
      <h2>Upload Transformation</h2>
      <label>Name</label>
      <input type="text" id="upload-name" placeholder="e.g. orders-in">
      <label>Source (.utlx) — paste or type</label>
      <textarea id="upload-source" rows="12" placeholder="%utlx 1.0
input json
output json
---
{
  result: $input.value
}"></textarea>
      <div class="btn-group">
        <button class="btn btn-primary" onclick="doUpload()">Upload</button>
        <button class="btn" onclick="doUploadAndTest()">Upload & Test</button>
      </div>
      <div id="upload-result"></div>
    </div>

    <div class="card">
      <h2>Upload Bundle (.zip / .utlar)</h2>
      <input type="file" id="bundle-file" accept=".zip,.utlar">
      <div class="btn-group">
        <button class="btn btn-primary" onclick="doUploadBundle()">Upload Bundle</button>
        <button class="btn" onclick="doValidateBundle()">Validate Only</button>
      </div>
      <div id="bundle-result"></div>
    </div>
    `}

    <div class="card">
      <h2>Export Bundle</h2>
      <p>Download the current state as a .utlar archive.</p>
      <div class="btn-group">
        <a href="/admin/bundle" class="btn btn-primary" download="bundle.utlar"
           onclick="event.preventDefault(); exportBundle()">Export Bundle</a>
      </div>
    </div>

    ${locked ? '' : `
    <div class="card">
      <h2>Delete All</h2>
      <p>Remove all transformations and schemas. Returns the engine to an empty state.</p>
      <div class="btn-group">
        <button class="btn btn-danger" onclick="doDeleteBundle()">Delete All Transformations</button>
      </div>
      <div id="delete-bundle-result"></div>
    </div>
    `}
  `);
}

async function doUpload() {
  const name = document.getElementById('upload-name').value.trim();
  const source = document.getElementById('upload-source').value;
  if (!name) { alert('Name is required'); return; }
  if (!source) { alert('Source is required'); return; }
  const resp = await apiPost(`/transformations/${name}`, source);
  const div = document.getElementById('upload-result');
  if (resp.status === 200) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">Deployed '${name}' (${resp.data.compiled_in_ms || 0}ms)</div>`;
  } else {
    div.innerHTML = `<div class="banner banner-error" style="margin-top:8px">${escapeHtml(resp.data.error || 'Upload failed')}</div>`;
  }
}

async function doUploadAndTest() {
  await doUpload();
  const name = document.getElementById('upload-name').value.trim();
  if (name) navigate(`#/transformations/${name}`);
}

async function doUploadBundle() {
  const file = document.getElementById('bundle-file').files[0];
  if (!file) { alert('Select a file'); return; }
  const bytes = await file.arrayBuffer();
  const resp = await fetch(API_BASE + '/admin/bundle', {
    method: 'POST',
    headers: { 'X-Admin-Key': adminKey, 'Content-Type': 'application/zip' },
    body: bytes
  });
  const data = await resp.json();
  const div = document.getElementById('bundle-result');
  if (resp.ok) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">Bundle deployed: ${(data.transformations || []).join(', ')}</div>`;
  } else {
    div.innerHTML = `<div class="banner banner-error" style="margin-top:8px">${escapeHtml(data.error || JSON.stringify(data.errors))}</div>`;
  }
}

async function doValidateBundle() {
  const file = document.getElementById('bundle-file').files[0];
  if (!file) { alert('Select a file'); return; }
  const bytes = await file.arrayBuffer();
  const resp = await fetch(API_BASE + '/admin/bundle/validate', {
    method: 'POST',
    headers: { 'X-Admin-Key': adminKey, 'Content-Type': 'application/zip' },
    body: bytes
  });
  const data = await resp.json();
  const div = document.getElementById('bundle-result');
  if (resp.ok) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">Valid: ${data.transformations} transformation(s)</div>`;
  } else {
    div.innerHTML = `<div class="banner banner-error" style="margin-top:8px">${escapeHtml(data.error || JSON.stringify(data.errors))}</div>`;
  }
}

async function exportBundle() {
  const resp = await fetch(API_BASE + '/admin/bundle', { headers: { 'X-Admin-Key': adminKey } });
  const blob = await resp.blob();
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'bundle.utlar';
  a.click();
}

async function doDeleteBundle() {
  if (!confirm('Delete ALL transformations and schemas? This cannot be undone.')) return;
  const resp = await apiDelete('/bundle');
  const div = document.getElementById('delete-bundle-result');
  if (resp.status === 200) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">All transformations deleted</div>`;
    setTimeout(() => navigate('#/'), 1000);
  } else {
    div.innerHTML = `<div class="banner banner-error" style="margin-top:8px">${escapeHtml(resp.data.error || 'Failed')}</div>`;
  }
}

// ── Messaging ──

async function renderMessaging(name) {
  layout('dashboard', '<div class="card"><h2>Loading...</h2></div>');
  const resp = await apiGet(`/transformations/${name}/messaging`);
  const msg = resp.data;
  const locked = engineInfo.mode === 'locked';

  const inputType = msg.input?.queue ? 'queue' : msg.input?.topic ? 'topic' : msg.input?.eventhub ? 'eventhub' : 'none';
  const outputType = msg.output?.queue ? 'queue' : msg.output?.topic ? 'topic' : msg.output?.eventhub ? 'eventhub' : 'none';
  const inputName = msg.input?.queue || msg.input?.topic || msg.input?.eventhub || '';
  const outputName = msg.output?.queue || msg.output?.topic || msg.output?.eventhub || '';
  const subscription = msg.input?.subscription || '';
  const consumerGroup = msg.input?.consumerGroup || '';

  layout('dashboard', `
    <div style="margin-bottom:16px">
      <a href="#/transformations/${name}" class="btn btn-sm">&larr; Back to ${name}</a>
    </div>
    <div class="card">
      <h2>Messaging: ${name}</h2>
      <p>Sync status: <strong class="status-${msg.sync_status || 'no_dapr'}">${msg.sync_status || 'no_dapr'}</strong>
         ${msg.last_synced ? ` (last synced: ${new Date(msg.last_synced).toLocaleString()})` : ''}</p>

      ${locked ? '<p style="color:#856404;margin-top:8px">Read-only in locked mode</p>' : `
      <fieldset style="border:1px solid #dde;border-radius:4px;padding:12px;margin-top:12px">
        <legend style="font-weight:600;font-size:13px">Input</legend>
        <div class="radio-group">
          <label><input type="radio" name="in-type" value="queue" ${inputType === 'queue' ? 'checked' : ''}> Queue</label>
          <label><input type="radio" name="in-type" value="topic" ${inputType === 'topic' ? 'checked' : ''}> Topic</label>
          <label><input type="radio" name="in-type" value="eventhub" ${inputType === 'eventhub' ? 'checked' : ''}> Event Hub</label>
          <label><input type="radio" name="in-type" value="none" ${inputType === 'none' ? 'checked' : ''}> None</label>
        </div>
        <label>Name</label>
        <input type="text" id="msg-in-name" value="${inputName}" placeholder="e.g. orders-in">
        <label>Subscription (topic only)</label>
        <input type="text" id="msg-in-sub" value="${subscription}" placeholder="e.g. utlxe">
        <label>Consumer Group (Event Hub only)</label>
        <input type="text" id="msg-in-cg" value="${consumerGroup}" placeholder="e.g. utlxe">
      </fieldset>

      <fieldset style="border:1px solid #dde;border-radius:4px;padding:12px;margin-top:12px">
        <legend style="font-weight:600;font-size:13px">Output</legend>
        <div class="radio-group">
          <label><input type="radio" name="out-type" value="queue" ${outputType === 'queue' ? 'checked' : ''}> Queue</label>
          <label><input type="radio" name="out-type" value="topic" ${outputType === 'topic' ? 'checked' : ''}> Topic</label>
          <label><input type="radio" name="out-type" value="eventhub" ${outputType === 'eventhub' ? 'checked' : ''}> Event Hub</label>
          <label><input type="radio" name="out-type" value="none" ${outputType === 'none' ? 'checked' : ''}> None</label>
        </div>
        <label>Name</label>
        <input type="text" id="msg-out-name" value="${outputName}" placeholder="e.g. processed-orders">
      </fieldset>

      <div class="btn-group">
        <button class="btn btn-primary" onclick="doSaveMessaging('${name}')">Save (draft)</button>
        <button class="btn" onclick="doSaveAndSync('${name}')">Save & Sync</button>
        <button class="btn btn-danger btn-sm" onclick="doDeleteMessaging('${name}')">Remove</button>
      </div>
      `}
      <div id="msg-result"></div>
    </div>
  `);
}

function buildMessagingBody() {
  const inType = document.querySelector('input[name="in-type"]:checked')?.value;
  const outType = document.querySelector('input[name="out-type"]:checked')?.value;
  const body = {};

  if (inType && inType !== 'none') {
    const name = document.getElementById('msg-in-name').value.trim();
    if (name) {
      const ep = {};
      ep[inType] = name;
      if (inType === 'topic') { const s = document.getElementById('msg-in-sub').value.trim(); if (s) ep.subscription = s; }
      if (inType === 'eventhub') { const c = document.getElementById('msg-in-cg').value.trim(); if (c) ep.consumerGroup = c; }
      body.input = ep;
    }
  }

  if (outType && outType !== 'none') {
    const name = document.getElementById('msg-out-name').value.trim();
    if (name) {
      const ep = {};
      ep[outType] = name;
      body.output = ep;
    }
  }

  return body;
}

async function doSaveMessaging(txName) {
  const body = buildMessagingBody();
  if (!body.input && !body.output) { alert('Configure at least input or output'); return; }
  const resp = await apiPost(`/transformations/${txName}/messaging`, body);
  const div = document.getElementById('msg-result');
  if (resp.status === 200) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">Saved (draft). Sync to push to Dapr.</div>`;
  } else {
    div.innerHTML = `<div class="banner banner-error" style="margin-top:8px">${escapeHtml(resp.data.error || 'Failed')}</div>`;
  }
}

async function doSaveAndSync(txName) {
  await doSaveMessaging(txName);
  const resp = await apiPost(`/transformations/${txName}/sync`, {});
  const div = document.getElementById('msg-result');
  if (resp.status === 200) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">Synced: ${resp.data.message || 'OK'}</div>`;
  }
}

async function doDeleteMessaging(txName) {
  if (!confirm('Remove messaging config?')) return;
  await apiDelete(`/transformations/${txName}/messaging`);
  renderMessaging(txName);
}

// ── Logs ──

async function renderLogs() {
  layout('logs', '<div class="card"><h2>Loading...</h2></div>');
  const [logsResp, levelResp] = await Promise.all([
    apiGet('/logs?limit=100'),
    apiGet('/log/level')
  ]);

  const entries = logsResp.data.entries || [];
  const currentLevel = levelResp.data.level || 'INFO';

  const logLines = entries.map(e => {
    const time = new Date(e.timestamp).toLocaleTimeString();
    return `<div class="log-entry log-${e.level}"><span style="color:#888">${time}</span> <strong>${e.level.padEnd(5)}</strong> ${e.logger}: ${escapeHtml(e.message)}</div>`;
  }).join('');

  layout('logs', `
    <div class="card">
      <h2>Log Level</h2>
      <div style="display:flex;gap:8px;align-items:center">
        <select id="log-level" style="width:auto">
          ${['TRACE','DEBUG','INFO','WARN','ERROR'].map(l => `<option ${l === currentLevel ? 'selected' : ''}>${l}</option>`).join('')}
        </select>
        <label style="margin:0;font-weight:normal">Auto-revert after</label>
        <input type="text" id="log-revert" value="30" style="width:50px"> min
        <button class="btn btn-primary btn-sm" onclick="doSetLogLevel()">Apply</button>
      </div>
      <div id="level-result"></div>
    </div>

    <div class="card">
      <h2>Log Entries (${logsResp.data.total_buffered || 0} buffered, showing ${entries.length})</h2>
      <div style="display:flex;gap:8px;margin-bottom:8px">
        <input type="text" id="log-filter" placeholder="Filter text..." style="flex:1">
        <select id="log-level-filter" style="width:auto">
          <option value="">All levels</option>
          <option>ERROR</option><option>WARN</option><option>INFO</option><option>DEBUG</option>
        </select>
        <button class="btn btn-sm" onclick="doFilterLogs()">Filter</button>
        <button class="btn btn-sm" onclick="renderLogs()">Refresh</button>
        <button class="btn btn-danger btn-sm" onclick="doClearLogs()">Clear</button>
      </div>
      <div id="log-entries" style="max-height:500px;overflow-y:auto;background:#fafafa;padding:8px;border:1px solid #eee;border-radius:4px">
        ${logLines || '<p style="color:#aaa">No log entries</p>'}
      </div>
    </div>
  `);
}

async function doSetLogLevel() {
  const level = document.getElementById('log-level').value;
  const revert = parseInt(document.getElementById('log-revert').value) || null;
  const body = { level };
  if (revert) body.revert_after_minutes = revert;
  const resp = await apiPost('/log/level', body);
  const div = document.getElementById('level-result');
  if (resp.status === 200) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">Level set to ${level}${revert ? ` (auto-revert in ${revert}m)` : ''}</div>`;
    engineInfo.log_level = level;
  } else {
    div.innerHTML = `<div class="banner banner-error" style="margin-top:8px">${escapeHtml(resp.data.error || 'Failed')}</div>`;
  }
}

async function doFilterLogs() {
  const contains = document.getElementById('log-filter').value;
  const level = document.getElementById('log-level-filter').value;
  let url = '/logs?limit=200';
  if (contains) url += `&contains=${encodeURIComponent(contains)}`;
  if (level) url += `&level=${level}`;
  const resp = await apiGet(url);
  const entries = resp.data.entries || [];
  const logLines = entries.map(e => {
    const time = new Date(e.timestamp).toLocaleTimeString();
    return `<div class="log-entry log-${e.level}"><span style="color:#888">${time}</span> <strong>${e.level.padEnd(5)}</strong> ${e.logger}: ${escapeHtml(e.message)}</div>`;
  }).join('');
  document.getElementById('log-entries').innerHTML = logLines || '<p style="color:#aaa">No entries match</p>';
}

async function doClearLogs() {
  if (!confirm('Clear all buffered log entries?')) return;
  await apiDelete('/logs');
  renderLogs();
}

// ── Schemas ──

async function renderSchemas() {
  layout('schemas', '<div class="card"><h2>Loading...</h2></div>');
  const resp = await apiGet('/schemas');
  const schemas = resp.data.schemas || [];
  const locked = engineInfo.mode === 'locked';

  const rows = schemas.map(s => `
    <tr>
      <td><strong>${s.filename}</strong></td>
      <td>${s.size_bytes} B</td>
      <td>${s.uploaded_at || ''}</td>
      <td>
        <button class="btn btn-sm" onclick="doDownloadSchema('${s.filename}')">Download</button>
        ${!locked ? `<button class="btn btn-danger btn-sm" onclick="doDeleteSchema('${s.filename}')">Delete</button>` : ''}
      </td>
    </tr>`).join('');

  layout('schemas', `
    ${!locked ? `
    <div class="card">
      <h2>Upload Schema</h2>
      <label>Filename</label>
      <input type="text" id="schema-filename" placeholder="e.g. order.xsd">
      <label>Content</label>
      <textarea id="schema-content" rows="6" placeholder="Paste schema content..."></textarea>
      <div class="btn-group">
        <button class="btn btn-primary" onclick="doUploadSchema()">Upload</button>
      </div>
      <div id="schema-result"></div>
    </div>` : ''}

    <div class="card">
      <h2>Schemas (${schemas.length})</h2>
      ${schemas.length > 0
        ? `<table><tr><th>Filename</th><th>Size</th><th>Uploaded</th><th></th></tr>${rows}</table>`
        : '<p style="color:#aaa">No schemas uploaded</p>'}
    </div>
  `);
}

async function doUploadSchema() {
  const filename = document.getElementById('schema-filename').value.trim();
  const content = document.getElementById('schema-content').value;
  if (!filename || !content) { alert('Filename and content required'); return; }
  const resp = await fetch(`${API_BASE}/admin/schemas/${filename}`, {
    method: 'POST',
    headers: { 'X-Admin-Key': adminKey, 'Content-Type': 'application/octet-stream' },
    body: content
  });
  const div = document.getElementById('schema-result');
  if (resp.ok) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">Uploaded '${filename}'</div>`;
    setTimeout(() => renderSchemas(), 500);
  } else {
    div.innerHTML = `<div class="banner banner-error" style="margin-top:8px">Upload failed</div>`;
  }
}

async function doDeleteSchema(filename) {
  if (!confirm(`Delete schema '${filename}'?`)) return;
  await apiDelete(`/schemas/${filename}`);
  renderSchemas();
}

async function doDownloadSchema(filename) {
  const resp = await fetch(`${API_BASE}/admin/schemas/${filename}`, { headers: { 'X-Admin-Key': adminKey } });
  const blob = await resp.blob();
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
}

// ── Sync Overview ──

async function renderSync() {
  layout('sync', '<div class="card"><h2>Loading...</h2></div>');
  const [syncResp, daprResp] = await Promise.all([apiGet('/sync'), apiGet('/dapr')]);
  const txs = syncResp.data.transformations || [];
  const dapr = daprResp.data;

  const rows = txs.map(t => {
    const cls = `status-${t.sync_status}`;
    return `<tr>
      <td><strong>${t.name}</strong></td>
      <td class="${cls}">${t.sync_status}</td>
      <td>${t.last_synced ? new Date(t.last_synced).toLocaleString() : ''}</td>
      <td>${(t.pending_changes || []).join(', ')}</td>
      <td>${t.error || ''}</td>
      <td>${t.sync_status === 'draft' ? `<button class="btn btn-primary btn-sm" onclick="doSyncOne('${t.name}')">Sync</button>` : ''}</td>
    </tr>`;
  }).join('');

  const compRows = (dapr.loaded_components || []).map(c =>
    `<tr><td>${c.name}</td><td>${c.type}</td><td>${c.version}</td></tr>`
  ).join('');

  layout('sync', `
    <div class="card">
      <h2>Dapr Integration</h2>
      <table>
        <tr><td style="width:180px"><strong>Mode</strong></td><td>${dapr.mode}</td></tr>
        <tr><td><strong>Sidecar</strong></td><td>${dapr.sidecar_reachable ? `reachable (v${dapr.sidecar_version})` : 'not reachable'}</td></tr>
        <tr><td><strong>Components dir</strong></td><td>${dapr.components_dir || 'not configured'}</td></tr>
        <tr><td><strong>Service Bus NS</strong></td><td>${dapr.servicebus_namespace || 'not configured'}</td></tr>
      </table>
      ${compRows ? `<h2 style="margin-top:16px">Loaded Components</h2><table><tr><th>Name</th><th>Type</th><th>Version</th></tr>${compRows}</table>` : ''}
    </div>

    <div class="card">
      <h2>Sync Status</h2>
      <div style="display:flex;gap:16px;margin-bottom:12px;font-size:13px">
        <span>Synced: <strong>${syncResp.data.synced_count || 0}</strong></span>
        <span>Draft: <strong>${syncResp.data.draft_count || 0}</strong></span>
        <span>Error: <strong>${syncResp.data.error_count || 0}</strong></span>
      </div>
      <table>
        <tr><th>Name</th><th>Status</th><th>Last Synced</th><th>Pending</th><th>Error</th><th></th></tr>
        ${rows || '<tr><td colspan="6" style="color:#aaa">No transformations</td></tr>'}
      </table>
      <div class="btn-group">
        <button class="btn btn-primary" onclick="doSyncAll()">Sync All Drafts</button>
      </div>
      <div id="sync-result"></div>
    </div>
  `);
}

async function doSyncOne(name) {
  const resp = await apiPost(`/transformations/${name}/sync`, {});
  renderSync();
}

async function doSyncAll() {
  const resp = await apiPost('/sync', {});
  const div = document.getElementById('sync-result');
  if (resp.status === 200) {
    div.innerHTML = `<div class="banner banner-success" style="margin-top:8px">${resp.data.message || 'Synced'}</div>`;
    setTimeout(() => renderSync(), 500);
  }
}

// ── Config ──

async function renderConfig() {
  layout('config', '<div class="card"><h2>Loading...</h2></div>');
  const [configResp, infoResp] = await Promise.all([apiGet('/config'), apiGet('/info')]);
  const config = configResp.data;
  const info = infoResp.data;

  layout('config', `
    <div class="card">
      <h2>Engine Configuration</h2>
      <table>
        ${Object.entries(config).map(([k, v]) =>
          `<tr><td style="width:200px"><strong>${k}</strong></td><td>${v ?? '<span style="color:#aaa">not set</span>'}</td></tr>`
        ).join('')}
      </table>
    </div>

    <div class="card">
      <h2>Engine Info</h2>
      <table>
        ${Object.entries(info).map(([k, v]) =>
          `<tr><td style="width:200px"><strong>${k}</strong></td><td>${typeof v === 'object' ? JSON.stringify(v) : v ?? '<span style="color:#aaa">null</span>'}</td></tr>`
        ).join('')}
      </table>
    </div>
  `);
}

// ── Utilities ──

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
