/**
 * Validation Result Dialog
 *
 * Displays a field-by-field comparison between expected and inferred output schemas.
 * Used by the Validate button in Design-Time mode.
 *
 * Status indicators:
 * - ✓ green   = match
 * - ✗ orange  = type mismatch (shows expected vs inferred type)
 * - ⊘ red     = missing (in expected, not in inferred)
 * - ⊕ yellow  = extra (in inferred, not in expected)
 */

import * as React from 'react';
import {
    SchemaComparisonResult,
    FieldComparisonResult,
    FieldComparisonStatus
} from '../utils/schema-comparator';

export interface ValidationResultDialogProps {
    result: SchemaComparisonResult;
    onClose: () => void;
}

interface FieldRowState {
    collapsed: Set<string>;
}

export class ValidationResultDialog extends React.Component<ValidationResultDialogProps, FieldRowState> {
    constructor(props: ValidationResultDialogProps) {
        super(props);
        this.state = { collapsed: new Set() };
    }

    render(): React.ReactNode {
        const { result, onClose } = this.props;

        return (
            <div className='utlx-dialog-overlay' onClick={onClose}>
                <div
                    className='utlx-dialog utlx-validation-dialog'
                    onClick={(e) => e.stopPropagation()}
                >
                    <div className='utlx-dialog-header'>
                        <h3>Validation Results</h3>
                        <button
                            className='utlx-dialog-close'
                            onClick={onClose}
                        >
                            ✕
                        </button>
                    </div>

                    <div className='utlx-dialog-body'>
                        {/* Summary Bar */}
                        <div className='utlx-validation-summary'>
                            <span className='utlx-validation-stat utlx-validation-match'>
                                {result.matchCount} matched
                            </span>
                            <span className='utlx-validation-stat utlx-validation-missing-stat'>
                                {result.missingCount} missing
                            </span>
                            <span className='utlx-validation-stat utlx-validation-extra-stat'>
                                {result.extraCount} extra
                            </span>
                            <span className='utlx-validation-stat utlx-validation-mismatch-stat'>
                                {result.typeMismatchCount} mismatch
                            </span>
                        </div>

                        {/* Overall status */}
                        <div className={`utlx-validation-status ${result.isValid ? 'valid' : 'invalid'}`}>
                            {result.isValid
                                ? '✓ Validation passed — all expected fields present with correct types'
                                : `✗ ${result.missingCount + result.typeMismatchCount} issue(s) found`
                            }
                        </div>

                        {/* Field comparison table */}
                        <div className='utlx-validation-table'>
                            <div className='utlx-validation-table-header'>
                                <span className='utlx-validation-col-status'></span>
                                <span className='utlx-validation-col-name'>Field</span>
                                <span className='utlx-validation-col-type'>Expected</span>
                                <span className='utlx-validation-col-type'>Inferred</span>
                            </div>
                            <div className='utlx-validation-table-body'>
                                {result.fields.map((field, idx) =>
                                    this.renderField(field, 0, `${idx}`)
                                )}
                            </div>
                        </div>
                    </div>

                    <div className='utlx-dialog-footer'>
                        <button
                            className='utlx-dialog-button utlx-dialog-button-primary'
                            onClick={onClose}
                        >
                            Close
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    private renderField(field: FieldComparisonResult, depth: number, key: string): React.ReactNode {
        const hasChildren = field.children && field.children.length > 0;
        const isCollapsed = this.state.collapsed.has(key);

        const statusInfo = this.getStatusInfo(field.status);

        return (
            <React.Fragment key={key}>
                <div
                    className={`utlx-validation-row utlx-validation-row-${field.status}`}
                    style={{ paddingLeft: `${12 + depth * 20}px` }}
                >
                    <span className='utlx-validation-col-status'>
                        {hasChildren ? (
                            <span
                                className='utlx-validation-toggle'
                                onClick={() => this.toggleCollapse(key)}
                            >
                                {isCollapsed ? '▸' : '▾'}
                            </span>
                        ) : (
                            <span className='utlx-validation-toggle-spacer'></span>
                        )}
                        <span
                            className={`utlx-validation-icon ${statusInfo.className}`}
                            title={statusInfo.title}
                        >
                            {statusInfo.icon}
                        </span>
                    </span>
                    <span
                        className='utlx-validation-col-name'
                        onClick={hasChildren ? () => this.toggleCollapse(key) : undefined}
                        style={hasChildren ? { cursor: 'pointer' } : undefined}
                    >
                        {field.fieldName}
                    </span>
                    <span className='utlx-validation-col-type'>
                        {field.expectedType || '\u2014'}
                    </span>
                    <span className='utlx-validation-col-type'>
                        {field.inferredType || '\u2014'}
                    </span>
                </div>

                {/* Render children if not collapsed */}
                {hasChildren && !isCollapsed && field.children!.map((child, idx) =>
                    this.renderField(child, depth + 1, `${key}-${idx}`)
                )}
            </React.Fragment>
        );
    }

    private getStatusInfo(status: FieldComparisonStatus): {
        icon: string;
        className: string;
        title: string;
    } {
        switch (status) {
            case 'match':
                return { icon: '✓', className: 'status-match', title: 'Match' };
            case 'type-mismatch':
                return { icon: '✗', className: 'status-mismatch', title: 'Type mismatch' };
            case 'missing':
                return { icon: '⊘', className: 'status-missing', title: 'Missing in inferred output' };
            case 'extra':
                return { icon: '⊕', className: 'status-extra', title: 'Extra field (not in expected schema)' };
        }
    }

    private toggleCollapse(key: string): void {
        this.setState(prevState => {
            const newCollapsed = new Set(prevState.collapsed);
            if (newCollapsed.has(key)) {
                newCollapsed.delete(key);
            } else {
                newCollapsed.add(key);
            }
            return { collapsed: newCollapsed };
        });
    }
}
