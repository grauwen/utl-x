/**
 * Kotlin ↔ TypeScript Round-Trip Bridge
 *
 * This script acts as a bridge for testing UDM round-trip compatibility between:
 * - Kotlin UDM implementation (CLI/backend)
 * - TypeScript UDM implementation (IDE/frontend)
 *
 * The Kotlin test will:
 * 1. Serialize a UDM object to .udm format
 * 2. Send it to this script via stdin
 * 3. This script parses it (TypeScript), serializes it back, sends to stdout
 * 4. Kotlin test parses the result and validates it matches original
 *
 * Run with: node lib/browser/udm/__tests__/kotlin-roundtrip-bridge.js
 */

import * as fs from 'fs';
import { UDMLanguageParser } from '../udm-language-parser';
import { toUDMLanguage } from '../udm-language-serializer';

async function main() {
    // Read .udm content from stdin or file
    let input = '';

    if (process.argv[2]) {
        // Read from file if provided as argument
        const filePath = process.argv[2];
        console.error(`[Bridge] Reading from file: ${filePath}`);
        input = fs.readFileSync(filePath, 'utf-8');
    } else {
        // Read from stdin
        console.error('[Bridge] Reading from stdin...');
        const chunks: Buffer[] = [];
        for await (const chunk of process.stdin) {
            chunks.push(chunk);
        }
        input = Buffer.concat(chunks).toString('utf-8');
    }

    console.error(`[Bridge] Received ${input.length} bytes`);
    console.error(`[Bridge] First 200 chars:\n${input.substring(0, 200)}`);

    try {
        // Parse the UDM content (TypeScript implementation)
        console.error('[Bridge] Parsing UDM with TypeScript parser...');
        const parsed = UDMLanguageParser.parse(input);
        console.error(`[Bridge] Parsed successfully! Type: ${parsed.type}`);

        // Serialize it back (TypeScript implementation)
        console.error('[Bridge] Serializing back to UDM format...');
        const serialized = toUDMLanguage(parsed, true);
        console.error(`[Bridge] Serialized ${serialized.length} bytes`);

        // Output to stdout for Kotlin to read
        process.stdout.write(serialized);

        console.error('[Bridge] ✅ Round-trip completed successfully');
        process.exit(0);
    } catch (error: any) {
        console.error(`[Bridge] ❌ Error: ${error.message}`);
        console.error(error.stack);
        process.exit(1);
    }
}

main();
