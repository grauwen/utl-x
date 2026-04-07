# JSON BOM Support

## Status: ✅ Implemented

The JSON parser correctly handles UTF-8 BOM (Byte Order Mark, U+FEFF) per RFC 8259.

### Implementation
- Location: `formats/json/src/main/kotlin/org/apache/utlx/formats/json/json_parser.kt` Line 30-34
- The parser strips BOM if present at the start of JSON input
- RFC 8259 compliance: Parsers MUST tolerate and ignore BOM

### Testing
Tested manually with file input containing BOM - works correctly.

Example:
```bash
# Create JSON with BOM
echo -ne "\xEF\xBB\xBF{\"test\": 123}" > input.json

# Transform works correctly
utlx transform script.utlx input.json
```

### Notes
- BOM handling works for file input
- Best practice per RFC 8259: Don't generate BOMs, but tolerate them when parsing
- Most JSON generators don't include BOMs (and shouldn't)
