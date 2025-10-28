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
import difflib
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Any, Optional, Tuple

# Store original capture state
_original_capture_env = None
_capture_was_enabled = False

# Test results file location
RESULTS_FILE = Path('.test-results.json')

# ANSI color codes for terminal output
class Colors:
    RED = '\033[91m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

def is_placeholder_match(expected: str, actual: Any) -> bool:
    """
    Check if an expected value is a placeholder and if the actual value matches it.

    Supported placeholders:
    - {{TIMESTAMP}} or {{ISO8601}} - Matches ISO 8601 timestamp strings
    - {{UUID}} - Matches UUID v4 strings
    - {{ANY}} - Matches any value
    - {{NUMBER}} - Matches any numeric value
    - {{STRING}} - Matches any string value
    - {{REGEX:pattern}} - Matches against custom regex pattern
    """
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
        # Match ISO 8601 format: YYYY-MM-DDTHH:MM:SSZ or with timezone
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

    # {{REGEX:pattern}} - custom regex matching
    if placeholder.startswith('REGEX:'):
        if not isinstance(actual, str):
            return False
        regex_pattern = placeholder[6:]  # Remove 'REGEX:' prefix
        try:
            return re.match(regex_pattern, actual) is not None
        except re.error:
            # Invalid regex pattern, treat as non-match
            return False

    # Unknown placeholder, treat as literal value
    return False

def find_json_differences(expected_data: Any, actual_data: Any, path: str = '') -> List[Dict[str, Any]]:
    """
    Recursively find differences between two JSON structures.
    Returns a list of difference objects with path, expected, and actual values.

    Supports placeholder values in expected data:
    - {{TIMESTAMP}} - matches ISO 8601 timestamps
    - {{UUID}} - matches UUID strings
    - {{ANY}} - matches any value
    - {{NUMBER}} - matches numeric values
    - {{STRING}} - matches string values
    - {{REGEX:pattern}} - matches custom regex patterns
    """
    differences = []

    # Type mismatch
    if type(expected_data) != type(actual_data):
        differences.append({
            'path': path or 'root',
            'expected': expected_data,
            'actual': actual_data,
            'type': 'type_mismatch',
            'expected_type': type(expected_data).__name__,
            'actual_type': type(actual_data).__name__
        })
        return differences

    # Dictionary comparison
    if isinstance(expected_data, dict):
        all_keys = set(expected_data.keys()) | set(actual_data.keys())
        for key in sorted(all_keys):
            new_path = f"{path}.{key}" if path else key

            if key not in expected_data:
                differences.append({
                    'path': new_path,
                    'expected': None,
                    'actual': actual_data[key],
                    'type': 'extra_key'
                })
            elif key not in actual_data:
                differences.append({
                    'path': new_path,
                    'expected': expected_data[key],
                    'actual': None,
                    'type': 'missing_key'
                })
            else:
                differences.extend(find_json_differences(
                    expected_data[key], actual_data[key], new_path
                ))

    # Array comparison
    elif isinstance(expected_data, list):
        max_len = max(len(expected_data), len(actual_data))

        if len(expected_data) != len(actual_data):
            differences.append({
                'path': path,
                'expected': f"array length {len(expected_data)}",
                'actual': f"array length {len(actual_data)}",
                'type': 'array_length_mismatch'
            })

        for i in range(max_len):
            new_path = f"{path}[{i}]"

            if i >= len(expected_data):
                differences.append({
                    'path': new_path,
                    'expected': None,
                    'actual': actual_data[i],
                    'type': 'extra_element'
                })
            elif i >= len(actual_data):
                differences.append({
                    'path': new_path,
                    'expected': expected_data[i],
                    'actual': None,
                    'type': 'missing_element'
                })
            else:
                differences.extend(find_json_differences(
                    expected_data[i], actual_data[i], new_path
                ))

    # Scalar comparison
    else:
        # Check if expected value is a placeholder
        if isinstance(expected_data, str) and is_placeholder_match(expected_data, actual_data):
            # Placeholder matches, no difference
            pass
        elif expected_data != actual_data:
            differences.append({
                'path': path or 'value',
                'expected': expected_data,
                'actual': actual_data,
                'type': 'value_mismatch'
            })

    return differences

