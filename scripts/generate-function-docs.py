#!/usr/bin/env python3
"""
Comprehensive UTL-X Function Documentation Generator

Generates enterprise-ready documentation from the UTL-X function registry.
Creates multiple output formats for different audiences.

Usage: python3 generate-function-docs.py
"""

import json
import yaml
import os
from pathlib import Path
from datetime import datetime

def load_function_registry():
    """Load the generated function registry"""
    registry_dir = Path("stdlib/build/generated/function-registry")
    json_file = registry_dir / "utlx-functions.json"
    
    if not json_file.exists():
        print("Error: Function registry not found. Run './gradlew :stdlib:generateFunctionRegistry' first.")
        return None
    
    with open(json_file, 'r') as f:
        return json.load(f)

def generate_markdown_reference(registry):
    """Generate comprehensive Markdown reference documentation"""
    
    md_content = f"""# UTL-X Standard Library Reference

**Generated**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}  
**Total Functions**: {registry['totalFunctions']}  
**Version**: {registry['version']}

## Overview

UTL-X provides {registry['totalFunctions']} built-in functions across {len(registry['categories'])} categories for data transformation, processing, and integration tasks.

## Quick Reference

| Category | Functions | Description |
|----------|-----------|-------------|
"""
    
    for category, functions in registry['categories'].items():
        count = len(functions)
        md_content += f"| {category} | {count} | {get_category_description(category)} |\n"
    
    md_content += "\n## Function Categories\n\n"
    
    # Generate detailed sections by category
    for category, functions in sorted(registry['categories'].items()):
        md_content += f"### {category} Functions ({len(functions)})\n\n"
        md_content += f"{get_category_description(category)}\n\n"
        
        # Table of functions in this category
        md_content += "| Function | Args | Description | Example |\n"
        md_content += "|----------|------|-------------|----------|\n"
        
        for func in sorted(functions, key=lambda x: x['name']):
            args_str = format_args(func)
            desc = func['description'].replace('|', '\\|')  # Escape pipes
            example = extract_example(func['description']) or ""
            example = example.replace('|', '\\|')  # Escape pipes
            
            md_content += f"| `{func['name']}` | {args_str} | {desc} | {example} |\n"
        
        md_content += "\n"
    
    return md_content

def get_category_description(category):
    """Get description for each category"""
    descriptions = {
        'Array': 'Functional operations, filtering, mapping, and array manipulation',
        'String': 'Text processing, case conversion, and string manipulation',
        'Math': 'Mathematical operations, arithmetic, and numeric functions',
        'XML': 'XML parsing, encoding detection, and namespace handling',
        'JSON': 'JSON canonicalization, formatting, and processing',
        'Encoding': 'Base64, URL encoding/decoding, and cryptographic hashing',
        'Date': 'Date/time parsing, formatting, and manipulation',
        'Other': 'Utility functions, system operations, and specialized tools'
    }
    return descriptions.get(category, 'Utility functions')

def format_args(func):
    """Format function arguments"""
    if func.get('minArgs') is not None and func.get('maxArgs') is not None:
        if func['minArgs'] == func['maxArgs']:
            return f"{func['minArgs']}"
        else:
            return f"{func['minArgs']}-{func['maxArgs']}"
    elif func.get('minArgs') is not None:
        return f"{func['minArgs']}+"
    elif func.get('maxArgs') is not None:
        return f"≤{func['maxArgs']}"
    else:
        return "var"

def extract_example(description):
    """Extract example from description if available"""
    # This would be enhanced to extract from the actual example field
    return ""

def generate_api_reference(registry):
    """Generate API reference in structured format"""
    
    api_ref = {
        "utlx_version": registry['version'],
        "generated_at": registry['generatedAt'],
        "total_functions": registry['totalFunctions'],
        "categories": {}
    }
    
    for category, functions in registry['categories'].items():
        api_ref["categories"][category] = {
            "description": get_category_description(category),
            "function_count": len(functions),
            "functions": functions
        }
    
    return api_ref

