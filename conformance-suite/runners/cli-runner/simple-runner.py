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
    input_data = json.dumps(test_case['input']['data'])
    
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
    expected_data = test_case['expected']['data']
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
    
    total_tests = 0
    passed_tests = 0
    
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
        sys.exit(0)
    else:
        print(f"✗ {total_tests - passed_tests} tests failed")
        sys.exit(1)

if __name__ == '__main__':
    main()