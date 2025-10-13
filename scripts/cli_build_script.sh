#!/bin/bash
# scripts/build-cli.sh
# Complete build and test script for UTL-X CLI

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_header() {
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}→ $1${NC}"
}

# Check if we're in the right directory
if [ ! -f "settings.gradle.kts" ]; then
    print_error "This script must be run from the project root directory"
    exit 1
fi

print_header "UTL-X CLI Build Script"

# Parse arguments
BUILD_JAR=true
BUILD_NATIVE=false
RUN_TESTS=true
CREATE_EXAMPLES=true
VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --native)
            BUILD_NATIVE=true
            shift
            ;;
        --jar-only)
            BUILD_NATIVE=false
            shift
            ;;
        --skip-tests)
            RUN_TESTS=false
            shift
            ;;
        --no-examples)
            CREATE_EXAMPLES=false
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --native         Also build native binary (requires GraalVM)"
            echo "  --jar-only       Build only JAR, skip native"
            echo "  --skip-tests     Skip running tests"
            echo "  --no-examples    Don't create example files"
            echo "  --verbose        Show detailed output"
            echo "  --help           Show this help message"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Step 1: Clean previous builds
print_header "Step 1: Clean Previous Builds"
print_info "Cleaning build directories..."
./gradlew clean
print_success "Clean complete"

# Step 2: Build dependencies (core and formats)
print_header "Step 2: Build Dependencies"
print_info "Building core module..."
./gradlew :modules:core:build
print_success "Core module built"

print_info "Building formats modules..."
./gradlew :formats:xml:build
./gradlew :formats:json:build
./gradlew :formats:csv:build
print_success "Format modules built"

# Step 3: Run tests if requested
if [ "$RUN_TESTS" = true ]; then
    print_header "Step 3: Run Tests"
    print_info "Running CLI tests..."
    ./gradlew :modules:cli:test
    print_success "All tests passed"
else
    print_info "Skipping tests (--skip-tests flag)"
fi

# Step 4: Build JAR
if [ "$BUILD_JAR" = true ]; then
    print_header "Step 4: Build JAR"
    print_info "Building CLI JAR with dependencies..."
    ./gradlew :modules:cli:jar
    
    JAR_PATH="modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar"
    if [ -f "$JAR_PATH" ]; then
        JAR_SIZE=$(du -h "$JAR_PATH" | cut -f1)
        print_success "JAR built successfully: $JAR_PATH ($JAR_SIZE)"
    else
        print_error "JAR build failed"
        exit 1
    fi
fi

# Step 5: Build native binary if requested
if [ "$BUILD_NATIVE" = true ]; then
    print_header "Step 5: Build Native Binary"
    
    # Check if GraalVM is installed
    if ! command -v native-image &> /dev/null; then
        print_error "GraalVM native-image not found"
        echo "Please install GraalVM and run: ./scripts/install-graalvm.sh"
        exit 1
    fi
    
    print_info "Building native binary with GraalVM..."
    print_info "This may take 2-5 minutes..."
    
    if [ "$VERBOSE" = true ]; then
        ./gradlew :modules:cli:nativeCompile -Dverbose=true
    else
        ./gradlew :modules:cli:nativeCompile
    fi
    
    NATIVE_PATH="modules/cli/build/native/nativeCompile/utlx"
    if [ -f "$NATIVE_PATH" ]; then
        NATIVE_SIZE=$(du -h "$NATIVE_PATH" | cut -f1)
        print_success "Native binary built: $NATIVE_PATH ($NATIVE_SIZE)"
    else
        print_error "Native binary build failed"
        exit 1
    fi
fi

# Step 6: Create example files
if [ "$CREATE_EXAMPLES" = true ]; then
    print_header "Step 6: Create Example Files"
    
    EXAMPLES_DIR="examples/cli-test"
    mkdir -p "$EXAMPLES_DIR"
    
    # Create sample input XML
    cat > "$EXAMPLES_DIR/input.xml" << 'EOF'
