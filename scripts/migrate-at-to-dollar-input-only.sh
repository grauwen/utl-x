#!/bin/bash
# Script to migrate @input to $input (but NOT @attributes)
# This replaces only @input, @input1, @input2, etc.
# It does NOT replace @id, @name, @attribute, etc.

# Pattern explanation:
# - @input followed by word boundary (space, dot, bracket, etc.)
# - @input1, @input2, @input3, etc.

if [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    echo "Usage: $0 [--dry-run] <directory>"
    echo ""
    echo "Migrate @input to \$input in all .yaml, .kt, and .md files"
    echo "Does NOT replace XML attribute syntax like @id, @name, etc."
    echo ""
    echo "Options:"
    echo "  --dry-run    Show what would be changed without making changes"
    echo ""
    echo "Examples:"
    echo "  $0 --dry-run conformance-suite/tests/"
    echo "  $0 conformance-suite/tests/"
    exit 0
fi

DRY_RUN=false
if [ "$1" == "--dry-run" ]; then
    DRY_RUN=true
    shift
fi

TARGET_DIR="${1:-.}"

if [ ! -d "$TARGET_DIR" ]; then
    echo "Error: Directory '$TARGET_DIR' does not exist"
    exit 1
fi

echo "Migrating @input to \$input in: $TARGET_DIR"
if [ "$DRY_RUN" = true ]; then
    echo "DRY RUN MODE - no changes will be made"
fi
echo ""

# Find all relevant files
FILES=$(find "$TARGET_DIR" -type f \( -name "*.yaml" -o -name "*.kt" -o -name "*.md" \) -not -path "*/.git/*" -not -name "*.bak")

COUNT=0
for file in $FILES; do
    # Check if file contains @input pattern
    if grep -q '@input' "$file"; then
        if [ "$DRY_RUN" = true ]; then
            echo "Would update: $file"
            grep -n '@input' "$file" | head -3
            echo ""
        else
            # Create backup
            cp "$file" "$file.bak"

            # Replace @input (with word boundaries) with $input
            # Patterns:
            # - @input followed by . (property access)
            # - @input followed by space, ), ], }, comma, newline, etc.
            # - @input1, @input2, @input3, etc.
            sed -i '' \
                -e 's/@input\([^a-zA-Z0-9_]\)/$input\1/g' \
                -e 's/@input$/$input/g' \
                "$file"

            echo "Updated: $file"
        fi
        COUNT=$((COUNT + 1))
    fi
done

echo ""
echo "Total files processed: $COUNT"
if [ "$DRY_RUN" = false ]; then
    echo "Backup files created with .bak extension"
fi
