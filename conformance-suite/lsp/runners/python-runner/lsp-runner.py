#!/usr/bin/env python3
"""
LSP Conformance Test Runner for UTL-X

This runner executes LSP protocol tests against the UTL-X daemon, validating:
- Standard LSP methods (initialize, hover, completion, diagnostics)
- Custom UTL-X methods (utlx/loadSchema, utlx/setMode, utlx/inferOutputSchema)
- Design-time and runtime mode behaviors

Test Format: YAML files with sequences of requests, notifications, and expectations
Protocol: JSON-RPC 2.0 over stdio
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
from threading import Thread, Lock
from queue import Queue, Empty


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


# ==================== JSON-RPC 2.0 Client ====================

class JsonRpcClient:
    """
    JSON-RPC 2.0 client for LSP communication over stdio.

    Handles bidirectional communication with LSP daemon:
    - Outgoing: requests and notifications
    - Incoming: responses and notifications
    """

    def __init__(self, process: subprocess.Popen, verbose: bool = False):
        self.process = process
        self.verbose = verbose
        self.request_id = 0
        self.pending_responses: Dict[int, Dict[str, Any]] = {}
        self.notifications: Queue = Queue()
        self.lock = Lock()

        # Start reader thread
        self.reader_thread = Thread(target=self._read_messages, daemon=True)
        self.reader_thread.start()

    def _read_messages(self):
        """Background thread to read messages from daemon"""
        while self.process.poll() is None:
            try:
                message = self._read_message()
                if message:
                    self._handle_message(message)
            except Exception as e:
                if self.verbose:
                    print_error(f"Error reading message: {e}")
                break

    def _read_message(self) -> Optional[Dict[str, Any]]:
        """Read a single JSON-RPC message from daemon"""
        try:
            # Read headers
            headers = {}
            while True:
                line = self.process.stdout.readline().decode('utf-8')
                if line == '\r\n' or line == '\n':
                    break
                if ':' in line:
                    key, value = line.split(':', 1)
                    headers[key.strip()] = value.strip()

            # Read content
            content_length = int(headers.get('Content-Length', 0))
            if content_length == 0:
                return None

            content = self.process.stdout.read(content_length).decode('utf-8')
            message = json.loads(content)

            if self.verbose:
                print_dim(f"← {json.dumps(message, indent=2)}")

            return message
        except Exception as e:
            if self.verbose:
                print_error(f"Failed to read message: {e}")
            return None

    def _handle_message(self, message: Dict[str, Any]):
        """Handle incoming message (response or notification)"""
        if 'id' in message:
            # Response to our request
            request_id = message['id']
            with self.lock:
                self.pending_responses[request_id] = message
        elif 'method' in message:
            # Server notification
            self.notifications.put(message)

    def _send_message(self, message: Dict[str, Any]):
        """Send JSON-RPC message to daemon"""
        content = json.dumps(message)
        headers = f"Content-Length: {len(content)}\r\n\r\n"
        full_message = headers + content

        if self.verbose:
            print_dim(f"→ {json.dumps(message, indent=2)}")

        self.process.stdin.write(full_message.encode('utf-8'))
        self.process.stdin.flush()

    def send_request(self, method: str, params: Dict[str, Any]) -> int:
        """
        Send JSON-RPC request and return request ID

        Returns: request_id for later retrieval of response
        """
        self.request_id += 1
        request = {
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": method,
            "params": params
        }
        self._send_message(request)
        return self.request_id

    def send_notification(self, method: str, params: Dict[str, Any]):
        """Send JSON-RPC notification (no response expected)"""
        notification = {
            "jsonrpc": "2.0",
            "method": method,
            "params": params
        }
        self._send_message(notification)

    def wait_for_response(self, request_id: int, timeout: float = 5.0) -> Optional[Dict[str, Any]]:
        """Wait for response to a specific request"""
        start_time = time.time()
        while time.time() - start_time < timeout:
            with self.lock:
                if request_id in self.pending_responses:
                    response = self.pending_responses.pop(request_id)
                    return response
            time.sleep(0.01)
        return None

    def wait_for_notification(self, method: str, timeout: float = 5.0) -> Optional[Dict[str, Any]]:
        """Wait for a notification with specific method"""
        start_time = time.time()
        collected = []

        while time.time() - start_time < timeout:
            try:
                notification = self.notifications.get(timeout=0.1)
                if notification.get('method') == method:
                    return notification
                else:
                    # Put it back for other waiters
                    collected.append(notification)
            except Empty:
                pass

        # Put back collected notifications
        for notif in collected:
            self.notifications.put(notif)

        return None


# ==================== Template Engine ====================

class TemplateEngine:
    """
    Template variable substitution for test files.

    Supports:
    - {{documents.test_doc.uri}} - document properties
    - {{variables.foo}} - custom variables
    - {{TIMESTAMP}}, {{UUID}} - dynamic placeholders
    """

    def __init__(self, documents: Dict[str, Any], variables: Optional[Dict[str, Any]] = None):
        self.documents = documents
        self.variables = variables or {}
        self.placeholders = {}

    def substitute(self, value: Any) -> Any:
        """Recursively substitute templates in value"""
        if isinstance(value, str):
            return self._substitute_string(value)
        elif isinstance(value, dict):
            return {k: self.substitute(v) for k, v in value.items()}
        elif isinstance(value, list):
            return [self.substitute(item) for item in value]
        else:
            return value

    def _substitute_string(self, text: str) -> str:
        """Substitute template variables in string"""
        # Match {{path.to.value}}
        pattern = r'\{\{([^}]+)\}\}'

        def replacer(match):
            path = match.group(1).strip()
            return str(self._resolve_path(path))

        return re.sub(pattern, replacer, text)

    def _resolve_path(self, path: str) -> Any:
        """Resolve dot-separated path to value"""
        parts = path.split('.')

        # Try documents first
        if parts[0] == 'documents' and len(parts) >= 2:
            doc_name = parts[1]
            if doc_name in self.documents:
                value = self.documents[doc_name]
                for part in parts[2:]:
                    if isinstance(value, dict):
                        value = value.get(part)
                    else:
                        return f"{{{{{path}}}}}"  # Keep original if not found
                return value

        # Try variables
        if parts[0] == 'variables':
            value = self.variables
            for part in parts[1:]:
                if isinstance(value, dict):
                    value = value.get(part)
                else:
                    return f"{{{{{path}}}}}"
            return value

        # Unknown path
        return f"{{{{{path}}}}}"


# ==================== Response Validator ====================

class ResponseValidator:
    """
    Validates LSP responses against expected values.

    Supports:
    - Exact matching
    - 'contains' checks for arrays and strings
    - Nested object validation
    - Null checks
    """

    @staticmethod
    def validate(actual: Any, expected: Any, path: str = "result") -> Tuple[bool, List[str]]:
        """
        Validate actual response against expected pattern.

        Returns: (success, errors)
        """
        errors = []

        if expected is None:
            if actual is not None:
                errors.append(f"{path}: expected null, got {type(actual).__name__}")
            return len(errors) == 0, errors

        if isinstance(expected, dict):
            # Check for 'contains' special key FIRST (before type checking)
            # This allows 'contains' to work with strings or other types
            if 'contains' in expected:
                success, errs = ResponseValidator._validate_contains(actual, expected['contains'], path)
                errors.extend(errs)
                # Don't check other keys if contains is present
                return success, errors

            # Now check if actual is dict for other validations
            if not isinstance(actual, dict):
                errors.append(f"{path}: expected object, got {type(actual).__name__}")
                return False, errors

            # Validate each expected key
            for key, expected_value in expected.items():
                actual_value = actual.get(key)
                success, errs = ResponseValidator.validate(actual_value, expected_value, f"{path}.{key}")
                errors.extend(errs)

        elif isinstance(expected, list):
            if not isinstance(actual, list):
                errors.append(f"{path}: expected array, got {type(actual).__name__}")
                return False, errors

            if len(actual) != len(expected):
                errors.append(f"{path}: expected {len(expected)} items, got {len(actual)}")
                return False, errors

            for i, (actual_item, expected_item) in enumerate(zip(actual, expected)):
                success, errs = ResponseValidator.validate(actual_item, expected_item, f"{path}[{i}]")
                errors.extend(errs)

        else:
            # Primitive value
            if actual != expected:
                errors.append(f"{path}: expected {expected!r}, got {actual!r}")

        return len(errors) == 0, errors

    @staticmethod
    def _validate_contains(actual: Any, expected_contains: Any, path: str) -> Tuple[bool, List[str]]:
        """Validate 'contains' expectation"""
        errors = []

        if isinstance(expected_contains, list):
            # Check if actual contains all expected items
            if isinstance(actual, str):
                # String contains all substrings
                for item in expected_contains:
                    if str(item) not in actual:
                        errors.append(f"{path}: expected to contain {item!r}")
            elif isinstance(actual, list):
                # Array contains all items
                for item in expected_contains:
                    if item not in actual:
                        errors.append(f"{path}: expected to contain {item!r}")
            elif isinstance(actual, dict):
                # Check if dict contains all expected values (recursive)
                actual_str = json.dumps(actual)
                for item in expected_contains:
                    if str(item) not in actual_str:
                        errors.append(f"{path}: expected to contain {item!r}")
            else:
                errors.append(f"{path}: cannot check 'contains' on {type(actual).__name__}")
        else:
            errors.append(f"{path}: 'contains' must be an array")

        return len(errors) == 0, errors


# ==================== LSP Daemon Manager ====================

class LSPDaemonManager:
    """Manages UTL-X LSP daemon process lifecycle"""

    def __init__(self, server_jar: str, port: int = 7778, verbose: bool = False):
        self.server_jar = server_jar
        self.port = port
        self.verbose = verbose
        self.process: Optional[subprocess.Popen] = None
        self.client: Optional[JsonRpcClient] = None

    def start(self) -> JsonRpcClient:
        """Start daemon process and return JSON-RPC client"""
        # UTL-X daemon is now in server module: java -jar utlxd.jar design daemon --stdio
        cmd = [
            'java', '-jar', self.server_jar,
            'design', 'daemon',
            '--stdio'
        ]

        if self.verbose:
            print_info(f"Starting daemon: {' '.join(cmd)}")

        self.process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE if not self.verbose else None
        )

        # Wait a bit for daemon to start
        time.sleep(0.5)

        if self.process.poll() is not None:
            raise RuntimeError(f"Daemon failed to start (exit code: {self.process.returncode})")

        self.client = JsonRpcClient(self.process, verbose=self.verbose)

        if self.verbose:
            print_success("Daemon started")

        return self.client

    def stop(self):
        """Stop daemon process"""
        if self.process:
            try:
                # Try shutdown request first
                if self.client:
                    self.client.send_request("shutdown", {})
                    time.sleep(0.2)
                    self.client.send_notification("exit", {})
                    time.sleep(0.2)

                # Force kill if still running
                if self.process.poll() is None:
                    self.process.terminate()
                    time.sleep(0.5)
                    if self.process.poll() is None:
                        self.process.kill()

                if self.verbose:
                    print_info("Daemon stopped")
            except Exception as e:
                if self.verbose:
                    print_warning(f"Error stopping daemon: {e}")


# ==================== Test Executor ====================

class TestExecutor:
    """Executes a single LSP test case"""

    def __init__(self, test_file: Path, server_jar: str, verbose: bool = False):
        self.test_file = test_file
        self.server_jar = server_jar
        self.verbose = verbose
        self.test_data: Optional[Dict[str, Any]] = None
        self.template_engine: Optional[TemplateEngine] = None
        self.daemon: Optional[LSPDaemonManager] = None
        self.client: Optional[JsonRpcClient] = None

    def load_test(self) -> bool:
        """Load and parse test YAML file"""
        try:
            with open(self.test_file, 'r') as f:
                self.test_data = yaml.safe_load(f)

            # Initialize template engine with documents
            documents = self.test_data.get('documents', {})
            variables = self.test_data.get('variables', {})
            self.template_engine = TemplateEngine(documents, variables)

            return True
        except Exception as e:
            print_error(f"Failed to load test: {e}")
            return False

    def execute(self) -> bool:
        """Execute test sequence and return success status"""
        if not self.test_data:
            return False

        test_name = self.test_data.get('name', self.test_file.stem)
        description = self.test_data.get('description', '')

        print_header(f"Running: {test_name}")
        if description:
            print_dim(f"  {description}")

        # Start daemon
        try:
            self.daemon = LSPDaemonManager(self.server_jar, verbose=self.verbose)
            self.client = self.daemon.start()
        except Exception as e:
            print_error(f"Failed to start daemon: {e}")
            return False

        # Execute sequence
        sequence = self.test_data.get('sequence', [])
        success = True

        for i, step in enumerate(sequence):
            step_type = step.get('type')

            if self.verbose:
                print_info(f"Step {i+1}: {step_type} - {step.get('method', 'N/A')}")

            if step_type == 'request':
                if not self._execute_request(step, i+1):
                    success = False
                    break
            elif step_type == 'notification':
                if not self._execute_notification(step, i+1):
                    success = False
                    break
            elif step_type == 'expect_notification':
                if not self._execute_expect_notification(step, i+1):
                    success = False
                    break
            else:
                print_warning(f"Unknown step type: {step_type}")

        # Stop daemon
        if self.daemon:
            self.daemon.stop()

        if success:
            print_success(f"PASSED: {test_name}")
        else:
            print_error(f"FAILED: {test_name}")

        return success

    def _execute_request(self, step: Dict[str, Any], step_num: int) -> bool:
        """Execute request step"""
        method = step.get('method')
        params = self.template_engine.substitute(step.get('params', {}))
        expect = step.get('expect', {})

        # Send request
        request_id = self.client.send_request(method, params)

        # Wait for response
        response = self.client.wait_for_response(request_id, timeout=5.0)

        if response is None:
            print_error(f"Step {step_num}: No response to {method}")
            return False

        # Check for error
        if 'error' in response:
            if 'error' not in expect:
                print_error(f"Step {step_num}: Unexpected error: {response['error']}")
                return False
            # TODO: Validate expected error

        # Validate result
        if 'result' in expect:
            expected_result = self.template_engine.substitute(expect['result'])
            actual_result = response.get('result')

            success, errors = ResponseValidator.validate(actual_result, expected_result)

            if not success:
                print_error(f"Step {step_num}: Response validation failed:")
                for error in errors:
                    print_error(f"  {error}")
                if self.verbose:
                    print_dim(f"Expected: {json.dumps(expected_result, indent=2)}")
                    print_dim(f"Actual: {json.dumps(actual_result, indent=2)}")
                return False

        return True

    def _execute_notification(self, step: Dict[str, Any], step_num: int) -> bool:
        """Execute notification step"""
        method = step.get('method')
        params = self.template_engine.substitute(step.get('params', {}))

        # Send notification
        self.client.send_notification(method, params)

        # Small delay for notification processing
        time.sleep(0.1)

        return True

    def _execute_expect_notification(self, step: Dict[str, Any], step_num: int) -> bool:
        """Wait for and validate notification"""
        method = step.get('method')
        params = step.get('params', {})

        # Wait for notification
        notification = self.client.wait_for_notification(method, timeout=5.0)

        if notification is None:
            print_error(f"Step {step_num}: Expected notification {method} not received")
            return False

        # Validate notification params
        if params:
            expected_params = self.template_engine.substitute(params)
            actual_params = notification.get('params', {})

            success, errors = ResponseValidator.validate(actual_params, expected_params)

            if not success:
                print_error(f"Step {step_num}: Notification validation failed:")
                for error in errors:
                    print_error(f"  {error}")
                return False

        return True


# ==================== Test Discovery ====================

class TestDiscovery:
    """Discovers LSP test files"""

    @staticmethod
    def discover_tests(test_dir: Path, pattern: Optional[str] = None) -> List[Path]:
        """
        Discover test YAML files in directory.

        Args:
            test_dir: Root directory to search
            pattern: Optional test name pattern to filter

        Returns: List of test file paths
        """
        tests = []

        for yaml_file in test_dir.rglob('*.yaml'):
            # Skip if pattern specified and doesn't match
            if pattern and pattern not in yaml_file.stem:
                continue

            tests.append(yaml_file)

        return sorted(tests)


# ==================== Main Runner ====================

def main():
    parser = argparse.ArgumentParser(description='UTL-X LSP Conformance Test Runner')
    parser.add_argument('test_dir', nargs='?', default='tests',
                       help='Test directory (default: tests)')
    parser.add_argument('pattern', nargs='?',
                       help='Test name pattern to filter')
    parser.add_argument('-v', '--verbose', action='store_true',
                       help='Verbose output (show JSON-RPC messages)')
    parser.add_argument('--server-jar', default='../../../../modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar',
                       help='Path to server JAR (daemon is now in server module)')

    args = parser.parse_args()

    # Resolve paths
    script_dir = Path(__file__).parent
    # If test_dir is relative, resolve from script's parent (../../tests)
    if not Path(args.test_dir).is_absolute():
        test_dir = (script_dir / '..' / '..' / args.test_dir).resolve()
    else:
        test_dir = Path(args.test_dir)

    # If server_jar is relative, resolve from script dir
    if not Path(args.server_jar).is_absolute():
        server_jar = (script_dir / args.server_jar).resolve()
    else:
        server_jar = Path(args.server_jar)

    if not test_dir.exists():
        print_error(f"Test directory not found: {test_dir}")
        return 1

    if not server_jar.exists():
        print_error(f"Server JAR not found: {server_jar}")
        print_info("Run: ./gradlew :modules:server:jar")
        return 1

    # Discover tests
    print_header("Discovering tests...")
    tests = TestDiscovery.discover_tests(test_dir, args.pattern)

    if not tests:
        print_warning("No tests found")
        return 0

    print_info(f"Found {len(tests)} test(s)")

    # Execute tests
    passed = 0
    failed = 0

    for test_file in tests:
        executor = TestExecutor(test_file, str(server_jar), verbose=args.verbose)

        if not executor.load_test():
            failed += 1
            continue

        if executor.execute():
            passed += 1
        else:
            failed += 1

    # Summary
    print_header("Summary")
    print(f"  Total:  {len(tests)}")
    print_success(f"Passed: {passed}")
    if failed > 0:
        print_error(f"Failed: {failed}")

    return 0 if failed == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
