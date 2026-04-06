/**
 * Operator information from UTL-X operator registry
 */
export interface OperatorInfo {
  /** Operator symbol (e.g., "+", "==", "|>") */
  symbol: string;

  /** Human-readable name */
  name: string;

  /** Category (Arithmetic, Comparison, Logical, Special) */
  category: string;

  /** Description of what the operator does */
  description: string;

  /** Syntax example showing how to use the operator */
  syntax: string;

  /** Operator precedence (1 = highest, 12 = lowest) */
  precedence: number;

  /** Associativity direction */
  associativity: 'left' | 'right';

  /** Code examples demonstrating usage */
  examples: string[];

  /** Tooltip text for IDE/editor */
  tooltip: string;

  /** Whether this is a unary operator (optional) */
  unary?: boolean;
}

/**
 * Operator registry response from /api/operators
 */
export interface OperatorRegistry {
  /** List of all operators */
  operators: OperatorInfo[];

  /** Registry version */
  version: string;

  /** Total count of operators */
  count: number;
}
