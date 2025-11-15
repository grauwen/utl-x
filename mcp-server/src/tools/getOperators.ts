/**
 * Tool: get_operators
 *
 * Retrieves UTL-X operator registry with precedence, associativity, and examples
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { OperatorInfo } from '../types/operator';
import { Logger } from 'winston';
import { z } from 'zod';

export const getOperatorsTool: Tool = {
  name: 'get_operators',
  description: 'Retrieves UTL-X operator registry with symbols, precedence, associativity, and usage examples. Optionally filter by category.',
  inputSchema: {
    type: 'object',
    properties: {
      category: {
        type: 'string',
        description: 'Optional category filter: Arithmetic, Comparison, Logical, Special',
      },
    },
    required: [],
  },
};

const GetOperatorsArgsSchema = z.object({
  category: z.string().optional(),
});

// In-memory cache for operators
let operatorsCache: OperatorInfo[] | null = null;

/**
 * Load operators from daemon REST API
 */
async function loadOperators(
  daemonClient: DaemonClient,
  logger: Logger
): Promise<OperatorInfo[]> {
  if (operatorsCache) {
    return operatorsCache;
  }

  try {
    // Call daemon /api/operators endpoint
    logger.info('Fetching operators from daemon REST API');
    const registry = await daemonClient.getOperators();

    operatorsCache = registry.operators;
    logger.info('Loaded operators from daemon', {
      count: registry.count,
      version: registry.version,
    });

    return operatorsCache;
  } catch (error) {
    logger.error('Error loading operators from daemon', { error });
    // Return hardcoded fallback if daemon is unavailable
    logger.warn('Using hardcoded operators fallback due to daemon error');
    operatorsCache = getHardcodedOperators();
    return operatorsCache;
  }
}

/**
 * Hardcoded minimal operators for fallback
 */
function getHardcodedOperators(): OperatorInfo[] {
  return [
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
      ],
    },
    {
      symbol: '-',
      name: 'Subtraction',
      category: 'Arithmetic',
      description: 'Subtracts two numbers.',
      syntax: 'value1 - value2',
      precedence: 5,
      associativity: 'left',
      tooltip: 'Subtraction',
      examples: ['10 - 5  // 5'],
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
      examples: ['10 * 5  // 50'],
    },
    {
      symbol: '/',
      name: 'Division',
      category: 'Arithmetic',
      description: 'Divides two numbers.',
      syntax: 'value1 / value2',
      precedence: 4,
      associativity: 'left',
      tooltip: 'Division',
      examples: ['10 / 5  // 2'],
    },
    {
      symbol: '==',
      name: 'Equality',
      category: 'Comparison',
      description: 'Checks if two values are equal.',
      syntax: 'value1 == value2',
      precedence: 8,
      associativity: 'left',
      tooltip: 'Equality comparison',
      examples: ['5 == 5  // true'],
    },
    {
      symbol: '!=',
      name: 'Inequality',
      category: 'Comparison',
      description: 'Checks if two values are not equal.',
      syntax: 'value1 != value2',
      precedence: 8,
      associativity: 'left',
      tooltip: 'Inequality comparison',
      examples: ['5 != 3  // true'],
    },
    {
      symbol: '&&',
      name: 'Logical AND',
      category: 'Logical',
      description: 'Logical AND operation.',
      syntax: 'condition1 && condition2',
      precedence: 10,
      associativity: 'left',
      tooltip: 'Logical AND',
      examples: ['true && false  // false'],
    },
    {
      symbol: '||',
      name: 'Logical OR',
      category: 'Logical',
      description: 'Logical OR operation.',
      syntax: 'condition1 || condition2',
      precedence: 11,
      associativity: 'left',
      tooltip: 'Logical OR',
      examples: ['true || false  // true'],
    },
    {
      symbol: '|>',
      name: 'Pipe',
      category: 'Special',
      description: 'Pipes the result of one expression into another function.',
      syntax: 'value |> function',
      precedence: 12,
      associativity: 'right',
      tooltip: 'Pipe operator for functional composition',
      examples: ['$input |> filter(x => x.active) |> map(x => x.name)'],
    },
  ];
}

export async function handleGetOperators(
  args: Record<string, unknown>,
  daemonClient: DaemonClient,
  logger: Logger
): Promise<ToolInvocationResponse> {
  try {
    // Validate arguments
    const { category } = GetOperatorsArgsSchema.parse(args);

    logger.info('Retrieving operators', { category });

    // Load operators
    let operators = await loadOperators(daemonClient, logger);

    // Filter by category if specified
    if (category) {
      operators = operators.filter((op) => op.category === category);
    }

    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(
            {
              success: true,
              operators,
              count: operators.length,
              message: `Found ${operators.length} operator(s)`,
            },
            null,
            2
          ),
        },
      ],
    };
  } catch (error) {
    logger.error('Error retrieving operators', { error });
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(
            {
              error: 'Failed to retrieve operators',
              message: error instanceof Error ? error.message : 'Unknown error',
            },
            null,
            2
          ),
        },
      ],
      isError: true,
    };
  }
}
