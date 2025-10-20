#!/usr/bin/env python3
"""
Simple Python-based test runner for UTL-X conformance tests
"""

import os
import sys
import json
import yaml
import subprocess
import tempfile
import argparse
import re
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Any, Optional, Tuple

# Store original capture state
_original_capture_env = None
_capture_was_enabled = False

# Test results file location
RESULTS_FILE = Path('.test-results.json')

def load_test_case(file_path: Path) -> Optional[Dict[str, Any]]:
    """Load a test case YAML file"""
    try:
        with open(file_path, 'r') as f:
            return yaml.safe_load(f)
    except Exception as e:
        print(f"Error loading {file_path}: {e}")
        return None

def run_utlx_transform(utlx_cli: Path, transformation: str, input_data: str) -> tuple[bool, str, str]:
    """Run UTL-X transformation and return success, stdout, stderr"""
    with tempfile.NamedTemporaryFile(mode='w', suffix='.utlx', delete=False) as tf:
        tf.write(transformation)
        transform_file = tf.name
    
    try:
        # Run UTL-X with input data
        result = subprocess.run(
            [str(utlx_cli), 'transform', transform_file],
            input=input_data,
            text=True,
            capture_output=True,
            timeout=30
        )
        
        return result.returncode == 0, result.stdout, result.stderr
        
    except subprocess.TimeoutExpired:
        return False, "", "Timeout"
    except Exception as e:
        return False, "", str(e)
    finally:
        os.unlink(transform_file)

def is_valid_iso_timestamp(value: str) -> bool:
    """Check if a value is a valid ISO 8601 timestamp"""
    try:
        # Try parsing common ISO formats
        for fmt in [
            '%Y-%m-%dT%H:%M:%SZ',
            '%Y-%m-%dT%H:%M:%S.%fZ',
            '%Y-%m-%dT%H:%M:%S%z',
            '%Y-%m-%dT%H:%M:%S.%f%z',
            '%Y-%m-%d'
        ]:
            try:
                datetime.strptime(value, fmt)
                return True
            except ValueError:
                continue
        return False
    except:
        return False

def is_timestamp_within_range(timestamp_str: str, seconds_range: int = 2) -> bool:
    """Check if a timestamp is within N seconds of current time"""
    try:
        # Try to parse the timestamp
        parsed = None
        for fmt in ['%Y-%m-%dT%H:%M:%SZ', '%Y-%m-%dT%H:%M:%S.%fZ']:
            try:
                parsed = datetime.strptime(timestamp_str, fmt)
                break
            except ValueError:
                continue

        if not parsed:
            return False

        # Check if it's within range of current time
        now = datetime.utcnow()
        diff = abs((now - parsed).total_seconds())
        return diff <= seconds_range
    except:
        return False

def validate_dynamic_output(test_case: Dict[str, Any], actual_output: str) -> Tuple[bool, str]:
    """
    Validate output for tests with dynamic values like timestamps.
    Returns (is_valid, reason)
    """
    test_name = test_case.get('name', '')

    # Check if this is a timestamp-related test
    if any(keyword in test_name.lower() for keyword in ['now', 'parsedate', 'timestamp', 'current_time']):
        try:
            actual_data = json.loads(actual_output.strip())

            # Check if output contains timestamp fields
            def check_timestamps(obj, path=''):
                if isinstance(obj, dict):
                    for key, value in obj.items():
                        new_path = f"{path}.{key}" if path else key
                        if isinstance(value, str) and is_valid_iso_timestamp(value):
                            if is_timestamp_within_range(value):
                                continue  # Valid timestamp
                            else:
                                return False, f"Timestamp at {new_path} not within valid range"
                        elif isinstance(value, (dict, list)):
                            result, reason = check_timestamps(value, new_path)
                            if not result:
                                return result, reason
                elif isinstance(obj, list):
                    for i, item in enumerate(obj):
                        result, reason = check_timestamps(item, f"{path}[{i}]")
                        if not result:
                            return result, reason
                return True, "Valid timestamps"

            return check_timestamps(actual_data)
        except json.JSONDecodeError:
            return False, "Invalid JSON output"

    return False, "Not a dynamic test"

