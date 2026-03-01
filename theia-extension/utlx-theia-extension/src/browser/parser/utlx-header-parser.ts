/**
 * UTLX Header Parser
 *
 * Parses UTLX transformation headers to extract input/output specifications.
 * Supports all UTLX header formats including parameters and options.
 */

/**
 * Parsed input specification
 */
export interface ParsedInput {
    name: string;
    format: string;
    csvHeaders?: boolean;
    csvDelimiter?: string;
    xmlArrays?: string[];
}

/**
 * Parsed output specification
 */
export interface ParsedOutput {
    format: string;
    csvHeaders?: boolean;
    csvDelimiter?: string;
    csvBom?: boolean;
    xmlEncoding?: string;
    odataMetadata?: 'minimal' | 'full' | 'none';
    odataContext?: string;
    odataWrapCollection?: boolean;
}

/**
 * Result of parsing UTLX headers
 */
export interface ParsedUTLXHeader {
    valid: boolean;
    version?: string;
    inputs: ParsedInput[];
    output: ParsedOutput;
    errors: string[];
}

/**
 * Regular expression patterns for parsing UTLX headers
 */
const PATTERNS = {
    // %utlx 1.0
    VERSION: /^%utlx\s+(\d+\.\d+)/,

    // ---
    SEPARATOR: /^---\s*$/,

    // input name format OR input name format {options}
    // Allow hyphens in input names: [a-zA-Z_][a-zA-Z0-9_-]*
    // Note: odata must appear before json/xml to prevent partial match on 'o' in input names
    SINGLE_INPUT: /^input\s+([a-zA-Z_][a-zA-Z0-9_-]*)\s+(csv|odata|osch|tsch|json|xml|yaml|xsd|jsch|avro|proto)(?:\s+(\{[^}]+\}))?/,

    // input: name1 format1, name2 format2, ...
    MULTI_INPUT: /^input:\s*(.+)$/,

    // Individual input in multi-input: name format {options}
    // Allow hyphens in input names: [a-zA-Z_][a-zA-Z0-9_-]*
    INPUT_PART: /([a-zA-Z_][a-zA-Z0-9_-]*)\s+(csv|odata|osch|tsch|json|xml|yaml|xsd|jsch|avro|proto)(?:\s+(\{[^}]+\}))?/g,

    // output format OR output format {options}
    OUTPUT: /^output\s+(csv|odata|osch|tsch|json|xml|yaml|xsd|jsch|avro|proto)(?:\s+(\{[^}]+\}))?/,

    // CSV options
    CSV_HEADERS: /headers:\s*(true|false)/,
    CSV_DELIMITER: /delimiter:\s*"([^"]+)"/,
    CSV_BOM: /bom:\s*(true|false)/,

    // XML options
    XML_ENCODING: /encoding:\s*"([^"]+)"/,
    XML_ARRAYS: /arrays:\s*\[([^\]]+)\]/,

    // OData JSON options
    ODATA_METADATA: /metadata:\s*"(minimal|full|none)"/,
    ODATA_CONTEXT: /context:\s*"([^"]+)"/,
    ODATA_WRAP_COLLECTION: /wrapCollection:\s*(true|false)/
};

/**
 * Parse CSV/XML options from parameter block
 * Example: {headers: false, delimiter: ";"} => {csvHeaders: false, csvDelimiter: ";"}
 */
