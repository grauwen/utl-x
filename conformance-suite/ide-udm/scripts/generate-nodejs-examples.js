"use strict";
/**
 * Generate comprehensive UDM examples using TypeScript/Node.js
 *
 * This creates .udm files that exercise:
 * 1. All UDM types (Scalar, Array, Object, DateTime, Date, LocalDateTime, Time, Binary, Lambda)
 * 2. All metadata scenarios (attributes, metadata maps, element names)
 * 3. USDL language features (%kind, %functions, etc.)
 * 4. Complex nested structures
 * 5. Real-world business scenarios
 *
 * Output files named: *_nodejs-generated.udm
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
Object.defineProperty(exports, "__esModule", { value: true });
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
const udm_core_1 = require("../../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-core");
const udm_language_serializer_1 = require("../../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-language-serializer");
const OUTPUT_DIR = path.join(__dirname, '..', 'examples', 'nodejs-generated');
// Ensure output directory exists
if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}
function writeUDMFile(filename, udm, description) {
    const filepath = path.join(OUTPUT_DIR, filename);
    const content = (0, udm_language_serializer_1.toUDMLanguage)(udm, true, {
        source: `nodejs-generated/${filename}`,
        'parsed-at': new Date().toISOString()
    });
    fs.writeFileSync(filepath, content, 'utf-8');
    console.log(`✅ Generated: ${filename}`);
    console.log(`   ${description}`);
    console.log(`   Size: ${content.length} bytes`);
    console.log('');
}
console.log('='.repeat(60));
console.log('Generating Node.js UDM Examples');
console.log('='.repeat(60));
console.log('');
// ============================================================================
// Example 1: All Scalar Types
// ============================================================================
console.log('--- All Scalar Types ---');
const allScalars = udm_core_1.UDMFactory.object(new Map([
    ['stringValue', udm_core_1.UDMFactory.scalar('Hello, World!')],
    ['intValue', udm_core_1.UDMFactory.scalar(42)],
    ['floatValue', udm_core_1.UDMFactory.scalar(3.14159)],
    ['booleanTrue', udm_core_1.UDMFactory.scalar(true)],
    ['booleanFalse', udm_core_1.UDMFactory.scalar(false)],
    ['nullValue', udm_core_1.UDMFactory.scalar(null)],
    ['negativeNumber', udm_core_1.UDMFactory.scalar(-273.15)],
    ['scientificNotation', udm_core_1.UDMFactory.scalar(6.022e23)],
    ['unicodeString', udm_core_1.UDMFactory.scalar('🚀 Unicode: αβγδ 中文')],
    ['escapedString', udm_core_1.UDMFactory.scalar('Line 1\nLine 2\tTabbed\r\n"Quoted"')]
]));
writeUDMFile('01_all-scalar-types_nodejs-generated.udm', allScalars, 'All scalar value types: string, int, float, boolean, null, with special chars');
// ============================================================================
// Example 2: All DateTime Types
// ============================================================================
console.log('--- All DateTime Types ---');
const allDateTimes = udm_core_1.UDMFactory.object(new Map([
    ['timestamp', udm_core_1.UDMFactory.datetime('2024-11-16T10:30:00Z')],
    ['timestampWithMillis', udm_core_1.UDMFactory.datetime('2024-11-16T10:30:00.123Z')],
    ['date', udm_core_1.UDMFactory.date('2024-11-16')],
    ['localDateTime', udm_core_1.UDMFactory.localdatetime('2024-11-16T10:30:00')],
    ['time', udm_core_1.UDMFactory.time('10:30:00')],
    ['timeWithMillis', udm_core_1.UDMFactory.time('10:30:00.123')],
    ['pastDate', udm_core_1.UDMFactory.date('1969-07-20')],
    ['futureDate', udm_core_1.UDMFactory.date('2099-12-31')],
]));
writeUDMFile('02_all-datetime-types_nodejs-generated.udm', allDateTimes, 'All DateTime variants: DateTime, Date, LocalDateTime, Time with various formats');
// ============================================================================
// Example 3: Arrays (Simple and Nested)
// ============================================================================
console.log('--- Arrays ---');
const arrays = udm_core_1.UDMFactory.object(new Map([
    ['emptyArray', udm_core_1.UDMFactory.array([])],
    ['numberArray', udm_core_1.UDMFactory.array([
            udm_core_1.UDMFactory.scalar(1),
            udm_core_1.UDMFactory.scalar(2),
            udm_core_1.UDMFactory.scalar(3)
        ])],
    ['stringArray', udm_core_1.UDMFactory.array([
            udm_core_1.UDMFactory.scalar('apple'),
            udm_core_1.UDMFactory.scalar('banana'),
            udm_core_1.UDMFactory.scalar('cherry')
        ])],
    ['mixedArray', udm_core_1.UDMFactory.array([
            udm_core_1.UDMFactory.scalar('text'),
            udm_core_1.UDMFactory.scalar(42),
            udm_core_1.UDMFactory.scalar(true),
            udm_core_1.UDMFactory.scalar(null)
        ])],
    ['arrayOfObjects', udm_core_1.UDMFactory.array([
            udm_core_1.UDMFactory.object(new Map([
                ['id', udm_core_1.UDMFactory.scalar(1)],
                ['name', udm_core_1.UDMFactory.scalar('Item 1')]
            ])),
            udm_core_1.UDMFactory.object(new Map([
                ['id', udm_core_1.UDMFactory.scalar(2)],
                ['name', udm_core_1.UDMFactory.scalar('Item 2')]
            ]))
        ])],
    ['nestedArrays', udm_core_1.UDMFactory.array([
            udm_core_1.UDMFactory.array([udm_core_1.UDMFactory.scalar(1), udm_core_1.UDMFactory.scalar(2)]),
            udm_core_1.UDMFactory.array([udm_core_1.UDMFactory.scalar(3), udm_core_1.UDMFactory.scalar(4)])
        ])]
]));
writeUDMFile('03_arrays_nodejs-generated.udm', arrays, 'All array types: empty, primitives, mixed, objects, nested arrays');
// ============================================================================
// Example 4: Objects with Attributes (XML-style)
// ============================================================================
console.log('--- Objects with Attributes ---');
const withAttributes = udm_core_1.UDMFactory.object(new Map([
    ['customer', udm_core_1.UDMFactory.object(new Map([
            ['name', udm_core_1.UDMFactory.scalar('John Doe')],
            ['email', udm_core_1.UDMFactory.scalar('john@example.com')]
        ]), new Map([
            ['id', 'CUST-001'],
            ['type', 'premium'],
            ['status', 'active']
        ]))],
    ['order', udm_core_1.UDMFactory.object(new Map([
            ['items', udm_core_1.UDMFactory.array([
                    udm_core_1.UDMFactory.object(new Map([
                        ['product', udm_core_1.UDMFactory.scalar('Laptop')],
                        ['price', udm_core_1.UDMFactory.scalar(999.99)]
                    ]), new Map([
                        ['sku', 'LAP-123'],
                        ['category', 'electronics']
                    ]))
                ])]
        ]), new Map([
            ['orderId', 'ORD-5678'],
            ['currency', 'USD']
        ]))]
]), new Map([
    ['xmlns', 'http://example.com/schema'],
    ['version', '1.0']
]));
writeUDMFile('04_objects-with-attributes_nodejs-generated.udm', withAttributes, 'Objects with XML-style attributes at multiple levels');
// ============================================================================
// Example 5: Objects with Metadata and Names (XML Elements)
// ============================================================================
console.log('--- Objects with Metadata ---');
const withMetadata = udm_core_1.UDMFactory.object(new Map([
    ['root', udm_core_1.UDMFactory.object(new Map([
            ['child1', udm_core_1.UDMFactory.scalar('value1')],
            ['child2', udm_core_1.UDMFactory.scalar('value2')]
        ]), new Map(), 'RootElement', new Map([
            ['source-file', 'example.xml'],
            ['line-number', '10'],
            ['validation-status', 'valid']
        ]))]
]), new Map(), undefined, new Map([
    ['schema-version', '2.0'],
    ['generated-by', 'nodejs-generator']
]));
writeUDMFile('05_objects-with-metadata_nodejs-generated.udm', withMetadata, 'Objects with element names and metadata maps');
// ============================================================================
// Example 6: Binary Type
// ============================================================================
console.log('--- Binary Type ---');
const binaryData = new Uint8Array([
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A // PNG header
]);
const withBinary = udm_core_1.UDMFactory.object(new Map([
    ['document', udm_core_1.UDMFactory.object(new Map([
            ['filename', udm_core_1.UDMFactory.scalar('image.png')],
            ['mimetype', udm_core_1.UDMFactory.scalar('image/png')],
            ['content', udm_core_1.UDMFactory.binary(binaryData, 'base64', binaryData.length)],
            ['uploadedAt', udm_core_1.UDMFactory.datetime('2024-11-16T10:30:00Z')]
        ]))]
]));
writeUDMFile('06_binary-type_nodejs-generated.udm', withBinary, 'Binary data type with encoding and size metadata');
// ============================================================================
// Example 7: Lambda Type
// ============================================================================
console.log('--- Lambda Type ---');
const withLambda = udm_core_1.UDMFactory.object(new Map([
    ['transformations', udm_core_1.UDMFactory.object(new Map([
            ['mapper', udm_core_1.UDMFactory.lambda('mapFunction', 1)],
            ['filter', udm_core_1.UDMFactory.lambda('filterPredicate', 1)],
            ['reducer', udm_core_1.UDMFactory.lambda('reduceFunction', 2)]
        ]))]
]));
writeUDMFile('07_lambda-type_nodejs-generated.udm', withLambda, 'Lambda function references with id and arity');
// ============================================================================
// Example 8: Real-World Healthcare Claim (Complex Nested)
// ============================================================================
console.log('--- Real-World Healthcare Claim ---');
const healthcareClaim = udm_core_1.UDMFactory.object(new Map([
    ['claimId', udm_core_1.UDMFactory.scalar('CLM-2024-001234')],
    ['submissionDate', udm_core_1.UDMFactory.datetime('2024-11-16T09:15:00Z')],
    ['status', udm_core_1.UDMFactory.scalar('submitted')],
    ['patient', udm_core_1.UDMFactory.object(new Map([
            ['firstName', udm_core_1.UDMFactory.scalar('Jane')],
            ['lastName', udm_core_1.UDMFactory.scalar('Smith')],
            ['dateOfBirth', udm_core_1.UDMFactory.date('1985-03-15')],
            ['memberId', udm_core_1.UDMFactory.scalar('MEM-789456')]
        ]), new Map([
            ['patientId', 'PAT-555'],
            ['relationshipToSubscriber', 'self']
        ]))],
    ['provider', udm_core_1.UDMFactory.object(new Map([
            ['npi', udm_core_1.UDMFactory.scalar('1234567890')],
            ['name', udm_core_1.UDMFactory.scalar('City Hospital')],
            ['address', udm_core_1.UDMFactory.object(new Map([
                    ['street', udm_core_1.UDMFactory.scalar('123 Medical Plaza')],
                    ['city', udm_core_1.UDMFactory.scalar('Boston')],
                    ['state', udm_core_1.UDMFactory.scalar('MA')],
                    ['zip', udm_core_1.UDMFactory.scalar('02101')]
                ]))]
        ]), new Map([['providerId', 'PRV-001']]))],
    ['services', udm_core_1.UDMFactory.array([
            udm_core_1.UDMFactory.object(new Map([
                ['procedureCode', udm_core_1.UDMFactory.scalar('99213')],
                ['description', udm_core_1.UDMFactory.scalar('Office Visit - Level 3')],
                ['serviceDate', udm_core_1.UDMFactory.date('2024-11-10')],
                ['chargeAmount', udm_core_1.UDMFactory.scalar(150.00)],
                ['diagnosisCodes', udm_core_1.UDMFactory.array([
                        udm_core_1.UDMFactory.scalar('J20.9'),
                        udm_core_1.UDMFactory.scalar('R05')
                    ])]
            ]), new Map([
                ['lineNumber', '1'],
                ['placeOfService', '11']
            ])),
            udm_core_1.UDMFactory.object(new Map([
                ['procedureCode', udm_core_1.UDMFactory.scalar('80053')],
                ['description', udm_core_1.UDMFactory.scalar('Comprehensive Metabolic Panel')],
                ['serviceDate', udm_core_1.UDMFactory.date('2024-11-10')],
                ['chargeAmount', udm_core_1.UDMFactory.scalar(85.00)]
            ]), new Map([['lineNumber', '2']]))
        ])],
    ['totalCharges', udm_core_1.UDMFactory.scalar(235.00)],
    ['attachments', udm_core_1.UDMFactory.array([
            udm_core_1.UDMFactory.object(new Map([
                ['filename', udm_core_1.UDMFactory.scalar('lab_results.pdf')],
                ['documentType', udm_core_1.UDMFactory.scalar('lab-report')],
                ['uploadedAt', udm_core_1.UDMFactory.datetime('2024-11-16T09:10:00Z')]
            ]))
        ])]
]), new Map([
    ['xmlns', 'http://hl7.org/fhir'],
    ['version', '4.0.1']
]), 'Claim', new Map([
    ['schema', 'hl7-fhir-claim'],
    ['validationStatus', 'passed']
]));
writeUDMFile('08_healthcare-claim_nodejs-generated.udm', healthcareClaim, 'Real-world healthcare claim with all UDM features: attributes, metadata, arrays, dates, money');
// ============================================================================
// Example 9: Deep Nesting (6+ levels)
// ============================================================================
console.log('--- Deep Nesting ---');
const deepNesting = udm_core_1.UDMFactory.object(new Map([
    ['level1', udm_core_1.UDMFactory.object(new Map([
            ['data1', udm_core_1.UDMFactory.scalar('L1')],
            ['level2', udm_core_1.UDMFactory.object(new Map([
                    ['data2', udm_core_1.UDMFactory.scalar('L2')],
                    ['level3', udm_core_1.UDMFactory.object(new Map([
                            ['data3', udm_core_1.UDMFactory.scalar('L3')],
                            ['level4', udm_core_1.UDMFactory.object(new Map([
                                    ['data4', udm_core_1.UDMFactory.scalar('L4')],
                                    ['level5', udm_core_1.UDMFactory.object(new Map([
                                            ['data5', udm_core_1.UDMFactory.scalar('L5')],
                                            ['level6', udm_core_1.UDMFactory.object(new Map([
                                                    ['data6', udm_core_1.UDMFactory.scalar('L6 - Deep value')]
                                                ]))]
                                        ]))]
                                ]))]
                        ]))]
                ]))]
        ]))]
]));
writeUDMFile('09_deep-nesting_nodejs-generated.udm', deepNesting, '6-level deep nesting to test parser depth handling');
// ============================================================================
// Example 10: All UDM Types Combined
// ============================================================================
console.log('--- All UDM Types Combined ---');
const allTypes = udm_core_1.UDMFactory.object(new Map([
    // Scalars
    ['scalars', udm_core_1.UDMFactory.object(new Map([
            ['string', udm_core_1.UDMFactory.scalar('text')],
            ['number', udm_core_1.UDMFactory.scalar(42)],
            ['boolean', udm_core_1.UDMFactory.scalar(true)],
            ['null', udm_core_1.UDMFactory.scalar(null)]
        ]))],
    // DateTime types
    ['datetimes', udm_core_1.UDMFactory.object(new Map([
            ['timestamp', udm_core_1.UDMFactory.datetime('2024-11-16T10:30:00Z')],
            ['date', udm_core_1.UDMFactory.date('2024-11-16')],
            ['localDateTime', udm_core_1.UDMFactory.localdatetime('2024-11-16T10:30:00')],
            ['time', udm_core_1.UDMFactory.time('10:30:00')]
        ]))],
    // Arrays
    ['arrays', udm_core_1.UDMFactory.array([
            udm_core_1.UDMFactory.scalar(1),
            udm_core_1.UDMFactory.scalar(2),
            udm_core_1.UDMFactory.scalar(3)
        ])],
    // Binary
    ['binary', udm_core_1.UDMFactory.binary(new Uint8Array([0x48, 0x65, 0x6C, 0x6C, 0x6F]))],
    // Lambda
    ['lambda', udm_core_1.UDMFactory.lambda('exampleFunction', 2)],
    // Nested object with attributes
    ['nestedWithAttributes', udm_core_1.UDMFactory.object(new Map([
            ['value', udm_core_1.UDMFactory.scalar('nested')]
        ]), new Map([['id', '123']]))]
]), new Map([['rootAttr', 'value']]), 'AllTypes', new Map([['comprehensive', 'true']]));
writeUDMFile('10_all-types-combined_nodejs-generated.udm', allTypes, 'COMPREHENSIVE: All UDM types, attributes, metadata, and nesting in one file');
console.log('');
console.log('='.repeat(60));
console.log('✅ Node.js UDM generation complete!');
console.log('='.repeat(60));
console.log('');
console.log(`Generated ${10} files in: ${OUTPUT_DIR}`);
