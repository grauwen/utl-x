#!/usr/bin/env python3
"""
MCP Server Conformance Test Runner for UTL-X

This runner executes MCP Server tests validating the Model Context Protocol
JSON-RPC 2.0 implementation:
- Protocol compliance (initialize, tools/list, tools/call)
- All 6 MCP tools
- Transport modes (stdio)
- Error handling and edge cases

Test Format: YAML files with JSON-RPC request/response sequences
Protocol: JSON-RPC 2.0 over stdio

Note: This tests the MCP Server (port 3000 or stdio), which uses the daemon
REST API (port 7779) as its backend.
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
    - {{JSON}} - Matches any valid JSON string
    - {{REGEX:pattern}} - Matches against custom regex pattern
    """
    if not isinstance(expected, str):
        return False

    if not expected.startswith('{{') or not expected.endswith('}}'):
        return False

    placeholder = expected[2:-2].strip()

    if placeholder == 'ANY':
        return True

    if placeholder == 'NUMBER':
        return isinstance(actual, (int, float))

    if placeholder == 'STRING':
        return isinstance(actual, str)

    if placeholder == 'BOOLEAN':
        return isinstance(actual, bool)

    if placeholder == 'JSON':
        if not isinstance(actual, str):
            return False
        try:
            json.loads(actual)
            return True
        except:
            return False

    if placeholder in ['TIMESTAMP', 'ISO8601']:
        if not isinstance(actual, str):
            return False
        # Match ISO 8601 format
        iso_pattern = r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})?$'
        return bool(re.match(iso_pattern, actual))

    if placeholder == 'UUID':
        if not isinstance(actual, str):
            return False
        uuid_pattern = r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        return bool(re.match(uuid_pattern, actual, re.IGNORECASE))

    if placeholder.startswith('REGEX:'):
        if not isinstance(actual, str):
            return False
        pattern = placeholder[6:]
        return bool(re.search(pattern, actual))

    return False


def deep_compare(expected: Any, actual: Any, path: str = "root") -> Tuple[bool, Optional[str]]:
    """
    Deep comparison of expected and actual values with placeholder support.

    Returns (is_match, error_message)
    """
    # Handle placeholder
    if isinstance(expected, str) and expected.startswith('{{') and expected.endswith('}}'):
        if is_placeholder_match(expected, actual):
            return True, None
        return False, f"At {path}: Placeholder {expected} did not match {repr(actual)}"

    # Type mismatch
    if type(expected) != type(actual):
        return False, f"At {path}: Type mismatch - expected {type(expected).__name__}, got {type(actual).__name__}"

    # Dict comparison
    if isinstance(expected, dict):
        for key, expected_value in expected.items():
            if key not in actual:
                return False, f"At {path}: Missing key '{key}'"
            match, error = deep_compare(expected_value, actual[key], f"{path}.{key}")
            if not match:
                return False, error
        return True, None

    # List comparison
    if isinstance(expected, list):
        if len(expected) != len(actual):
            return False, f"At {path}: List length mismatch - expected {len(expected)}, got {len(actual)}"
        for i, (exp_item, act_item) in enumerate(zip(expected, actual)):
            match, error = deep_compare(exp_item, act_item, f"{path}[{i}]")
            if not match:
                return False, error
        return True, None

    # Direct comparison
    if expected != actual:
        return False, f"At {path}: Value mismatch - expected {repr(expected)}, got {repr(actual)}"

    return True, None


# ==================== MCP Server Manager ====================

