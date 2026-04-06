/**
 * Operators Tree Component
 *
 * Displays UTLX operators in a collapsible tree organized by category.
 * Similar to the function tree, but for operators.
 */

import * as React from 'react';
import { OperatorInfo } from './operators-data';

export interface OperatorsTreeProps {
    operators: OperatorInfo[];
    onInsertOperator: (operator: OperatorInfo) => void;
    selectedOperator: OperatorInfo | null;
    onSelectOperator: (operator: OperatorInfo | null) => void;
}

export const OperatorsTree: React.FC<OperatorsTreeProps> = ({
    operators,
    onInsertOperator,
    selectedOperator,
    onSelectOperator
}) => {
    const [expandedCategories, setExpandedCategories] = React.useState<Set<string>>(
        new Set() // Start collapsed - user can expand categories as needed
    );

    const operatorsByCategory = React.useMemo(() => {
        // Group operators by category
        const grouped = new Map<string, OperatorInfo[]>();
        for (const op of operators) {
            if (!grouped.has(op.category)) {
                grouped.set(op.category, []);
            }
            grouped.get(op.category)!.push(op);
        }

        // Sort categories in logical order
        const orderedCategories = ['Arithmetic', 'Comparison', 'Logical', 'Special'];
        const orderedMap = new Map<string, OperatorInfo[]>();
        for (const category of orderedCategories) {
            if (grouped.has(category)) {
                orderedMap.set(category, grouped.get(category)!);
            }
        }
        return orderedMap;
    }, [operators]);

    const toggleCategory = (category: string) => {
        const newExpanded = new Set(expandedCategories);
        if (newExpanded.has(category)) {
            newExpanded.delete(category);
        } else {
            newExpanded.add(category);
        }
        setExpandedCategories(newExpanded);
    };

    return (
        <div className='operators-tree'>
            {Array.from(operatorsByCategory.entries()).map(([category, operators]) => (
                <div key={category} className='operator-category'>
                    <div
                        className='category-header'
                        onClick={() => toggleCategory(category)}
                    >
                        <span className={`codicon codicon-chevron-${expandedCategories.has(category) ? 'down' : 'right'}`}></span>
                        <span className='category-name'>{category}</span>
                        <span className='operator-count'>({operators.length})</span>
                    </div>

                    {expandedCategories.has(category) && (
                        <div className='category-operators'>
                            {operators.map((op) => (
                                <div
                                    key={op.symbol}
                                    className={`operator-item ${selectedOperator?.symbol === op.symbol ? 'selected' : ''}`}
                                    onClick={() => onSelectOperator(op)}
                                    title={op.tooltip}
                                >
                                    <span className='operator-symbol'>{op.symbol}</span>
                                    <span className='operator-name'>{op.name}</span>
                                    <div className='operator-actions'>
                                        <button
                                            className='insert-btn'
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                onInsertOperator(op);
                                            }}
                                            title='Insert operator'
                                        >
                                            <span className='codicon codicon-insert'></span>
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            ))}
        </div>
    );
};
