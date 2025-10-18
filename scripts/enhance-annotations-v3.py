#!/usr/bin/env python3
"""
Enhanced Function Annotation Generator v3
Implements the v2 specification from Enhanced-Function-Annotations-v2.md
"""

import re
import sys
from pathlib import Path
from typing import List, Tuple, Optional

def extract_kdoc_comprehensive(lines: List[str], func_line_num: int) -> dict:
    """Extract comprehensive information from KDoc comment"""
    result = {
        'description': None,
        'usage_examples': [],
        'filters_out': [],
        'keeps': [],
        'notes': [],
        'complement_to': []
    }

    # Find KDoc block
    kdoc_lines = []
    i = func_line_num - 1

    # Skip whitespace
    while i >= 0 and lines[i].strip() == '':
        i -= 1

    # Check for KDoc end
    if i >= 0 and '*/' in lines[i]:
        end_line = i
        # Find start
        while i >= 0 and '/**' not in lines[i]:
            i -= 1
        if i >= 0:
            kdoc_lines = lines[i:end_line+1]

    if not kdoc_lines:
        return result

    # Clean KDoc
    cleaned_lines = []
    for line in kdoc_lines:
        cleaned = re.sub(r'^\s*/?\*+/?', '', line).strip()
        if cleaned:
            cleaned_lines.append(cleaned)

    # Parse content
    current_section = None
    description_found = False

    for line in cleaned_lines:
        # Skip tags for now
        if line.startswith('@'):
            continue

        # Usage examples
        if line.startswith('Usage:'):
            example = line.replace('Usage:', '').strip()
            result['usage_examples'].append(example)
            continue

        # Filters out section
        if line.startswith('Filters out:'):
            current_section = 'filters_out'
            continue

        # Keeps section
        if line.startswith('Keeps:'):
            current_section = 'keeps'
            continue

        # Complement/See also
        if 'complement' in line.lower() or 'see also' in line.lower():
            # Extract function names
            funcs = re.findall(r'`?(\w+)\(\)`?', line)
            result['complement_to'].extend(funcs)
            result['notes'].append(line)
            current_section = None
            continue

        # Returns section (not @return tag)
        if line.startswith('Returns'):
            result['notes'].append(line)
            current_section = None
            continue

        # List items under current section
        if current_section and line.startswith('-'):
            item = line[1:].strip()
            result[current_section].append(item)
            continue

        # Description (first non-empty, non-usage line)
        if not description_found and not line.startswith('Usage:') and current_section is None:
            result['description'] = line
            description_found = True
            continue

        # Other notes
        if description_found and current_section is None and line:
            result['notes'].append(line)

    return result

def infer_parameters(func_body: str, func_name: str) -> List[str]:
    """Infer parameter descriptions from function body"""
    params = []

    # Look for arg extraction patterns
    array_checks = re.findall(r'args\[(\d+)\].*?as.*?(Array|String|Number|Boolean|Lambda)', func_body)
    arg_names = re.findall(r'val (\w+) = args\[(\d+)\]', func_body)
    error_messages = re.findall(r'"(\w+) expects.*?(\w+)\s+(?:as|argument)', func_body)

    # Common parameter patterns based on function name and checks
    if 'array' in func_body.lower() and not params:
        params.append("array: Input array to process")

    if 'predicate' in func_body.lower() or 'lambda' in func_body.lower():
        params.append("predicate: Function to test each element (element) => boolean")

    if 'index' in func_name.lower():
        if len(params) < 2:
            params.append("array: Array to search")
        if 'predicate' not in str(params):
            params.append("predicate: Condition to match")

    # If we found specific arg extractions, be more specific
    for var_name, arg_idx in arg_names:
        idx = int(arg_idx)
        if idx >= len(params):
            params.extend([""] * (idx + 1 - len(params)))
        if not params[idx]:
            params[idx] = f"{var_name}: {var_name.capitalize()} value"

    return [p for p in params if p]  # Remove empty

