/**
 * TypeScript type definitions for USDL Directive Registry
 *
 * These types match the Kotlin data classes in:
 * schema/src/main/kotlin/org/apache/utlx/schema/usdl/DirectiveRegistry.kt
 */

/**
 * Information about a single USDL directive
 */
export interface DirectiveInfo {
    /** Directive name with % prefix (e.g., "%namespace") */
    name: string;

    /** Directive tier classification */
    tier: string;

    /** Valid scopes where directive can be used */
    scopes: string[];

    /** Expected value type (String, Integer, Boolean, Object, Array, etc.) */
    valueType: string;

    /** Whether this directive is required in USDL schemas */
    required: boolean;

    /** Human-readable description of the directive */
    description: string;

    /** List of format abbreviations that support this directive */
    supportedFormats: string[];

    /** Short usage examples for this directive */
    examples: string[];

    /** Syntax pattern for the directive */
    syntax: string;

    /** Tooltip text for IDE hover (includes description, scopes, type) */
    tooltip: string;

    /** Related directive names */
    seeAlso: string[];
}

/**
 * Information about a schema format
 */
export interface FormatInfo {
    /** Full format name (e.g., "XML Schema Definition") */
    name: string;

    /** Format abbreviation (e.g., "xsd") */
    abbreviation: string;

    /** Percentage of Tier 1 (core) directives supported (0-100) */
    tier1Support: number;

    /** Percentage of Tier 2 (common) directives supported (0-100) */
    tier2Support: number;

    /** Percentage of Tier 3 (format-specific) directives supported (0-100) */
    tier3Support: number;

    /** Weighted overall compatibility percentage (0-100) */
    overallSupport: number;

    /** List of directive names supported by this format */
    supportedDirectives: string[];

    /** Additional notes about format support */
    notes: string;

    /** Format domain classification */
    domain: string;
}

/**
 * Complete USDL directive registry
 */
export interface DirectiveRegistry {
    /** USDL specification version */
    version: string;

    /** Timestamp when registry was generated */
    generatedAt: string;

    /** Total number of directives (all tiers) */
    totalDirectives: number;

    /** List of all directives (flat) */
    directives: DirectiveInfo[];

    /** Directives grouped by tier */
    tiers: {
        core: DirectiveInfo[];
        common: DirectiveInfo[];
        format_specific: DirectiveInfo[];
        reserved: DirectiveInfo[];
    };

    /** Directives grouped by scope (TOP_LEVEL, TYPE_DEFINITION, FIELD_DEFINITION, etc.) */
    scopes: Record<string, DirectiveInfo[]>;

    /** Format metadata for all supported schema formats */
    formats: Record<string, FormatInfo>;
}

/**
 * Registry statistics
 */
export interface DirectiveStatistics {
    /** Total number of directives */
    totalDirectives: number;

    /** Count of directives per tier */
    tierCounts: Record<string, number>;

    /** Count of directives per scope */
    scopeCounts: Record<string, number>;

    /** Total number of formats */
    totalFormats: number;

    /** Average compatibility percentage across all formats */
    averageCompatibility: number;
}
