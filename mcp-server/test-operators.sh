#!/bin/bash

echo "Testing get_operators MCP tool..."
echo ""

# Test 1: List tools
echo "Test 1: Checking if get_operators is in tools list..."
TOOLS_COUNT=$(echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | node dist/index.js 2>/dev/null | jq -r '.result.tools | length')
echo "Total tools: $TOOLS_COUNT"

# Test 2: Get all operators
echo ""
echo "Test 2: Getting all operators..."
echo '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_operators","arguments":{}}}' | \
  timeout 3 node dist/index.js 2>/dev/null | \
  jq -r '.result.content[0].text' | \
  jq '{success, count, message}'

# Test 3: Filter by category
echo ""
echo "Test 3: Getting Arithmetic operators only..."
echo '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_operators","arguments":{"category":"Arithmetic"}}}' | \
  timeout 3 node dist/index.js 2>/dev/null | \
  jq -r '.result.content[0].text' | \
  jq '{success, count, operators: [.operators[] | .symbol]}'

echo ""
echo "All tests completed!"
