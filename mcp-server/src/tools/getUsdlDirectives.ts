/**
 * MCP Tool: get_usdl_directives
 *
 * Retrieves USDL directive registry for schema generation assistance.
 * Supports filtering by tier, scope, format, directive name, or keyword search.
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';
import { DirectiveRegistry, DirectiveInfo, FormatInfo } from '../types/usdl';

/**
 * Tool definition
 */
export const getUsdlDirectivesTool: Tool = {
  name: 'get_usdl_directives',
  description: `Retrieves USDL (Universal Schema Definition Language) directive registry for schema generation assistance.

USDL directives are used to define data schemas that can be transformed to multiple formats (XSD, JSON Schema, Avro, Protobuf, etc.).

Query modes:
- No filters: Returns all 119 directives
- tier: Filter by tier (core, common, format_specific, reserved)
- scope: Filter by scope (TOP_LEVEL, TYPE_DEFINITION, FIELD_DEFINITION, ENUMERATION, etc.)
- format: Filter by format abbreviation (xsd, jsch, proto, avro, odata, graphql, sql, etc.)
- directive: Get specific directive by name (e.g., "%namespace", "%fields")
- keyword: Search directives by keyword in name/description/examples
- query: Get metadata (statistics, formats, tiers, scopes)

Use this tool to help users:
- Discover available USDL directives
- Understand directive syntax and usage
- Find format-specific directives
- Generate USDL schema code
- Check directive compatibility across formats`,
  inputSchema: {
    type: 'object',
    properties: {
      tier: {
        type: 'string',
        description: 'Filter by tier: core (9 directives), common (51), format_specific (44), reserved (15)',
        enum: ['core', 'common', 'format_specific', 'reserved'],
      },
      scope: {
        type: 'string',
        description: 'Filter by scope where directive can be used (e.g., TOP_LEVEL, TYPE_DEFINITION, FIELD_DEFINITION)',
      },
      format: {
        type: 'string',
        description: 'Filter by format abbreviation (e.g., xsd, jsch, proto, avro, odata, graphql, sql)',
      },
      directive: {
        type: 'string',
        description: 'Get specific directive by name (with or without % prefix)',
      },
      keyword: {
        type: 'string',
        description: 'Search directives by keyword (searches name, description, and examples)',
      },
      query: {
        type: 'string',
        description: 'Get metadata: "statistics" (counts), "formats" (supported formats), "tiers" (tier summary), "scopes" (scope summary)',
        enum: ['statistics', 'formats', 'tiers', 'scopes'],
      },
    },
  },
};

/**
 * In-memory cache for directive registry
 */
let cachedRegistry: DirectiveRegistry | null = null;
let cacheTimestamp: number = 0;
const CACHE_TTL_MS = 60000; // 1 minute cache

/**
 * Tool handler implementation
 */
export async function handleGetUsdlDirectives(
  args: Record<string, unknown>,
  daemonClient: DaemonClient,
  logger: Logger
): Promise<ToolInvocationResponse> {
  try {
    // Load registry (with caching)
    const registry = await loadRegistry(daemonClient, logger);

    // Handle different query modes
    if (args.query) {
      return handleMetadataQuery(args.query as string, registry);
    }

    if (args.directive) {
      return handleDirectiveQuery(args.directive as string, registry);
    }

    if (args.keyword) {
      return handleKeywordSearch(args.keyword as string, registry);
    }

    if (args.tier) {
      return handleTierFilter(args.tier as string, registry);
    }

    if (args.scope) {
      return handleScopeFilter(args.scope as string, registry);
    }

    if (args.format) {
      return handleFormatFilter(args.format as string, registry);
    }

    // No filters - return all directives
    return formatDirectivesResponse(registry.directives, registry);
  } catch (error) {
    logger.error('Error in get_usdl_directives:', error);
    return {
      content: [
        {
          type: 'text' as const,
          text: `Error retrieving USDL directives: ${error instanceof Error ? error.message : String(error)}\n\nPlease ensure the UTL-X daemon is running.`,
        },
      ],
    };
  }
}