def generate_vs_code_snippets(registry):
    """Generate VS Code snippets for UTL-X functions"""
    
    snippets = {}
    
    for category, functions in registry['categories'].items():
        for func in functions:
            if func.get('minArgs') is not None:
                # Generate snippet with placeholders
                args = []
                for i in range(func['minArgs']):
                    args.append(f"${{{i+1}:arg{i+1}}}")
                
                snippet_body = f"{func['name']}({', '.join(args)})"
                
                snippets[f"utlx-{func['name']}"] = {
                    "prefix": func['name'],
                    "body": snippet_body,
                    "description": func['description'],
                    "scope": "utlx"
                }
    
    return snippets

def generate_cli_cheatsheet(registry):
    """Generate CLI cheat sheet"""
    
    cheat_content = f"""# UTL-X CLI Function Cheat Sheet

**Total Functions**: {registry['totalFunctions']}
**Generated**: {datetime.now().strftime('%Y-%m-%d')}

## Quick Search Commands

```bash
# Search by category
./utlx-functions search "array"      # Array functions
./utlx-functions search "string"     # String functions  
./utlx-functions search "math"       # Math functions
./utlx-functions search "xml"        # XML functions

# Search by operation
./utlx-functions search "encode"     # Encoding operations
./utlx-functions search "convert"    # Conversion functions
./utlx-functions search "transform"  # Transformation functions
```

## Most Used Functions

### Array Operations
"""
    
    # Add most common functions from each category
    important_functions = {
        'Array': ['map', 'filter', 'reduce', 'find'],
        'String': ['upper', 'lower', 'trim', 'replace'],
        'Math': ['abs', 'round', 'ceil', 'floor'],
        'Encoding': ['base64Encode', 'base64Decode', 'urlEncode', 'urlDecode']
    }
    
    for category, func_names in important_functions.items():
        cheat_content += f"\n### {category}\n"
        
        for func_name in func_names:
            # Find function in registry
            for functions in registry['categories'].values():
                func = next((f for f in functions if f['name'] == func_name), None)
                if func:
                    args_str = format_args(func)
                    cheat_content += f"- `{func['name']}({args_str})` - {func['description']}\n"
                    break
    
    return cheat_content

def main():
    print("UTL-X Function Documentation Generator")
    print("=====================================")
    
    # Load function registry
    registry = load_function_registry()
    if not registry:
        return
    
    print(f"Loaded registry with {registry['totalFunctions']} functions")
    
    # Create output directory
    docs_dir = Path("docs/functions")
    docs_dir.mkdir(parents=True, exist_ok=True)
    
    # Generate Markdown reference
    print("Generating Markdown reference...")
    md_content = generate_markdown_reference(registry)
    with open(docs_dir / "function-reference.md", 'w') as f:
        f.write(md_content)
    
    # Generate API reference
    print("Generating API reference...")
    api_ref = generate_api_reference(registry)
    with open(docs_dir / "api-reference.json", 'w') as f:
        json.dump(api_ref, f, indent=2)
    
    # Generate VS Code snippets
    print("Generating VS Code snippets...")
    snippets = generate_vs_code_snippets(registry)
    with open(docs_dir / "utlx-snippets.json", 'w') as f:
        json.dump(snippets, f, indent=2)
    
    # Generate CLI cheat sheet
    print("Generating CLI cheat sheet...")
    cheat_content = generate_cli_cheatsheet(registry)
    with open(docs_dir / "cli-cheatsheet.md", 'w') as f:
        f.write(cheat_content)
    
    print(f"\nDocumentation generated in: {docs_dir.absolute()}")
    print("Files created:")
    print("  - function-reference.md  (Complete function documentation)")
    print("  - api-reference.json     (Structured API reference)")
    print("  - utlx-snippets.json     (VS Code snippets)")
    print("  - cli-cheatsheet.md      (Quick reference for CLI)")
    
    # Show summary
    print(f"\nFunction Summary:")
    for category, functions in sorted(registry['categories'].items()):
        annotated_count = sum(1 for f in functions if 'No description available' not in f['description'])
        total_count = len(functions)
        print(f"  {category:12}: {annotated_count:3}/{total_count:3} annotated ({annotated_count/total_count*100:.1f}%)")

if __name__ == '__main__':
    main()