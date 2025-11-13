/**
 * Insertion Generator
 *
 * Generates context-aware code insertions based on:
 * - The selected function or field
 * - The cursor context (where the user is typing)
 * - Available inputs and their UDM structures
 *
 * This provides intelligent code completion that adapts to the user's current context.
 */

import { FunctionInfo } from '../../common/protocol';
import { InsertionContext } from './context-analyzer';

/**
 * Generate smart code insertion for a stdlib function
 */
export function generateFunctionInsertion(
    fn: FunctionInfo,
    context: InsertionContext,
    availableInputs: string[]
): string {
    // Don't insert in strings or comments
    if (context.type === 'string-context' || context.type === 'comment-context') {
        return '';
    }

    // Get primary input name
    const primaryInput = availableInputs[0] || 'input';

    switch (context.type) {
        case 'lambda-param':
            // User typed "e." - suggest function that works on the parameter
            // Example: "e." → "e.FieldName" or "count(e)"
            return generateLambdaParamExpression(fn, context);

        case 'lambda-body':
            // User is inside lambda body - suggest expression using lambda parameter
            // Example: "map($input, e => |)" → "map($input, e => e.FieldName)"
            return generateLambdaBodyExpression(fn, context, primaryInput);

        case 'function-args':
            // User is typing function arguments
            // Example: "filter(|)" → "filter($input, e => e.Status == 'active')"
            return generateFunctionArgExpression(fn, context, primaryInput);

        case 'object-field-value':
            // User is assigning to an object field
            // Example: "result: |" → "result: map($input, e => e.Name)"
            return generateObjectFieldExpression(fn, context, primaryInput);

        case 'array-element':
            // User is inside an array
            // Example: "[|]" → "[map($input, e => e.Name)]"
            return generateArrayElementExpression(fn, context, primaryInput);

        case 'top-level':
        default:
            // Top-level: full function template
            return generateDefaultTemplate(fn, primaryInput);
    }
}

/**
 * Generate insertion for lambda parameter access
 * Example: "e." → suggest "Department" (not a full function call)
 */
function generateLambdaParamExpression(fn: FunctionInfo, context: InsertionContext): string {
    const param = context.lambdaParam || 'e';

    // For simple accessors, just return the field access
    // (This is typically handled by field insertion, not function insertion)

    // For functions that work on values
    const category = fn.category?.toLowerCase() || '';

    if (category.includes('string')) {
        // String function: e.Name → toUpperCase(e.Name)
        return `${fn.name}(${param}.field)`;
    }

    if (category.includes('date') || category.includes('time')) {
        // Date function: e.HireDate → formatDate(e.HireDate, "yyyy-MM-dd")
        return `${fn.name}(${param}.dateField|)`;
    }

    if (category.includes('math') || category.includes('number')) {
        // Math function: e.Salary → round(e.Salary, 2)
        return `${fn.name}(${param}.numberField|)`;
    }

    // Default: function call on parameter field
    return `${fn.name}(${param}.|)`;
}

/**
 * Generate insertion for lambda body
 * Example: "map($input, e => |)" → "e.FieldName"
 */
function generateLambdaBodyExpression(
    fn: FunctionInfo,
    context: InsertionContext,
    primaryInput: string
): string {
    const param = context.lambdaParam || 'e';
    const category = fn.category?.toLowerCase() || '';

    // Array functions inside lambda → nested map/filter
    if (category.includes('array') && fn.name.match(/^(map|filter|flatMap)$/)) {
        return `${fn.name}(${param}.arrayField, x => x.|)`;
    }

    // Aggregation functions on lambda parameter
    if (category.includes('aggregation') || fn.name.match(/^(count|sum|avg|min|max)$/)) {
        return `${fn.name}(${param}.arrayField)`;
    }

    // String functions on field
    if (category.includes('string')) {
        return `${fn.name}(${param}.field|)`;
    }

    // Conditional/logic - if/case
    if (fn.name === 'if') {
        return `if(${param}.status == "active", ${param}.value, 0)`;
    }

    // Default: access lambda parameter field
    return `${param}.|`;
}

/**
 * Generate insertion for function arguments
 * Example: "filter(|)" → "$input, e => e.Status == 'active'"
 */