def show_json_diff(expected_json: str, actual_json: str, verbose: bool = False, transformation: str = None):
    """Show a detailed diff of JSON structures with color highlighting"""
    try:
        expected_data = json.loads(expected_json)
        actual_data = json.loads(actual_json)

        # Find all differences
        differences = find_json_differences(expected_data, actual_data)

        if not differences:
            print(f"    {Colors.GREEN}✓ JSON structures are identical{Colors.RESET}")
            return differences

        # Show summary
        print(f"\n    {Colors.BOLD}{Colors.RED}Found {len(differences)} difference(s):{Colors.RESET}")

        # Show each difference with clear formatting
        for i, diff in enumerate(differences[:10], 1):  # Limit to first 10 differences
            print(f"\n    {Colors.YELLOW}[{i}] Path: {Colors.CYAN}{diff['path']}{Colors.RESET}")

            if diff['type'] == 'type_mismatch':
                print(f"        {Colors.RED}✗ Type mismatch:{Colors.RESET}")
                print(f"          Expected type: {Colors.GREEN}{diff['expected_type']}{Colors.RESET}")
                print(f"          Actual type:   {Colors.RED}{diff['actual_type']}{Colors.RESET}")
                print(f"          Expected: {Colors.GREEN}{json.dumps(diff['expected'])}{Colors.RESET}")
                print(f"          Actual:   {Colors.RED}{json.dumps(diff['actual'])}{Colors.RESET}")

            elif diff['type'] == 'missing_key':
                print(f"        {Colors.RED}✗ Key missing in actual output{Colors.RESET}")
                print(f"          Expected: {Colors.GREEN}{json.dumps(diff['expected'])}{Colors.RESET}")

            elif diff['type'] == 'extra_key':
                print(f"        {Colors.RED}✗ Unexpected key in actual output{Colors.RESET}")
                print(f"          Actual: {Colors.RED}{json.dumps(diff['actual'])}{Colors.RESET}")

            elif diff['type'] == 'array_length_mismatch':
                print(f"        {Colors.RED}✗ Array length mismatch:{Colors.RESET}")
                print(f"          Expected: {Colors.GREEN}{diff['expected']}{Colors.RESET}")
                print(f"          Actual:   {Colors.RED}{diff['actual']}{Colors.RESET}")

            elif diff['type'] == 'value_mismatch':
                print(f"        {Colors.RED}✗ Value mismatch:{Colors.RESET}")
                print(f"          Expected: {Colors.GREEN}{json.dumps(diff['expected'])}{Colors.RESET}")
                print(f"          Actual:   {Colors.RED}{json.dumps(diff['actual'])}{Colors.RESET}")

            else:
                print(f"        {Colors.RED}✗ {diff['type']}{Colors.RESET}")
                if diff['expected'] is not None:
                    print(f"          Expected: {Colors.GREEN}{json.dumps(diff['expected'])}{Colors.RESET}")
                if diff['actual'] is not None:
                    print(f"          Actual:   {Colors.RED}{json.dumps(diff['actual'])}{Colors.RESET}")

        if len(differences) > 10:
            print(f"\n    {Colors.YELLOW}... and {len(differences) - 10} more difference(s){Colors.RESET}")

        # Show transformation hints if available
        if transformation:
            show_transformation_hints(transformation, differences)

        # Optionally show unified diff
        if verbose:
            print(f"\n    {Colors.BOLD}Unified Diff:{Colors.RESET}")
            expected_pretty = json.dumps(expected_data, indent=2, sort_keys=True)
            actual_pretty = json.dumps(actual_data, indent=2, sort_keys=True)

            diff_lines = difflib.unified_diff(
                expected_pretty.splitlines(keepends=True),
                actual_pretty.splitlines(keepends=True),
                fromfile='Expected',
                tofile='Actual',
                lineterm=''
            )

            for line in diff_lines:
                if line.startswith('+++') or line.startswith('---'):
                    print(f"    {Colors.BOLD}{line}{Colors.RESET}", end='')
                elif line.startswith('+'):
                    print(f"    {Colors.GREEN}{line}{Colors.RESET}", end='')
                elif line.startswith('-'):
                    print(f"    {Colors.RED}{line}{Colors.RESET}", end='')
                elif line.startswith('@@'):
                    print(f"    {Colors.CYAN}{line}{Colors.RESET}", end='')
                else:
                    print(f"    {line}", end='')

        return differences

    except json.JSONDecodeError as e:
        # Fallback to text diff if JSON parsing fails
        print(f"    {Colors.RED}✗ JSON parse error: {e}{Colors.RESET}")
        print(f"    {Colors.YELLOW}Falling back to text comparison{Colors.RESET}")
        show_text_diff(expected_json, actual_json)
        return []

