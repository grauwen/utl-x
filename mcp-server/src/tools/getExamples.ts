/**
 * Tool 6: get_examples
 *
 * Indexes conformance suite tests and provides TF-IDF similarity search for relevant examples
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';
import { z } from 'zod';
import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'yaml';
import { TfIdf } from 'natural';

export const getExamplesTool: Tool = {
  name: 'get_examples',
  description: 'Searches conformance suite tests for relevant UTLX transformation examples. Uses TF-IDF similarity to find the most relevant examples based on your query.',
  inputSchema: {
    type: 'object',
    properties: {
      query: {
        type: 'string',
        description: 'Search query describing the transformation you need (e.g., "flatten nested arrays", "XML to JSON conversion")',
      },
      limit: {
        type: 'number',
        description: 'Maximum number of examples to return (default: 5)',
        default: 5,
      },
      category: {
        type: 'string',
        description: 'Optional category filter: basic, transformation, aggregation, etc.',
      },
    },
    required: ['query'],
  },
};

const GetExamplesArgsSchema = z.object({
  query: z.string(),
  limit: z.number().optional().default(5),
  category: z.string().optional(),
});

interface ConformanceExample {
  id: string;
  name: string;
  description: string;
  category: string;
  utlx: string;
  input: string;
  output: string;
  inputFormat: string;
  outputFormat: string;
  filePath: string;
}

// In-memory cache for indexed examples
let examplesCache: ConformanceExample[] | null = null;
let tfidfIndex: TfIdf | null = null;

/**
 * Index conformance suite tests from directory
 */
async function indexConformanceSuite(
  conformanceDir: string,
  logger: Logger
): Promise<ConformanceExample[]> {
  if (examplesCache) {
    return examplesCache;
  }

  const examples: ConformanceExample[] = [];

  try {
    // Check if conformance directory exists
    if (!fs.existsSync(conformanceDir)) {
      logger.warn('Conformance suite directory not found', { conformanceDir });
      return [];
    }

    // Recursively scan directory for .yaml and .yml files
    const scanDirectory = (dir: string, category: string = 'general') => {
      const entries = fs.readdirSync(dir, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);

        if (entry.isDirectory()) {
          // Use directory name as category
          scanDirectory(fullPath, entry.name);
        } else if (entry.isFile() && (entry.name.endsWith('.yaml') || entry.name.endsWith('.yml'))) {
          try {
            const content = fs.readFileSync(fullPath, 'utf-8');
            const data = yaml.parse(content);

            // Extract test cases from YAML
            if (data.tests && Array.isArray(data.tests)) {
              for (let i = 0; i < data.tests.length; i++) {
                const test = data.tests[i];
                examples.push({
                  id: `${entry.name.replace(/\.(yaml|yml)$/, '')}_${i}`,
                  name: test.name || data.name || entry.name,
                  description: test.description || data.description || '',
                  category: data.category || category,
                  utlx: test.utlx || '',
                  input: typeof test.input === 'string' ? test.input : JSON.stringify(test.input),
                  output: typeof test.expected === 'string' ? test.expected : JSON.stringify(test.expected),
                  inputFormat: test.inputFormat || data.inputFormat || 'json',
                  outputFormat: test.outputFormat || data.outputFormat || 'json',
                  filePath: fullPath,
                });
              }
            } else {
              // Single test case in file
              examples.push({
                id: entry.name.replace(/\.(yaml|yml)$/, ''),
                name: data.name || entry.name,
                description: data.description || '',
                category: data.category || category,
                utlx: data.utlx || '',
                input: typeof data.input === 'string' ? data.input : JSON.stringify(data.input),
                output: typeof data.expected === 'string' ? data.expected : JSON.stringify(data.expected),
                inputFormat: data.inputFormat || 'json',
                outputFormat: data.outputFormat || 'json',
                filePath: fullPath,
              });
            }
          } catch (error) {
            logger.warn('Failed to parse conformance test file', {
              file: fullPath,
              error: error instanceof Error ? error.message : 'Unknown error',
            });
          }
        }
      }
    };

    scanDirectory(conformanceDir);
    examplesCache = examples;

    logger.info('Indexed conformance suite', {
      count: examples.length,
      directory: conformanceDir,
    });

    return examples;
  } catch (error) {
    logger.error('Error indexing conformance suite', { error });
    return [];
  }
}

