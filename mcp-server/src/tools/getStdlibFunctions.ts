/**
 * Tool 2: get_stdlib_functions
 *
 * Retrieves UTL-X standard library function registry with signatures and documentation
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { FunctionInfo } from '../types/daemon';
import { Logger } from 'winston';
import { z } from 'zod';

export const getStdlibFunctionsTool: Tool = {
  name: 'get_stdlib_functions',
  description: 'Retrieves UTL-X standard library function registry with signatures, descriptions, and usage examples. Optionally filter by category.',
  inputSchema: {
    type: 'object',
    properties: {
      category: {
        type: 'string',
        description: 'Optional category filter: string, array, object, math, date, etc.',
      },
      query: {
        type: 'string',
        description: 'Optional search query to filter functions by name or description',
      },
    },
    required: [],
  },
};

const GetStdlibFunctionsArgsSchema = z.object({
  category: z.string().optional(),
  query: z.string().optional(),
});

// In-memory cache for stdlib functions
let stdlibCache: FunctionInfo[] | null = null;

/**
 * Load stdlib functions from daemon REST API
 */
async function loadStdlibFunctions(
  daemonClient: DaemonClient,
  logger: Logger
): Promise<FunctionInfo[]> {
  if (stdlibCache) {
    return stdlibCache;
  }

  try {
    // Call daemon /api/functions endpoint
    logger.info('Fetching stdlib functions from daemon REST API');
    const registry = await daemonClient.getFunctions();

    stdlibCache = registry.functions;
    logger.info('Loaded stdlib functions from daemon', {
      count: registry.totalFunctions,
      version: registry.version,
    });

    return stdlibCache;
  } catch (error) {
    logger.error('Error loading stdlib functions from daemon', { error });
    // Return hardcoded fallback if daemon is unavailable
    logger.warn('Using hardcoded stdlib fallback due to daemon error');
    stdlibCache = getHardcodedStdlib();
    return stdlibCache;
  }
}

/**
 * Hardcoded minimal stdlib for fallback
 */
function getHardcodedStdlib(): FunctionInfo[] {
  return [
    {
      name: 'length',
      category: 'string',
      signature: 'length(str: String): Number',
      description: 'Returns the length of a string',
      parameters: [
        { name: 'str', type: 'String', description: 'The input string' },
      ],
      returns: { type: 'Number' },
      examples: ['length("hello") => 5', 'length(input.name) => 10'],
    },
    {
      name: 'upper',
      category: 'string',
      signature: 'upper(str: String): String',
      description: 'Converts a string to uppercase',
      parameters: [
        { name: 'str', type: 'String', description: 'The input string' },
      ],
      returns: { type: 'String' },
      examples: ['upper("hello") => "HELLO"', 'upper(input.name) => "JOHN DOE"'],
    },
    {
      name: 'lower',
      category: 'string',
      signature: 'lower(str: String): String',
      description: 'Converts a string to lowercase',
      parameters: [
        { name: 'str', type: 'String', description: 'The input string' },
      ],
      returns: { type: 'String' },
      examples: ['lower("HELLO") => "hello"', 'lower(input.name) => "john doe"'],
    },
    {
      name: 'concat',
      category: 'string',
      signature: 'concat(...strs: String[]): String',
      description: 'Concatenates multiple strings',
      parameters: [
        { name: 'strs', type: 'String[]', description: 'Variable number of strings to concatenate' },
      ],
      returns: { type: 'String' },
      examples: ['concat("hello", " ", "world") => "hello world"'],
    },
    {
      name: 'size',
      category: 'array',
      signature: 'size(arr: Array): Number',
      description: 'Returns the size of an array',
      parameters: [
        { name: 'arr', type: 'Array', description: 'The input array' },
      ],
      returns: { type: 'Number' },
      examples: ['size([1, 2, 3]) => 3', 'size(input.items) => 10'],
    },
    {
      name: 'map',
      category: 'array',
      signature: 'map(arr: Array, fn: Function): Array',
      description: 'Applies a function to each element of an array',
      parameters: [
        { name: 'arr', type: 'Array', description: 'The input array' },
        { name: 'fn', type: 'Function', description: 'The transformation function' },
      ],
      returns: { type: 'Array' },
      examples: ['map([1, 2, 3], x => x * 2) => [2, 4, 6]'],
    },
    {
      name: 'filter',
      category: 'array',
      signature: 'filter(arr: Array, predicate: Function): Array',
      description: 'Filters an array based on a predicate function',
      parameters: [
        { name: 'arr', type: 'Array', description: 'The input array' },
        { name: 'predicate', type: 'Function', description: 'The filter predicate' },
      ],
      returns: { type: 'Array' },
      examples: ['filter([1, 2, 3, 4], x => x > 2) => [3, 4]'],
    },
    {
      name: 'keys',
      category: 'object',
      signature: 'keys(obj: Object): Array<String>',
      description: 'Returns the keys of an object as an array',
      parameters: [
        { name: 'obj', type: 'Object', description: 'The input object' },
      ],
      returns: { type: 'Array<String>' },
      examples: ['keys({a: 1, b: 2}) => ["a", "b"]'],
    },
    {
      name: 'values',
      category: 'object',
      signature: 'values(obj: Object): Array',
      description: 'Returns the values of an object as an array',
      parameters: [
        { name: 'obj', type: 'Object', description: 'The input object' },
      ],
      returns: { type: 'Array' },
      examples: ['values({a: 1, b: 2}) => [1, 2]'],
    },
  ];
}

export async function handleGetStdlibFunctions(
  args: Record<string, unknown>,
  daemonClient: DaemonClient,
  logger: Logger
): Promise<ToolInvocationResponse> {
  try {
    // Validate arguments
    const { category, query } = GetStdlibFunctionsArgsSchema.parse(args);

    logger.info('Retrieving stdlib functions', { category, query });

    // Load stdlib functions
    let functions = await loadStdlibFunctions(daemonClient, logger);

    // Filter by category if specified
    if (category) {
      functions = functions.filter((fn) => fn.category === category);
    }

    // Filter by query if specified (case-insensitive search in name and description)
    if (query) {
      const lowerQuery = query.toLowerCase();
      functions = functions.filter(
        (fn) =>
          fn.name.toLowerCase().includes(lowerQuery) ||
          fn.description.toLowerCase().includes(lowerQuery)
      );
    }

    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(
            {
              success: true,
              functions,
              count: functions.length,
              message: `Found ${functions.length} stdlib function(s)`,
            },
            null,
            2
          ),
        },
      ],
    };
  } catch (error) {
    logger.error('Error retrieving stdlib functions', { error });
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(
            {
              error: 'Failed to retrieve stdlib functions',
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