def infer_returns(kdoc_info: dict, func_name: str, func_body: str) -> str:
    """Infer return value description"""
    # Check notes for return info
    for note in kdoc_info['notes']:
        if note.startswith('Returns'):
            return note.replace('Returns', '').strip()

    # Common patterns
    if 'index' in func_name.lower():
        if '-1 if no match' in func_body or '-1 if not found' in func_body:
            return "Index of matching element, or -1 if not found"
        return "Index of the element"

    if func_name.startswith('is') or func_name.startswith('has'):
        return "Boolean indicating the result"

    if 'filter' in func_name.lower() or 'compact' in func_name.lower():
        return "New array with filtered elements"

    if 'find' in func_name.lower():
        return "First matching element, or null if none found"

    return "Result of the operation"

def determine_category(filepath: Path) -> str:
    """Determine category from file path"""
    path_str = str(filepath).lower()
    if '/array/' in path_str:
        return 'Array'
    elif '/string/' in path_str:
        return 'String'
    elif '/math/' in path_str:
        return 'Math'
    elif '/date/' in path_str:
        return 'Date'
    elif '/xml/' in path_str:
        return 'XML'
    elif '/json/' in path_str:
        return 'JSON'
    elif '/csv/' in path_str:
        return 'CSV'
    elif '/yaml/' in path_str:
        return 'YAML'
    elif '/binary/' in path_str:
        return 'Binary'
    elif '/encoding/' in path_str:
        return 'Encoding'
    elif '/type/' in path_str:
        return 'Type'
    elif '/object/' in path_str:
        return 'Object'
    elif '/core/' in path_str:
        return 'Core'
    elif '/util/' in path_str:
        return 'Utility'
    elif '/finance/' in path_str:
        return 'Financial'
    elif '/geo/' in path_str:
        return 'Geospatial'
    elif '/jws/' in path_str or '/jwt/' in path_str:
        return 'Security'
    else:
        return 'Other'

def generate_tags(func_name: str, category: str, kdoc_info: dict) -> List[str]:
    """Generate intelligent tags"""
    tags = set()

    # Category
    tags.add(category.lower())

    # Function name patterns
    name_lower = func_name.lower()
    if 'find' in name_lower:
        tags.add('search')
    if 'filter' in name_lower or 'compact' in name_lower:
        tags.add('filter')
    if 'index' in name_lower:
        tags.add('index')
    if 'map' in name_lower:
        tags.add('transform')
    if 'reduce' in name_lower:
        tags.add('aggregate')
    if 'sort' in name_lower:
        tags.add('sort')
    if 'every' in name_lower or 'some' in name_lower or 'all' in name_lower:
        tags.add('predicate')

    # From notes
    all_text = ' '.join(kdoc_info['notes']).lower()
    if 'null' in all_text:
        tags.add('null-handling')
    if 'empty' in all_text:
        tags.add('cleanup')
    if 'predicate' in all_text or 'condition' in all_text:
        tags.add('predicate')

    return sorted(list(tags))

def build_notes(kdoc_info: dict) -> str:
    """Build notes field from extracted info"""
    parts = []

    if kdoc_info['filters_out']:
        parts.append("Filters out: " + ", ".join(kdoc_info['filters_out']))

    if kdoc_info['keeps']:
        parts.append("Keeps: " + ", ".join(kdoc_info['keeps']))

    # Add other notes
    for note in kdoc_info['notes']:
        if not note.startswith('Filters out:') and not note.startswith('Keeps:'):
            parts.append(note)

    return "\n".join(parts) if parts else ""

def infer_args_count(func_body: str) -> Tuple[int, int]:
    """Infer min/max args from function body"""
    # Look for args.size checks
    size_checks = re.findall(r'args\.size\s*[!<>=]+\s*(\d+)', func_body)
    range_checks = re.findall(r'args\.size\s+in\s+(\d+)\.\.(\d+)', func_body)
    exact_checks = re.findall(r'args\.size\s*[!=]=\s*(\d+)', func_body)

    if range_checks:
        return int(range_checks[0][0]), int(range_checks[0][1])
    elif exact_checks:
        num = int(exact_checks[0])
        return num, num
    elif size_checks:
        num = int(size_checks[0])
        return num, num

    # Default
    return 1, 1