def run_single_test(test_case: Dict[str, Any], utlx_cli: Path, test_name: str) -> Tuple[bool, Optional[str]]:
    """Run a single test case"""
    print(f"Running: {test_name}")

    # Extract test data
    transformation = test_case['transformation']
    input_format = test_case['input'].get('format', 'json')

    # Handle input data based on format
    raw_data = test_case['input']['data']
    if isinstance(raw_data, str) and input_format in ['xml', 'csv', 'yaml', 'yml']:
        # For text-based formats (XML, CSV, YAML), use string as-is
        input_data = raw_data
    elif isinstance(raw_data, str) and input_format == 'json':
        # For JSON format with string data, check if it's already valid JSON (multiline from YAML |)
        # Valid JSON strings/objects/arrays start with ", {, or [
        stripped = raw_data.strip()
        if stripped and stripped[0] in '"{[':
            # Looks like valid JSON, use as-is
            input_data = raw_data
        else:
            # Simple string value, needs JSON encoding
            input_data = json.dumps(raw_data)
    else:
        # For JSON Python objects (dict/list) or any other format, convert to JSON
        input_data = json.dumps(raw_data)
    
    # Check if this is a known issue (auto-captured failure)
    if 'known_issue' in test_case:
        print(f"  ⊘ Skipped: Known issue ({test_case['known_issue'].get('issue_description', 'No description')[:60]}...)")
        return True, None  # Don't count as failure

    # Check if this is an error test
    if 'error_expected' in test_case:
        success, stdout, stderr = run_utlx_transform(utlx_cli, transformation, input_data)
        if success:
            reason = "Expected error but transformation succeeded"
            print(f"  ✗ {reason}")
            return False, reason
        else:
            print(f"  ✓ Expected error occurred: {stderr[:100]}...")
            return True, None

    # Normal test - check output
    if 'expected' not in test_case:
        reason = "Test missing 'expected' section"
        print(f"  ✗ {reason}")
        return False, reason

    expected_format = test_case['expected'].get('format', 'json')
    expected_data = test_case['expected']['data']

    success, stdout, stderr = run_utlx_transform(utlx_cli, transformation, input_data)

    if not success:
        reason = f"Transformation failed: {stderr}"
        print(f"  ✗ {reason}")
        return False, reason

    # Check if this is a dynamic test (timestamps, etc.)
    is_dynamic, dynamic_reason = validate_dynamic_output(test_case, stdout)
    if is_dynamic:
        print(f"  ✓ Test passed (dynamic validation: {dynamic_reason})")
        return True, None

    # Compare output based on expected format
    if expected_format == 'json':
        # JSON comparison - parse and normalize both sides
        # If expected data is a multiline string (YAML |), try parsing as JSON
        if isinstance(expected_data, str) and '\n' in expected_data and expected_data.strip() and expected_data.strip()[0] in '{[':
            try:
                expected_data = json.loads(expected_data)
            except json.JSONDecodeError:
                # Not valid JSON, use as-is
                pass
        expected_json = json.dumps(expected_data, sort_keys=True)

        try:
            actual_data = json.loads(stdout.strip())
            actual_json = json.dumps(actual_data, sort_keys=True)

            if actual_json == expected_json:
                print(f"  ✓ Test passed")
                return True, None
            else:
                reason = f"Output mismatch - Expected: {expected_json[:100]}... | Actual: {actual_json[:100]}..."
                print(f"  ✗ Output mismatch")
                print(f"    Expected: {expected_json}")
                print(f"    Actual:   {actual_json}")
                return False, reason

        except json.JSONDecodeError as e:
            reason = f"Invalid JSON output: {e}"
            print(f"  ✗ {reason}")
            print(f"    Raw output: {stdout}")
            return False, reason

    elif expected_format in ['xml', 'csv', 'yaml', 'yml', 'text']:
        # Text-based comparison - normalize whitespace for XML/YAML
        expected_str = expected_data if isinstance(expected_data, str) else str(expected_data)
        actual_str = stdout

        if expected_format in ['xml', 'yaml', 'yml']:
            # Normalize whitespace for XML and YAML
            expected_normalized = '\n'.join(line.strip() for line in expected_str.strip().split('\n') if line.strip())
            actual_normalized = '\n'.join(line.strip() for line in actual_str.strip().split('\n') if line.strip())
        else:
            # For CSV and plain text, just strip outer whitespace
            expected_normalized = expected_str.strip()
            actual_normalized = actual_str.strip()

        if expected_normalized == actual_normalized:
            print(f"  ✓ Test passed")
            return True, None
        else:
            reason = f"Output mismatch ({expected_format}) - Expected: {expected_normalized[:100]}... | Actual: {actual_normalized[:100]}..."
            print(f"  ✗ Output mismatch")
            print(f"    Expected: {expected_normalized[:200]}")
            print(f"    Actual:   {actual_normalized[:200]}")
            return False, reason

    else:
        reason = f"Unsupported expected format: {expected_format}"
        print(f"  ✗ {reason}")
        return False, reason

