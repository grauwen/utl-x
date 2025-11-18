/**
 * USDL Directives Tree Component
 *
 * Displays USDL directives grouped by tier, filtered by output format.
 * Provides search, details panel, and insert/copy functionality.
 */

import * as React from 'react';
import { DirectiveInfo, DirectiveRegistry } from '../../common/usdl-types';

interface DirectivesTreeProps {
    directiveRegistry: DirectiveRegistry | null;
    outputFormat: string;
    onInsert: (directive: string) => void;
    onCopy: (directive: string) => void;
}

export const DirectivesTree: React.FC<DirectivesTreeProps> = ({
    directiveRegistry,
    outputFormat,
    onInsert,
    onCopy
}) => {
    const [expandedTiers, setExpandedTiers] = React.useState<Set<string>>(
        new Set(['core'])  // Core tier expanded by default
    );
    const [selectedDirective, setSelectedDirective] = React.useState<DirectiveInfo | null>(null);
    const [searchQuery, setSearchQuery] = React.useState('');
    const [splitPosition, setSplitPosition] = React.useState(50);  // Split between list and details

    // Filter directives based on search and output format
    const filteredDirectives = React.useMemo(() => {
        console.log('[DirectivesTree] Filtering directives:', {
            registryExists: !!directiveRegistry,
            outputFormat,
            searchQuery
        });

        if (!directiveRegistry) return {};

        const query = searchQuery.toLowerCase();
        const result: { [tier: string]: DirectiveInfo[] } = {};

        Object.entries(directiveRegistry.tiers).forEach(([tier, directives]) => {
            const filtered = directives.filter(dir => {
                // Search filter
                const matchesSearch = !query ||
                    dir.name.toLowerCase().includes(query) ||
                    dir.description.toLowerCase().includes(query);

                // Format filter (only show directives supported by current output format)
                const matchesFormat = dir.supportedFormats.includes(outputFormat.toLowerCase());

                return matchesSearch && matchesFormat;
            });

            if (filtered.length > 0) {
                result[tier] = filtered;
            }
        });

        console.log('[DirectivesTree] Filtered directives:', {
            totalTiers: Object.keys(result).length,
            tierCounts: Object.entries(result).map(([tier, dirs]) => `${tier}:${dirs.length}`)
        });

        return result;
    }, [directiveRegistry, searchQuery, outputFormat]);

    const toggleTier = (tier: string) => {
        const newExpanded = new Set(expandedTiers);
        if (newExpanded.has(tier)) {
            newExpanded.delete(tier);
        } else {
            newExpanded.add(tier);
        }
        setExpandedTiers(newExpanded);
        console.log('[DirectivesTree] Toggled tier:', tier, 'expanded:', !expandedTiers.has(tier));
    };

    const handleInsertDirective = () => {
        if (!selectedDirective) return;

        // Generate directive template from first example or syntax
        const template = selectedDirective.examples[0] || selectedDirective.syntax;
        console.log('[DirectivesTree] Inserting directive:', selectedDirective.name, template);
        onInsert(template);
    };

    const handleCopyDirective = () => {
        if (!selectedDirective) return;

        const template = selectedDirective.examples[0] || selectedDirective.syntax;
        console.log('[DirectivesTree] Copying directive:', selectedDirective.name);
        onCopy(template);
    };

    const handleSplitDrag = (e: React.MouseEvent) => {
        e.preventDefault();
        const startY = e.clientY;
        const container = (e.target as HTMLElement).parentElement;
        if (!container) return;

        const containerHeight = container.getBoundingClientRect().height;
        const startSplit = splitPosition;

        const onMouseMove = (moveEvent: MouseEvent) => {
            const deltaY = moveEvent.clientY - startY;
            const deltaPercent = (deltaY / containerHeight) * 100;
            const newPosition = Math.max(20, Math.min(80, startSplit + deltaPercent));
            setSplitPosition(newPosition);
        };

        const onMouseUp = () => {
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', onMouseUp);
        };

        document.addEventListener('mousemove', onMouseMove);
        document.addEventListener('mouseup', onMouseUp);
    };

    if (!directiveRegistry || directiveRegistry.totalDirectives === 0) {
        return (
            <div className='empty-state'>
                <div className='codicon codicon-info' style={{ fontSize: '24px', marginBottom: '10px' }}></div>
                <div>USDL directives not loaded</div>
                <small>Check daemon connection</small>
            </div>
        );
    }

    const totalFiltered = Object.values(filteredDirectives)
        .reduce((sum, dirs) => sum + dirs.length, 0);

    return (
        <div className='directives-tree'>
            {/* Search Bar */}
            <div className='search-container'>
                <span className='codicon codicon-search'></span>
                <input
                    type='text'
                    placeholder='Search directives...'
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className='search-input'
                />
                {searchQuery && (
                    <button
                        className='codicon codicon-close clear-button'
                        onClick={() => setSearchQuery('')}
                        title='Clear search'
                    />
                )}
            </div>

            {/* Format Badge */}
            <div className='format-indicator'>
                <span className='codicon codicon-file-code'></span>
                <span>Output format: <strong>{outputFormat.toUpperCase()}</strong></span>
                <span className='badge'>{totalFiltered} directives</span>
            </div>

            {/* Split View: Directives List + Details Panel */}
            <div className='split-container'>
                {/* Top: Directives List */}
                <div className='directives-list' style={{ height: `${splitPosition}%` }}>
                    {Object.entries(filteredDirectives).length === 0 ? (
                        <div className='empty-state'>
                            <div className='codicon codicon-search' style={{ fontSize: '20px' }}></div>
                            <div>No directives found</div>
                            <small>{searchQuery ? 'Try different search terms' : 'No directives for this format'}</small>
                        </div>
                    ) : (
                        Object.entries(filteredDirectives).map(([tier, directives]) => (
                            <div key={tier} className='tier-group'>
                                {/* Tier Header */}
                                <div
                                    className='tier-header'
                                    onClick={() => toggleTier(tier)}
                                >
                                    <span className={`codicon codicon-chevron-${expandedTiers.has(tier) ? 'down' : 'right'}`}></span>
                                    <span className='tier-name'>{getTierDisplayName(tier)}</span>
                                    <span className='tier-count'>{directives.length}</span>
                                </div>

                                {/* Directives in Tier */}
                                {expandedTiers.has(tier) && (
                                    <div className='directive-list'>
                                        {directives.map(directive => (
                                            <div
                                                key={directive.name}
                                                className={`directive-item ${selectedDirective?.name === directive.name ? 'selected' : ''}`}
                                                onClick={() => {
                                                    setSelectedDirective(directive);
                                                    console.log('[DirectivesTree] Selected directive:', directive.name);
                                                }}
                                            >
                                                <span className='codicon codicon-symbol-property'></span>
                                                <span className='directive-name'>{directive.name}</span>
                                                {directive.required && (
                                                    <span className='required-badge' title='Required'>*</span>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        ))
                    )}
                </div>

                {/* Split Divider */}
                <div
                    className='split-divider'
                    onMouseDown={handleSplitDrag}
                ></div>

                {/* Bottom: Directive Details */}
                <div className='directive-details' style={{ height: `${100 - splitPosition}%` }}>
                    {selectedDirective ? (
                        <>
                            <div className='details-header'>
                                <h3>{selectedDirective.name}</h3>
                                <div className='details-actions'>
                                    <button
                                        className='action-button'
                                        onClick={handleInsertDirective}
                                        title='Insert at cursor'
                                    >
                                        <span className='codicon codicon-insert'></span>
                                        Insert
                                    </button>
                                    <button
                                        className='action-button'
                                        onClick={handleCopyDirective}
                                        title='Copy to clipboard'
                                    >
                                        <span className='codicon codicon-copy'></span>
                                        Copy
                                    </button>
                                </div>
                            </div>

                            <div className='details-content'>
                                {/* Description */}
                                <div className='detail-section'>
                                    <div className='section-label'>Description</div>
                                    <div className='section-value'>{selectedDirective.description}</div>
                                </div>

                                {/* Syntax */}
                                <div className='detail-section'>
                                    <div className='section-label'>Syntax</div>
                                    <code className='syntax-code'>{selectedDirective.syntax}</code>
                                </div>

                                {/* Value Type */}
                                <div className='detail-section'>
                                    <div className='section-label'>Value Type</div>
                                    <span className='badge type-badge'>{selectedDirective.valueType}</span>
                                    {selectedDirective.required && (
                                        <span className='badge required-badge'>REQUIRED</span>
                                    )}
                                </div>

                                {/* Scopes */}
                                <div className='detail-section'>
                                    <div className='section-label'>Valid Scopes</div>
                                    <div className='scope-list'>
                                        {selectedDirective.scopes.map(scope => (
                                            <span key={scope} className='badge scope-badge'>{scope}</span>
                                        ))}
                                    </div>
                                </div>

                                {/* Examples */}
                                {selectedDirective.examples.length > 0 && (
                                    <div className='detail-section'>
                                        <div className='section-label'>Examples</div>
                                        {selectedDirective.examples.map((example, idx) => (
                                            <code key={idx} className='example-code'>{example}</code>
                                        ))}
                                    </div>
                                )}

                                {/* See Also */}
                                {selectedDirective.seeAlso.length > 0 && (
                                    <div className='detail-section'>
                                        <div className='section-label'>See Also</div>
                                        <div className='see-also-list'>
                                            {selectedDirective.seeAlso.map(related => (
                                                <span key={related} className='related-directive'>{related}</span>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        </>
                    ) : (
                        <div className='empty-state'>
                            <div className='codicon codicon-info' style={{ fontSize: '20px' }}></div>
                            <div>Select a directive to see details</div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

/**
 * Get display name for tier
 */
function getTierDisplayName(tier: string): string {
    const names: { [key: string]: string } = {
        'core': 'Tier 1: Core',
        'common': 'Tier 2: Common',
        'format_specific': 'Tier 3: Format-Specific',
        'reserved': 'Tier 4: Reserved'
    };
    return names[tier] || tier;
}