def format_string_for_annotation(text: str) -> str:
    """Format string for use in annotation, handling quotes"""
    if not text:
        return '""'

    # For single-line strings, escape quotes and use regular quotes
    if '\n' not in text:
        # Escape existing backslashes first
        text = text.replace('\\', '\\\\')
        # Escape double quotes
        text = text.replace('"', '\\"')
        return '"' + text + '"'

    # For multi-line strings with code blocks (```), use escaped newlines for cleaner output
    # This makes the annotation more compact and avoids visual confusion with triple quotes
    if '```' in text or '"""' in text:
        # Escape existing backslashes
        text = text.replace('\\', '\\\\')
        # Escape double quotes
        text = text.replace('"', '\\"')
        # Escape newlines and other special chars
        text = text.replace('\n', '\\n').replace('\t', '\\t').replace('\r', '\\r')
        return '"' + text + '"'
    else:
        # Safe to use triple quotes for clean multi-line strings
        return '"""' + text + '"""'

def build_annotation(func_name: str, kdoc_info: dict, category: str, filepath: Path, func_body: str) -> str:
    """Build complete @UTLXFunction annotation"""

    # Core fields
    description = kdoc_info['description'] or f"Performs {func_name} operation"
    min_args, max_args = infer_args_count(func_body)

    # Parameters
    parameters = infer_parameters(func_body, func_name)
    params_str = ",\n        ".join([format_string_for_annotation(p) for p in parameters])

    # Returns
    returns = infer_returns(kdoc_info, func_name, func_body)

    # Examples
    example = kdoc_info['usage_examples'][0] if kdoc_info['usage_examples'] else f"{func_name}(...) => result"
    additional_examples = kdoc_info['usage_examples'][1:] if len(kdoc_info['usage_examples']) > 1 else []

    # Notes
    notes = build_notes(kdoc_info)

    # Tags
    tags = generate_tags(func_name, category, kdoc_info)
    tags_str = ', '.join([f'"{t}"' for t in tags])

    # See also
    see_also = kdoc_info['complement_to']

    # Build annotation
    lines = [
    "    @UTLXFunction(",
        f"        description = {format_string_for_annotation(description)},",
        f"        minArgs = {min_args},",
        f"        maxArgs = {max_args},",
        f"        category = \"{category}\","
    ]

    if parameters:
        lines.append(f"        parameters = [")
        lines.append(f"            {params_str}")
        lines.append(f"        ],")

    lines.append(f"        returns = {format_string_for_annotation(returns)},")
    lines.append(f"        example = {format_string_for_annotation(example)},")

    if additional_examples:
        examples_str = ",\n            ".join([format_string_for_annotation(ex) for ex in additional_examples])
        lines.append(f"        additionalExamples = [")
        lines.append(f"            {examples_str}")
        lines.append(f"        ],")

    if notes:
        lines.append(f"        notes = {format_string_for_annotation(notes)},")

    lines.append(f"        tags = [{tags_str}],")

    if see_also:
        see_also_str = ', '.join([f'"{f}"' for f in see_also])
        lines.append(f"        seeAlso = [{see_also_str}],")

    lines.append(f"        since = \"1.0\"")
    lines.append("    )")

    return "\n".join(lines)

def add_import_if_needed(lines: List[str]) -> Tuple[List[str], int]:
    """Add UTLXFunction import if not present, return (lines, offset)"""
    # Check if import already exists
    for line in lines:
        if 'import org.apache.utlx.stdlib.annotations.UTLXFunction' in line:
            return lines, 0  # Already has import

    # Find package line
    for i, line in enumerate(lines):
        if line.startswith('package '):
            # Find last import or package line
            insert_pos = i + 1
            for j in range(i+1, len(lines)):
                if lines[j].startswith('import '):
                    insert_pos = j + 1
                elif lines[j].strip() == '':
                    continue
                else:
                    break

            # Insert import
            lines.insert(insert_pos, 'import org.apache.utlx.stdlib.annotations.UTLXFunction')
            return lines, 1  # Added 1 line

    return lines, 0