def show_text_diff(expected_text: str, actual_text: str):
    """Show a unified diff for text content"""
    print(f"\n    {Colors.BOLD}Unified Diff:{Colors.RESET}")

    diff_lines = difflib.unified_diff(
        expected_text.splitlines(keepends=True),
        actual_text.splitlines(keepends=True),
        fromfile='Expected',
        tofile='Actual',
        lineterm=''
    )

    for line in diff_lines:
        if line.startswith('+++') or line.startswith('---'):
            print(f"    {Colors.BOLD}{line}{Colors.RESET}", end='')
        elif line.startswith('+'):
            print(f"    {Colors.GREEN}{line}{Colors.RESET}", end='')
        elif line.startswith('-'):
            print(f"    {Colors.RED}{line}{Colors.RESET}", end='')
        elif line.startswith('@@'):
            print(f"    {Colors.CYAN}{line}{Colors.RESET}", end='')
        else:
            print(f"    {line}", end='')

def show_xml_diff(expected_xml: str, actual_xml: str):
    """Show structural diff for XML content"""
    try:
        import xml.etree.ElementTree as ET

        # Parse both XML strings
        expected_root = ET.fromstring(expected_xml)
        actual_root = ET.fromstring(actual_xml)

        # Convert to normalized dictionaries for comparison
        def xml_to_dict(element):
            result = {
                'tag': element.tag,
                'text': (element.text or '').strip(),
                'attrib': dict(element.attrib),
                'children': [xml_to_dict(child) for child in element]
            }
            return result

        expected_dict = xml_to_dict(expected_root)
        actual_dict = xml_to_dict(actual_root)

        # Convert to JSON for comparison using existing diff logic
        expected_json = json.dumps(expected_dict, sort_keys=True)
        actual_json = json.dumps(actual_dict, sort_keys=True)

        differences = find_json_differences(expected_dict, actual_dict)

        if not differences:
            print(f"    {Colors.GREEN}✓ XML structures are identical{Colors.RESET}")
            return

        print(f"\n    {Colors.BOLD}{Colors.RED}Found {len(differences)} XML difference(s):{Colors.RESET}")

        for i, diff in enumerate(differences[:10], 1):
            path = diff['path'].replace('.children', '').replace('.tag', '/tag').replace('.text', '/text').replace('.attrib.', '/@')
            print(f"\n    {Colors.YELLOW}[{i}] Path: {Colors.CYAN}{path}{Colors.RESET}")

            if diff['type'] == 'value_mismatch':
                print(f"        {Colors.RED}✗ Value mismatch:{Colors.RESET}")
                print(f"          Expected: {Colors.GREEN}{json.dumps(diff['expected'])}{Colors.RESET}")
                print(f"          Actual:   {Colors.RED}{json.dumps(diff['actual'])}{Colors.RESET}")
            elif diff['type'] == 'missing_key':
                print(f"        {Colors.RED}✗ Missing element/attribute{Colors.RESET}")
                print(f"          Expected: {Colors.GREEN}{json.dumps(diff['expected'])}{Colors.RESET}")
            elif diff['type'] == 'extra_key':
                print(f"        {Colors.RED}✗ Unexpected element/attribute{Colors.RESET}")
                print(f"          Actual: {Colors.RED}{json.dumps(diff['actual'])}{Colors.RESET}")

        if len(differences) > 10:
            print(f"\n    {Colors.YELLOW}... and {len(differences) - 10} more difference(s){Colors.RESET}")

    except ET.ParseError as e:
        print(f"    {Colors.RED}✗ XML parse error: {e}{Colors.RESET}")
        print(f"    {Colors.YELLOW}Falling back to text comparison{Colors.RESET}")
        show_text_diff(expected_xml, actual_xml)