class McpServerManager:
    """Manages MCP server lifecycle"""

    def __init__(self, server_dir: str, verbose: bool = False):
        self.server_dir = server_dir
        self.verbose = verbose
        self.process: Optional[subprocess.Popen] = None

    def start(self) -> bool:
        """Start MCP server with stdio transport"""
        server_path = os.path.join(self.server_dir, "dist", "index.js")

        if not os.path.exists(server_path):
            print_error(f"MCP server not found: {server_path}")
            print_info("Please build the MCP server first: cd mcp-server && npm run build")
            return False

        print_info("Starting MCP server...")

        try:
            self.process = subprocess.Popen(
                ["node", server_path],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1  # Line buffered
            )

            # Give server a moment to start
            time.sleep(0.5)

            # Check if process started successfully
            if self.process.poll() is not None:
                stderr = self.process.stderr.read()
                print_error(f"MCP server failed to start: {stderr}")
                return False

            print_success("MCP server started")
            return True

        except Exception as e:
            print_error(f"Failed to start MCP server: {e}")
            return False

    def stop(self):
        """Stop MCP server"""
        if self.process:
            print_info("Stopping MCP server...")
            try:
                self.process.terminate()
                self.process.wait(timeout=5)
                print_success("MCP server stopped")
            except subprocess.TimeoutExpired:
                print_warning("MCP server did not stop gracefully, killing...")
                self.process.kill()
            except Exception as e:
                print_error(f"Error stopping MCP server: {e}")

    def send_request(self, request: Dict) -> Optional[Dict]:
        """Send JSON-RPC request and get response"""
        if not self.process or not self.process.stdin or not self.process.stdout:
            print_error("MCP server not running")
            return None

        try:
            # Send request
            request_str = json.dumps(request) + "\n"
            if self.verbose:
                print_dim(f"→ {request_str.strip()}")

            self.process.stdin.write(request_str)
            self.process.stdin.flush()

            # Read response (one line)
            response_str = self.process.stdout.readline()
            if not response_str:
                print_error("No response from MCP server")
                return None

            if self.verbose:
                print_dim(f"← {response_str.strip()}")

            response = json.loads(response_str)
            return response

        except Exception as e:
            print_error(f"Error communicating with MCP server: {e}")
            return None


# ==================== MCP Server Test Runner ====================

class McpServerTestRunner:
    """Main test runner for MCP Server conformance tests"""

    def __init__(self, server_dir: str, verbose: bool = False):
        self.server_dir = server_dir
        self.verbose = verbose
        self.total_tests = 0
        self.passed_tests = 0
        self.failed_tests = 0
        self.server_manager: Optional[McpServerManager] = None

    def discover_tests(
        self,
        test_root: Path,
        category_filter: Optional[str] = None,
        tag_filter: Optional[str] = None,
        name_filter: Optional[str] = None
    ) -> List[Path]:
        """Discover test files in the test directory"""
        tests = []

        for yaml_file in test_root.rglob("*.yaml"):
            # Apply category filter
            if category_filter:
                rel_path = yaml_file.relative_to(test_root)
                if not str(rel_path).startswith(category_filter):
                    continue

            # Apply name filter
            if name_filter and name_filter.lower() not in yaml_file.stem.lower():
                continue

            # Apply tag filter (need to load file)
            if tag_filter:
                try:
                    with open(yaml_file, 'r') as f:
                        test_data = yaml.safe_load(f)
                        tags = test_data.get('tags', [])
                        if tag_filter not in tags:
                            continue
                except Exception:
                    continue

            tests.append(yaml_file)

        return sorted(tests)

    def run_test_file(self, test_file: Path) -> bool:
        """Run a single test file"""
        try:
            with open(test_file, 'r') as f:
                test_data = yaml.safe_load(f)

            test_name = test_data.get('name', test_file.stem)
            test_desc = test_data.get('description', '')

            print_header(f"Test: {test_name}")
            if test_desc:
                print_dim(f"  {test_desc}")

            sequence = test_data.get('sequence', [])
            if not sequence:
                print_warning(f"No test sequence found in {test_file}")
                return True

            # Run each step in sequence
            for step_idx, step in enumerate(sequence, 1):
                step_desc = step.get('description', f'Step {step_idx}')
                request = step.get('request')
                expect = step.get('expect')

                if not request or not expect:
                    print_warning(f"  Step {step_idx}: Missing request or expect")
                    continue

                if self.verbose:
                    print_info(f"  {step_desc}")

                # Send request
                response = self.server_manager.send_request(request)

                if response is None:
                    print_error(f"  {step_desc}: No response")
                    return False

                # Compare response
                match, error = deep_compare(expect, response)

                if match:
                    print_success(f"  {step_desc}")
                else:
                    print_error(f"  {step_desc}")
                    if error:
                        print_error(f"    {error}")
                    if self.verbose:
                        print_dim(f"    Expected: {json.dumps(expect, indent=2)}")
                        print_dim(f"    Got:      {json.dumps(response, indent=2)}")
                    return False

            return True

        except Exception as e:
            print_error(f"Error running test {test_file}: {e}")
            if self.verbose:
                import traceback
                traceback.print_exc()
            return False

    def run_tests(self, test_files: List[Path]) -> int:
        """Run all tests"""
        if not test_files:
            print_warning("No tests found")
            return 0

        print_header(f"Running {len(test_files)} test(s)")

        # Start MCP server
        self.server_manager = McpServerManager(self.server_dir, self.verbose)
        if not self.server_manager.start():
            return 1

        try:
            for test_file in test_files:
                self.total_tests += 1
                if self.run_test_file(test_file):
                    self.passed_tests += 1
                else:
                    self.failed_tests += 1

            # Print summary
            print_header("Test Summary")
            print(f"Total:  {self.total_tests}")
            print(f"{Colors.GREEN}Passed: {self.passed_tests}{Colors.RESET}")
            if self.failed_tests > 0:
                print(f"{Colors.RED}Failed: {self.failed_tests}{Colors.RESET}")

            success_rate = (self.passed_tests / self.total_tests * 100) if self.total_tests > 0 else 0
            print(f"Success rate: {success_rate:.1f}%")

            if self.failed_tests == 0:
                print_success("All tests passed!")
                return 0
            else:
                print_error(f"{self.failed_tests} test(s) failed")
                return 1

        finally:
            # Stop MCP server
            if self.server_manager:
                self.server_manager.stop()