/**
 * Load directive registry with caching
 */
async function loadRegistry(daemonClient: DaemonClient, logger: Logger): Promise<DirectiveRegistry> {
  const now = Date.now();

  // Check cache
  if (cachedRegistry && now - cacheTimestamp < CACHE_TTL_MS) {
    logger.debug('Using cached USDL directive registry');
    return cachedRegistry;
  }

  // Fetch from daemon
  logger.info('Fetching USDL directive registry from daemon');
  const registry = await daemonClient.getUsdlDirectives();

  // Update cache
  cachedRegistry = registry;
  cacheTimestamp = now;

  return registry;
}

/**
 * Handle metadata queries (statistics, formats, tiers, scopes)
 */
function handleMetadataQuery(query: string, registry: DirectiveRegistry): ToolInvocationResponse {
  switch (query) {
    case 'statistics':
      return formatStatisticsResponse(registry);
    case 'formats':
      return formatFormatsResponse(registry);
    case 'tiers':
      return formatTiersResponse(registry);
    case 'scopes':
      return formatScopesResponse(registry);
    default:
      return {
        content: [{ type: 'text' as const, text: `Unknown query: ${query}` }],
      };
  }
}

/**
 * Handle single directive query
 */
function handleDirectiveQuery(directiveName: string, registry: DirectiveRegistry): ToolInvocationResponse {
  // Normalize directive name (add % prefix if missing)
  const name = directiveName.startsWith('%') ? directiveName : `%${directiveName}`;

  const directive = registry.directives.find((d) => d.name === name);

  if (!directive) {
    return {
      content: [
        {
          type: 'text' as const,
          text: `Directive "${name}" not found.\n\nUse get_usdl_directives without arguments to see all available directives.`,
        },
      ],
    };
  }

  return formatSingleDirectiveResponse(directive);
}

/**
 * Handle keyword search
 */
function handleKeywordSearch(keyword: string, registry: DirectiveRegistry): ToolInvocationResponse {
  const lowerKeyword = keyword.toLowerCase();
  const matches = registry.directives.filter(
    (d) =>
      d.name.toLowerCase().includes(lowerKeyword) ||
      d.description.toLowerCase().includes(lowerKeyword) ||
      d.examples.some((ex) => ex.toLowerCase().includes(lowerKeyword))
  );

  if (matches.length === 0) {
    return {
      content: [
        {
          type: 'text' as const,
          text: `No directives found matching keyword: "${keyword}"`,
        },
      ],
    };
  }

  return formatDirectivesResponse(matches, registry, `Directives matching "${keyword}"`);
}

/**
 * Handle tier filter
 */
function handleTierFilter(tier: string, registry: DirectiveRegistry): ToolInvocationResponse {
  const directives = registry.tiers[tier as keyof typeof registry.tiers];

  if (!directives) {
    return {
      content: [{ type: 'text' as const, text: `Unknown tier: ${tier}` }],
    };
  }

  return formatDirectivesResponse(directives, registry, `Tier: ${tier}`);
}

/**
 * Handle scope filter
 */
function handleScopeFilter(scope: string, registry: DirectiveRegistry): ToolInvocationResponse {
  const directives = registry.scopes[scope];

  if (!directives) {
    const availableScopes = Object.keys(registry.scopes).join(', ');
    return {
      content: [
        {
          type: 'text' as const,
          text: `Unknown scope: ${scope}\n\nAvailable scopes: ${availableScopes}`,
        },
      ],
    };
  }

  return formatDirectivesResponse(directives, registry, `Scope: ${scope}`);
}

/**
 * Handle format filter
 */
