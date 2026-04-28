#!/bin/bash

# Test script to verify stdlib integration
echo "Testing UTL-X Standard Library Integration"
echo "=========================================="

# Build the project
echo "1. Building project..."
./gradlew build -x test || {
    echo "❌ Build failed"
    exit 1
}

echo "✅ Build successful"

# Run stdlib integration tests
echo "2. Running stdlib integration tests..."
./gradlew :modules:core:test --tests "*StandardLibraryIntegrationTest*" || {
    echo "❌ Integration tests failed"
    exit 1
}

echo "✅ Integration tests passed"

# Try to build CLI with stdlib functions
echo "3. Building CLI..."
./gradlew :modules:cli:jar || {
    echo "❌ CLI build failed"
    exit 1
}

echo "✅ CLI build successful"

# Test the functions command
echo "4. Testing functions command..."
java -jar modules/cli/build/libs/cli-1.0.2.jar functions --help || {
    echo "❌ Functions command failed"
    exit 1
}

echo "✅ Functions command working"

echo ""
echo "🎉 All tests passed! Standard library successfully integrated into UTL-X CLI and interpreter."
echo ""
echo "Try these commands:"
echo "  java -jar modules/cli/build/libs/cli-1.0.2.jar functions"
echo "  java -jar modules/cli/build/libs/cli-1.0.2.jar functions --module string"
echo "  java -jar modules/cli/build/libs/cli-1.0.2.jar functions --search date"