# ==================== Main ====================

def main():
    parser = argparse.ArgumentParser(
        description="MCP Server Conformance Test Runner for UTL-X",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )

    parser.add_argument(
        'test_path',
        nargs='?',
        default='tests',
        help='Path to test file or directory (default: tests)'
    )
    parser.add_argument(
        '--category',
        help='Filter by category (e.g., protocol, tools/validate_utlx)'
    )
    parser.add_argument(
        '-t', '--tag',
        help='Filter by tag'
    )
    parser.add_argument(
        '-n', '--name',
        help='Filter by test name (substring match)'
    )
    parser.add_argument(
        '--server-dir',
        default='../../../../mcp-server',
        help='Path to MCP server directory (default: ../../../../mcp-server)'
    )
    parser.add_argument(
        '-v', '--verbose',
        action='store_true',
        help='Verbose output'
    )

    args = parser.parse_args()

    # Resolve paths
    script_dir = Path(__file__).parent.resolve()
    test_root = (script_dir / '../..' / args.test_path).resolve()
    server_dir = (script_dir / args.server_dir).resolve()

    if not test_root.exists():
        print_error(f"Test path not found: {test_root}")
        return 1

    if not server_dir.exists():
        print_error(f"MCP server directory not found: {server_dir}")
        return 1

    try:
        # Create runner
        runner = McpServerTestRunner(str(server_dir), args.verbose)

        # Discover tests
        tests = runner.discover_tests(
            test_root,
            category_filter=args.category,
            tag_filter=args.tag,
            name_filter=args.name
        )

        if not tests:
            print_warning("No tests found matching criteria")
            return 0

        # Run tests
        return runner.run_tests(tests)

    except KeyboardInterrupt:
        print_warning("\nTest run interrupted by user")
        return 130
    except Exception as e:
        print_error(f"Fatal error: {e}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        return 1


if __name__ == '__main__':
    sys.exit(main())