def show_csv_diff(expected_csv: str, actual_csv: str):
    """Show row-by-row diff for CSV content"""
    import csv
    from io import StringIO

    try:
        # Parse both CSV strings
        expected_reader = list(csv.reader(StringIO(expected_csv)))
        actual_reader = list(csv.reader(StringIO(actual_csv)))

        print(f"\n    {Colors.BOLD}{Colors.RED}CSV Comparison:{Colors.RESET}")

        # Compare row counts
        if len(expected_reader) != len(actual_reader):
            print(f"\n    {Colors.RED}✗ Row count mismatch:{Colors.RESET}")
            print(f"      Expected: {Colors.GREEN}{len(expected_reader)} rows{Colors.RESET}")
            print(f"      Actual:   {Colors.RED}{len(actual_reader)} rows{Colors.RESET}")

        # Compare row by row
        max_rows = max(len(expected_reader), len(actual_reader))
        differences_found = 0

        for i in range(max_rows):
            if i >= len(expected_reader):
                differences_found += 1
                print(f"\n    {Colors.YELLOW}[Row {i+1}]{Colors.RESET} {Colors.RED}Extra row in actual:{Colors.RESET}")
                print(f"      {Colors.RED}{actual_reader[i]}{Colors.RESET}")
            elif i >= len(actual_reader):
                differences_found += 1
                print(f"\n    {Colors.YELLOW}[Row {i+1}]{Colors.RESET} {Colors.RED}Missing row in actual:{Colors.RESET}")
                print(f"      {Colors.GREEN}{expected_reader[i]}{Colors.RESET}")
            elif expected_reader[i] != actual_reader[i]:
                differences_found += 1
                print(f"\n    {Colors.YELLOW}[Row {i+1}]{Colors.RESET} {Colors.RED}Row mismatch:{Colors.RESET}")

                # Compare column by column
                max_cols = max(len(expected_reader[i]), len(actual_reader[i]))
                for j in range(max_cols):
                    if j >= len(expected_reader[i]):
                        print(f"      Column {j+1}: {Colors.RED}Extra column: {actual_reader[i][j]}{Colors.RESET}")
                    elif j >= len(actual_reader[i]):
                        print(f"      Column {j+1}: {Colors.RED}Missing column: {expected_reader[i][j]}{Colors.RESET}")
                    elif expected_reader[i][j] != actual_reader[i][j]:
                        print(f"      Column {j+1}:")
                        print(f"        Expected: {Colors.GREEN}{expected_reader[i][j]}{Colors.RESET}")
                        print(f"        Actual:   {Colors.RED}{actual_reader[i][j]}{Colors.RESET}")

                if differences_found >= 10:
                    break

        if differences_found == 0:
            print(f"    {Colors.GREEN}✓ CSV files are identical{Colors.RESET}")
        elif differences_found > 10:
            print(f"\n    {Colors.YELLOW}... and more differences (showing first 10){Colors.RESET}")

    except Exception as e:
        print(f"    {Colors.RED}✗ CSV parse error: {e}{Colors.RESET}")
        print(f"    {Colors.YELLOW}Falling back to text comparison{Colors.RESET}")
        show_text_diff(expected_csv, actual_csv)

