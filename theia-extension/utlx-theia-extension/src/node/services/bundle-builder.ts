/**
 * Bundle builder (IF03 Phase 3) — produce an engine-loadable `.utlar` from an open
 * `.utlxp` bundle directory.
 *
 * The `.utlar` is a ZIP whose layout the engine reads (EF09; BundleMode.loadUtlar /
 * readManifestFromUtlar):
 *   - manifest.json                                (at the ZIP ROOT)
 *   - transformations/<name>/{<name>.utlx, transform.yaml, …}
 *   - schemas/<file>
 *   - engine.yaml                                  (included for round-trip; ignored by loadUtlar)
 *
 * Dependency-free: a minimal STORE (no-compression) ZIP writer — valid for
 * java.util.zip.ZipInputStream, which the engine uses. (Deflate can be added later.)
 */

import * as fs from 'fs';
import * as path from 'path';

// ── CRC-32 (ZIP requires it per entry) ──────────────────────────────────────
const CRC_TABLE = (() => {
    const t = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) {
            c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
        }
        t[n] = c >>> 0;
    }
    return t;
})();
function crc32(buf: Buffer): number {
    let c = 0xFFFFFFFF;
    for (let i = 0; i < buf.length; i++) {
        c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
    }
    return (c ^ 0xFFFFFFFF) >>> 0;
}

interface ZipEntry { name: string; data: Buffer; crc: number; offset: number; }

/** Minimal STORE-method ZIP writer. `entries` paths use forward slashes. */
function writeZip(entries: Array<{ name: string; data: Buffer }>): Buffer {
    const chunks: Buffer[] = [];
    const records: ZipEntry[] = [];
    let offset = 0;

    // DOS time/date (fixed — content hashing/checksum is the engine's integrity story).
    const dosTime = 0, dosDate = 0x21; // 1980-01-01

    for (const e of entries) {
        const nameBuf = Buffer.from(e.name, 'utf8');
        const crc = crc32(e.data);
        const local = Buffer.alloc(30);
        local.writeUInt32LE(0x04034b50, 0);   // local file header sig
        local.writeUInt16LE(20, 4);            // version needed
        local.writeUInt16LE(0, 6);             // flags
        local.writeUInt16LE(0, 8);             // method = STORE
        local.writeUInt16LE(dosTime, 10);
        local.writeUInt16LE(dosDate, 12);
        local.writeUInt32LE(crc, 14);
        local.writeUInt32LE(e.data.length, 18); // compressed size (== uncompressed)
        local.writeUInt32LE(e.data.length, 22); // uncompressed size
        local.writeUInt16LE(nameBuf.length, 26);
        local.writeUInt16LE(0, 28);            // extra len
        records.push({ name: e.name, data: e.data, crc, offset });
        chunks.push(local, nameBuf, e.data);
        offset += local.length + nameBuf.length + e.data.length;
    }

    const cdStart = offset;
    for (const r of records) {
        const nameBuf = Buffer.from(r.name, 'utf8');
        const cd = Buffer.alloc(46);
        cd.writeUInt32LE(0x02014b50, 0);       // central dir header sig
        cd.writeUInt16LE(20, 4);               // version made by
        cd.writeUInt16LE(20, 6);               // version needed
        cd.writeUInt16LE(0, 8);                // flags
        cd.writeUInt16LE(0, 10);               // method = STORE
        cd.writeUInt16LE(dosTime, 12);
        cd.writeUInt16LE(dosDate, 14);
        cd.writeUInt32LE(r.crc, 16);
        cd.writeUInt32LE(r.data.length, 20);
        cd.writeUInt32LE(r.data.length, 24);
        cd.writeUInt16LE(nameBuf.length, 28);
        cd.writeUInt16LE(0, 30);               // extra
        cd.writeUInt16LE(0, 32);               // comment
        cd.writeUInt16LE(0, 34);               // disk #
        cd.writeUInt16LE(0, 36);               // internal attrs
        cd.writeUInt32LE(0, 38);               // external attrs
        cd.writeUInt32LE(r.offset, 42);        // local header offset
        chunks.push(cd, nameBuf);
        offset += cd.length + nameBuf.length;
    }
    const cdSize = offset - cdStart;

    const eocd = Buffer.alloc(22);
    eocd.writeUInt32LE(0x06054b50, 0);         // end of central dir sig
    eocd.writeUInt16LE(records.length, 8);     // entries on this disk
    eocd.writeUInt16LE(records.length, 10);    // total entries
    eocd.writeUInt32LE(cdSize, 12);
    eocd.writeUInt32LE(cdStart, 16);
    chunks.push(eocd);

    return Buffer.concat(chunks);
}

/** Recursively collect files under `dir`, returning [{zipPath, absPath}] with forward slashes. */
function collect(dir: string, prefix: string, out: Array<{ zip: string; abs: string }>): void {
    if (!fs.existsSync(dir)) { return; }
    for (const name of fs.readdirSync(dir)) {
        const abs = path.join(dir, name);
        const zip = prefix ? `${prefix}/${name}` : name;
        if (fs.statSync(abs).isDirectory()) {
            collect(abs, zip, out);
        } else {
            out.push({ zip, abs });
        }
    }
}

export interface BuildResult {
    outPath: string;
    transformations: string[];
    schemaCount: number;
    entries: number;
}

/**
 * Build `<bundleDir>/../<name>.utlar` from an open bundle directory. `nowIso` is the
 * manifest timestamp (injected for testability).
 */
export function buildUtlar(bundleDir: string, nowIso: string): BuildResult {
    const txDir = path.join(bundleDir, 'transformations');
    if (!fs.existsSync(txDir) || !fs.statSync(txDir).isDirectory()) {
        throw new Error('Not a bundle: no transformations/ directory.');
    }
    const name = path.basename(bundleDir).replace(/\.utlxp$/i, '');

    const files: Array<{ zip: string; abs: string }> = [];
    collect(txDir, 'transformations', files);
    collect(path.join(bundleDir, 'schemas'), 'schemas', files);
    const engineYaml = path.join(bundleDir, 'engine.yaml');
    if (fs.existsSync(engineYaml)) { files.push({ zip: 'engine.yaml', abs: engineYaml }); }

    const transformations = fs.readdirSync(txDir)
        .filter(d => fs.statSync(path.join(txDir, d)).isDirectory())
        .sort();
    const schemaCount = files.filter(f => f.zip.startsWith('schemas/')).length;

    const manifest = {
        name,
        version: '1.0',
        created: nowIso,
        transformations,
        schemas: schemaCount
    };

    const entries: Array<{ name: string; data: Buffer }> = [
        { name: 'manifest.json', data: Buffer.from(JSON.stringify(manifest, null, 2), 'utf8') },
        ...files.map(f => ({ name: f.zip, data: fs.readFileSync(f.abs) }))
    ];

    const outPath = path.join(path.dirname(bundleDir), `${name}.utlar`);
    fs.writeFileSync(outPath, writeZip(entries));

    return { outPath, transformations, schemaCount, entries: entries.length };
}
