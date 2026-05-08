#!/bin/bash
# Serve the UI locally for development/testing.
# Requires a running UTLXe on localhost:8081 (admin) and :8085 (data).
#
# Usage:
#   ./gradlew :modules:engine:jar
#   java -jar modules/engine/build/libs/utlxe-*.jar --mode http &
#   cd utlxe-ui && ./serve-local.sh
#
# Then open http://localhost:8080 in your browser.
# API calls to /admin/* are proxied to localhost:8081.

PORT=${1:-8088}
ADMIN_PORT=${ADMIN_PORT:-8081}

echo "Serving UI on http://localhost:$PORT"
echo "Proxying /admin/* to localhost:$ADMIN_PORT (requires Docker for proxy)"
echo "Press Ctrl+C to stop"

# Use Python's http.server if available (no proxy, direct CORS)
# For full proxy support, use the Docker setup
if command -v python3 &>/dev/null; then
  cd static
  python3 -m http.server $PORT
else
  echo "Python3 not found. Use Docker: docker build -t utlxe-ui . && docker run -p 8080:8080 utlxe-ui"
fi