def process_file(filepath: Path, dry_run: bool = False):
    """Process a single file"""
    print(f"\n{'[DRY RUN] ' if dry_run else ''}Processing {filepath.name}...")

    with open(filepath, 'r') as f:
        content = f.read()
        lines = content.split('\n')

    # Find all function definitions BEFORE adding import
    functions = []
    for i, line in enumerate(lines):
        match = re.search(r'fun ([a-z][A-Za-z0-9_]*)\(args: List<UDM>\)', line)
        if match:
            func_name = match.group(1)
            functions.append((i, func_name))

    # Add import if needed
    lines, import_offset = add_import_if_needed(lines)
    if import_offset > 0:
        print(f"  ✓ Added UTLXFunction import")

    print(f"  Found {len(functions)} functions")

    # Check which already have annotations
    annotated = set()
    for i, line in enumerate(lines):
        if '@UTLXFunction' in line:
            for j in range(i+1, min(i+25, len(lines))):
                match = re.search(r'fun ([a-z][A-Za-z0-9_]*)\(args: List<UDM>\)', lines[j])
                if match:
                    annotated.add(match.group(1))
                    break

    to_annotate = [(line_num, func_name) for line_num, func_name in functions
                   if func_name not in annotated]

    if not to_annotate:
        print(f"  ✓ All functions already annotated")
        # Still write file if we added import
        if import_offset > 0:
            with open(filepath, 'w') as f:
                f.write('\n'.join(lines))
        return

    print(f"  Adding annotations to {len(to_annotate)} functions:\n")

    category = determine_category(filepath)
    new_lines = lines.copy()
    offset = import_offset  # Start with import offset

    for line_num, func_name in sorted(to_annotate):
        actual_line = line_num + offset

        # Extract KDoc
        kdoc_info = extract_kdoc_comprehensive(new_lines, actual_line)

        # Get function body for analysis
        func_body_end = min(actual_line + 50, len(new_lines))
        func_body = '\n'.join(new_lines[actual_line:func_body_end])

        # Build annotation
        annotation = build_annotation(func_name, kdoc_info, category, filepath, func_body)

        print(f"  {func_name}:")
        print(f"    Description: {kdoc_info['description'] or '(inferred)'}")
        if kdoc_info['usage_examples']:
            print(f"    Examples: {len(kdoc_info['usage_examples'])}")
        if kdoc_info['filters_out'] or kdoc_info['keeps']:
            print(f"    Behavioral notes: Yes")
        print()

        if dry_run:
            print("    Generated annotation:")
            for ann_line in annotation.split('\n'):
                print(f"      {ann_line}")
            print()
            continue

        # Find insertion point - need to insert BEFORE the KDoc if present
        insert_line = actual_line

        # Step 1: Skip back over empty lines immediately before function
        while insert_line > 0 and new_lines[insert_line-1].strip() == '':
            insert_line -= 1

        # Step 2: Check if there's a KDoc comment (ending with */)
        has_kdoc = False
        if insert_line > 0 and new_lines[insert_line-1].strip().endswith('*/'):
            has_kdoc = True
            # Find the start of the KDoc (/**)
            while insert_line > 0:
                insert_line -= 1
                if '/**' in new_lines[insert_line]:
                    # Found start of KDoc, insert_line now points to /** line
                    break

        # Step 3: Only skip back over empty lines if there's NO KDoc
        # (if there's a KDoc, we want the annotation directly before it)
        if not has_kdoc:
            while insert_line > 0 and new_lines[insert_line-1].strip() == '':
                insert_line -= 1

        # Insert annotation with proper spacing
        ann_lines = annotation.split('\n')

        # Add blank line BEFORE annotation if the previous line isn't already blank
        if insert_line > 0 and new_lines[insert_line-1].strip() != '':
            ann_lines.insert(0, '')  # Add blank line before annotation

        # Insert in reverse order
        for ann_line in reversed(ann_lines):
            new_lines.insert(insert_line, ann_line)
            offset += 1

    if not dry_run:
        with open(filepath, 'w') as f:
            f.write('\n'.join(new_lines))
        print(f"  ✓ Wrote {len(to_annotate)} annotations")

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 enhance-annotations-v3.py <file.kt> [--dry-run]")
        sys.exit(1)

    filepath = Path(sys.argv[1])
    dry_run = '--dry-run' in sys.argv

    if not filepath.exists():
        print(f"Error: File not found: {filepath}")
        sys.exit(1)

    process_file(filepath, dry_run)

if __name__ == '__main__':
    main()
