/**
 * UDM Path Extractor
 *
 * Extracts all possible access paths from UDM structure for LLM context.
 * Provides a compact, readable format showing how to access data in UTLX.
 */

import { UDMLanguageParser } from '../udm/udm-language-parser';
import { isArray, isObject, UDMObjectHelper } from '../udm/udm-core';

export interface PathInfo {
    path: string;
    type: string;
    description?: string;
}

/**
 * Extract all accessible paths from UDM structure
 */
export function extractInputPaths(udm: string, inputName: string): PathInfo[] {
    const paths: PathInfo[] = [];

    try {
        // Parse UDM
        const parsed = UDMLanguageParser.parse(udm);

        // Extract paths recursively
        extractPathsRecursive(parsed, `$${inputName}`, paths);

    } catch (error) {
        console.error('[UDM Path Extractor] Failed to parse UDM:', error);
    }

    return paths;
}

/**
 * Recursively extract paths from UDM node
 */
function extractPathsRecursive(
    node: any,
    currentPath: string,
    paths: PathInfo[],
    depth: number = 0,
    maxDepth: number = 10
): void {
    // Prevent infinite recursion
    if (depth > maxDepth) {
        return;
    }

    // Add current node
    const nodeType = getNodeType(node);
    paths.push({
        path: currentPath,
        type: nodeType,
    });

    // Handle arrays
    if (isArray(node) && node.elements.length > 0) {
        // Get first element as template
        const firstElement = node.elements[0];
        extractPathsRecursive(firstElement, `${currentPath}[]`, paths, depth + 1, maxDepth);
    }
    // Handle objects
    else if (isObject(node)) {
        // Extract properties
        const propertyKeys = UDMObjectHelper.keys(node);
        for (const key of propertyKeys) {
            const value = UDMObjectHelper.get(node, key);
            if (value !== undefined && value !== null) {
                extractPathsRecursive(value, `${currentPath}.${key}`, paths, depth + 1, maxDepth);
            }
        }

        // Extract attributes (with @ prefix)
        const attrKeys = UDMObjectHelper.attributeKeys(node);
        for (const key of attrKeys) {
            const attrValue = UDMObjectHelper.getAttribute(node, key);
            if (attrValue !== undefined && attrValue !== null) {
                paths.push({
                    path: `${currentPath}.@${key}`,
                    type: typeof attrValue === 'string' ? 'string' :
                          typeof attrValue === 'number' ? 'number' :
                          typeof attrValue === 'boolean' ? 'boolean' : 'unknown',
                });
            }
        }
    }
}

/**
 * Get human-readable type label for UDM node
 */
function getNodeType(node: any): string {
    if (node === null) return 'null';
    if (node === undefined) return 'undefined';

    if (isArray(node)) {
        return 'array';
    }

    if (isObject(node)) {
        return 'object';
    }

    const type = typeof node;
    if (type === 'string' || type === 'number' || type === 'boolean') {
        return type;
    }

    return 'unknown';
}

/**
 * Format paths as a readable string for LLM
 */
export function formatPathsForLLM(paths: PathInfo[]): string {
    if (paths.length === 0) {
        return 'No paths available';
    }

    const lines: string[] = [];

    // Group by base path for better readability
    const grouped = groupPathsByPrefix(paths);

    for (const [prefix, pathList] of grouped) {
        if (pathList.length === 1) {
            const p = pathList[0];
            lines.push(`${p.path} (${p.type})`);
        } else {
            // Multiple paths under same prefix - show hierarchy
            lines.push(`${prefix}:`);
            for (const p of pathList) {
                const suffix = p.path.substring(prefix.length);
                lines.push(`  ${suffix} (${p.type})`);
            }
        }
    }

    return lines.join('\n');
}

/**
 * Group paths by common prefix for better formatting
 */
function groupPathsByPrefix(paths: PathInfo[]): Map<string, PathInfo[]> {
    const grouped = new Map<string, PathInfo[]>();

    // Simple grouping - just list all paths
    grouped.set('Accessible paths', paths);

    return grouped;
}

/**
 * Format as simple flat list (most compact)
 */
export function formatPathsAsSimpleList(paths: PathInfo[]): string {
    return paths.map(p => `${p.path} (${p.type})`).join('\n');
}

/**
 * Format as tree structure (more visual)
 */
export function formatPathsAsTree(paths: PathInfo[], inputName: string): string {
    const root = `$${inputName}`;
    const lines: string[] = [];

    // Build tree structure
    const tree = buildTree(paths, root);

    // Render tree
    renderTree(tree, '', true, lines);

    return lines.join('\n');
}

interface TreeNode {
    name: string;
    type: string;
    children: Map<string, TreeNode>;
}

function buildTree(paths: PathInfo[], root: string): TreeNode {
    const rootNode: TreeNode = {
        name: root,
        type: paths.find(p => p.path === root)?.type || 'unknown',
        children: new Map(),
    };

    for (const path of paths) {
        if (path.path === root) continue;

        const relativePath = path.path.substring(root.length + 1); // +1 for dot
        const parts = relativePath.split(/\.|\[\]\.?/).filter(p => p.length > 0);

        let currentNode = rootNode;
        let currentPath = root;

        for (let i = 0; i < parts.length; i++) {
            const part = parts[i];
            const isLast = i === parts.length - 1;

            currentPath += relativePath.includes('[]') && i === 0 ? '[].' + part : '.' + part;

            if (!currentNode.children.has(part)) {
                const nodeType = isLast ? path.type : 'object';
                currentNode.children.set(part, {
                    name: part,
                    type: nodeType,
                    children: new Map(),
                });
            }

            currentNode = currentNode.children.get(part)!;
        }
    }

    return rootNode;
}

function renderTree(node: TreeNode, prefix: string, isLast: boolean, lines: string[]): void {
    const connector = isLast ? '└── ' : '├── ';
    const line = prefix + connector + node.name + (node.type !== 'unknown' ? ` (${node.type})` : '');
    lines.push(line);

    const children = Array.from(node.children.values());
    const childPrefix = prefix + (isLast ? '    ' : '│   ');

    children.forEach((child, index) => {
        const childIsLast = index === children.length - 1;
        renderTree(child, childPrefix, childIsLast, lines);
    });
}
