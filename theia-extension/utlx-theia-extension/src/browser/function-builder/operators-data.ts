/**
 * UTLX Operators Data
 *
 * Complete list of UTLX operators with metadata for documentation and smart insertion.
 */

export interface OperatorInfo {
    symbol: string;           // e.g., "+"
    name: string;            // e.g., "Addition"
    category: string;        // "Arithmetic", "Comparison", "Logical", "Special"
    description: string;     // Full description
    syntax: string;          // e.g., "value1 + value2"
    precedence: number;      // Operator precedence level (higher = evaluated first)
    associativity: 'left' | 'right';  // Associativity
    examples: string[];      // Usage examples
    tooltip: string;         // Brief tooltip text
    unary?: boolean;         // True if unary operator
}

/**
 * All UTLX operators organized by category
 */
export const UTLX_OPERATORS: OperatorInfo[] = [
    // ========== Arithmetic Operators ==========
    {
        symbol: '+',
        name: 'Addition',
        category: 'Arithmetic',
        description: 'Adds two numbers or concatenates strings.',
        syntax: 'value1 + value2',
        precedence: 5,
        associativity: 'left',
        tooltip: 'Addition / String concatenation',
        examples: [
            '10 + 5  // 15',
            '"Hello" + " World"  // "Hello World"',
            '$input[0].price + $input[1].price'
        ]
    },
    {
        symbol: '-',
        name: 'Subtraction',
        category: 'Arithmetic',
        description: 'Subtracts the second number from the first, or negates a number (unary).',
        syntax: 'value1 - value2',
        precedence: 5,
        associativity: 'left',
        tooltip: 'Subtraction / Negation',
        examples: [
            '10 - 5  // 5',
            '-42  // -42',
            '$input[0].total - $input[0].discount'
        ]
    },
    {
        symbol: '*',
        name: 'Multiplication',
        category: 'Arithmetic',
        description: 'Multiplies two numbers.',
        syntax: 'value1 * value2',
        precedence: 4,
        associativity: 'left',
        tooltip: 'Multiplication',
        examples: [
            '10 * 5  // 50',
            '$input[0].quantity * $input[0].price',
            '3.14 * radius * radius'
        ]
    },
    {
        symbol: '/',
        name: 'Division',
        category: 'Arithmetic',
        description: 'Divides the first number by the second.',
        syntax: 'value1 / value2',
        precedence: 4,
        associativity: 'left',
        tooltip: 'Division',
        examples: [
            '10 / 5  // 2',
            '$input[0].total / $input[0].quantity',
            '100 / 3  // 33.333...'
        ]
    },
    {
        symbol: '%',
        name: 'Modulo',
        category: 'Arithmetic',
        description: 'Returns the remainder of division.',
        syntax: 'value1 % value2',
        precedence: 4,
        associativity: 'left',
        tooltip: 'Modulo / Remainder',
        examples: [
            '10 % 3  // 1',
            '100 % 7  // 2',
            '$input[0].id % 2 == 0  // Check if even'
        ]
    },
    {
        symbol: '**',
        name: 'Exponentiation',
        category: 'Arithmetic',
        description: 'Raises the first number to the power of the second.',
        syntax: 'base ** exponent',
        precedence: 3,
        associativity: 'right',
        tooltip: 'Exponentiation / Power',
        examples: [
            '2 ** 3  // 8',
            '10 ** 2  // 100',
            'radius ** 2 * 3.14159  // Circle area'
        ]
    },

    // ========== Comparison Operators ==========
    {
        symbol: '==',
        name: 'Equal',
        category: 'Comparison',
        description: 'Checks if two values are equal.',
        syntax: 'value1 == value2',
        precedence: 7,
        associativity: 'left',
        tooltip: 'Equality comparison',
        examples: [
            '$input[0].status == "active"',
            'count == 0',
            '$input[0].id == $input[1].id'
        ]
    },
    {
        symbol: '!=',
        name: 'Not Equal',
        category: 'Comparison',
        description: 'Checks if two values are not equal.',
        syntax: 'value1 != value2',
        precedence: 7,
        associativity: 'left',
        tooltip: 'Inequality comparison',
        examples: [
            '$input[0].status != "deleted"',
            'result != null',
            '$input[0].type != $input[1].type'
        ]
    },
    {
        symbol: '<',
        name: 'Less Than',
        category: 'Comparison',
        description: 'Checks if the first value is less than the second.',
        syntax: 'value1 < value2',
        precedence: 6,
        associativity: 'left',
        tooltip: 'Less than',
        examples: [
            '$input[0].price < 100',
            'age < 18',
            '$input[0].date < parseDate("2024-01-01")'
        ]
    },
    {
        symbol: '>',
        name: 'Greater Than',
        category: 'Comparison',
        description: 'Checks if the first value is greater than the second.',
        syntax: 'value1 > value2',
        precedence: 6,
        associativity: 'left',
        tooltip: 'Greater than',
        examples: [
            '$input[0].price > 100',
            'age > 65',
            '$input[0].score > threshold'
        ]
    },
    {
        symbol: '<=',
        name: 'Less Than or Equal',
        category: 'Comparison',
        description: 'Checks if the first value is less than or equal to the second.',
        syntax: 'value1 <= value2',
        precedence: 6,
        associativity: 'left',
        tooltip: 'Less than or equal',
        examples: [
            '$input[0].price <= 100',
            'age <= 18',
            '$input[0].count <= maxCount'
        ]
    },
    {
        symbol: '>=',
        name: 'Greater Than or Equal',
        category: 'Comparison',
        description: 'Checks if the first value is greater than or equal to the second.',
        syntax: 'value1 >= value2',
        precedence: 6,
        associativity: 'left',
        tooltip: 'Greater than or equal',
        examples: [
            '$input[0].price >= 100',
            'age >= 18',
            '$input[0].score >= passingGrade'
        ]
    },

    // ========== Logical Operators ==========
    {
        symbol: '&&',
        name: 'Logical AND',
        category: 'Logical',
        description: 'Returns true if both operands are true.',
        syntax: 'condition1 && condition2',
        precedence: 8,
        associativity: 'left',
        tooltip: 'Logical AND',
        examples: [
            '$input[0].status == "active" && $input[0].price > 0',
            'age >= 18 && hasLicense',
            'x > 0 && x < 100'
        ]
    },
    {
        symbol: '||',
        name: 'Logical OR',
        category: 'Logical',
        description: 'Returns true if at least one operand is true.',
        syntax: 'condition1 || condition2',
        precedence: 9,
        associativity: 'left',
        tooltip: 'Logical OR',
        examples: [
            '$input[0].status == "active" || $input[0].status == "pending"',
            'age < 18 || age > 65',
            'isAdmin || isOwner'
        ]
    },
    {
        symbol: '!',
        name: 'Logical NOT',
        category: 'Logical',
        description: 'Negates a boolean value.',
        syntax: '!condition',
        precedence: 2,
        associativity: 'right',
        tooltip: 'Logical NOT / Negation',
        unary: true,
        examples: [
            '!$input[0].isDeleted',
            '!(x > 10)',
            '!isEmpty($input)'
        ]
    },

    // ========== Special Operators ==========
    {
        symbol: '|>',
        name: 'Pipe',
        category: 'Special',
        description: 'Pipes the result of one expression into another function. Enables functional composition.',
        syntax: 'value |> function',
        precedence: 12,
        associativity: 'right',
        tooltip: 'Pipe operator for functional composition',
        examples: [
            '$input |> filter(x => x.active) |> map(x => x.name)',
            'parseDate($input[0].date) |> formatDate("yyyy-MM-dd")',
            '$input |> groupBy(x => x.category) |> count'
        ]
    },
    {
        symbol: '?.',
        name: 'Safe Navigation',
        category: 'Special',
        description: 'Safely accesses a property. Returns null if the object is null/undefined instead of throwing an error.',
        syntax: 'object?.property',
        precedence: 1,
        associativity: 'left',
        tooltip: 'Safe navigation / Optional chaining',
        examples: [
            '$input[0]?.address?.city',
            'user?.profile?.email',
            '$input?.data?.items'
        ]
    },
    {
        symbol: '??',
        name: 'Nullish Coalescing',
        category: 'Special',
        description: 'Returns the right operand if the left operand is null or undefined, otherwise returns the left operand.',
        syntax: 'value ?? defaultValue',
        precedence: 10,
        associativity: 'left',
        tooltip: 'Nullish coalescing / Default value',
        examples: [
            '$input[0].name ?? "Unknown"',
            'user.age ?? 0',
            '$input[0]?.email ?? "no-email@example.com"'
        ]
    },
    {
        symbol: '=>',
        name: 'Lambda Arrow',
        category: 'Special',
        description: 'Defines a lambda (anonymous function). Used in map, filter, and other higher-order functions.',
        syntax: 'param => expression',
        precedence: 1,
        associativity: 'right',
        tooltip: 'Lambda / Arrow function',
        examples: [
            'map($input, x => x.name)',
            'filter($input, item => item.price > 100)',
            'sortBy($input, e => e.date)'
        ]
    },
    {
        symbol: '...',
        name: 'Spread',
        category: 'Special',
        description: 'Spreads properties from one object into another object literal.',
        syntax: '{ ...object, newProp: value }',
        precedence: 1,
        associativity: 'left',
        tooltip: 'Object spread operator',
        examples: [
            '{ ...user, status: "active" }',
            '{ name: "John", ...otherFields }',
            '{ ...$input[0], updatedAt: now() }'
        ]
    }
];

/**
 * Get operators grouped by category
 */
export function getOperatorsByCategory(): Map<string, OperatorInfo[]> {
    const grouped = new Map<string, OperatorInfo[]>();

    for (const op of UTLX_OPERATORS) {
        if (!grouped.has(op.category)) {
            grouped.set(op.category, []);
        }
        grouped.get(op.category)!.push(op);
    }

    // Sort categories in logical order
    const orderedCategories = ['Arithmetic', 'Comparison', 'Logical', 'Special'];
    const orderedMap = new Map<string, OperatorInfo[]>();

    for (const category of orderedCategories) {
        if (grouped.has(category)) {
            orderedMap.set(category, grouped.get(category)!);
        }
    }

    return orderedMap;
}
