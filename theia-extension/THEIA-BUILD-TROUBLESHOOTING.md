# Theia Build & Installation Troubleshooting

## Common Issues During Theia 1.64.0 Setup

This document covers all the npm/yarn issues encountered during the UTL-X Theia Extension setup and how to resolve them.

---

## Issue 1: Package Not Found in Registry

### Problem
```bash
error Error: https://registry.yarnpkg.com/utlx-theia-extension: Not found
```

### Root Cause
`package.json` referenced `"utlx-theia-extension": "0.1.0"`, and yarn tried to fetch it from npm registry.

### Solution
Use `file:` reference for local packages:

```json
{
  "dependencies": {
    "utlx-theia-extension": "file:../utlx-theia-extension"
  }
}
```

### Prevention
Always use `file:../path` for local workspace dependencies in Theia apps.

---

## Issue 2: Native Modules Not Built

### Problem
```bash
Error: Cannot find module '@vscode/ripgrep'
Error: The module 'node-pty' was compiled against a different Node.js version
```

### Root Cause
Using `--ignore-scripts` flag prevents post-install scripts from running, which are needed to build native modules.

### Affected Modules
- `@vscode/ripgrep` - File search functionality
- `node-pty` - Terminal emulation
- `drivelist` - File system operations
- `keytar` - Secure credential storage

### Solution

**Step 1**: Install without scripts to avoid errors:
```bash
cd browser-app
yarn install --ignore-scripts --network-timeout 100000
```

**Step 2**: Manually run ripgrep post-install:
```bash
cd node_modules/@vscode/ripgrep
npm run postinstall
cd ../..
```

**Step 3**: Rebuild native modules:
```bash
npm rebuild node-pty drivelist keytar
```

### Quick Fix Script
```bash
#!/bin/bash
# rebuild-natives.sh
cd browser-app
yarn install --ignore-scripts --network-timeout 100000
cd node_modules/@vscode/ripgrep && npm run postinstall && cd ../..
npm rebuild node-pty drivelist keytar
cd ..
```

---

## Issue 3: Deprecated @theia/languages Package

### Problem
```bash
error Couldn't find any versions for "@theia/languages" that matches "^1.64.0"
```

### Root Cause
`@theia/languages` was deprecated after version 1.4.0. LSP integration is now done via VS Code extensions.

### Solution
**Remove** `@theia/languages` from both:
- `utlx-theia-extension/package.json`
- `browser-app/package.json`

### Migration Path
If you need LSP integration:

1. **Option A**: Use VS Code extension format
   ```
   vscode-extension/
   ├── package.json
   ├── extension.js
   └── language-server/
   ```

2. **Option B**: Direct LSP client in extension
   ```typescript
   import { LanguageClientFactory } from '@theia/languages/lib/browser';
   // No longer available in 1.64.0+
   ```

3. **Option C**: External LSP server (what UTL-X uses)
   - Run UTLXD as standalone daemon
   - Theia connects via JSON-RPC

---

## Issue 4: Theia Build Fails with "Cannot find module"

### Problem
```bash
ERROR in ./src/browser/utlx-frontend-contribution.ts
Module not found: Error: Can't resolve '@theia/core/lib/browser'
```

### Root Cause
Import paths reorganized in Theia 1.64.0.

### Solution
Update imports:

**Command/Menu (moved to common)**:
```typescript
// OLD
import { Command, CommandContribution } from '@theia/core/lib/browser';

// NEW
import { Command, CommandContribution } from '@theia/core/lib/common';
```

**ReactWidget**:
```typescript
// OLD
import { ReactWidget } from '@theia/core/lib/browser';

// NEW
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
```

See `THEIA-1.64.0-MIGRATION.md` for complete API changes.

---

## Issue 5: TypeScript Errors with DOM APIs

### Problem
```typescript
error TS2339: Property 'value' does not exist on type 'EventTarget'
error TS2304: Cannot find name 'TextEncoder'
```

### Root Cause
Missing DOM library in TypeScript configuration.

### Solution
Update `tsconfig.json`:

```json
{
  "compilerOptions": {
    "lib": ["ES2017", "DOM"]
  }
}
```

---

## Issue 6: Peer Dependency Conflicts

### Problem
```bash
warning " > utlx-theia-extension@0.1.0" has unmet peer dependency "@theia/core@^1.45.0"
```

### Root Cause
Extension and app have mismatched Theia versions.

### Solution
**Ensure all @theia packages use the same version** across:
- `utlx-theia-extension/package.json`
- `browser-app/package.json`

```json
{
  "dependencies": {
    "@theia/core": "1.64.0",
    "@theia/editor": "1.64.0",
    "@theia/filesystem": "1.64.0"
    // ... all must be 1.64.0
  }
}
```

### Verification
```bash
grep -r "@theia/" */package.json | grep -v node_modules
```

All versions should be identical.

---

## Issue 7: Yarn Cache Permissions

### Problem
```bash
error EACCES: permission denied, mkdir '/tmp/npm-cache'
```

### Root Cause
Cache directory permissions or stale cache.

### Solution

**Option A**: Clean cache:
```bash
yarn cache clean
npm cache clean --force
```

**Option B**: Use temporary cache:
```bash
yarn install --cache /tmp/yarn-cache-$(date +%s)
```

**Option C**: Fix permissions:
```bash
sudo chown -R $(whoami) ~/.npm
sudo chown -R $(whoami) ~/.yarn
```

