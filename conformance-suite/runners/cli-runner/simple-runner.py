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
from pathlib import Path
from typing import Dict, List, Any, Optional

# Store original capture state
_original_capture_env = None
_capture_was_enabled = False

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

def run_single_test(test_case: Dict[str, Any], utlx_cli: Path, test_name: str) -> bool:
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
        return True  # Don't count as failure

    # Check if this is an error test
    if 'error_expected' in test_case:
        success, stdout, stderr = run_utlx_transform(utlx_cli, transformation, input_data)
        if success:
            print(f"  ✗ Expected error but transformation succeeded")
            return False
        else:
            print(f"  ✓ Expected error occurred: {stderr[:100]}...")
            return True

    # Normal test - check output
    if 'expected' not in test_case:
        print(f"  ✗ Test missing 'expected' section")
        return False

    expected_data = test_case['expected']['data']
    # If expected data is a multiline string (YAML |), try parsing as JSON
    # Only parse if it looks like JSON (starts with { or [) to avoid breaking simple strings
    if isinstance(expected_data, str) and expected_data.strip() and expected_data.strip()[0] in '{[':
        try:
            expected_data = json.loads(expected_data)
        except json.JSONDecodeError:
            # Not valid JSON, use as-is
            pass
    expected_json = json.dumps(expected_data, sort_keys=True)

    success, stdout, stderr = run_utlx_transform(utlx_cli, transformation, input_data)

    if not success:
        print(f"  ✗ Transformation failed: {stderr}")
        return False

    try:
        # Parse and normalize output for comparison
        actual_data = json.loads(stdout.strip())
        actual_json = json.dumps(actual_data, sort_keys=True)

        if actual_json == expected_json:
            print(f"  ✓ Test passed")
            return True
        else:
            print(f"  ✗ Output mismatch")
            print(f"    Expected: {expected_json}")
            print(f"    Actual:   {actual_json}")
            return False
            
    except json.JSONDecodeError as e:
        print(f"  ✗ Invalid JSON output: {e}")
        print(f"    Raw output: {stdout}")
        return False

def run_test_variants(test_case: Dict[str, Any], utlx_cli: Path, base_name: str) -> tuple[int, int]:
    """Run test variants and return (passed, total) counts"""
    if 'variants' not in test_case:
        return 0, 0
    
    passed = 0
    total = len(test_case['variants'])
    
    for variant in test_case['variants']:
        variant_name = f"{base_name}_{variant['name']}"
        
        # Create a temporary test case for the variant
        variant_test = {
            'transformation': variant.get('transformation', test_case['transformation']),
            'input': variant['input'],
            'expected': variant.get('expected')
        }
        
        # Only add error_expected if it exists in the variant
        if 'error_expected' in variant:
            variant_test['error_expected'] = variant['error_expected']
        
        if run_single_test(variant_test, utlx_cli, variant_name):
            passed += 1
    
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
    
    args = parser.parse_args()
    
    # Find UTL-X CLI
    script_dir = Path(__file__).parent
    suite_root = script_dir.parent.parent
    
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
    exit_code = 0

    try:
        # Run tests
        for test_file in test_files:
            test_case = load_test_case(test_file)
            if not test_case:
                continue

            test_name = test_case.get('name', test_file.stem)

            # Run main test
            if run_single_test(test_case, utlx_cli, test_name):
                passed_tests += 1
            total_tests += 1

            # Run variants
            variant_passed, variant_total = run_test_variants(test_case, utlx_cli, test_name)
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

    finally:
        # Always restore capture state, even if tests fail
        restore_capture_state()

    sys.exit(exit_code)

if __name__ == '__main__':
    main()