function parseOptions(optionsStr: string | undefined): {
    csvHeaders?: boolean;
    csvDelimiter?: string;
    csvBom?: boolean;
    xmlEncoding?: string;
    xmlArrays?: string[];
    odataMetadata?: 'minimal' | 'full' | 'none';
    odataContext?: string;
    odataWrapCollection?: boolean;
} {
    if (!optionsStr) {
        return {};
    }

    const options: any = {};

    // Parse CSV headers
    const headersMatch = optionsStr.match(PATTERNS.CSV_HEADERS);
    if (headersMatch) {
        options.csvHeaders = headersMatch[1] === 'true';
    }

    // Parse CSV delimiter
    const delimiterMatch = optionsStr.match(PATTERNS.CSV_DELIMITER);
    if (delimiterMatch) {
        // Handle escape sequences
        options.csvDelimiter = delimiterMatch[1]
            .replace(/\\t/g, '\t')
            .replace(/\\n/g, '\n')
            .replace(/\\r/g, '\r');
    }

    // Parse CSV BOM
    const bomMatch = optionsStr.match(PATTERNS.CSV_BOM);
    if (bomMatch) {
        options.csvBom = bomMatch[1] === 'true';
    }

    // Parse XML encoding
    const encodingMatch = optionsStr.match(PATTERNS.XML_ENCODING);
    if (encodingMatch) {
        options.xmlEncoding = encodingMatch[1];
    }

    // Parse XML arrays
    const arraysMatch = optionsStr.match(PATTERNS.XML_ARRAYS);
    if (arraysMatch) {
        options.xmlArrays = arraysMatch[1]
            .split(',')
            .map(s => s.trim().replace(/^["']|["']$/g, ''));
    }

    // Parse OData metadata level
    const metadataMatch = optionsStr.match(PATTERNS.ODATA_METADATA);
    if (metadataMatch) {
        options.odataMetadata = metadataMatch[1] as 'minimal' | 'full' | 'none';
    }

    // Parse OData context URL
    const contextMatch = optionsStr.match(PATTERNS.ODATA_CONTEXT);
    if (contextMatch) {
        options.odataContext = contextMatch[1];
    }

    // Parse OData wrapCollection
    const wrapMatch = optionsStr.match(PATTERNS.ODATA_WRAP_COLLECTION);
    if (wrapMatch) {
        options.odataWrapCollection = wrapMatch[1] === 'true';
    }

    return options;
}

/**
 * Parse a single input line
 * Example: input employees csv {headers: false}
 */
function parseSingleInput(line: string): ParsedInput | null {
    const match = line.match(PATTERNS.SINGLE_INPUT);
    if (!match) {
        return null;
    }

    const [, name, format, optionsStr] = match;
    const options = parseOptions(optionsStr);

    return {
        name,
        format,
        ...options
    };
}

/**
 * Parse multiple inputs line
 * Example: input: employees csv, orders json, products xml {arrays: ["Product"]}
 */
function parseMultiInput(line: string): ParsedInput[] | null {
    const match = line.match(PATTERNS.MULTI_INPUT);
    if (!match) {
        return null;
    }

    const inputsStr = match[1];
    const inputs: ParsedInput[] = [];

    // Use INPUT_PART regex to match each input
    let inputMatch;
    while ((inputMatch = PATTERNS.INPUT_PART.exec(inputsStr)) !== null) {
        const [, name, format, optionsStr] = inputMatch;
        const options = parseOptions(optionsStr);

        inputs.push({
            name,
            format,
            ...options
        });
    }

    return inputs.length > 0 ? inputs : null;
}

/**
 * Parse output line
 * Example: output csv {headers: false, delimiter: ";"}
 */
function parseOutput(line: string): ParsedOutput | null {
    const match = line.match(PATTERNS.OUTPUT);
    if (!match) {
        return null;
    }

    const [, format, optionsStr] = match;
    const options = parseOptions(optionsStr);

    return {
        format,
        ...options
    };
}

/**
 * Parse UTLX headers from content
 *
 * @param content Full UTLX file content
 * @returns Parsed header information
 */
export function parseUTLXHeaders(content: string): ParsedUTLXHeader {
    const result: ParsedUTLXHeader = {
        valid: false,
        inputs: [],
        output: { format: 'json' },
        errors: []
    };

    // Split into lines and process only first ~20 lines (headers section)
    const lines = content.split('\n').slice(0, 20);
    let separatorFound = false;

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();

        // Skip empty lines
        if (!line) {
            continue;
        }

        // Check for separator (end of headers)
        if (PATTERNS.SEPARATOR.test(line)) {
            separatorFound = true;
            break;
        }

        // Parse version
        if (line.startsWith('%utlx')) {
            const versionMatch = line.match(PATTERNS.VERSION);
            if (versionMatch) {
                result.version = versionMatch[1];
            } else {
                result.errors.push(`Invalid version line: ${line}`);
            }
            continue;
        }

        // Parse input (single or multiple)
        if (line.startsWith('input:')) {
            // Multiple inputs
            const inputs = parseMultiInput(line);
            if (inputs) {
                result.inputs = inputs;
            } else {
                result.errors.push(`Invalid multi-input line: ${line}`);
            }
            continue;
        } else if (line.startsWith('input ')) {
            // Single input
            const input = parseSingleInput(line);
            if (input) {
                result.inputs = [input];
            } else {
                result.errors.push(`Invalid input line: ${line}`);
            }
            continue;
        }

        // Parse output
        if (line.startsWith('output ')) {
            const output = parseOutput(line);
            if (output) {
                result.output = output;
            } else {
                result.errors.push(`Invalid output line: ${line}`);
            }
            continue;
        }
    }

    // Validation: Must have version, separator, at least one input, and output
    if (!result.version) {
        result.errors.push('Missing %utlx version line');
    }

    if (!separatorFound) {
        result.errors.push('Missing --- separator');
    }

    if (result.inputs.length === 0) {
        result.errors.push('No input declarations found');
    }

    // Mark as valid if we have minimum required elements
    result.valid = result.version !== undefined &&
                   separatorFound &&
                   result.inputs.length > 0 &&
                   result.errors.length === 0;

    return result;
}