function handleFormatFilter(format: string, registry: DirectiveRegistry): ToolInvocationResponse {
  const directives = registry.directives.filter((d) => d.supportedFormats.includes(format));

  if (directives.length === 0) {
    const availableFormats = Object.keys(registry.formats).join(', ');
    return {
      content: [
        {
          type: 'text' as const,
          text: `No directives found for format: ${format}\n\nAvailable formats: ${availableFormats}`,
        },
      ],
    };
  }

  const formatInfo = registry.formats[format];
  const formatName = formatInfo ? formatInfo.name : format;

  return formatDirectivesResponse(directives, registry, `Format: ${formatName} (${format})`);
}

/**
 * Format list of directives
 */
function formatDirectivesResponse(
  directives: DirectiveInfo[],
  registry: DirectiveRegistry,
  title?: string
): ToolInvocationResponse {
  let output = `# USDL Directive Registry v${registry.version}\n\n`;

  if (title) {
    output += `## ${title}\n\n`;
  }

  output += `**Total directives:** ${directives.length}\n\n`;

  // Group by tier
  const byTier = {
    core: directives.filter((d) => d.tier === 'core'),
    common: directives.filter((d) => d.tier === 'common'),
    format_specific: directives.filter((d) => d.tier === 'format_specific'),
    reserved: directives.filter((d) => d.tier === 'reserved'),
  };

  for (const [tier, tierDirectives] of Object.entries(byTier)) {
    if (tierDirectives.length === 0) continue;

    output += `### ${tier.toUpperCase()} (${tierDirectives.length})\n\n`;

    for (const directive of tierDirectives) {
      output += `**${directive.name}**${directive.required ? ' *(REQUIRED)*' : ''}\n`;
      output += `- ${directive.description}\n`;
      output += `- Type: ${directive.valueType}\n`;
      output += `- Scopes: ${directive.scopes.join(', ')}\n`;
      output += `- Formats: ${directive.supportedFormats.length} formats\n`;

      if (directive.examples.length > 0) {
        output += `- Example: \`${directive.examples[0]}\`\n`;
      }

      output += '\n';
    }
  }

  return {
    content: [{ type: 'text' as const, text: output }],
  };
}

/**
 * Format single directive details
 */
function formatSingleDirectiveResponse(directive: DirectiveInfo): ToolInvocationResponse {
  let output = `# ${directive.name}\n\n`;
  output += `${directive.description}\n\n`;

  output += `## Details\n\n`;
  output += `- **Tier:** ${directive.tier}${directive.required ? ' *(REQUIRED)*' : ''}\n`;
  output += `- **Value Type:** ${directive.valueType}\n`;
  output += `- **Scopes:** ${directive.scopes.join(', ')}\n`;
  output += `- **Supported Formats:** ${directive.supportedFormats.join(', ')}\n\n`;

  output += `## Syntax\n\n\`\`\`\n${directive.syntax}\n\`\`\`\n\n`;

  if (directive.examples.length > 0) {
    output += `## Examples\n\n`;
    for (const example of directive.examples) {
      output += `\`\`\`\n${example}\n\`\`\`\n\n`;
    }
  }

  if (directive.seeAlso.length > 0) {
    output += `## Related Directives\n\n`;
    output += directive.seeAlso.map((d) => `- ${d}`).join('\n') + '\n\n';
  }

  return {
    content: [{ type: 'text' as const, text: output }],
  };
}

/**
 * Format statistics response
 */
