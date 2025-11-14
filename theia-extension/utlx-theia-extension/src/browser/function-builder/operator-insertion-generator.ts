/**
 * Operator Insertion Generator
 *
 * Generates smart context-aware operator insertions based on cursor position.
 */

import { OperatorInfo } from './operators-data';
import { InsertionContext } from './context-analyzer';

/**
 * Generate smart operator insertion based on context
 */
export function generateOperatorInsertion(
    operator: OperatorInfo,
    context: InsertionContext
): string {
    // Check if we have a cursor value (expression at cursor)
    const hasValue = !!(context.cursorValue?.hasValue && context.cursorValue.expression);

    // Unary operators (!, -, +)
    if (operator.unary) {
        return generateUnaryOperatorInsertion(operator, context, hasValue);
    }

    // Binary operators
    return generateBinaryOperatorInsertion(operator, context, hasValue);
}

/**
 * Generate insertion for unary operators (!, -, +)
 */
function generateUnaryOperatorInsertion(
    operator: OperatorInfo,
    context: InsertionContext,
    hasValue: boolean
): string {
    if (hasValue && context.cursorValue?.expression) {
        // Wrap the existing expression
        const expr = context.cursorValue.expression;
        // Add parentheses if expression contains operators (for clarity)
        if (containsOperators(expr)) {
            return `${operator.symbol}(${expr})`;
        }
        return `${operator.symbol}${expr}`;
    }

    // No value - use placeholder
    return `${operator.symbol}value|`;
}

/**
 * Generate insertion for binary operators (+, -, *, /, ==, etc.)
 */
function generateBinaryOperatorInsertion(
    operator: OperatorInfo,
    context: InsertionContext,
    hasValue: boolean
): string {
    // Special handling for specific operators
    if (operator.symbol === '|>') {
        return generatePipeOperatorInsertion(context, hasValue);
    }

    if (operator.symbol === '?.') {
        return generateSafeNavigationInsertion(context, hasValue);
    }

    if (operator.symbol === '??') {
        return generateNullishCoalescingInsertion(context, hasValue);
    }

    if (operator.symbol === '@') {
        return generateAttributeAccessInsertion(context, hasValue);
    }

    if (operator.symbol === '=>') {
        return generateLambdaArrowInsertion(context, hasValue);
    }

    if (operator.symbol === '...') {
        return generateSpreadOperatorInsertion(context, hasValue);
    }

    // Standard binary operators (+, -, *, /, ==, !=, <, >, etc.)
    if (hasValue && context.cursorValue?.expression) {
        // We have an expression at cursor - use it as left operand
        const leftExpr = context.cursorValue.expression;

        // Check if we're at the end of the expression or in the middle
        const afterCursor = context.textAfterCursor.trim();

        if (afterCursor && !afterCursor.match(/^[,;:)\]}]/)) {
            // There's content after cursor that could be right operand
            // Insert operator with spaces
            return ` ${operator.symbol} `;
        }

        // At end or before delimiter - add operator with placeholder
        return ` ${operator.symbol} value|`;
    }

    // No value at cursor - use placeholder for both operands
    return `value1 ${operator.symbol} value2|`;
}

/**
 * Pipe operator insertion (|>)
 */
function generatePipeOperatorInsertion(context: InsertionContext, hasValue: boolean): string {
    if (hasValue && context.cursorValue?.expression) {
        return ` |> function|`;
    }
    return `value |> function|`;
}

/**
 * Safe navigation insertion (?.)
 */
function generateSafeNavigationInsertion(context: InsertionContext, hasValue: boolean): string {
    if (hasValue && context.cursorValue?.expression) {
        // Append to existing expression
        return `?.property|`;
    }
    return `object?.property|`;
}

/**
 * Nullish coalescing insertion (??)
 */
function generateNullishCoalescingInsertion(context: InsertionContext, hasValue: boolean): string {
    if (hasValue && context.cursorValue?.expression) {
        return ` ?? defaultValue|`;
    }
    return `value ?? defaultValue|`;
}

/**
 * Attribute access insertion (@)
 */
function generateAttributeAccessInsertion(context: InsertionContext, hasValue: boolean): string {
    if (hasValue && context.cursorValue?.expression) {
        // Append to existing expression
        return `@attribute|`;
    }
    return `value@attribute|`;
}

/**
 * Lambda arrow insertion (=>)
 */
function generateLambdaArrowInsertion(context: InsertionContext, hasValue: boolean): string {
    if (hasValue && context.cursorValue?.expression) {
        // Use existing expression as parameter
        return ` => expression|`;
    }
    return `param => expression|`;
}

/**
 * Spread operator insertion (...)
 */
function generateSpreadOperatorInsertion(context: InsertionContext, hasValue: boolean): string {
    if (hasValue && context.cursorValue?.expression) {
        // Use existing expression
        return `...${context.cursorValue.expression}`;
    }
    return `...object|`;
}

/**
 * Check if expression contains operators (for determining if parentheses are needed)
 */
function containsOperators(expr: string): boolean {
    // Simple check - look for common operators
    const operators = ['+', '-', '*', '/', '%', '==', '!=', '<', '>', '<=', '>=', '&&', '||', '|>'];
    return operators.some(op => expr.includes(op));
}

/**
 * Generate insertion preview text (for display in footer)
 */
export function generateOperatorInsertionPreview(
    operator: OperatorInfo,
    context: InsertionContext
): string {
    const generated = generateOperatorInsertion(operator, context);

    // Replace cursor placeholder for preview
    return generated.replace(/\|/g, '...');
}