<?xml version="1.0"?>
<Order id="ORD-001" date="2025-01-15">
  <Customer type="VIP">
    <Name>Alice Johnson</Name>
    <Email>alice@example.com</Email>
  </Customer>
  <Items>
    <Item sku="WIDGET-001" quantity="2" price="75.00"/>
    <Item sku="GADGET-002" quantity="1" price="150.00"/>
  </Items>
</Order>
EOF
    
    # Create sample input JSON
    cat > "$EXAMPLES_DIR/input.json" << 'EOF'
{
  "order": {
    "id": "ORD-001",
    "date": "2025-01-15",
    "customer": {
      "name": "Bob Smith",
      "email": "bob@example.com",
      "type": "Regular"
    },
    "items": [
      {"sku": "WIDGET-001", "quantity": 3, "price": 75.00},
      {"sku": "TOOL-003", "quantity": 1, "price": 200.00}
    ]
  }
}
EOF
    
    # Create sample transformation script
    cat > "$EXAMPLES_DIR/transform.utlx" << 'EOF'
%utlx 1.0
input auto
output json
---
{
  invoice: {
    id: "INV-" + input.order.id,
    customer: input.order.customer.name,
    items: input.order.items |> map(item => {
      sku: item.sku,
      total: item.quantity * item.price
    }),
    total: sum(input.order.items.(quantity * price))
  }
}
EOF
    
    print_success "Example files created in $EXAMPLES_DIR/"
fi

# Step 7: Test the CLI
print_header "Step 7: Test CLI"

if [ "$BUILD_JAR" = true ]; then
    print_info "Testing JAR version..."
    
    # Test with --version
    java -jar "$JAR_PATH" version
    
    # Test with example if created
    if [ "$CREATE_EXAMPLES" = true ]; then
        print_info "Running example transformation..."
        java -jar "$JAR_PATH" transform \
            "$EXAMPLES_DIR/input.json" \
            "$EXAMPLES_DIR/transform.utlx" \
            -o "$EXAMPLES_DIR/output.json" \
            --verbose
        
        if [ -f "$EXAMPLES_DIR/output.json" ]; then
            print_success "Transformation successful!"
            echo ""
            echo "Output:"
            cat "$EXAMPLES_DIR/output.json"
            echo ""
        fi
    fi
fi

if [ "$BUILD_NATIVE" = true ] && [ -f "$NATIVE_PATH" ]; then
    print_info "Testing native binary..."
    
    # Test with --version
    "$NATIVE_PATH" version
    
    print_success "Native binary works!"
fi

# Summary
print_header "Build Summary"

echo ""
if [ "$BUILD_JAR" = true ]; then
    echo "JAR Location:"
    echo "  $JAR_PATH"
    echo ""
    echo "Run with:"
    echo "  java -jar $JAR_PATH transform input.xml script.utlx"
    echo "  or use: ./modules/cli/scripts/utlx transform input.xml script.utlx"
    echo ""
fi

if [ "$BUILD_NATIVE" = true ] && [ -f "$NATIVE_PATH" ]; then
    echo "Native Binary:"
    echo "  $NATIVE_PATH"
    echo ""
    echo "Run with:"
    echo "  $NATIVE_PATH transform input.xml script.utlx"
    echo ""
    echo "Install system-wide:"
    echo "  sudo cp $NATIVE_PATH /usr/local/bin/utlx"
    echo ""
fi

if [ "$CREATE_EXAMPLES" = true ]; then
    echo "Example Files:"
    echo "  $EXAMPLES_DIR/"
    echo ""
    echo "Try it:"
    if [ "$BUILD_NATIVE" = true ] && [ -f "$NATIVE_PATH" ]; then
        echo "  $NATIVE_PATH transform $EXAMPLES_DIR/input.json $EXAMPLES_DIR/transform.utlx"
    else
        echo "  java -jar $JAR_PATH transform $EXAMPLES_DIR/input.json $EXAMPLES_DIR/transform.utlx"
    fi
    echo ""
fi

print_success "Build complete!"
