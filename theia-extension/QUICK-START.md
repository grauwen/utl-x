# UTL-X Theia Extension - Quick Start Guide

**Complete setup and testing guide for the UTL-X IDE**

---

## Prerequisites Checklist

Before starting, ensure you have:

- [x] **Node.js 18+** installed (`node --version`)
- [x] **Yarn** package manager (`yarn --version`)
- [x] **Java 11+** for UTLXD daemon (`java --version`)
- [x] **Git** for cloning (`git --version`)

---

## Step 1: Build UTLXD Daemon

The extension requires the UTLXD daemon server running.

```bash
# Navigate to UTL-X root
cd /path/to/utl-x

# Build the daemon
./gradlew :modules:server:build

# Verify build
ls -lh modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar

# Test daemon
java -jar modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar --version
```

**Expected output**: Version information

---

## Step 2: Install Extension Dependencies

```bash
# Navigate to extension directory
cd theia-extension/utlx-theia-extension

# Install dependencies
yarn install

# This will install:
# - Theia framework (~200MB)
# - TypeScript compiler
# - Development tools
```

---

## Step 3: Build Extension

```bash
# Build the extension
yarn build

# Watch mode (for development)
yarn watch &
```

**Expected output**: Compiled successfully

---

## Step 4: Link Extension Locally

```bash
# Create global link
yarn link

# Verify link
ls -l ~/.yarn-config/link/utlx-theia-extension
```

---

## Step 5: Set Up Test Application

```bash
# Navigate to test app
cd ../browser-app

# Link extension
yarn link utlx-theia-extension

# Install dependencies
yarn install

# This may take 5-10 minutes (downloads Theia framework)
```

---

## Step 6: Start UTLXD Daemon

In a **separate terminal**:

```bash
cd /path/to/utl-x

# Start daemon with LSP and REST API
java -jar modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar start \
  --daemon-lsp \
  --daemon-rest \
  --daemon-rest-port 7779 \
  > /tmp/utlxd.log 2>&1 &

# Note the process ID
echo $! > /tmp/utlxd.pid

# Verify daemon is running
curl http://localhost:7779/api/health
```

**Expected output**: `{"status":"ok","version":"1.0.0",...}`

---

## Step 7: Start Theia Application

```bash
# In browser-app directory
yarn start

# Or with debug logging
yarn start:debug
```

**Expected output**:
```
@theia/core: 1.45.0
Theia app listening on http://localhost:3000
```

---

## Step 8: Open in Browser

1. Open browser: **http://localhost:3000**
2. Wait for Theia to load (~10 seconds)
3. Look for "UTL-X" in menu bar

---

## Step 9: Open UTL-X Workbench

**Method 1**: Menu
- Click **View** → **UTL-X Workbench**

**Method 2**: Command Palette
- Press `Ctrl+Shift+P` (Mac: `Cmd+Shift+P`)
- Type: **UTL-X: Open Workbench**
- Press Enter

**What you should see**:
- Three-panel layout appears
- Mode selector at top
- Input panel (left)
- Output panel (right)
- Status bar at bottom

---

## Step 10: Test Runtime Mode

### Load Sample Data

1. Click **📁 Load** in left panel
2. Navigate to: `theia-extension/examples/data/01-order.json`
3. Click **Open**

### Load Transformation

1. In middle panel (editor), paste this:

```utlx
%utlx 1.0
input json
output json
---
{
  customer: $input.order.customer.firstName + " " + $input.order.customer.lastName,
  total: sum($input.order.items.(price * quantity))
}
```

### Execute

1. Click **▶️ Execute** button
2. See result in right panel

**Expected output**:
```json
{
  "customer": "John Doe",
  "total": 100.0
}
```

---

## Step 11: Test Design-Time Mode

### Switch Mode

1. Click **🎨 Design-Time** button at top

### Load Schema

1. In left panel, select format: **XSD**
2. Paste this schema:

