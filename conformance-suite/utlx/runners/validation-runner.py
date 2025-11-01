#!/usr/bin/env python3
"""
Validation & Lint Test Runner for UTL-X

This runner executes validation and lint tests, which test error/warning detection
rather than successful transformations.

Usage:
    python3 validation-runner.py validation-tests           # Run all validation tests
    python3 validation-runner.py lint-tests                 # Run all lint tests
    python3 validation-runner.py validation-tests/level1-syntax  # Run specific category
    python3 validation-runner.py path/to/test.yaml          # Run single test
    python3 validation-runner.py --verbose validation-tests # Verbose output
"""

import os
import sys
import yaml
import subprocess
import tempfile
import argparse
import re
from pathlib import Path
from typing import Dict, List, Any, Optional, Tuple

# ANSI color codes
class Colors:
    RED = '\033[91m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

class TestResult:
    """Result of a single test execution"""
    def __init__(self, name: str, passed: bool, message: str = "", details: str = ""):
        self.name = name
        self.passed = passed
        self.message = message
        self.details = details

class ValidationTestRunner:
    """Runner for validation and lint tests"""

    def __init__(self, utlx_cli_path: str, verbose: bool = False):
        self.utlx_cli = utlx_cli_path
        self.verbose = verbose
        self.results = []

    def find_tests(self, path: str) -> List[Path]:
        """Find all YAML test files in the given path"""
        path_obj = Path(path)

        if path_obj.is_file() and path_obj.suffix == '.yaml':
            return [path_obj]

        if path_obj.is_dir():
            return sorted(path_obj.rglob('*.yaml'))

        return []

    def load_test(self, test_file: Path) -> Optional[Dict[str, Any]]:
        """Load and parse a test YAML file"""
        try:
            with open(test_file, 'r') as f:
                return yaml.safe_load(f)
        except Exception as e:
            print(f"{Colors.RED}✗ Error loading {test_file}: {e}{Colors.RESET}")
            return None

    def determine_command(self, test_data: Dict[str, Any]) -> str:
        """Determine whether to run validate or lint based on test structure"""
        # If test has 'lint_expected', it's a lint test
        if 'lint_expected' in test_data:
            return 'lint'
        # Otherwise it's a validation test
        return 'validate'

    def run_command(self, script: str, command: str, flags: List[str] = None) -> Tuple[int, str, str]:
        """
        Run utlx validate or lint command on the given script

        Returns: (exit_code, stdout, stderr)
        """
        if flags is None:
            flags = []

        # Write script to temporary file
        with tempfile.NamedTemporaryFile(mode='w', suffix='.utlx', delete=False) as f:
            f.write(script)
            script_path = f.name

        try:
            # Build command
            cmd = [self.utlx_cli, command, script_path] + flags

            if self.verbose:
                print(f"{Colors.CYAN}  Running: {' '.join(cmd)}{Colors.RESET}")

            # Execute command
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=10
            )

            return result.returncode, result.stdout, result.stderr

        except subprocess.TimeoutExpired:
            return -1, "", "Command timed out"
        except Exception as e:
            return -1, "", str(e)
        finally:
            # Clean up temp file
            try:
                os.unlink(script_path)
            except:
                pass

    def check_exit_code(self, expected: int, actual: int) -> bool:
        """Check if exit code matches expectation"""
        return expected == actual

    def check_errors(self, expected_errors: List[Dict], output: str) -> Tuple[bool, str]:
        """
        Check if expected errors are present in output

        Returns: (all_found, details_message)
        """
        if not expected_errors:
            # No errors expected
            if re.search(r'error|fail', output, re.IGNORECASE):
                return False, "Unexpected errors found in output"
            return True, "No errors as expected"

        found_errors = []
        missing_errors = []

        for expected_error in expected_errors:
            pattern = expected_error.get('message_pattern', '.*')
            error_type = expected_error.get('type', 'unknown')

            if re.search(pattern, output, re.IGNORECASE | re.MULTILINE):
                found_errors.append(error_type)
            else:
                missing_errors.append(f"{error_type}: {pattern}")

        if missing_errors:
            return False, f"Missing expected errors: {', '.join(missing_errors)}"

        return True, f"Found all {len(found_errors)} expected error(s)"

    def check_warnings(self, expected_warnings: List[Dict], output: str) -> Tuple[bool, str]:
        """
        Check if expected warnings are present in output

        Returns: (all_found, details_message)
        """
        if not expected_warnings:
            # No warnings expected - check output doesn't contain warning indicators
            # Be careful: some commands might output "0 warnings" which is OK
            warning_lines = [line for line in output.split('\n')
                           if re.search(r'\bwarning\b', line, re.IGNORECASE)
                           and not re.search(r'0\s+warning', line, re.IGNORECASE)]

            if warning_lines:
                return False, f"Unexpected warnings found: {warning_lines[0]}"
            return True, "No warnings as expected"

        found_warnings = []
        missing_warnings = []

        for expected_warning in expected_warnings:
            pattern = expected_warning.get('message_pattern', '.*')
            category = expected_warning.get('category', 'unknown')

            if re.search(pattern, output, re.IGNORECASE | re.MULTILINE):
                found_warnings.append(category)
            else:
                missing_warnings.append(f"{category}: {pattern}")

        if missing_warnings:
            return False, f"Missing expected warnings: {', '.join(missing_warnings)}"

        return True, f"Found all {len(found_warnings)} expected warning(s)"

    def run_test(self, test_file: Path) -> TestResult:
        """Run a single validation or lint test"""
        test_data = self.load_test(test_file)
        if test_data is None:
            return TestResult(test_file.name, False, "Failed to load test file")

        test_name = test_data.get('name', test_file.stem)
        script = test_data.get('script', '')
        command = self.determine_command(test_data)

        if not script:
            return TestResult(test_name, False, "No script defined in test")

        # Get expected results (either validation_expected or lint_expected)
        expected_key = 'lint_expected' if command == 'lint' else 'validation_expected'
        expected = test_data.get(expected_key, {})

        expected_exit_code = expected.get('exit_code', 0)
        expected_should_pass = expected.get('should_pass', True)
        expected_errors = expected.get('errors', [])
        expected_warnings = expected.get('warnings', [])

        # Run the command
        exit_code, stdout, stderr = self.run_command(script, command, flags=[])
        combined_output = stdout + "\n" + stderr

        # Check exit code
        if not self.check_exit_code(expected_exit_code, exit_code):
            return TestResult(
                test_name,
                False,
                f"Exit code mismatch: expected {expected_exit_code}, got {exit_code}",
                f"Output:\n{combined_output}"
            )

        # Check errors
        errors_ok, error_details = self.check_errors(expected_errors, combined_output)
        if not errors_ok:
            return TestResult(
                test_name,
                False,
                f"Error check failed: {error_details}",
                f"Output:\n{combined_output}"
            )

        # Check warnings
        warnings_ok, warning_details = self.check_warnings(expected_warnings, combined_output)
        if not warnings_ok:
            return TestResult(
                test_name,
                False,
                f"Warning check failed: {warning_details}",
                f"Output:\n{combined_output}"
            )

        # Test passed!
        details_msg = []
        if error_details:
            details_msg.append(error_details)
        if warning_details:
            details_msg.append(warning_details)

        return TestResult(
            test_name,
            True,
            " | ".join(details_msg) if details_msg else "All checks passed"
        )

    def run_test_with_variants(self, test_file: Path) -> List[TestResult]:
        """Run a test and all its variants"""
        test_data = self.load_test(test_file)
        if test_data is None:
            return [TestResult(test_file.name, False, "Failed to load test file")]

        results = []

        # Run base test
        results.append(self.run_test(test_file))

        # Run variants if they exist
        variants = test_data.get('variants', [])
        for variant in variants:
            variant_name = variant.get('name', 'unnamed_variant')
            # TODO: Implement variant testing with different flags
            # For now, skip variants
            pass

        return results

    def run_all_tests(self, path: str) -> bool:
        """
        Run all tests in the given path

        Returns: True if all tests passed
        """
        test_files = self.find_tests(path)

        if not test_files:
            print(f"{Colors.YELLOW}No test files found in {path}{Colors.RESET}")
            return True

        print(f"{Colors.BOLD}Running {len(test_files)} test(s) from {path}{Colors.RESET}\n")

        passed = 0
        failed = 0

        for test_file in test_files:
            print(f"Running: {test_file.name}")

            test_results = self.run_test_with_variants(test_file)

            for result in test_results:
                self.results.append(result)

                if result.passed:
                    passed += 1
                    print(f"  {Colors.GREEN}✓ {result.name}{Colors.RESET}")
                    if self.verbose and result.message:
                        print(f"    {Colors.CYAN}{result.message}{Colors.RESET}")
                else:
                    failed += 1
                    print(f"  {Colors.RED}✗ {result.name}{Colors.RESET}")
                    print(f"    {Colors.RED}{result.message}{Colors.RESET}")
                    if self.verbose and result.details:
                        print(f"    {Colors.YELLOW}{result.details}{Colors.RESET}")

        # Print summary
        print("\n" + "=" * 50)
        total = passed + failed
        success_rate = (passed / total * 100) if total > 0 else 0

        print(f"Results: {Colors.GREEN}{passed}/{total}{Colors.RESET} tests passed")
        print(f"Success rate: {success_rate:.1f}%")

        if failed == 0:
            print(f"{Colors.GREEN}✓ All tests passed!{Colors.RESET}")
            return True
        else:
            print(f"{Colors.RED}✗ {failed} test(s) failed{Colors.RESET}")
            return False

