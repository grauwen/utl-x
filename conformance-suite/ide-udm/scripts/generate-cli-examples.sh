#!/bin/bash

# Generate UDM examples from all 8 format types using CLI
# This validates that CLI → UDM conversion works for all formats
#
# Output files will be named: <format>_<example-name>_cli-generated.udm

set -e

EXAMPLES_DIR="/Users/magr/data/mapping/github-git/utl-x/examples"
UDM_DIR="/Users/magr/data/mapping/github-git/utl-x/examples/udm"
CLI="./utlxd"

echo "=============================================="
echo "Generating CLI-generated UDM examples"
echo "=============================================="
echo ""

# Check if CLI exists
if [ ! -f "$CLI" ]; then
    echo "❌ CLI not found at: $CLI"
    echo "Please build the project first: ./gradlew assemble"
    exit 1
fi

# Create output directory
mkdir -p "$UDM_DIR/cli-generated"

# Function to convert file to UDM
convert_to_udm() {
    local format=$1
    local input_file=$2
    local example_name=$3

    echo "📝 Converting $format: $example_name"

    local output_file="$UDM_DIR/cli-generated/${format}_${example_name}_cli-generated.udm"

    # Use CLI to parse and output as UDM
    if $CLI parse --format "$format" "$input_file" --output-format udm > "$output_file" 2>/dev/null; then
        echo "   ✅ Generated: $output_file"
        # Show first few lines
        head -10 "$output_file" | sed 's/^/   | /'
        echo ""
    else
        echo "   ⚠️  Failed to convert: $input_file"
        echo ""
    fi
}

echo "=== TIER 1 FORMATS ==="
echo ""

# 1. JSON
echo "--- JSON ---"
convert_to_udm "json" "$EXAMPLES_DIR/json/enterprise-order.json" "enterprise-order"
convert_to_udm "json" "$EXAMPLES_DIR/jsch/01_customer_profile.json" "customer-profile"
convert_to_udm "json" "$EXAMPLES_DIR/jsch/03_ecommerce_order.json" "ecommerce-order"

# 2. XML
echo "--- XML ---"
convert_to_udm "xml" "$EXAMPLES_DIR/xml/healthcare-claim.xml" "healthcare-claim"
convert_to_udm "xml" "$EXAMPLES_DIR/IDOC/Orders05-idoc.xml" "orders-idoc"

# 3. CSV
echo "--- CSV ---"
convert_to_udm "csv" "$EXAMPLES_DIR/csv/employees.csv" "employees"
convert_to_udm "csv" "$EXAMPLES_DIR/csv/sales-data.csv" "sales-data"

# 4. YAML
echo "--- YAML ---"
convert_to_udm "yaml" "$EXAMPLES_DIR/yaml/config.yaml" "config"

echo ""
echo "=== TIER 2 FORMATS ==="
echo ""

# 5. XSD (Schema)
echo "--- XSD ---"
convert_to_udm "xsd" "$EXAMPLES_DIR/xsd/purchase-order.xsd" "purchase-order-schema"
convert_to_udm "xsd" "$EXAMPLES_DIR/IDOC/ORDERS05_IDOC_Schema.xsd" "orders-idoc-schema"

# 6. JSON Schema
echo "--- JSON Schema ---"
convert_to_udm "jsch" "$EXAMPLES_DIR/jsch/01_customer_profile.schema.json" "customer-profile-schema"
convert_to_udm "jsch" "$EXAMPLES_DIR/jsch/03_ecommerce_order.schema.json" "ecommerce-order-schema"

# 7. Avro
echo "--- Avro ---"
convert_to_udm "avro" "$EXAMPLES_DIR/avro/user.avsc" "user-schema"

# 8. Protobuf
echo "--- Protobuf ---"
convert_to_udm "proto" "$EXAMPLES_DIR/protobuf/person.proto" "person-schema"

echo ""
echo "=============================================="
echo "✅ CLI UDM generation complete!"
echo "=============================================="
echo ""
echo "Generated files in: $UDM_DIR/cli-generated/"
ls -lh "$UDM_DIR/cli-generated/" | tail -n +2 | awk '{print "  - " $9 " (" $5 ")"}'
echo ""
