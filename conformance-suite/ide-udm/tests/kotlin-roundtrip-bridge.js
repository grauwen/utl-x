"use strict";
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
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __asyncValues = (this && this.__asyncValues) || function (o) {
    if (!Symbol.asyncIterator) throw new TypeError("Symbol.asyncIterator is not defined.");
    var m = o[Symbol.asyncIterator], i;
    return m ? m.call(o) : (o = typeof __values === "function" ? __values(o) : o[Symbol.iterator](), i = {}, verb("next"), verb("throw"), verb("return"), i[Symbol.asyncIterator] = function () { return this; }, i);
    function verb(n) { i[n] = o[n] && function (v) { return new Promise(function (resolve, reject) { v = o[n](v), settle(resolve, reject, v.done, v.value); }); }; }
    function settle(resolve, reject, d, v) { Promise.resolve(v).then(function(v) { resolve({ value: v, done: d }); }, reject); }
};
Object.defineProperty(exports, "__esModule", { value: true });
const fs = __importStar(require("fs"));
const udm_language_parser_1 = require("../udm-language-parser");
const udm_language_serializer_1 = require("../udm-language-serializer");
async function main() {
    var _a, e_1, _b, _c;
    // Read .udm content from stdin or file
    let input = '';
    if (process.argv[2]) {
        // Read from file if provided as argument
        const filePath = process.argv[2];
        console.error(`[Bridge] Reading from file: ${filePath}`);
        input = fs.readFileSync(filePath, 'utf-8');
    }
    else {
        // Read from stdin
        console.error('[Bridge] Reading from stdin...');
        const chunks = [];
        try {
            for (var _d = true, _e = __asyncValues(process.stdin), _f; _f = await _e.next(), _a = _f.done, !_a; _d = true) {
                _c = _f.value;
                _d = false;
                const chunk = _c;
                chunks.push(chunk);
            }
        }
        catch (e_1_1) { e_1 = { error: e_1_1 }; }
        finally {
            try {
                if (!_d && !_a && (_b = _e.return)) await _b.call(_e);
            }
            finally { if (e_1) throw e_1.error; }
        }
        input = Buffer.concat(chunks).toString('utf-8');
    }
    console.error(`[Bridge] Received ${input.length} bytes`);
    console.error(`[Bridge] First 200 chars:\n${input.substring(0, 200)}`);
    try {
        // Parse the UDM content (TypeScript implementation)
        console.error('[Bridge] Parsing UDM with TypeScript parser...');
        const parsed = udm_language_parser_1.UDMLanguageParser.parse(input);
        console.error(`[Bridge] Parsed successfully! Type: ${parsed.type}`);
        // Serialize it back (TypeScript implementation)
        console.error('[Bridge] Serializing back to UDM format...');
        const serialized = (0, udm_language_serializer_1.toUDMLanguage)(parsed, true);
        console.error(`[Bridge] Serialized ${serialized.length} bytes`);
        // Output to stdout for Kotlin to read
        process.stdout.write(serialized);
        console.error('[Bridge] ✅ Round-trip completed successfully');
        process.exit(0);
    }
    catch (error) {
        console.error(`[Bridge] ❌ Error: ${error.message}`);
        console.error(error.stack);
        process.exit(1);
    }
}
main();
//# sourceMappingURL=kotlin-roundtrip-bridge.js.map