function generateFunctionArgExpression(
    fn: FunctionInfo,
    context: InsertionContext,
    primaryInput: string
): string {
    const functionName = context.functionName || '';
    const category = fn.category?.toLowerCase() || '';

    // If we're inside an array function that expects lambda, suggest lambda structure
    const arrayFunctions = ['map', 'filter', 'flatMap', 'groupBy', 'sortBy', 'find'];

    if (arrayFunctions.includes(functionName)) {
        // Inside map/filter/etc - suggest lambda
        return `$${primaryInput}, e => ${fn.name}(e.|)`;
    }

    // For other functions, suggest based on function category
    if (category.includes('array')) {
        return `$${primaryInput}, e => e.|`;
    }

    if (category.includes('aggregation')) {
        return `$${primaryInput}`;
    }

    // Default: just the input
    return `$${primaryInput}|`;
}

/**
 * Generate insertion for object field value
 * Example: "result: |" → "map($input, e => e.Name)"
 */
function generateObjectFieldExpression(
    fn: FunctionInfo,
    context: InsertionContext,
    primaryInput: string
): string {
    const category = fn.category?.toLowerCase() || '';

    // Array transformation functions
    if (category.includes('array') && fn.name.match(/^(map|filter|flatMap)$/)) {
        return `${fn.name}($${primaryInput}, e => e.|)`;
    }

    // Aggregation functions
    if (category.includes('aggregation') || fn.name.match(/^(count|sum|avg|min|max)$/)) {
        return `${fn.name}($${primaryInput})`;
    }

    // String functions
    if (category.includes('string')) {
        return `${fn.name}($${primaryInput}[0].field|)`;
    }

    // Default: full template
    return generateDefaultTemplate(fn, primaryInput);
}

/**
 * Generate insertion for array element
 * Example: "[|]" → "map($input, e => e.Name)"
 */
function generateArrayElementExpression(
    fn: FunctionInfo,
    context: InsertionContext,
    primaryInput: string
): string {
    const category = fn.category?.toLowerCase() || '';

    // Array functions are perfect for array literals
    if (category.includes('array')) {
        return `${fn.name}($${primaryInput}, e => e.|)`;
    }

    // Aggregation
    if (category.includes('aggregation')) {
        return `${fn.name}($${primaryInput})`;
    }

    // Default
    return generateDefaultTemplate(fn, primaryInput);
}

/**
 * Generate default function template (top-level)
 */
function generateDefaultTemplate(fn: FunctionInfo, primaryInput: string): string {
    const category = fn.category?.toLowerCase() || '';

    // Array functions need lambda
    if (category.includes('array') && fn.name.match(/^(map|filter|flatMap)$/)) {
        return `${fn.name}($${primaryInput}, e => e.|)`;
    }

    // Aggregation functions take array directly
    if (category.includes('aggregation') || fn.name.match(/^(count|sum|avg|min|max|length)$/)) {
        return `${fn.name}($${primaryInput})`;
    }

    // groupBy needs lambda with key selector
    if (fn.name === 'groupBy') {
        return `${fn.name}($${primaryInput}, e => e.|)`;
    }

    // sortBy needs lambda with field selector
    if (fn.name === 'sortBy') {
        return `${fn.name}($${primaryInput}, e => e.|)`;
    }

    // String functions on field
    if (category.includes('string')) {
        return `${fn.name}($${primaryInput}[0].field|)`;
    }

    // Date/time functions
    if (category.includes('date') || category.includes('time')) {
        return `${fn.name}(|)`;
    }

    // Math functions
    if (category.includes('math') || category.includes('number')) {
        return `${fn.name}(|)`;
    }

    // Conditional
    if (fn.name === 'if') {
        return `if(condition|, trueValue, falseValue)`;
    }

    // Default: function with placeholder
    return `${fn.name}(|)`;
}

/**
 * Generate insertion preview text (for display in footer)
 */
export function generateInsertionPreview(
    fn: FunctionInfo,
    context: InsertionContext,
    availableInputs: string[]
): string {
    const generated = generateFunctionInsertion(fn, context, availableInputs);

    // Replace cursor placeholder for preview
    return generated.replace(/\|/g, '...');
}