```xml
<?xml version="1.0"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="Order">
    <xs:complexType>
      <xs:attribute name="id" type="xs:string"/>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

### Write Transformation

```utlx
%utlx 1.0
input xml
output json
---
{
  orderId: $input.Order.@id
}
```

### Infer Schema

1. Click **🔍 Infer Schema** button
2. See inferred JSON Schema in right panel

---

## Keyboard Shortcuts

- **Execute**: `Ctrl+Shift+E` (Mac: `Cmd+Shift+E`)
- **Validate**: `Ctrl+Shift+V`
- **Toggle Mode**: `Ctrl+Shift+M`
- **Clear**: `Ctrl+Shift+C`

---

## Troubleshooting

### Issue: "Failed to start UTL-X daemon"

**Solution**:
```bash
# Check if daemon is running
ps aux | grep utlxd

# Check logs
tail -f /tmp/utlxd.log

# Restart daemon
kill $(cat /tmp/utlxd.pid)
# Then restart from Step 6
```

### Issue: "Extension not loading"

**Solution**:
```bash
# Rebuild extension
cd theia-extension/utlx-theia-extension
yarn clean && yarn build

# Rebuild application
cd ../browser-app
rm -rf node_modules .theia
yarn install
yarn build
```

### Issue: "Port 3000 already in use"

**Solution**:
```bash
# Kill existing process
lsof -ti:3000 | xargs kill -9

# Or use different port
yarn start -- --port=3001
```

### Issue: "Cannot find module '@theia/core'"

**Solution**:
```bash
# Reinstall dependencies
cd browser-app
rm -rf node_modules yarn.lock
yarn install
```

---

## Development Workflow

### Watch Mode (Recommended)

Terminal 1: **Extension**
```bash
cd theia-extension/utlx-theia-extension
yarn watch
```

Terminal 2: **Application**
```bash
cd theia-extension/browser-app
yarn watch
```

Terminal 3: **Daemon**
```bash
cd /path/to/utl-x
# Start daemon (see Step 6)
```

Terminal 4: **Server**
```bash
cd theia-extension/browser-app
yarn start
```

### Making Changes

1. Edit TypeScript files in `src/`
2. Watch mode automatically recompiles
3. Refresh browser (`Ctrl+R`)
4. Changes appear immediately

---

## Stopping Everything

```bash
# Stop Theia application
# Press Ctrl+C in terminal

# Stop daemon
kill $(cat /tmp/utlxd.pid)

# Clean up
rm /tmp/utlxd.pid /tmp/utlxd.log
```

---

## Next Steps

### Learn More
- Read `README.md` for detailed documentation
- Check `examples/` for more transformations
- Review `IMPLEMENTATION-SUMMARY.md` for architecture

### Customize
- Modify widgets in `src/browser/`
- Add new commands in `utlx-frontend-contribution.ts`
- Extend LSP features in `language/`

### Contribute
- Report issues on GitHub
- Submit pull requests
- Join discussions

---

## Quick Reference

### File Locations

```
theia-extension/
├── utlx-theia-extension/     # Main extension
│   ├── src/                  # Source code
│   ├── lib/                  # Compiled output
│   └── package.json
├── browser-app/              # Test application
│   ├── .theia/              # Theia config
│   └── package.json
└── examples/                 # Sample files
    ├── transformations/
    ├── data/
    └── schemas/
```

### Important Commands

```bash
# Extension
yarn build          # Compile
yarn watch          # Watch mode
yarn clean          # Clean build

# Application
yarn start          # Start Theia
yarn start:debug    # Debug mode
yarn rebuild        # Clean rebuild

# Daemon
java -jar .../utlxd-1.0.0-SNAPSHOT.jar start --daemon-lsp --daemon-rest
curl http://localhost:7779/api/health
```

### Default Ports

- **Theia IDE**: http://localhost:3000
- **UTLXD REST API**: http://localhost:7779
- **UTLXD LSP**: stdio (process communication)

---

## Success Checklist

After completing this guide, you should be able to:

- [x] Start Theia IDE with UTL-X extension
- [x] See three-panel workbench
- [x] Switch between Design-Time and Runtime modes
- [x] Load input files
- [x] Write UTL-X transformations
- [x] Execute transformations
- [x] View output
- [x] Use keyboard shortcuts

---

**Setup Time**: ~30 minutes
**Last Updated**: 2025-11-05
**Status**: Ready for testing

Need help? Check the troubleshooting section or review the logs.
