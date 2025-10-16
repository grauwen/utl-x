#!/usr/bin/env python3
"""
Bulk fix all remaining lambda wrapper registrations in Functions.kt
"""

import re

# Read the Functions.kt file
with open('/Users/magr/Library/CloudStorage/OneDrive-GLOMIDCOB.V/experiments/mapping/github-git/utl-x/stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt', 'r') as f:
    content = f.read()

# Pattern to match lambda wrapper registrations
# register("functionName") { args -> SomeClass.someFunction(...) }
lambda_pattern = r'register\("([^"]+)"\)\s*\{\s*args\s*->\s*([^.]+)\.([^(]+)\([^}]+\)\s*\}'

# Find all lambda wrapper registrations
matches = re.findall(lambda_pattern, content)

print(f"Found {len(matches)} lambda wrapper registrations to convert:")
for function_name, class_name, method_name in matches:
    print(f"  - {function_name}: {class_name}::{method_name}")

# Generate replacement patterns
replacements = []
for function_name, class_name, method_name in matches:
    old_pattern = f'register("{function_name}") {{ args -> {class_name}.{method_name}('
    new_replacement = f'register("{function_name}", {class_name}::{method_name})'
    
    # Find the full lambda registration to replace
    full_pattern = r'register\("' + re.escape(function_name) + r'"\)\s*\{[^}]+\}'
    full_matches = re.findall(full_pattern, content)
    
    if full_matches:
        old_full = full_matches[0]
        replacements.append((old_full, new_replacement))

print(f"\nGenerating {len(replacements)} replacements...")

# Apply replacements
new_content = content
for old, new in replacements:
    new_content = new_content.replace(old, new)

# Count the changes made
changes_made = len([1 for old, new in replacements if old in content and old != new])

print(f"\nMade {changes_made} changes.")
print("\nNote: This script identifies patterns but manual verification is needed")
print("since function signatures must match (List<UDM>) -> UDM for direct method references to work.")