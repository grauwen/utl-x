/**
 * Context Analyzer
 *
 * Analyzes the cursor position in the Monaco editor to determine
 * the appropriate context for smart code insertion.
 *
 * This enables context-aware insertions that generate different code
 * based on where the cursor is positioned.
 */

import * as monaco from '@theia/monaco-editor-core';

/**
 * Insertion context types
 */
export type ContextType =
    | 'lambda-body'        // Inside lambda: map($input, e => |)
    | 'lambda-param'       // Lambda parameter name: map($input, e => e.|)
    | 'function-args'      // Function arguments: filter(|)
    | 'object-field-value' // Object field: { result: | }
    | 'array-element'      // Array element: [|]
    | 'top-level'          // Top level expression
    | 'string-context'     // Inside string (no insertion)
    | 'comment-context';   // Inside comment (no insertion)

/**
 * Context information for smart insertion
 */
export interface InsertionContext {
    type: ContextType;

    // Lambda context info
    lambdaParam?: string;       // e.g., "e" from "map($input, e => |)"
    lambdaInputName?: string;   // e.g., "input" from "map($input, e => |)"

    // Function context info
    functionName?: string;      // e.g., "filter" from "filter(|)"

    // Object context info
    fieldName?: string;         // e.g., "result" from "result: |"

    // Position info
    lineNumber: number;
    column: number;

    // Full line content for debugging
    lineContent: string;
    textBeforeCursor: string;
    textAfterCursor: string;
}

/**
 * Analyze cursor context in the editor
 */
export function analyzeInsertionContext(
    model: monaco.editor.ITextModel,
    position: monaco.Position
): InsertionContext {
    const lineContent = model.getLineContent(position.lineNumber);
    const textBeforeCursor = lineContent.substring(0, position.column - 1);
    const textAfterCursor = lineContent.substring(position.column - 1);

    // Base context
    const context: InsertionContext = {
        type: 'top-level',
        lineNumber: position.lineNumber,
        column: position.column,
        lineContent,
        textBeforeCursor,
        textAfterCursor
    };

    // Check if inside string
    if (isInsideString(textBeforeCursor, textAfterCursor)) {
        return { ...context, type: 'string-context' };
    }

    // Check if inside comment
    if (isInsideComment(textBeforeCursor, textAfterCursor)) {
        return { ...context, type: 'comment-context' };
    }

    // Pattern 1: Lambda parameter access - "e.|" or "employee.|"
    // This is when user types the lambda parameter name followed by dot
    const lambdaParamMatch = textBeforeCursor.match(/\b(\w+)\.$/);
    if (lambdaParamMatch) {
        const paramName = lambdaParamMatch[1];

        // Try to find the lambda definition to extract input name
        const lambdaDefMatch = textBeforeCursor.match(/\$(\w+)\s*,\s*\w+\s*=>/);
        const inputName = lambdaDefMatch ? lambdaDefMatch[1] : undefined;

        return {
            ...context,
            type: 'lambda-param',
            lambdaParam: paramName,
            lambdaInputName: inputName
        };
    }

    // Pattern 2: Lambda body - "=> |" or "=> expr "
    // User is inside lambda body but hasn't typed parameter access yet
    const lambdaBodyMatch = textBeforeCursor.match(/(\w+)\s*=>\s*(?:[\w.$[\]()]+\s*)?$/);
    if (lambdaBodyMatch) {
        const paramName = lambdaBodyMatch[1];

        // Extract input name from lambda definition
        const fullLambdaMatch = textBeforeCursor.match(/\$(\w+)\s*,\s*(\w+)\s*=>/);
        const inputName = fullLambdaMatch ? fullLambdaMatch[1] : undefined;
        const extractedParam = fullLambdaMatch ? fullLambdaMatch[2] : paramName;

        return {
            ...context,
            type: 'lambda-body',
            lambdaParam: extractedParam,
            lambdaInputName: inputName
        };
    }

    // Pattern 3: Function arguments - "functionName(...|" or "functionName(|"
    const functionMatch = textBeforeCursor.match(/(\w+)\([^)]*$/);
    if (functionMatch) {
        const functionName = functionMatch[1];

        // Check if it's a known array function that expects lambda
        const arrayFunctions = ['map', 'filter', 'flatMap', 'groupBy', 'sortBy', 'find', 'reduce'];

        return {
            ...context,
            type: 'function-args',
            functionName
        };
    }

    // Pattern 4: Object field value - "fieldName: |" or "fieldName:|"
    const fieldMatch = textBeforeCursor.match(/(\w+)\s*:\s*$/);
    if (fieldMatch) {
        return {
            ...context,
            type: 'object-field-value',
            fieldName: fieldMatch[1]
        };
    }

    // Pattern 5: Array element - "[|" or "[..., |"
    if (/\[\s*(?:.*,\s*)?$/.test(textBeforeCursor)) {
        return {
            ...context,
            type: 'array-element'
        };
    }

    // Default: Top-level expression
    return context;
}

/**
 * Check if cursor is inside a string literal
 */
function isInsideString(textBefore: string, textAfter: string): boolean {
    // Count unescaped quotes before cursor
    const doubleQuotesBefore = countUnescapedQuotes(textBefore, '"');
    const singleQuotesBefore = countUnescapedQuotes(textBefore, "'");

    // Count quotes after cursor
    const doubleQuotesAfter = countUnescapedQuotes(textAfter, '"');
    const singleQuotesAfter = countUnescapedQuotes(textAfter, "'");

    // Inside string if odd number of quotes before and at least one after
    return (doubleQuotesBefore % 2 === 1 && doubleQuotesAfter > 0) ||
           (singleQuotesBefore % 2 === 1 && singleQuotesAfter > 0);
}

/**
 * Check if cursor is inside a comment
 */
function isInsideComment(textBefore: string, textAfter: string): boolean {
    // Single-line comment: // ...
    if (textBefore.includes('//')) {
        return true;
    }

    // Multi-line comment: /* ... */
    const openComments = (textBefore.match(/\/\*/g) || []).length;
    const closeComments = (textBefore.match(/\*\//g) || []).length;

    return openComments > closeComments;
}

/**
 * Count unescaped quotes in a string
 */
function countUnescapedQuotes(text: string, quoteChar: string): number {
    let count = 0;
    let escaped = false;

    for (const char of text) {
        if (escaped) {
            escaped = false;
            continue;
        }

        if (char === '\\') {
            escaped = true;
        } else if (char === quoteChar) {
            count++;
        }
    }

    return count;
}

/**
 * Get a human-readable description of the context
 */
export function getContextDescription(context: InsertionContext): string {
    switch (context.type) {
        case 'lambda-param':
            return `Lambda parameter access: ${context.lambdaParam}`;
        case 'lambda-body':
            return `Inside lambda body (param: ${context.lambdaParam})`;
        case 'function-args':
            return `Function argument: ${context.functionName}()`;
        case 'object-field-value':
            return `Object field value: ${context.fieldName}`;
        case 'array-element':
            return 'Array element';
        case 'string-context':
            return 'Inside string (no insertion)';
        case 'comment-context':
            return 'Inside comment (no insertion)';
        case 'top-level':
        default:
            return 'Top-level expression';
    }
}
