JSON can technically have a BOM (Byte Order Mark), though it’s generally discouraged.

According to **RFC 8259** (the current JSON specification), JSON text **may** begin with a BOM (U+FEFF), but implementations:

- **Must not** add a BOM when generating JSON
- **Must** tolerate and ignore a BOM if present when parsing JSON

The BOM in JSON would be the UTF-8 encoded bytes `EF BB BF` at the very start of the document.

## Why it’s discouraged

The JSON specification recommends against using a BOM because:

1. **JSON is UTF-8 by default** - Unlike XML which supports multiple encodings, JSON must be UTF-8 (or UTF-16/UTF-32 in specific contexts), so a BOM serves little purpose
1. **It can cause interoperability issues** - Some older parsers or systems may not handle it correctly
1. **It’s unnecessary overhead** - The BOM doesn’t provide useful information for JSON

## In practice

- Most JSON generators don’t include a BOM
- Most modern JSON parsers will handle it correctly if present
- However, if you’re troubleshooting weird parsing issues with JSON from an external source, a BOM is worth checking for - it can cause problems like preventing exact string matches on the first key

So while JSON *can* have a BOM, best practice is to avoid including one when creating JSON files.
