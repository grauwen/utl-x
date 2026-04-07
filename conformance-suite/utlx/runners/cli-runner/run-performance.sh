#!/bin/bash
# Run performance benchmark tests
exec "$(dirname "$0")/run-tests.sh" performance --performance "$@"