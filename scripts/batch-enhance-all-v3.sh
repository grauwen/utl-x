#!/bin/bash
# Batch enhance all stdlib function files with v3 annotations

echo "=== UTL-X Function Annotation Enhancement (v3) ==="
echo ""

# Find all *Functions.kt files
FILES=$(find stdlib/src/main/kotlin/org/apache/utlx/stdlib -name "*Functions.kt" -type f | grep -v "^stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt$" | sort)

TOTAL=$(echo "$FILES" | wc -l | tr -d ' ')
COUNT=0
ENHANCED=0

echo "Found $TOTAL function files to process"
echo ""

for file in $FILES; do
  COUNT=$((COUNT + 1))
  basename=$(basename "$file")
  echo "[$COUNT/$TOTAL] Processing: $basename"

  if [ ! -f "$file" ]; then
    echo "  ⚠️  File not found, skipping"
    continue
  fi

  # Run enhancement script
  python3 scripts/enhance-annotations-v3.py "$file" 2>&1 | grep -E "(Found|Adding|✓)" || echo "  No changes needed"

  if [ $? -eq 0 ]; then
    ENHANCED=$((ENHANCED + 1))
  fi

  echo ""
done

echo "=== Summary ==="
echo "Total files processed: $COUNT"
echo "Files enhanced: $ENHANCED"
echo ""
echo "Next steps:"
echo "1. Run: ./gradlew :stdlib:compileKotlin"
echo "2. Run: ./gradlew :stdlib:generateFunctionRegistry"
echo "3. Review generated registry at: stdlib/build/generated/function-registry/"
echo ""