def show_yaml_diff(expected_yaml: str, actual_yaml: str) -> bool:
    """Show structural diff for YAML content

    Returns:
        True if YAML structures are identical, False otherwise
    """
    try:
        import yaml as yaml_module

        # Parse both YAML strings
        expected_data = yaml_module.safe_load(expected_yaml)
        actual_data = yaml_module.safe_load(actual_yaml)

        # Use the same JSON diff logic since YAML parses to dicts/lists/scalars
        differences = find_json_differences(expected_data, actual_data)

        if not differences:
            print(f"    {Colors.GREEN}✓ YAML structures are identical{Colors.RESET}")
            return True

        print(f"\n    {Colors.BOLD}{Colors.RED}Found {len(differences)} YAML difference(s):{Colors.RESET}")

        # Show each difference with clear formatting
        for i, diff in enumerate(differences[:10], 1):
            print(f"\n    {Colors.YELLOW}[{i}] Path: {Colors.CYAN}{diff['path']}{Colors.RESET}")

            if diff['type'] == 'type_mismatch':
                print(f"        {Colors.RED}✗ Type mismatch:{Colors.RESET}")
                print(f"          Expected type: {Colors.GREEN}{diff['expected_type']}{Colors.RESET}")
                print(f"          Actual type:   {Colors.RED}{diff['actual_type']}{Colors.RESET}")
                print(f"          Expected: {Colors.GREEN}{json.dumps(diff['expected'])}{Colors.RESET}")
                print(f"          Actual:   {Colors.RED}{json.dumps(diff['actual'])}{Colors.RESET}")

            elif diff['type'] == 'missing_key':
                print(f"        {Colors.RED}✗ Key missing in actual output{Colors.RESET}")
                print(f"          Expected: {Colors.GREEN}{json.dumps(diff['expected'])}{Colors.RESET}")

            elif diff['type'] == 'extra_key':
                print(f"        {Colors.RED}✗ Unexpected key in actual output{Colors.RESET}")
                print(f"          Actual: {Colors.RED}{json.dumps(diff['actual'])}{Colors.RESET}")

            elif diff['type'] == 'array_length_mismatch':
                print(f"        {Colors.RED}✗ Array length mismatch:{Colors.RESET}")
                print(f"          Expected: {Colors.GREEN}{diff['expected']}{Colors.RESET}")
                print(f"          Actual:   {Colors.RED}{diff['actual']}{Colors.RESET}")

            elif diff['type'] == 'value_mismatch':
                print(f"        {Colors.RED}✗ Value mismatch:{Colors.RESET}")
                print(f"          Expected: {Colors.GREEN}{json.dumps(diff['expected'])}{Colors.RESET}")
                print(f"          Actual:   {Colors.RED}{json.dumps(diff['actual'])}{Colors.RESET}")

            else:
                print(f"        {Colors.RED}✗ {diff['type']}{Colors.RESET}")
                if diff['expected'] is not None:
                    print(f"          Expected: {Colors.GREEN}{json.dumps(diff['expected'])}{Colors.RESET}")
                if diff['actual'] is not None:
                    print(f"          Actual:   {Colors.RED}{json.dumps(diff['actual'])}{Colors.RESET}")

        if len(differences) > 10:
            print(f"\n    {Colors.YELLOW}... and {len(differences) - 10} more difference(s){Colors.RESET}")

        return False

    except yaml_module.YAMLError as e:
        print(f"    {Colors.RED}✗ YAML parse error: {e}{Colors.RESET}")
        print(f"    {Colors.YELLOW}Falling back to text comparison{Colors.RESET}")
        show_text_diff(expected_yaml, actual_yaml)
        return False
    except Exception as e:
        print(f"    {Colors.RED}✗ Error comparing YAML: {e}{Colors.RESET}")
        print(f"    {Colors.YELLOW}Falling back to text comparison{Colors.RESET}")
        show_text_diff(expected_yaml, actual_yaml)
        return False

def find_transformation_lines(transformation: str, field_path: str) -> List[str]:
    """
    Try to find relevant lines in the transformation that might produce the given field path.
    This is a simple heuristic search - not perfect but helpful.
    """
    lines = transformation.split('\n')
    relevant_lines = []

    # Extract the last part of the path (field name)
    path_parts = field_path.replace('[', '.').replace(']', '').split('.')

    for i, line in enumerate(lines, 1):
        # Skip header lines
        if line.strip().startswith('%utlx') or line.strip().startswith('input ') or \
           line.strip().startswith('output ') or line.strip() == '---':
            continue

        # Look for field assignments or references
        for part in path_parts:
            if part and part in line:
                # Found a line mentioning this field
                relevant_lines.append(f"    Line {i}: {line}")
                break

    return relevant_lines

def show_transformation_hints(transformation: str, differences: List[Dict[str, Any]]):
    """Show hints about which transformation lines might be causing issues"""
    if not differences:
        return

    print(f"\n    {Colors.BOLD}{Colors.CYAN}💡 Transformation hints:{Colors.RESET}")

    # Get unique paths from differences
    paths = list(set(diff['path'] for diff in differences[:5]))  # Limit to first 5

    for path in paths:
        lines = find_transformation_lines(transformation, path)
        if lines:
            print(f"\n    {Colors.YELLOW}Field '{path}' might be generated by:{Colors.RESET}")
            for line in lines[:3]:  # Show max 3 lines per field
                print(line)

def load_test_case(file_path: Path) -> Optional[Dict[str, Any]]:
    """Load a test case YAML file"""
    try:
        with open(file_path, 'r') as f:
            return yaml.safe_load(f)
    except Exception as e:
        print(f"Error loading {file_path}: {e}")
        return None