/**
 * Build TF-IDF index for examples
 */
function buildTfIdfIndex(examples: ConformanceExample[], logger: Logger): TfIdf {
  if (tfidfIndex) {
    return tfidfIndex;
  }

  const tfidf = new TfIdf();

  // Add each example to the TF-IDF index
  for (const example of examples) {
    const document = [
      example.name,
      example.description,
      example.category,
      example.utlx,
    ].join(' ');

    tfidf.addDocument(document);
  }

  tfidfIndex = tfidf;

  logger.info('Built TF-IDF index', { documentCount: examples.length });

  return tfidf;
}

/**
 * Search examples using TF-IDF similarity
 */
function searchExamples(
  query: string,
  examples: ConformanceExample[],
  tfidf: TfIdf,
  limit: number,
  category?: string
): ConformanceExample[] {
  // Get TF-IDF scores for query
  const scores: Array<{ index: number; score: number }> = [];

  tfidf.tfidfs(query, (i, score) => {
    if (score > 0) {
      scores.push({ index: i, score });
    }
  });

  // Sort by score descending
  scores.sort((a, b) => b.score - a.score);

  // Filter by category if specified
  let results = scores.map(({ index }) => examples[index]);

  if (category) {
    results = results.filter((ex) => ex.category === category);
  }

  // Return top N results
  return results.slice(0, limit);
}

export async function handleGetExamples(
  args: Record<string, unknown>,
  _daemonClient: DaemonClient,
  logger: Logger
): Promise<ToolInvocationResponse> {
  try {
    // Validate arguments
    const { query, limit, category } = GetExamplesArgsSchema.parse(args);

    logger.info('Searching for examples', { query, limit, category });

    // Determine conformance suite directory
    // Try multiple possible locations
    const possiblePaths = [
      path.join(__dirname, '../../..', 'tests/conformance'),
      path.join(__dirname, '../../..', 'tests/formats'),
      path.join(process.cwd(), 'tests/conformance'),
      path.join(process.cwd(), 'tests/formats'),
    ];

    let conformanceDir = '';
    for (const dir of possiblePaths) {
      if (fs.existsSync(dir)) {
        conformanceDir = dir;
        break;
      }
    }

    if (!conformanceDir) {
      logger.warn('Conformance suite directory not found in any expected location');
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify(
              {
                success: false,
                error: 'Conformance suite directory not found',
                message: 'No test examples are available. Please ensure the conformance suite is installed.',
                searchedPaths: possiblePaths,
              },
              null,
              2
            ),
          },
        ],
        isError: true,
      };
    }

    // Index conformance suite
    const examples = await indexConformanceSuite(conformanceDir, logger);

    if (examples.length === 0) {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify(
              {
                success: true,
                examples: [],
                count: 0,
                message: 'No examples found in conformance suite',
              },
              null,
              2
            ),
          },
        ],
      };
    }

    // Build TF-IDF index
    const tfidf = buildTfIdfIndex(examples, logger);

    // Search for relevant examples
    const results = searchExamples(query, examples, tfidf, limit, category);

    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(
            {
              success: true,
              examples: results,
              count: results.length,
              totalIndexed: examples.length,
              message: `Found ${results.length} relevant example(s)`,
            },
            null,
            2
          ),
        },
      ],
    };
  } catch (error) {
    logger.error('Error searching examples', { error });
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(
            {
              error: 'Failed to search examples',
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
