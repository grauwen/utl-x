#!/bin/bash
#
# UTL-X Migration Script: @ to $ for Input References
#
# This script migrates UTL-X files from using @ for input references
# to using $ (keeping @ for XML attributes only).
#
# Usage:
#   ./migrate-at-to-dollar.sh <file-or-directory>
#   ./migrate-at-to-dollar.sh --dry-run <file-or-directory>
#   ./migrate-at-to-dollar.sh --help
#

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Flags
DRY_RUN=false
VERBOSE=false
BACKUP=true

# Statistics
FILES_PROCESSED=0
FILES_MODIFIED=0
REPLACEMENTS_MADE=0

usage() {
    cat << EOF
UTL-X Migration Script: @ to $ for Input References

Usage:
    $0 [OPTIONS] <file-or-directory>

Options:
    --dry-run           Show what would be changed without modifying files
    --no-backup         Don't create .bak backup files
    --verbose           Show detailed output
    --help              Show this help message

Examples:
    # Migrate a single file
    $0 transformation.utlx

    # Migrate all test files (dry run first)
    $0 --dry-run conformance-suite/tests/

    # Migrate with verbose output
    $0 --verbose --no-backup myfile.utlx

Description:
    Migrates UTL-X syntax from @ to $ for input references:
    - @input → \$input
    - @orders → \$orders
    - @customers → \$customers

    Preserves @ for XML attributes:
    - element.@id (no change)
    - Order.@customerId (no change)

EOF
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --no-backup)
            BACKUP=false
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            usage
            exit 0
            ;;
        -*)
            echo -e "${RED}Error: Unknown option $1${NC}"
            usage
            exit 1
            ;;
        *)
            TARGET="$1"
            shift
            ;;
    esac
done

# Check if target provided
if [ -z "${TARGET:-}" ]; then
    echo -e "${RED}Error: No file or directory specified${NC}"
    usage
    exit 1
fi

# Check if target exists
if [ ! -e "$TARGET" ]; then
    echo -e "${RED}Error: File or directory not found: $TARGET${NC}"
    exit 1
fi

# Log function
log() {
    if [ "$VERBOSE" = true ]; then
        echo -e "$@"
    fi
}

# Migrate a single file
migrate_file() {
    local file="$1"
    local changes=0

    FILES_PROCESSED=$((FILES_PROCESSED + 1))

    log "${BLUE}Processing: $file${NC}"

    # Read file content
    content=$(<"$file")

    # Migrate @ to $ for input references
    # This regex matches @identifier but NOT .@identifier
    # Pattern: @([a-zA-Z_][a-zA-Z0-9_]*) where @ is not preceded by .

    # Use perl for negative lookbehind (not supported in sed)
    migrated=$(echo "$content" | perl -pe 's/(?<!\.)@([a-zA-Z_][a-zA-Z0-9_]*)/\$$1/g')

    # Count changes
    if [ "$content" != "$migrated" ]; then
        FILES_MODIFIED=$((FILES_MODIFIED + 1))

        # Count number of replacements
        local orig_count=$(echo "$content" | grep -o '@[a-zA-Z_][a-zA-Z0-9_]*' | grep -v '\\.@' | wc -l || true)
        changes=$orig_count
        REPLACEMENTS_MADE=$((REPLACEMENTS_MADE + changes))

        echo -e "${GREEN}✓${NC} $file ${YELLOW}($changes replacements)${NC}"

        if [ "$DRY_RUN" = false ]; then
            # Create backup if requested
            if [ "$BACKUP" = true ]; then
                cp "$file" "$file.bak"
                log "  ${BLUE}Backup created: $file.bak${NC}"
            fi

            # Write migrated content
            echo "$migrated" > "$file"
            log "  ${GREEN}File updated${NC}"
        else
            log "  ${YELLOW}[DRY RUN] Would replace:${NC}"
            # Show diff
            diff_output=$(diff -u <(echo "$content") <(echo "$migrated") | head -20 || true)
            echo "$diff_output" | grep "^-@" | head -5 | while read -r line; do
                log "    ${RED}$line${NC}"
            done
            echo "$diff_output" | grep "^+\\\$" | head -5 | while read -r line; do
                log "    ${GREEN}$line${NC}"
            done
        fi
    else
        log "  ${BLUE}No changes needed${NC}"
    fi
}

# Process directory recursively
migrate_directory() {
    local dir="$1"

    echo -e "${BLUE}Scanning directory: $dir${NC}"

    # Find all .utlx and .yaml files
    while IFS= read -r -d '' file; do
        migrate_file "$file"
    done < <(find "$dir" -type f \( -name "*.utlx" -o -name "*.yaml" \) -print0)
}

# Main logic
main() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}UTL-X Migration: @ → \$ for Inputs${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""

    if [ "$DRY_RUN" = true ]; then
        echo -e "${YELLOW}DRY RUN MODE - No files will be modified${NC}"
        echo ""
    fi

    # Check if target is file or directory
    if [ -f "$TARGET" ]; then
        migrate_file "$TARGET"
    elif [ -d "$TARGET" ]; then
        migrate_directory "$TARGET"
    fi

    # Print summary
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}Migration Summary${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo -e "Files processed:  ${BLUE}$FILES_PROCESSED${NC}"
    echo -e "Files modified:   ${GREEN}$FILES_MODIFIED${NC}"
    echo -e "Replacements:     ${YELLOW}$REPLACEMENTS_MADE${NC}"

    if [ "$DRY_RUN" = true ]; then
        echo ""
        echo -e "${YELLOW}This was a DRY RUN. No files were modified.${NC}"
        echo -e "${YELLOW}Run without --dry-run to apply changes.${NC}"
    elif [ "$BACKUP" = true ] && [ $FILES_MODIFIED -gt 0 ]; then
        echo ""
        echo -e "${BLUE}Backups created with .bak extension${NC}"
        echo -e "${BLUE}To restore: for f in *.bak; do mv \"\$f\" \"\${f%.bak}\"; done${NC}"
    fi

    if [ $FILES_MODIFIED -eq 0 ]; then
        echo ""
        echo -e "${GREEN}✓ No files needed migration!${NC}"
    fi
}

# Run main
main

exit 0
