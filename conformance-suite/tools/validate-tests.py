#!/usr/bin/env python3
"""
UTL-X Conformance Suite Test Validator

Validates test cases against the schema and checks for consistency.
"""

import json
import yaml
import sys
import os
import re
from pathlib import Path
from typing import Dict, List, Any, Optional
from jsonschema import validate, ValidationError

def load_schema() -> Dict[str, Any]:
    """Load the test case JSON schema"""
    schema_path = Path(__file__).parent.parent / "test-case-schema.json"
    with open(schema_path, 'r') as f:
        return json.load(f)

def load_test_case(file_path: Path) -> Optional[Dict[str, Any]]:
    """Load a test case YAML file"""
    try:
        with open(file_path, 'r') as f:
            return yaml.safe_load(f)
    except Exception as e:
        print(f"Error loading {file_path}: {e}")
        return None

def validate_test_case(test_case: Dict[str, Any], schema: Dict[str, Any], file_path: Path) -> List[str]:
    """Validate a single test case against the schema"""
    errors = []
    
    try:
        validate(instance=test_case, schema=schema)
    except ValidationError as e:
        errors.append(f"Schema validation failed: {e.message}")
    
    # Additional validations
    
    # Check name matches file name
    expected_name = file_path.stem
    if test_case.get('name') != expected_name:
        errors.append(f"Test name '{test_case.get('name')}' doesn't match file name '{expected_name}'")
    
    # Check category matches directory structure
    if 'category' in test_case:
        category_parts = test_case['category'].split('/')
        file_parts = file_path.parts[:-1]  # Exclude filename
        
        # Find where category path starts in file path
        category_found = False
        for i in range(len(file_parts)):
            if i + len(category_parts) <= len(file_parts):
                if file_parts[i:i+len(category_parts)] == tuple(category_parts):
                    category_found = True
                    break
        
        if not category_found:
            errors.append(f"Category '{test_case['category']}' doesn't match directory structure")
    
    # Validate transformation syntax (basic check)
    if 'transformation' in test_case:
        transformation = test_case['transformation']
        if not transformation.strip().startswith('%utlx'):
            errors.append("Transformation must start with %utlx directive")
        
        required_sections = ['input', 'output']
        for section in required_sections:
            if section not in transformation:
                errors.append(f"Transformation missing required '{section}' section")
    
    # Validate input/expected format consistency
    if 'input' in test_case and 'expected' in test_case:
        input_format = test_case['input'].get('format')
        expected_format = test_case['expected'].get('format')
        
        # For most tests, input and output formats should match
        if input_format != expected_format:
            # This is a warning, not an error, as format conversion is valid
            print(f"Warning: Input format '{input_format}' differs from expected format '{expected_format}'")
    
    # Validate variants
    if 'variants' in test_case:
        for i, variant in enumerate(test_case['variants']):
            if 'name' in variant:
                variant_name = variant['name']
                if not re.match(r'^[a-z0-9_]+$', variant_name):
                    errors.append(f"Variant {i} name '{variant_name}' should use snake_case")
    
    # Check for required metadata in certain categories
    category = test_case.get('category', '')
    if category.startswith('performance'):
        if 'performance_limits' not in test_case:
            errors.append("Performance tests must include performance_limits")
    
    return errors

def validate_registry(registry_path: Path, test_files: List[Path]) -> List[str]:
    """Validate the test registry against actual test files"""
    errors = []
    
    try:
        with open(registry_path, 'r') as f:
            registry = json.load(f)
    except Exception as e:
        return [f"Cannot load registry: {e}"]
    
    # Check if all test files are registered
    actual_tests = {f.stem for f in test_files}
    registered_tests = set()
    
    for category_data in registry.get('categories', {}).values():
        registered_tests.update(category_data.get('tests', []))
    
    missing_from_registry = actual_tests - registered_tests
    missing_from_files = registered_tests - actual_tests
    
    if missing_from_registry:
        errors.append(f"Tests not in registry: {sorted(missing_from_registry)}")
    
    if missing_from_files:
        errors.append(f"Tests in registry but missing files: {sorted(missing_from_files)}")
    
    # Validate test counts
    for category, data in registry.get('categories', {}).items():
        expected_count = data.get('test_count', 0)
        actual_count = len(data.get('tests', []))
        if expected_count != actual_count:
            errors.append(f"Category {category}: expected {expected_count} tests, found {actual_count}")
    
    return errors

def find_test_files(test_dir: Path) -> List[Path]:
    """Find all test case files"""
    test_files = []
    for pattern in ['*.yaml', '*.yml']:
        test_files.extend(test_dir.rglob(pattern))
    return sorted(test_files)

def main():
    """Main validation function"""
    script_dir = Path(__file__).parent
    suite_root = script_dir.parent
    test_dir = suite_root / "tests"
    registry_path = test_dir / "registry.json"
    
    if not test_dir.exists():
        print(f"Error: Test directory not found: {test_dir}")
        sys.exit(1)
    
    print("UTL-X Conformance Suite Test Validator")
    print("=" * 40)
    
    # Load schema
    try:
        schema = load_schema()
        print(f"✓ Loaded test case schema")
    except Exception as e:
        print(f"✗ Failed to load schema: {e}")
        sys.exit(1)
    
    # Find test files
    test_files = find_test_files(test_dir)
    print(f"✓ Found {len(test_files)} test files")
    
    if not test_files:
        print("No test files found")
        sys.exit(0)
    
    # Validate each test case
    total_errors = 0
    valid_tests = 0
    
    for test_file in test_files:
        test_case = load_test_case(test_file)
        if test_case is None:
            total_errors += 1
            continue
        
        errors = validate_test_case(test_case, schema, test_file)
        
        if errors:
            print(f"✗ {test_file.relative_to(suite_root)}")
            for error in errors:
                print(f"    {error}")
            total_errors += len(errors)
        else:
            print(f"✓ {test_file.relative_to(suite_root)}")
            valid_tests += 1
    
    # Validate registry
    if registry_path.exists():
        registry_errors = validate_registry(registry_path, test_files)
        if registry_errors:
            print(f"✗ Registry validation failed:")
            for error in registry_errors:
                print(f"    {error}")
            total_errors += len(registry_errors)
        else:
            print(f"✓ Registry validation passed")
    else:
        print(f"⚠ No registry file found at {registry_path}")
    
    # Summary
    print("\nValidation Summary:")
    print(f"  Total test files: {len(test_files)}")
    print(f"  Valid tests: {valid_tests}")
    print(f"  Total errors: {total_errors}")
    
    if total_errors > 0:
        print(f"\n✗ Validation failed with {total_errors} errors")
        sys.exit(1)
    else:
        print(f"\n✓ All validations passed!")
        sys.exit(0)

if __name__ == "__main__":
    main()