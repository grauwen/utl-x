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

import * as fs from 'fs';
import * as path from 'path';
import { UDM, UDMFactory } from '../../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-core';
import { toUDMLanguage } from '../../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-language-serializer';

const OUTPUT_DIR = path.join(__dirname, '..', 'examples', 'nodejs-generated');

// Ensure output directory exists
if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}

function writeUDMFile(filename: string, udm: UDM, description: string): void {
    const filepath = path.join(OUTPUT_DIR, filename);
    const content = toUDMLanguage(udm, true, {
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

const allScalars = UDMFactory.object(new Map<string, UDM>([
    ['stringValue', UDMFactory.scalar('Hello, World!')],
    ['intValue', UDMFactory.scalar(42)],
    ['floatValue', UDMFactory.scalar(3.14159)],
    ['booleanTrue', UDMFactory.scalar(true)],
    ['booleanFalse', UDMFactory.scalar(false)],
    ['nullValue', UDMFactory.scalar(null)],
    ['negativeNumber', UDMFactory.scalar(-273.15)],
    ['scientificNotation', UDMFactory.scalar(6.022e23)],
    ['unicodeString', UDMFactory.scalar('🚀 Unicode: αβγδ 中文')],
    ['escapedString', UDMFactory.scalar('Line 1\nLine 2\tTabbed\r\n"Quoted"')]
]));

writeUDMFile(
    '01_all-scalar-types_nodejs-generated.udm',
    allScalars,
    'All scalar value types: string, int, float, boolean, null, with special chars'
);

// ============================================================================
// Example 2: All DateTime Types
// ============================================================================
console.log('--- All DateTime Types ---');

const allDateTimes = UDMFactory.object(new Map<string, UDM>([
    ['timestamp', UDMFactory.datetime('2024-11-16T10:30:00Z')],
    ['timestampWithMillis', UDMFactory.datetime('2024-11-16T10:30:00.123Z')],
    ['date', UDMFactory.date('2024-11-16')],
    ['localDateTime', UDMFactory.localdatetime('2024-11-16T10:30:00')],
    ['time', UDMFactory.time('10:30:00')],
    ['timeWithMillis', UDMFactory.time('10:30:00.123')],
    ['pastDate', UDMFactory.date('1969-07-20')],
    ['futureDate', UDMFactory.date('2099-12-31')],
]));

writeUDMFile(
    '02_all-datetime-types_nodejs-generated.udm',
    allDateTimes,
    'All DateTime variants: DateTime, Date, LocalDateTime, Time with various formats'
);

// ============================================================================
// Example 3: Arrays (Simple and Nested)
// ============================================================================
console.log('--- Arrays ---');

const arrays = UDMFactory.object(new Map<string, UDM>([
    ['emptyArray', UDMFactory.array([])],
    ['numberArray', UDMFactory.array([
        UDMFactory.scalar(1),
        UDMFactory.scalar(2),
        UDMFactory.scalar(3)
    ])],
    ['stringArray', UDMFactory.array([
        UDMFactory.scalar('apple'),
        UDMFactory.scalar('banana'),
        UDMFactory.scalar('cherry')
    ])],
    ['mixedArray', UDMFactory.array([
        UDMFactory.scalar('text'),
        UDMFactory.scalar(42),
        UDMFactory.scalar(true),
        UDMFactory.scalar(null)
    ])],
    ['arrayOfObjects', UDMFactory.array([
        UDMFactory.object(new Map<string, UDM>([
            ['id', UDMFactory.scalar(1)],
            ['name', UDMFactory.scalar('Item 1')]
        ])),
        UDMFactory.object(new Map<string, UDM>([
            ['id', UDMFactory.scalar(2)],
            ['name', UDMFactory.scalar('Item 2')]
        ]))
    ])],
    ['nestedArrays', UDMFactory.array([
        UDMFactory.array([UDMFactory.scalar(1), UDMFactory.scalar(2)]),
        UDMFactory.array([UDMFactory.scalar(3), UDMFactory.scalar(4)])
    ])]
]));

writeUDMFile(
    '03_arrays_nodejs-generated.udm',
    arrays,
    'All array types: empty, primitives, mixed, objects, nested arrays'
);

// ============================================================================
// Example 4: Objects with Attributes (XML-style)
// ============================================================================
console.log('--- Objects with Attributes ---');

const withAttributes = UDMFactory.object(
    new Map<string, UDM>([
        ['customer', UDMFactory.object(
            new Map<string, UDM>([
                ['name', UDMFactory.scalar('John Doe')],
                ['email', UDMFactory.scalar('john@example.com')]
            ]),
            new Map<string, string>([
                ['id', 'CUST-001'],
                ['type', 'premium'],
                ['status', 'active']
            ])
        )],
        ['order', UDMFactory.object(
            new Map<string, UDM>([
                ['items', UDMFactory.array([
                    UDMFactory.object(
                        new Map<string, UDM>([
                            ['product', UDMFactory.scalar('Laptop')],
                            ['price', UDMFactory.scalar(999.99)]
                        ]),
                        new Map<string, string>([
                            ['sku', 'LAP-123'],
                            ['category', 'electronics']
                        ])
                    )
                ])]
            ]),
            new Map<string, string>([
                ['orderId', 'ORD-5678'],
                ['currency', 'USD']
            ])
        )]
    ]),
    new Map<string, string>([
        ['xmlns', 'http://example.com/schema'],
        ['version', '1.0']
    ])
);

writeUDMFile(
    '04_objects-with-attributes_nodejs-generated.udm',
    withAttributes,
    'Objects with XML-style attributes at multiple levels'
);

// ============================================================================
// Example 5: Objects with Metadata and Names (XML Elements)
// ============================================================================
console.log('--- Objects with Metadata ---');

const withMetadata = UDMFactory.object(
    new Map<string, UDM>([
        ['root', UDMFactory.object(
            new Map<string, UDM>([
                ['child1', UDMFactory.scalar('value1')],
                ['child2', UDMFactory.scalar('value2')]
            ]),
            new Map<string, string>(),
            'RootElement',
            new Map<string, string>([
                ['source-file', 'example.xml'],
                ['line-number', '10'],
                ['validation-status', 'valid']
            ])
        )]
    ]),
    new Map<string, string>(),
    undefined,
    new Map<string, string>([
        ['schema-version', '2.0'],
        ['generated-by', 'nodejs-generator']
    ])
);

writeUDMFile(
    '05_objects-with-metadata_nodejs-generated.udm',
    withMetadata,
    'Objects with element names and metadata maps'
);

// ============================================================================
// Example 6: Binary Type
// ============================================================================
console.log('--- Binary Type ---');

const binaryData = new Uint8Array([
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A  // PNG header
]);

const withBinary = UDMFactory.object(new Map<string, UDM>([
    ['document', UDMFactory.object(new Map<string, UDM>([
        ['filename', UDMFactory.scalar('image.png')],
        ['mimetype', UDMFactory.scalar('image/png')],
        ['content', UDMFactory.binary(binaryData, 'base64', binaryData.length)],
        ['uploadedAt', UDMFactory.datetime('2024-11-16T10:30:00Z')]
    ]))]
]));

writeUDMFile(
    '06_binary-type_nodejs-generated.udm',
    withBinary,
    'Binary data type with encoding and size metadata'
);

// ============================================================================
// Example 7: Lambda Type
// ============================================================================
console.log('--- Lambda Type ---');

const withLambda = UDMFactory.object(new Map<string, UDM>([
    ['transformations', UDMFactory.object(new Map<string, UDM>([
        ['mapper', UDMFactory.lambda('mapFunction', 1)],
        ['filter', UDMFactory.lambda('filterPredicate', 1)],
        ['reducer', UDMFactory.lambda('reduceFunction', 2)]
    ]))]
]));

writeUDMFile(
    '07_lambda-type_nodejs-generated.udm',
    withLambda,
    'Lambda function references with id and arity'
);

// ============================================================================
// Example 8: Real-World Healthcare Claim (Complex Nested)
// ============================================================================
console.log('--- Real-World Healthcare Claim ---');

const healthcareClaim = UDMFactory.object(
    new Map<string, UDM>([
        ['claimId', UDMFactory.scalar('CLM-2024-001234')],
        ['submissionDate', UDMFactory.datetime('2024-11-16T09:15:00Z')],
        ['status', UDMFactory.scalar('submitted')],
        ['patient', UDMFactory.object(
            new Map<string, UDM>([
                ['firstName', UDMFactory.scalar('Jane')],
                ['lastName', UDMFactory.scalar('Smith')],
                ['dateOfBirth', UDMFactory.date('1985-03-15')],
                ['memberId', UDMFactory.scalar('MEM-789456')]
            ]),
            new Map<string, string>([
                ['patientId', 'PAT-555'],
                ['relationshipToSubscriber', 'self']
            ])
        )],
        ['provider', UDMFactory.object(
            new Map<string, UDM>([
                ['npi', UDMFactory.scalar('1234567890')],
                ['name', UDMFactory.scalar('City Hospital')],
                ['address', UDMFactory.object(new Map<string, UDM>([
                    ['street', UDMFactory.scalar('123 Medical Plaza')],
                    ['city', UDMFactory.scalar('Boston')],
                    ['state', UDMFactory.scalar('MA')],
                    ['zip', UDMFactory.scalar('02101')]
                ]))]
            ]),
            new Map<string, string>([['providerId', 'PRV-001']])
        )],
        ['services', UDMFactory.array([
            UDMFactory.object(
                new Map<string, UDM>([
                    ['procedureCode', UDMFactory.scalar('99213')],
                    ['description', UDMFactory.scalar('Office Visit - Level 3')],
                    ['serviceDate', UDMFactory.date('2024-11-10')],
                    ['chargeAmount', UDMFactory.scalar(150.00)],
                    ['diagnosisCodes', UDMFactory.array([
                        UDMFactory.scalar('J20.9'),
                        UDMFactory.scalar('R05')
                    ])]
                ]),
                new Map<string, string>([
                    ['lineNumber', '1'],
                    ['placeOfService', '11']
                ])
            ),
            UDMFactory.object(
                new Map<string, UDM>([
                    ['procedureCode', UDMFactory.scalar('80053')],
                    ['description', UDMFactory.scalar('Comprehensive Metabolic Panel')],
                    ['serviceDate', UDMFactory.date('2024-11-10')],
                    ['chargeAmount', UDMFactory.scalar(85.00)]
                ]),
                new Map<string, string>([['lineNumber', '2']])
            )
        ])],
        ['totalCharges', UDMFactory.scalar(235.00)],
        ['attachments', UDMFactory.array([
            UDMFactory.object(new Map<string, UDM>([
                ['filename', UDMFactory.scalar('lab_results.pdf')],
                ['documentType', UDMFactory.scalar('lab-report')],
                ['uploadedAt', UDMFactory.datetime('2024-11-16T09:10:00Z')]
            ]))
        ])]
    ]),
    new Map<string, string>([
        ['xmlns', 'http://hl7.org/fhir'],
        ['version', '4.0.1']
    ]),
    'Claim',
    new Map<string, string>([
        ['schema', 'hl7-fhir-claim'],
        ['validationStatus', 'passed']
    ])
);

writeUDMFile(
    '08_healthcare-claim_nodejs-generated.udm',
    healthcareClaim,
    'Real-world healthcare claim with all UDM features: attributes, metadata, arrays, dates, money'
);

// ============================================================================
// Example 9: Deep Nesting (6+ levels)
// ============================================================================
console.log('--- Deep Nesting ---');

const deepNesting = UDMFactory.object(new Map<string, UDM>([
    ['level1', UDMFactory.object(new Map<string, UDM>([
        ['data1', UDMFactory.scalar('L1')],
        ['level2', UDMFactory.object(new Map<string, UDM>([
            ['data2', UDMFactory.scalar('L2')],
            ['level3', UDMFactory.object(new Map<string, UDM>([
                ['data3', UDMFactory.scalar('L3')],
                ['level4', UDMFactory.object(new Map<string, UDM>([
                    ['data4', UDMFactory.scalar('L4')],
                    ['level5', UDMFactory.object(new Map<string, UDM>([
                        ['data5', UDMFactory.scalar('L5')],
                        ['level6', UDMFactory.object(new Map<string, UDM>([
                            ['data6', UDMFactory.scalar('L6 - Deep value')]
                        ]))]
                    ]))]
                ]))]
            ]))]
        ]))]
    ]))]
]));

writeUDMFile(
    '09_deep-nesting_nodejs-generated.udm',
    deepNesting,
    '6-level deep nesting to test parser depth handling'
);

// ============================================================================
// Example 10: All UDM Types Combined
// ============================================================================
console.log('--- All UDM Types Combined ---');

const allTypes = UDMFactory.object(
    new Map<string, UDM>([
        // Scalars
        ['scalars', UDMFactory.object(new Map<string, UDM>([
            ['string', UDMFactory.scalar('text')],
            ['number', UDMFactory.scalar(42)],
            ['boolean', UDMFactory.scalar(true)],
            ['null', UDMFactory.scalar(null)]
        ]))],
        // DateTime types
        ['datetimes', UDMFactory.object(new Map<string, UDM>([
            ['timestamp', UDMFactory.datetime('2024-11-16T10:30:00Z')],
            ['date', UDMFactory.date('2024-11-16')],
            ['localDateTime', UDMFactory.localdatetime('2024-11-16T10:30:00')],
            ['time', UDMFactory.time('10:30:00')]
        ]))],
        // Arrays
        ['arrays', UDMFactory.array([
            UDMFactory.scalar(1),
            UDMFactory.scalar(2),
            UDMFactory.scalar(3)
        ])],
        // Binary
        ['binary', UDMFactory.binary(new Uint8Array([0x48, 0x65, 0x6C, 0x6C, 0x6F]))],
        // Lambda
        ['lambda', UDMFactory.lambda('exampleFunction', 2)],
        // Nested object with attributes
        ['nestedWithAttributes', UDMFactory.object(
            new Map<string, UDM>([
                ['value', UDMFactory.scalar('nested')]
            ]),
            new Map<string, string>([['id', '123']])
        )]
    ]),
    new Map<string, string>([['rootAttr', 'value']]),
    'AllTypes',
    new Map<string, string>([['comprehensive', 'true']])
);

writeUDMFile(
    '10_all-types-combined_nodejs-generated.udm',
    allTypes,
    'COMPREHENSIVE: All UDM types, attributes, metadata, and nesting in one file'
);

console.log('');
console.log('='.repeat(60));
console.log('✅ Node.js UDM generation complete!');
console.log('='.repeat(60));
console.log('');
console.log(`Generated ${10} files in: ${OUTPUT_DIR}`);