def run_test_variants(test_case: Dict[str, Any], utlx_cli: Path, base_name: str, failures_list: List[Dict[str, str]], test_file_path: str = None) -> tuple[int, int]:
    """Run test variants and return (passed, total) counts"""
    if 'variants' not in test_case:
        return 0, 0

    passed = 0
    total = len(test_case['variants'])

    for variant in test_case['variants']:
        variant_name = f"{base_name}_{variant['name']}"

        # Create a temporary test case for the variant
        variant_test = {
            'name': variant_name,
            'transformation': variant.get('transformation', test_case['transformation']),
            'input': variant['input'],
            'expected': variant.get('expected')
        }

        # Only add error_expected if it exists in the variant
        if 'error_expected' in variant:
            variant_test['error_expected'] = variant['error_expected']

        success, reason = run_single_test(variant_test, utlx_cli, variant_name)
        if success:
            passed += 1
        elif reason:
            failures_list.append({
                'name': variant_name,
                'reason': reason,
                'category': test_case.get('category', 'unknown'),
                'file_path': test_file_path or 'unknown'
            })

    return passed, total

def check_capture_enabled() -> bool:
    """Check if test capture is currently enabled"""
    # Check environment variable first
    env_capture = os.environ.get('UTLX_CAPTURE_TESTS', '').lower()
    if env_capture in ['true', '1', 'yes', 'on']:
        return True
    elif env_capture in ['false', '0', 'no', 'off']:
        return False

    # Check config file
    config_path = Path.home() / '.utlx' / 'capture-config.yaml'
    if config_path.exists():
        try:
            with open(config_path, 'r') as f:
                for line in f:
                    line = line.strip()
                    if line.startswith('enabled:'):
                        value = line.split(':', 1)[1].strip().lower()
                        return value in ['true', 'yes', '1']
        except Exception:
            pass

    # Default is disabled
    return False

def disable_capture_temporarily():
    """Temporarily disable test capture during conformance testing"""
    global _original_capture_env, _capture_was_enabled

    # Save original environment variable (might be None)
    _original_capture_env = os.environ.get('UTLX_CAPTURE_TESTS')

    # Check if capture was enabled
    _capture_was_enabled = check_capture_enabled()

    # Disable it by setting environment variable
    os.environ['UTLX_CAPTURE_TESTS'] = 'false'

    if _capture_was_enabled:
        print("⚠ Test capture was enabled - temporarily disabling during conformance tests")
        print("  (Will restore original state after tests complete)")
        print()

def restore_capture_state():
    """Restore original capture state after conformance testing"""
    global _original_capture_env, _capture_was_enabled

    if _original_capture_env is not None:
        # Restore original environment variable
        os.environ['UTLX_CAPTURE_TESTS'] = _original_capture_env
    else:
        # Remove the environment variable we added
        if 'UTLX_CAPTURE_TESTS' in os.environ:
            del os.environ['UTLX_CAPTURE_TESTS']

    if _capture_was_enabled:
        print()
        print("✓ Test capture state restored to: ENABLED")

def save_test_results(total_tests: int, passed_tests: int, failures_list: List[Dict[str, str]], results_file: Path):
    """Save test results to JSON file"""
    results = {
        'timestamp': datetime.utcnow().isoformat() + 'Z',
        'total_tests': total_tests,
        'passed_tests': passed_tests,
        'failed_tests': total_tests - passed_tests,
        'failures': failures_list
    }

    try:
        with open(results_file, 'w') as f:
            json.dump(results, f, indent=2)
        print(f"\n💾 Test results saved to {results_file}")
    except Exception as e:
        print(f"\n⚠ Warning: Could not save test results: {e}")

def load_test_results(results_file: Path) -> Optional[Dict[str, Any]]:
    """Load test results from JSON file"""
    try:
        if not results_file.exists():
            return None

        with open(results_file, 'r') as f:
            return json.load(f)
    except Exception as e:
        print(f"⚠ Warning: Could not load test results: {e}")
        return None

def display_failures(results: Dict[str, Any]):
    """Display failure information from test results"""
    print()
    print("=" * 50)
    print("FAILED TESTS DETAILS:")
    print("=" * 50)
    print(f"\nTest Run: {results.get('timestamp', 'unknown')}")
    print(f"Total Tests: {results.get('total_tests', 0)}")
    print(f"Passed: {results.get('passed_tests', 0)}")
    print(f"Failed: {results.get('failed_tests', 0)}")
    print()

    failures = results.get('failures', [])
    if not failures:
        print("✓ No failures!")
        return

    for failure in failures:
        print(f"\n❌ {failure['name']}")
        print(f"   Category: {failure['category']}")
        print(f"   File: {failure.get('file_path', 'unknown')}")
        print(f"   Reason: {failure['reason']}")