function formatStatisticsResponse(registry: DirectiveRegistry): ToolInvocationResponse {
  let output = `# USDL Directive Registry Statistics\n\n`;
  output += `**Version:** ${registry.version}\n`;
  output += `**Generated:** ${registry.generatedAt}\n\n`;

  output += `## Directive Counts\n\n`;
  output += `- **Total Directives:** ${registry.totalDirectives}\n`;
  output += `- **Core (Tier 1):** ${registry.tiers.core.length}\n`;
  output += `- **Common (Tier 2):** ${registry.tiers.common.length}\n`;
  output += `- **Format-Specific (Tier 3):** ${registry.tiers.format_specific.length}\n`;
  output += `- **Reserved (Tier 4):** ${registry.tiers.reserved.length}\n\n`;

  output += `## Scope Coverage\n\n`;
  for (const [scope, directives] of Object.entries(registry.scopes)) {
    output += `- **${scope}:** ${directives.length} directives\n`;
  }
  output += '\n';

  output += `## Format Support\n\n`;
  output += `- **Total Formats:** ${Object.keys(registry.formats).length}\n\n`;

  return {
    content: [{ type: 'text' as const, text: output }],
  };
}

/**
 * Format formats response
 */
function formatFormatsResponse(registry: DirectiveRegistry): ToolInvocationResponse {
  let output = `# Supported Schema Formats\n\n`;

  // Group by domain
  const byDomain: Record<string, FormatInfo[]> = {};
  for (const format of Object.values(registry.formats)) {
    if (!byDomain[format.domain]) {
      byDomain[format.domain] = [];
    }
    byDomain[format.domain].push(format);
  }

  for (const [domain, formats] of Object.entries(byDomain)) {
    output += `## ${domain.toUpperCase()}\n\n`;

    for (const format of formats) {
      output += `### ${format.name} (${format.abbreviation})\n\n`;
      output += `- **Overall Support:** ${format.overallSupport}%\n`;
      output += `- **Tier 1 (Core):** ${format.tier1Support}%\n`;
      output += `- **Tier 2 (Common):** ${format.tier2Support}%\n`;
      output += `- **Tier 3 (Format-Specific):** ${format.tier3Support}%\n`;
      output += `- **Supported Directives:** ${format.supportedDirectives.length}\n`;
      if (format.notes) {
        output += `- **Notes:** ${format.notes}\n`;
      }
      output += '\n';
    }
  }

  return {
    content: [{ type: 'text' as const, text: output }],
  };
}

/**
 * Format tiers response
 */
function formatTiersResponse(registry: DirectiveRegistry): ToolInvocationResponse {
  let output = `# USDL Directive Tiers\n\n`;

  output += `## Tier 1: Core (${registry.tiers.core.length} directives)\n\n`;
  output += `Essential directives required for basic schema definition.\n\n`;
  output += registry.tiers.core.map((d) => `- ${d.name}: ${d.description}`).join('\n') + '\n\n';

  output += `## Tier 2: Common (${registry.tiers.common.length} directives)\n\n`;
  output += `Recommended directives for most schemas.\n\n`;
  output += registry.tiers.common.map((d) => `- ${d.name}: ${d.description}`).join('\n') + '\n\n';

  output += `## Tier 3: Format-Specific (${registry.tiers.format_specific.length} directives)\n\n`;
  output += `Specialized directives for specific formats.\n\n`;
  output += registry.tiers.format_specific.map((d) => `- ${d.name}: ${d.description}`).join('\n') + '\n\n';

  output += `## Tier 4: Reserved (${registry.tiers.reserved.length} directives)\n\n`;
  output += `Reserved for future USDL versions.\n\n`;
  output += registry.tiers.reserved.map((d) => `- ${d.name}: ${d.description}`).join('\n') + '\n\n';

  return {
    content: [{ type: 'text' as const, text: output }],
  };
}

/**
 * Format scopes response
 */
function formatScopesResponse(registry: DirectiveRegistry): ToolInvocationResponse {
  let output = `# USDL Directive Scopes\n\n`;
  output += `Scopes define where directives can be used in a USDL schema.\n\n`;

  for (const [scope, directives] of Object.entries(registry.scopes)) {
    output += `## ${scope} (${directives.length} directives)\n\n`;
    output += directives.map((d) => `- ${d.name}: ${d.description}`).join('\n') + '\n\n';
  }

  return {
    content: [{ type: 'text' as const, text: output }],
  };
}
