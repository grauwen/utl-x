#!/bin/bash

# Set environment variables for auto-start
export UTLXD_JAR_PATH="/Users/magr/data/mapping/github-git/utl-x/modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar"
export MCP_SERVER_PATH="/Users/magr/data/mapping/github-git/utl-x/mcp-server/dist/index.js"
export UTLXD_REST_PORT=7779
export MCP_SERVER_PORT=3001

echo "=== Starting Theia with Environment Variables ==="
echo "UTLXD_JAR_PATH=$UTLXD_JAR_PATH"
echo "MCP_SERVER_PATH=$MCP_SERVER_PATH"
echo "UTLXD_REST_PORT=$UTLXD_REST_PORT"
echo "MCP_SERVER_PORT=$MCP_SERVER_PORT"
echo ""

# Start Theia
yarn start