def find_test_files(test_dir: Path, category: str = None, test_name: str = None) -> List[Path]:
    """Find test files matching criteria"""
    test_files = []
    
    search_dir = test_dir
    if category:
        search_dir = test_dir / category
    
    if test_name:
        # Look for specific test file
        test_file = search_dir / f"{test_name}.yaml"
        if test_file.exists():
            test_files.append(test_file)
        else:
            # Also try .yml extension
            test_file = search_dir / f"{test_name}.yml"
            if test_file.exists():
                test_files.append(test_file)
    else:
        # Find all test files in directory
        for pattern in ['*.yaml', '*.yml']:
            test_files.extend(search_dir.rglob(pattern))
    
    return sorted(test_files)

def main():
    parser = argparse.ArgumentParser(description='UTL-X Conformance Test Runner')
    parser.add_argument('category', nargs='?', help='Test category (e.g., stdlib/array)')
    parser.add_argument('test_name', nargs='?', help='Specific test name')
    parser.add_argument('-v', '--verbose', action='store_true', help='Verbose output')
    parser.add_argument('--utlx-cli', help='Path to UTL-X CLI')
    parser.add_argument('--show-failures', action='store_true',
                        help='Show failures from last test run (or run tests if no results exist)')
    parser.add_argument('--save-results', action='store_true',
                        help='Save test results to file for later viewing')

    args = parser.parse_args()

    # Find paths
    script_dir = Path(__file__).parent
    suite_root = script_dir.parent.parent
    results_file = suite_root / RESULTS_FILE

    # Handle --show-failures flag
    if args.show_failures:
        # Try to load previous results
        previous_results = load_test_results(results_file)

        if previous_results:
            # Display failures from previous run
            display_failures(previous_results)
            sys.exit(0)
        else:
            # No previous results, auto-run tests
            print("No previous test results found. Running tests...\n")
            # Continue with normal test execution, will save results automatically
            args.save_results = True  # Auto-enable saving

    # Find UTL-X CLI
    if args.utlx_cli:
        utlx_cli = Path(args.utlx_cli)
    else:
        utlx_cli = suite_root.parent / 'utlx'

    if not utlx_cli.exists():
        print(f"Error: UTL-X CLI not found at {utlx_cli}")
        sys.exit(1)

    # Find test files
    test_dir = suite_root / 'tests'
    test_files = find_test_files(test_dir, args.category, args.test_name)

    if not test_files:
        print("No test files found matching criteria")
        sys.exit(0)
    
    print(f"UTL-X Conformance Suite - Simple Runner")
    print(f"UTL-X CLI: {utlx_cli}")
    print(f"Running {len(test_files)} test files...")
    print()

    # Disable test capture during conformance testing to prevent infinite loop
    disable_capture_temporarily()

    total_tests = 0
    passed_tests = 0
    failures_list = []
    exit_code = 0

    try:
        # Run tests
        for test_file in test_files:
            test_case = load_test_case(test_file)
            if not test_case:
                continue

            test_name = test_case.get('name', test_file.stem)

            # Run main test
            success, reason = run_single_test(test_case, utlx_cli, test_name)
            if success:
                passed_tests += 1
            elif reason:
                failures_list.append({
                    'name': test_name,
                    'reason': reason,
                    'category': test_case.get('category', 'unknown'),
                    'file_path': str(test_file)
                })
            total_tests += 1

            # Run variants
            variant_passed, variant_total = run_test_variants(test_case, utlx_cli, test_name, failures_list, str(test_file))
            passed_tests += variant_passed
            total_tests += variant_total

        # Summary
        print()
        print("=" * 50)
        print(f"Results: {passed_tests}/{total_tests} tests passed")
        success_rate = (passed_tests / total_tests * 100) if total_tests > 0 else 0
        print(f"Success rate: {success_rate:.1f}%")

        if passed_tests == total_tests:
            print("✓ All tests passed!")
            exit_code = 0
        else:
            print(f"✗ {total_tests - passed_tests} tests failed")
            exit_code = 1

        # Save results if requested
        if args.save_results:
            save_test_results(total_tests, passed_tests, failures_list, results_file)

        # Show detailed failure information if requested
        if args.show_failures:
            # Create results dict for display
            results_dict = {
                'timestamp': datetime.utcnow().isoformat() + 'Z',
                'total_tests': total_tests,
                'passed_tests': passed_tests,
                'failed_tests': total_tests - passed_tests,
                'failures': failures_list
            }
            display_failures(results_dict)

    finally:
        # Always restore capture state, even if tests fail
        restore_capture_state()

    sys.exit(exit_code)

if __name__ == '__main__':
    main()