def run_utlx_transform(utlx_cli: Path, transformation: str, input_data: str = None, named_inputs: Dict[str, str] = None) -> tuple[bool, str, str]:
    """Run UTL-X transformation and return success, stdout, stderr

    Args:
        utlx_cli: Path to UTL-X CLI
        transformation: UTL-X transformation script
        input_data: Single input data (for backward compatibility)
        named_inputs: Dictionary of input_name -> input_data for multi-input tests
    """
    with tempfile.NamedTemporaryFile(mode='w', suffix='.utlx', delete=False) as tf:
        tf.write(transformation)
        transform_file = tf.name

    temp_input_files = []

    try:
        # Build command
        cmd = [str(utlx_cli), 'transform', transform_file]

        # Handle multi-input case
        if named_inputs:
            for input_name, data in named_inputs.items():
                # Create temp file for this input
                with tempfile.NamedTemporaryFile(mode='w', suffix=f'_{input_name}', delete=False) as inf:
                    inf.write(data)
                    temp_input_files.append(inf.name)
                    cmd.extend(['--input', f'{input_name}={inf.name}'])

            # Run without stdin
            result = subprocess.run(
                cmd,
                text=True,
                capture_output=True,
                timeout=30
            )
        else:
            # Single input via stdin (backward compatible)
            result = subprocess.run(
                cmd,
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
        # Clean up temp input files
        for temp_file in temp_input_files:
            try:
                os.unlink(temp_file)
            except:
                pass

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

    # Check if this is a multi-input test or single-input test
    if 'inputs' in test_case:
        # Multi-input test
        named_inputs = {}
        for input_name, input_spec in test_case['inputs'].items():
            input_format = input_spec.get('format', 'json')
            raw_data = input_spec['data']

            # Handle input data based on format
            if isinstance(raw_data, str) and input_format in ['xml', 'csv', 'yaml', 'yml', 'avro']:
                # For text-based formats (XML, CSV, YAML, Avro), use string as-is
                named_inputs[input_name] = raw_data
            elif isinstance(raw_data, str) and input_format == 'json':
                # For JSON format with string data, check if it's already valid JSON (multiline from YAML |)
                stripped = raw_data.strip()
                if stripped and stripped[0] in '"{[':
                    # Looks like valid JSON, use as-is
                    named_inputs[input_name] = raw_data
                else:
                    # Simple string value, needs JSON encoding
                    named_inputs[input_name] = json.dumps(raw_data)
            else:
                # For JSON Python objects (dict/list) or any other format, convert to JSON
                named_inputs[input_name] = json.dumps(raw_data)

        input_data = None  # No single input
    else:
        # Single-input test (backward compatible)
        input_format = test_case['input'].get('format', 'json')
        raw_data = test_case['input']['data']

        # Handle input data based on format
        if isinstance(raw_data, str) and input_format in ['xml', 'csv', 'yaml', 'yml', 'xsd', 'jsch', 'avro']:
            # For text-based formats (XML, CSV, YAML, XSD, JSON Schema, Avro), use string as-is
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

        named_inputs = None  # No named inputs
    
    # Check if this is a known issue (auto-captured failure)
    if 'known_issue' in test_case:
        print(f"  ⊘ Skipped: Known issue ({test_case['known_issue'].get('issue_description', 'No description')[:60]}...)")
        return True, None  # Don't count as failure

    # Check if this is an error test
    if 'error_expected' in test_case:
        success, stdout, stderr = run_utlx_transform(utlx_cli, transformation, input_data, named_inputs)
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

    success, stdout, stderr = run_utlx_transform(utlx_cli, transformation, input_data, named_inputs)

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
    if expected_format in ['json', 'avro', 'avsc', 'jsch']:
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

            # Use find_json_differences to support placeholder matching
            differences = find_json_differences(expected_data, actual_data)

            if not differences:
                print(f"  ✓ Test passed")
                return True, None
            else:
                print(f"  ✗ Output mismatch")
                show_json_diff(expected_json, actual_json, verbose=False, transformation=transformation)
                reason = f"Output mismatch - See detailed diff above"
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

        # For YAML, use structural comparison instead of string comparison
        if expected_format in ['yaml', 'yml']:
            yaml_identical = show_yaml_diff(expected_str, actual_str)
            if yaml_identical:
                print(f"  ✓ Test passed")
                return True, None
            else:
                print(f"  ✗ Output mismatch ({expected_format})")
                reason = f"Output mismatch ({expected_format}) - See detailed diff above"
                return False, reason

        # For other formats, use string comparison
        if expected_normalized == actual_normalized:
            print(f"  ✓ Test passed")
            return True, None
        else:
            print(f"  ✗ Output mismatch ({expected_format})")

            # Use format-specific diff display
            if expected_format == 'xml':
                show_xml_diff(expected_normalized, actual_normalized)
            elif expected_format == 'csv':
                show_csv_diff(expected_normalized, actual_normalized)
            else:
                # For plain text, use text diff
                show_text_diff(expected_normalized, actual_normalized)

            reason = f"Output mismatch ({expected_format}) - See detailed diff above"
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

        if previous_results and previous_results.get('failures'):
            # Re-test the previously failed tests to see if they're still failing
            print("Re-testing previously failed tests to verify current status...\n")

            # Find UTL-X CLI
            if args.utlx_cli:
                utlx_cli = Path(args.utlx_cli)
            else:
                utlx_cli = suite_root.parent / 'utlx'

            if not utlx_cli.exists():
                print(f"Error: UTL-X CLI not found at {utlx_cli}")
                sys.exit(1)

            # Disable test capture during re-testing
            disable_capture_temporarily()

            # Re-test each failed test
            still_failing = []
            now_passing = []
            tested_names = set()  # Track what we've tested to avoid duplicates

            for failure in previous_results['failures']:
                test_name = failure['name']

                # Skip if already tested (happens with variants)
                if test_name in tested_names:
                    continue
                tested_names.add(test_name)

                file_path = Path(failure.get('file_path', ''))
                if not file_path.exists():
                    still_failing.append(failure)  # Can't re-test, keep in list
                    continue

                test_case = load_test_case(file_path)
                if not test_case:
                    still_failing.append(failure)
                    continue

                base_test_name = test_case.get('name', file_path.stem)

                # Check if this is a variant by looking at the test_name
                is_variant = test_name != base_test_name and test_name.startswith(base_test_name + "_")

                if is_variant:
                    # This is a variant - find and test it specifically
                    if 'variants' in test_case:
                        variant_suffix = test_name[len(base_test_name) + 1:]  # Remove "base_name_" prefix
                        found_variant = False
                        for variant in test_case['variants']:
                            if variant['name'] == variant_suffix:
                                variant_case = dict(test_case)
                                variant_case['name'] = test_name
                                variant_case['input'] = variant['input']
                                variant_case['expected'] = variant['expected']

                                success, reason = run_single_test(variant_case, utlx_cli, test_name)
                                if success:
                                    now_passing.append(test_name)
                                else:
                                    failure['reason'] = reason
                                    still_failing.append(failure)
                                found_variant = True
                                break
                        if not found_variant:
                            still_failing.append(failure)  # Variant not found, keep failure
                else:
                    # This is a base test
                    success, reason = run_single_test(test_case, utlx_cli, base_test_name)
                    if success:
                        now_passing.append(base_test_name)
                    else:
                        failure['reason'] = reason
                        still_failing.append(failure)

            restore_capture_state()

            # Display updated results
            print()
            print("=" * 50)
            print("FAILED TESTS DETAILS (Re-tested):")
            print("=" * 50)
            print(f"\nOriginal Test Run: {previous_results.get('timestamp', 'unknown')}")
            print(f"Re-tested: {datetime.utcnow().isoformat()}Z")
            print(f"Total Previously Failed: {len(previous_results['failures'])}")
            print(f"Now Passing: {len(now_passing)}")
            print(f"Still Failing: {len(still_failing)}")
            print()

            if now_passing:
                print("✓ Now Passing:")
                for name in now_passing:
                    print(f"  • {name}")
                print()

            if not still_failing:
                print("✓ All previously failed tests are now passing!")
            else:
                for failure in still_failing:
                    print(f"\n❌ {failure['name']}")
                    print(f"   Category: {failure['category']}")
                    print(f"   File: {failure.get('file_path', 'unknown')}")
                    print(f"   Reason: {failure['reason']}")

            sys.exit(0)
        elif previous_results:
            # No failures in cache
            print("✓ No failures in previous test run!")
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