def main():
    parser = argparse.ArgumentParser(description='Run UTL-X validation and lint tests')
    parser.add_argument('path', help='Path to test file or directory')
    parser.add_argument('--verbose', '-v', action='store_true', help='Verbose output')
    parser.add_argument('--utlx-cli', default=None, help='Path to utlx CLI (default: find in PATH)')

    args = parser.parse_args()

    # Find utlx CLI
    if args.utlx_cli:
        utlx_cli = args.utlx_cli
    else:
        # Try to find utlx in common locations
        script_dir = Path(__file__).parent.parent.parent.parent
        possible_paths = [
            script_dir / 'utlx',
            script_dir / 'modules' / 'cli' / 'build' / 'libs' / 'cli-1.0.0-SNAPSHOT.jar',
            'utlx',  # In PATH
        ]

        utlx_cli = None
        for path in possible_paths:
            if Path(path).exists():
                utlx_cli = str(path)
                break

        if utlx_cli is None:
            print(f"{Colors.RED}Error: Could not find utlx CLI{Colors.RESET}")
            print("Please specify --utlx-cli or ensure utlx is in PATH")
            sys.exit(1)

    if not os.path.exists(utlx_cli):
        print(f"{Colors.RED}Error: utlx CLI not found at {utlx_cli}{Colors.RESET}")
        sys.exit(1)

    print(f"{Colors.CYAN}UTL-X CLI: {utlx_cli}{Colors.RESET}\n")

    # Run tests
    runner = ValidationTestRunner(utlx_cli, verbose=args.verbose)
    success = runner.run_all_tests(args.path)

    sys.exit(0 if success else 1)

if __name__ == '__main__':
    main()
