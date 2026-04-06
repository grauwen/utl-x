# UTL-X CLI Function Cheat Sheet

**Total Functions**: 652
**Generated**: 2026-04-06

## Quick Search Commands

```bash
# Search by category
utlx functions search "array"      # Array functions
utlx functions search "string"     # String functions  
utlx functions search "math"       # Math functions
utlx functions search "xml"        # XML functions

# Search by operation
utlx functions search "encode"     # Encoding operations
utlx functions search "convert"    # Conversion functions
utlx functions search "transform"  # Transformation functions
```

## Most Used Functions

### Array Operations

### Array
- `map(1)` - Map function over array
- `filter(1)` - Generic filter function that works on arrays, objects, and strings
- `reduce(1)` - Reduce array to single value
- `find(1)` - Find first element matching predicate

### String
- `upper(1)` - Convert string to uppercase (Legacy naming - use upperCase)
- `lower(1)` - Convert string to lowercase (Legacy naming - use lowerCase)
- `trim(2)` - Trim whitespace from both ends
- `replace(2-3)` - Replace occurrences in string. Supports single replacement or multiple replacements via object/array.

### Math
- `abs(1)` - Performs abs operation
- `round(1)` - Performs round operation
- `ceil(1)` - Performs ceil operation
- `floor(1)` - Performs floor operation

### Encoding
- `base64Encode(1)` - Base64 encode
- `base64Decode(1)` - Base64 decode
- `urlEncode(1)` - URL encode
- `urlDecode(1)` - URL decode
