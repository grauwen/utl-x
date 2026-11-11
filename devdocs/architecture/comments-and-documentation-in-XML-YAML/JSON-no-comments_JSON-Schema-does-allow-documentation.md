**JSON itself does not allow comments.** The JSON specification explicitly excludes any form of comments. If you try to add comments using `//`, `/* */`, or `#`, it will be invalid JSON and parsers will reject it.

**JSON Schema does allow documentation.** JSON Schema provides several properties specifically for documentation purposes:

- `title` - A short description of the schema
- `description` - A more detailed explanation
- `$comment` - For internal comments (not meant for users, and may be stripped by some tools)
- `examples` - To provide example values

For example:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "User Profile",
  "description": "Schema for user profile data",
  "type": "object",
  "properties": {
    "username": {
      "type": "string",
      "description": "The user's unique username",
      "examples": ["john_doe"]
    }
  },
  "$comment": "This is an internal note about the schema"
}
```

So while you can't document your actual JSON data files, you can thoroughly document your JSON Schema definitions, which is one of the reasons schemas are so useful!