---

## Issue 8: Build Hangs or Times Out

### Problem
```bash
info There appears to be trouble with your network connection. Retrying...
```

### Root Cause
- Slow network
- Large packages (@theia/monaco is ~50MB)
- Default timeout too short

### Solution
Increase network timeout:

```bash
yarn install --network-timeout 100000
# or
npm install --network-timeout 100000
```

For persistent issues:
```bash
# Use legacy peer deps resolution
yarn install --legacy-peer-deps --network-timeout 100000

# Or disable strict SSL
yarn install --network-timeout 100000 --strict-ssl false
```

---

## Issue 9: "gyp ERR!" During Native Module Build

### Problem
```bash
gyp ERR! build error
gyp ERR! stack Error: `make` failed with exit code: 2
```

### Root Cause
Missing build tools or incompatible Python version.

### Solution

**macOS**:
```bash
xcode-select --install
```

**Linux (Ubuntu/Debian)**:
```bash
sudo apt-get install build-essential python3
```

**All platforms**:
```bash
# Ensure Python 3.x
python3 --version

# Set Python for node-gyp
npm config set python python3
```

---

## Issue 10: Webpack Config Generation Fails

### Problem
```bash
Error: Cannot find module './gen-webpack.config.js'
```

### Root Cause
Theia CLI hasn't generated webpack configs yet.

### Solution
```bash
cd browser-app
yarn theia build --mode development
```

This generates:
- `src-gen/` - Generated source files
- `gen-webpack.config.js` - Webpack config
- `gen-webpack.node.config.js` - Backend config

**Note**: These files are ignored by git (in `.gitignore`).

---

## Complete Build Procedure (Verified)

Here's the full sequence that works:

```bash
# 1. Build the extension first
cd utlx-theia-extension
yarn install
yarn build  # or tsc
cd ..

# 2. Install browser-app dependencies
cd browser-app
yarn install --ignore-scripts --network-timeout 100000

# 3. Build native modules
cd node_modules/@vscode/ripgrep
npm run postinstall
cd ../..
npm rebuild node-pty drivelist keytar

# 4. Generate Theia app
cd ..
yarn theia build --mode development

# 5. Verify build
ls -la src-gen/
ls -la lib/

# 6. Start IDE
yarn start
```

---

## Verification Commands

### Check Service Health
```bash
# UTLXD daemon
curl http://localhost:7779/api/health

# MCP server
curl -X POST http://localhost:3001 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# Theia (when running)
curl http://localhost:3000
```

### Check Port Usage
```bash
lsof -i :3000  # Theia
lsof -i :3001  # MCP Server
lsof -i :7779  # UTLXD
```

### Check Processes
```bash
ps aux | grep -E "utlxd|mcp-server|theia"
```

---

## Clean Rebuild (Nuclear Option)

If all else fails:

```bash
cd /Users/magr/data/mapping/github-git/utl-x/theia-extension

# Clean everything
rm -rf browser-app/node_modules browser-app/.theia browser-app/lib browser-app/src-gen
rm -rf utlx-theia-extension/node_modules utlx-theia-extension/lib
rm -rf browser-app/gen-webpack*.js browser-app/webpack.config.js
find . -name "*.js.map" -delete
find . -name "*.d.ts" -not -path "*/node_modules/*" -delete

# Rebuild extension
cd utlx-theia-extension
yarn install
yarn build
cd ..

# Rebuild app
cd browser-app
yarn install --ignore-scripts --network-timeout 100000
cd node_modules/@vscode/ripgrep && npm run postinstall && cd ../..
npm rebuild node-pty drivelist keytar
cd ..
yarn theia build --mode development

# Start
yarn start
```

---

## Platform-Specific Issues

### macOS
- **Issue**: Native modules fail with architecture mismatch (ARM vs x86)
- **Solution**:
  ```bash
  arch -arm64 npm rebuild node-pty  # For M1/M2 Macs
  ```

### Linux
- **Issue**: ENOSPC error during build
- **Solution**: Increase inotify watchers
  ```bash
  echo fs.inotify.max_user_watches=524288 | sudo tee -a /etc/sysctl.conf
  sudo sysctl -p
  ```

### Windows
- **Issue**: Long path errors
- **Solution**: Enable long paths
  ```powershell
  git config --system core.longpaths true
  ```

---

## Debug Logging

Enable verbose logging to diagnose issues:

```bash
# Yarn verbose
yarn install --verbose

# NPM debug
npm install --loglevel verbose

# Theia debug
yarn start --loglevel=debug

# Check Theia backend logs
tail -f /tmp/utlxd.log
tail -f /tmp/mcp-server.log
```

---

## Key Takeaways

1. **Always install extension before app** - The app depends on the built extension
2. **Native modules need manual rebuilding** - Don't rely on post-install hooks
3. **@theia/languages is deprecated** - Remove it entirely for 1.64.0+
4. **Use file: references for local packages** - Not version numbers
5. **All @theia packages must match versions** - No mixing 1.45.0 and 1.64.0
6. **Increase network timeout** - Theia packages are large
7. **Keep .gitignore tight** - Avoid committing build artifacts

---

**Last Updated**: 2025-11-05
**Theia Version**: 1.64.0 (Community Release 2025-08)
**Node Version**: 18.x or 20.x recommended
