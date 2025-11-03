#!/usr/bin/env python3
"""
MCP Conformance Test Runner for UTL-X Daemon

This runner executes MCP (Model Context Protocol) REST API tests against the
UTL-X daemon server (utlxd), validating:
- Health and status endpoints
- MCP tool endpoints (transform, validate, schema generation)
- JSON-RPC 2.0 protocol compliance
- Session management
- Error handling and edge cases

Test Format: YAML files with HTTP request/response sequences
Protocol: HTTP/REST with JSON payloads
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
import yaml
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import urljoin

try:
    import requests
except ImportError:
    print("Error: 'requests' module not found. Install with: pip install requests")
    sys.exit(1)


# ==================== Color Output ====================

class Colors:
    """ANSI color codes for terminal output"""
    RESET = '\033[0m'
    RED = '\033[91m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    MAGENTA = '\033[95m'
    CYAN = '\033[96m'
    BOLD = '\033[1m'
    DIM = '\033[2m'


def print_header(text: str):
    """Print section header"""
    print(f"\n{Colors.BOLD}{Colors.CYAN}{text}{Colors.RESET}")


def print_success(text: str):
    """Print success message"""
    print(f"{Colors.GREEN}✓ {text}{Colors.RESET}")


def print_error(text: str):
    """Print error message"""
    print(f"{Colors.RED}✗ {text}{Colors.RESET}")


def print_warning(text: str):
    """Print warning message"""
    print(f"{Colors.YELLOW}⚠ {text}{Colors.RESET}")


def print_info(text: str):
    """Print info message"""
    print(f"{Colors.BLUE}ℹ {text}{Colors.RESET}")


def print_dim(text: str):
    """Print dimmed text"""
    print(f"{Colors.DIM}{text}{Colors.RESET}")


# ==================== Placeholder Matching ====================

def is_placeholder_match(expected: str, actual: Any) -> bool:
    """
    Check if an expected value is a placeholder and if the actual value matches it.

    Supported placeholders:
    - {{TIMESTAMP}} or {{ISO8601}} - Matches ISO 8601 timestamp strings
    - {{UUID}} - Matches UUID v4 strings
    - {{ANY}} - Matches any value
    - {{NUMBER}} - Matches any numeric value
    - {{STRING}} - Matches any string value
    - {{BOOLEAN}} - Matches boolean values
    - {{REGEX:pattern}} - Matches against custom regex pattern
    """
    if not isinstance(expected, str):
        return False

    if not expected.startswith('{{') or not expected.endswith('}}'):
        return False

    placeholder = expected[2:-2].strip()

    # {{ANY}} - accepts any value
    if placeholder == 'ANY':
        return True

    # {{TIMESTAMP}} or {{ISO8601}} - validate ISO 8601 timestamp
    if placeholder in ['TIMESTAMP', 'ISO8601']:
        if not isinstance(actual, str):
            return False
        iso_pattern = r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})?$'
        return re.match(iso_pattern, actual) is not None

    # {{UUID}} - validate UUID format
    if placeholder == 'UUID':
        if not isinstance(actual, str):
            return False
        uuid_pattern = r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        return re.match(uuid_pattern, actual.lower()) is not None

    # {{NUMBER}} - validate numeric value
    if placeholder == 'NUMBER':
        return isinstance(actual, (int, float))

    # {{STRING}} - validate string value
    if placeholder == 'STRING':
        return isinstance(actual, str)

    # {{BOOLEAN}} - validate boolean value
    if placeholder == 'BOOLEAN':
        return isinstance(actual, bool)

    # {{REGEX:pattern}} - custom regex matching
    if placeholder.startswith('REGEX:'):
        pattern = placeholder[6:].strip()
        if not isinstance(actual, str):
            return False
        try:
            return re.match(pattern, actual) is not None
        except re.error:
            print_warning(f"Invalid regex pattern: {pattern}")
            return False

    return False


def deep_compare(expected: Any, actual: Any, path: str = "$") -> List[str]:
    """
    Deep compare two values, supporting placeholders.

    Returns a list of error messages (empty if match).
    """
    errors = []

    # Handle placeholders
    if isinstance(expected, str) and is_placeholder_match(expected, actual):
        return []

    # Type mismatch
    if type(expected) != type(actual):
        # Allow int/float comparison
        if isinstance(expected, (int, float)) and isinstance(actual, (int, float)):
            if expected != actual:
                errors.append(f"{path}: expected {expected}, got {actual}")
        else:
            errors.append(f"{path}: type mismatch - expected {type(expected).__name__}, got {type(actual).__name__}")
        return errors

    # Compare by type
    if isinstance(expected, dict):
        # Check all expected keys are present
        for key in expected.keys():
            if key not in actual:
                errors.append(f"{path}.{key}: missing in actual")
            else:
                errors.extend(deep_compare(expected[key], actual[key], f"{path}.{key}"))
    elif isinstance(expected, list):
        if len(expected) != len(actual):
            errors.append(f"{path}: length mismatch - expected {len(expected)}, got {len(actual)}")
        else:
            for i, (exp_item, act_item) in enumerate(zip(expected, actual)):
                errors.extend(deep_compare(exp_item, act_item, f"{path}[{i}]"))
    else:
        # Scalar comparison
        if expected != actual:
            errors.append(f"{path}: expected {repr(expected)}, got {repr(actual)}")

    return errors


# ==================== Daemon Management ====================

class DaemonManager:
    """Manages the utlxd daemon lifecycle"""

    def __init__(self, jar_path: str, port: int, verbose: bool = False):
        self.jar_path = jar_path
        self.port = port
        self.verbose = verbose
        self.process: Optional[subprocess.Popen] = None

    def is_running(self, host: str = "localhost", port: int = None) -> bool:
        """Check if daemon is already running"""
        port = port or self.port
        try:
            response = requests.get(f"http://{host}:{port}/health", timeout=2)
            return response.status_code == 200
        except requests.RequestException:
            return False

    def start(self, wait_seconds: int = 5) -> bool:
        """Start the daemon if not already running"""
        if self.is_running():
            if self.verbose:
                print_info(f"Daemon already running on port {self.port}")
            return True

        if not os.path.exists(self.jar_path):
            print_error(f"Daemon JAR not found: {self.jar_path}")
            return False

        print_info(f"Starting daemon on port {self.port}...")
        cmd = [
            "java", "-jar", self.jar_path,
            "start", "--rest-api", "--no-lsp",
            "--port", str(self.port)
        ]

        log_file = open("/tmp/utlxd_conformance.log", "w")
        self.process = subprocess.Popen(
            cmd,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            start_new_session=True  # Detach from parent
        )

        # Wait for daemon to start
        for i in range(wait_seconds * 2):
            time.sleep(0.5)
            if self.is_running():
                print_success(f"Daemon started successfully (PID: {self.process.pid})")
                return True

        print_error("Daemon failed to start within timeout")
        return False

    def stop(self):
        """Stop the daemon"""
        if self.process:
            print_info("Stopping daemon...")
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
                print_success("Daemon stopped")
            except subprocess.TimeoutExpired:
                print_warning("Daemon did not stop gracefully, killing...")
                self.process.kill()
        else:
            # Try to stop any running daemon on the port
            subprocess.run(
                ["pkill", "-f", f"utlxd.*--port {self.port}"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )


# ==================== MCP Test Runner ====================

class McpTestRunner:
    """Main test runner for MCP conformance tests"""

    def __init__(self, base_url: str, verbose: bool = False):
        self.base_url = base_url.rstrip('/')
        self.verbose = verbose
        self.total_tests = 0
        self.passed_tests = 0
        self.failed_tests = 0
        self.skipped_tests = 0

    def load_test_file(self, filepath: Path) -> Optional[Dict]:
        """Load and parse a YAML test file"""
        try:
            with open(filepath, 'r') as f:
                test_data = yaml.safe_load(f)
            return test_data
        except Exception as e:
            print_error(f"Failed to load {filepath}: {e}")
            return None

    def execute_request(self, step: Dict) -> Tuple[bool, Dict, str]:
        """
        Execute a single HTTP request step.

        Returns: (success, response_data, error_message)
        """
        method = step.get('method', 'GET').upper()
        endpoint = step.get('endpoint', '/')
        headers = step.get('headers', {})
        body = step.get('body')
        timeout = step.get('timeout', 10)

        url = urljoin(self.base_url, endpoint)

        if self.verbose:
            print_dim(f"    {method} {endpoint}")
            if body:
                print_dim(f"    Body: {json.dumps(body, indent=2)}")

        try:
            if method == 'GET':
                response = requests.get(url, headers=headers, timeout=timeout)
            elif method == 'POST':
                response = requests.post(url, json=body, headers=headers, timeout=timeout)
            elif method == 'PUT':
                response = requests.put(url, json=body, headers=headers, timeout=timeout)
            elif method == 'DELETE':
                response = requests.delete(url, headers=headers, timeout=timeout)
            else:
                return False, {}, f"Unsupported HTTP method: {method}"

            # Parse response
            try:
                response_data = {
                    'status': response.status_code,
                    'headers': dict(response.headers),
                    'body': response.json() if response.text else {}
                }
            except json.JSONDecodeError:
                response_data = {
                    'status': response.status_code,
                    'headers': dict(response.headers),
                    'body': response.text
                }

            return True, response_data, ""

        except requests.Timeout:
            return False, {}, f"Request timed out after {timeout}s"
        except requests.RequestException as e:
            return False, {}, f"Request failed: {str(e)}"

    def validate_response(self, actual: Dict, expected: Dict) -> List[str]:
        """Validate response against expectations"""
        errors = []

        # Validate status code
        if 'status' in expected:
            expected_status = expected['status']
            actual_status = actual.get('status', 0)
            if expected_status != actual_status:
                errors.append(f"Status: expected {expected_status}, got {actual_status}")

        # Validate headers
        if 'headers' in expected:
            for header_name, expected_value in expected['headers'].items():
                actual_value = actual.get('headers', {}).get(header_name)
                if isinstance(expected_value, str) and is_placeholder_match(expected_value, actual_value):
                    continue
                if actual_value != expected_value:
                    errors.append(f"Header '{header_name}': expected {expected_value}, got {actual_value}")

        # Validate body
        if 'body' in expected:
            body_errors = deep_compare(expected['body'], actual.get('body', {}), "body")
            errors.extend(body_errors)

        return errors

    def run_test(self, test_file: Path) -> bool:
        """Run a single test file"""
        self.total_tests += 1
        test_data = self.load_test_file(test_file)
        if not test_data:
            self.failed_tests += 1
            return False

        test_name = test_data.get('name', test_file.stem)
        description = test_data.get('description', '')
        category = test_data.get('category', '')
        tags = test_data.get('tags', [])
        sequence = test_data.get('sequence', [])

        print_header(f"Test: {test_name}")
        if description:
            print_dim(f"  {description}")
        if category:
            print_dim(f"  Category: {category}")
        if tags:
            print_dim(f"  Tags: {', '.join(tags)}")

        # Execute sequence
        for step_idx, step in enumerate(sequence, 1):
            step_type = step.get('type', 'request')

            if step_type != 'request':
                print_warning(f"  Step {step_idx}: Unknown type '{step_type}', skipping")
                continue

            # Execute request
            success, response, error = self.execute_request(step)

            if not success:
                print_error(f"  Step {step_idx}: {error}")
                self.failed_tests += 1
                return False

            # Validate response
            if 'expect' in step:
                validation_errors = self.validate_response(response, step['expect'])
                if validation_errors:
                    print_error(f"  Step {step_idx}: Validation failed:")
                    for err in validation_errors:
                        print_error(f"    {err}")
                    self.failed_tests += 1
                    return False

            if self.verbose:
                print_success(f"  Step {step_idx}: Passed")

        print_success(f"{test_name}: PASSED")
        self.passed_tests += 1
        return True

    def discover_tests(self, test_path: Path, category_filter: Optional[str] = None,
                      tag_filter: Optional[List[str]] = None, name_filter: Optional[str] = None) -> List[Path]:
        """Discover test files matching filters"""
        tests = []

        if test_path.is_file():
            return [test_path]

        # Find all YAML files recursively
        for yaml_file in test_path.rglob("*.yaml"):
            # Apply filters
            if category_filter:
                category_parts = category_filter.split('/')
                file_parts = yaml_file.relative_to(test_path).parts
                if not all(part in file_parts for part in category_parts):
                    continue

            if name_filter and name_filter not in yaml_file.stem:
                continue

            # Load file to check tags
            if tag_filter:
                test_data = self.load_test_file(yaml_file)
                if not test_data:
                    continue
                test_tags = test_data.get('tags', [])
                if not any(tag in test_tags for tag in tag_filter):
                    continue

            tests.append(yaml_file)

        return sorted(tests)

    def run_tests(self, test_files: List[Path]) -> int:
        """Run all tests and print summary"""
        if not test_files:
            print_warning("No tests found matching criteria")
            return 1

        start_time = time.time()

        for test_file in test_files:
            self.run_test(test_file)

        elapsed = time.time() - start_time

        # Print summary
        print_header(f"\n{'='*60}")
        print_header("Test Summary")
        print_header(f"{'='*60}")
        print(f"{Colors.BOLD}Total:{Colors.RESET}   {self.total_tests}")
        print(f"{Colors.GREEN}Passed:{Colors.RESET}  {self.passed_tests}")
        print(f"{Colors.RED}Failed:{Colors.RESET}  {self.failed_tests}")
        if self.skipped_tests > 0:
            print(f"{Colors.YELLOW}Skipped:{Colors.RESET} {self.skipped_tests}")
        print(f"{Colors.CYAN}Time:{Colors.RESET}    {elapsed:.2f}s")

        return 0 if self.failed_tests == 0 else 1


# ==================== Main ====================

def main():
    parser = argparse.ArgumentParser(
        description="MCP Conformance Test Runner for UTL-X",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )

    parser.add_argument(
        'test_path',
        nargs='?',
        default='tests',
        help='Path to test file or directory (default: tests/)'
    )
    parser.add_argument(
        '-c', '--category',
        help='Filter tests by category (e.g., protocol, endpoints)'
    )
    parser.add_argument(
        '-t', '--tag',
        action='append',
        help='Filter tests by tag (can specify multiple times)'
    )
    parser.add_argument(
        '-n', '--name',
        help='Filter tests by name (substring match)'
    )
    parser.add_argument(
        '--host',
        default='localhost',
        help='Daemon host (default: localhost)'
    )
    parser.add_argument(
        '--port',
        type=int,
        default=7778,
        help='Daemon port (default: 7778)'
    )
    parser.add_argument(
        '--no-auto-start',
        action='store_true',
        help='Do not auto-start daemon'
    )
    parser.add_argument(
        '--jar',
        default='../../modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar',
        help='Path to utlxd JAR file'
    )
    parser.add_argument(
        '-v', '--verbose',
        action='store_true',
        help='Verbose output'
    )

    args = parser.parse_args()

    # Resolve paths
    script_dir = Path(__file__).parent
    test_root = script_dir.parent / args.test_path
    jar_path = script_dir / args.jar

    if not test_root.exists():
        print_error(f"Test path not found: {test_root}")
        return 1

    base_url = f"http://{args.host}:{args.port}"

    # Start daemon if needed
    daemon = DaemonManager(str(jar_path), args.port, args.verbose)
    daemon_started_by_us = False

    if not args.no_auto_start:
        if not daemon.is_running(args.host, args.port):
            if not daemon.start():
                print_error("Failed to start daemon")
                return 1
            daemon_started_by_us = True

    try:
        # Create runner
        runner = McpTestRunner(base_url, args.verbose)

        # Discover tests
        tests = runner.discover_tests(
            test_root,
            category_filter=args.category,
            tag_filter=args.tag,
            name_filter=args.name
        )

        print_info(f"Found {len(tests)} test(s)")

        # Run tests
        exit_code = runner.run_tests(tests)

        return exit_code

    finally:
        # Cleanup daemon if we started it
        if daemon_started_by_us:
            daemon.stop()


if __name__ == '__main__':
    sys.exit(main())
