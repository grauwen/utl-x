# Toon Token Oriented Object Notation (TOON)

## Introduction

Toon Token Oriented Object Notation (TOON) is a lightweight data serialization format designed for representing structured data with an emphasis on human readability and simplicity. TOON aims to provide an alternative to formats like JSON and YAML while maintaining clear tokenization boundaries and intuitive object-oriented structure.

## Core Concepts

### Tokens

In TOON, every piece of data is represented as a token. Tokens are the fundamental units of data representation and come in several types:

- **Primitive tokens**: strings, numbers, booleans, and null
- **Container tokens**: objects and arrays
- **Special tokens**: references and metadata markers

### Syntax Fundamentals

TOON uses a clean, minimalist syntax that prioritizes readability:

```
# This is a comment
key: value
nested_object: {
  property: "string value"
  count: 42
  active: true
}
```

## Data Types

### Primitives

**Strings**: Enclosed in double quotes, supporting escape sequences
```
name: "John Doe"
message: "Line one\nLine two"
```

**Numbers**: Integer or floating-point values
```
age: 30
price: 19.99
scientific: 1.5e-10
```

**Booleans**: `true` or `false`
```
active: true
deleted: false
```

**Null**: Represents absence of value
```
optional_field: null
```

### Objects

Objects are collections of key-value pairs enclosed in curly braces:

```
person: {
  name: "Alice"
  age: 28
  email: "alice@example.com"
}
```

Keys can be unquoted if they follow identifier rules (alphanumeric and underscores), otherwise they must be quoted.

### Arrays

Arrays are ordered collections enclosed in square brackets:

```
colors: ["red", "green", "blue"]
numbers: [1, 2, 3, 4, 5]
mixed: [1, "two", true, null]
```

## Advanced Features

### Nested Structures

TOON supports arbitrary nesting of objects and arrays:

```
company: {
  name: "TechCorp"
  employees: [
    {
      name: "Bob"
      role: "Developer"
      skills: ["Python", "Go", "Rust"]
    }
    {
      name: "Carol"
      role: "Designer"
      skills: ["Figma", "Sketch"]
    }
  ]
}
```

### Multi-line Strings

Long strings can be represented using pipe notation for better readability:

```
description: |
  This is a multi-line string
  that preserves line breaks
  and indentation.
```

### References

TOON supports internal references to avoid data duplication:

```
default_config: &defaults {
  timeout: 30
  retries: 3
}

service_a: {
  <<: *defaults
  port: 8080
}

service_b: {
  <<: *defaults
  port: 8081
}
```

## Comparison with Other Formats

### TOON vs JSON

**Advantages of TOON:**
- More human-readable with optional commas
- Supports comments
- Cleaner syntax for nested structures
- Built-in reference system

**JSON Advantages:**
- Wider ecosystem support
- Faster parsing in most languages
- More established standard

### TOON vs YAML

**Advantages of TOON:**
- More explicit structure with braces
- Less sensitive to indentation errors
- Clearer token boundaries

**YAML Advantages:**
- Extremely minimal syntax
- Mature tooling ecosystem
- Widely adopted

## Use Cases

TOON is particularly well-suited for:

1. **Configuration files**: Clear, readable config files for applications
2. **Data exchange**: Lightweight API responses and request bodies
3. **Documentation**: Embedded data examples in technical documentation
4. **Testing**: Test fixtures and mock data definitions
5. **Serialization**: Object serialization where human readability matters

## Best Practices

### Naming Conventions

Use consistent naming conventions for keys:
```
# snake_case (recommended)
user_name: "Alice"
email_address: "alice@example.com"

# camelCase (alternative)
userName: "Alice"
emailAddress: "alice@example.com"
```

### Indentation

Use consistent indentation (2 or 4 spaces):
```
root: {
  level1: {
    level2: {
      value: "deep nested"
    }
  }
}
```

### Comments

Use comments to document complex structures:
```
# Database configuration
database: {
  # Primary database connection
  primary: {
    host: "localhost"
    port: 5432
  }
  
  # Read replica for load distribution
  replica: {
    host: "replica.example.com"
    port: 5432
  }
}
```

## Implementation Considerations

### Parsing Strategy

A typical TOON parser would implement:

1. **Lexical analysis**: Tokenize the input stream into meaningful tokens
2. **Syntax analysis**: Build an abstract syntax tree (AST) from tokens
3. **Semantic analysis**: Resolve references and validate structure
4. **Object construction**: Convert AST to native data structures

### Error Handling

Good TOON implementations should provide:
- Clear error messages with line and column numbers
- Helpful suggestions for common syntax errors
- Validation against schemas when applicable

### Performance Optimization

For high-performance scenarios:
- Implement streaming parsers for large files
- Use efficient data structures for token storage
- Cache parsed results when appropriate
- Consider binary encoding for performance-critical paths

## Ecosystem and Tooling

A complete TOON ecosystem would include:

- **Parsers**: Libraries for major programming languages
- **Validators**: Schema validation tools
- **Formatters**: Auto-formatting and prettifying tools
- **Converters**: Bidirectional conversion with JSON, YAML, XML
- **IDE Support**: Syntax highlighting and auto-completion
- **Linters**: Code quality and style checking tools

## Grammar Specification

### Formal Grammar (EBNF)

```ebnf
document    ::= element*
element     ::= comment | assignment
comment     ::= '#' [^\n]* '\n'
assignment  ::= key ':' value

key         ::= identifier | string
identifier  ::= [a-zA-Z_][a-zA-Z0-9_]*

value       ::= primitive | object | array | reference
primitive   ::= string | number | boolean | null

string      ::= '"' char* '"' | '|' multiline
number      ::= integer | float
integer     ::= '-'? [0-9]+
float       ::= '-'? [0-9]+ '.' [0-9]+ exponent?
exponent    ::= ('e' | 'E') ('+' | '-')? [0-9]+
boolean     ::= 'true' | 'false'
null        ::= 'null'

object      ::= '{' (assignment)* '}'
array       ::= '[' (value (',' value)*)? ']'
reference   ::= '&' identifier | '*' identifier | '<<' ':' '*' identifier
multiline   ::= ('\n' ' '+ [^\n]*)*
```

## Example Use Cases

### Configuration File Example

```
# Application Configuration
app: {
  name: "MyApp"
  version: "1.0.0"
  
  server: {
    host: "0.0.0.0"
    port: 8080
    ssl: true
    cert_path: "/etc/ssl/cert.pem"
  }
  
  database: {
    driver: "postgresql"
    host: "localhost"
    port: 5432
    name: "myapp_db"
    pool_size: 10
  }
  
  logging: {
    level: "info"
    format: "json"
    outputs: ["stdout", "file"]
  }
  
  features: {
    authentication: true
    rate_limiting: true
    caching: false
  }
}
```

### API Response Example

```
# User Profile API Response
response: {
  status: "success"
  data: {
    user: {
      id: 12345
      username: "johndoe"
      email: "john@example.com"
      created_at: "2024-01-15T10:30:00Z"
      profile: {
        first_name: "John"
        last_name: "Doe"
        bio: |
          Software engineer passionate about
          clean code and elegant solutions.
        avatar_url: "https://example.com/avatars/johndoe.jpg"
      }
      preferences: {
        theme: "dark"
        language: "en"
        notifications: true
      }
      roles: ["user", "contributor"]
    }
  }
  metadata: {
    request_id: "req_abc123"
    timestamp: "2024-11-13T14:25:30Z"
    version: "v1"
  }
}
```

### Test Fixture Example

```
# Test Data for User Service
test_users: [
  {
    id: 1
    username: "alice"
    email: "alice@test.com"
    active: true
    roles: ["admin"]
  }
  {
    id: 2
    username: "bob"
    email: "bob@test.com"
    active: true
    roles: ["user"]
  }
  {
    id: 3
    username: "charlie"
    email: "charlie@test.com"
    active: false
    roles: ["user"]
  }
]

test_scenarios: {
  login_success: {
    input: {
      username: "alice"
      password: "correct_password"
    }
    expected: {
      status: 200
      token: "mock_token_abc123"
    }
  }
  
  login_failure: {
    input: {
      username: "alice"
      password: "wrong_password"
    }
    expected: {
      status: 401
      error: "Invalid credentials"
    }
  }
}
```

## Schema Validation

TOON can support schema validation for type checking and structure enforcement:

```
# TOON Schema Example
schema: {
  type: "object"
  properties: {
    name: {
      type: "string"
      required: true
      min_length: 1
      max_length: 100
    }
    age: {
      type: "number"
      required: false
      minimum: 0
      maximum: 150
    }
    email: {
      type: "string"
      required: true
      pattern: "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    }
    tags: {
      type: "array"
      items: {
        type: "string"
      }
    }
  }
}
```

## Future Extensions

Potential future enhancements to TOON:

1. **Binary format**: Compact binary representation for performance
2. **Streaming support**: Event-based parsing for large datasets
3. **Schema evolution**: Versioning and migration tools
4. **Compression**: Built-in compression support
5. **Encryption**: Native encryption for sensitive data
6. **Templating**: Variable substitution and template inheritance
7. **Macros**: User-defined shortcuts and transformations

## Contributing to TOON

To contribute to TOON development:

1. **Specification**: Help refine the language specification
2. **Implementation**: Build parsers and tools in various languages
3. **Documentation**: Improve guides and examples
4. **Testing**: Create comprehensive test suites
5. **Ecosystem**: Develop plugins and integrations

## Resources

### Learning Materials
- Official TOON Specification
- Tutorial: Getting Started with TOON
- Video Series: TOON in 10 Minutes
- Interactive Playground

### Tools and Libraries
- TOON Parser (Python, JavaScript, Go, Rust)
- TOON Validator
- TOON Formatter
- Online TOON Editor

### Community
- GitHub Repository
- Discord Server
- Stack Overflow Tag
- Monthly Newsletter

## Conclusion

Toon Token Oriented Object Notation offers a balanced approach to data serialization, emphasizing human readability while maintaining clear structural boundaries. While it may not replace established formats in all scenarios, TOON provides a compelling alternative for use cases where clarity and ease of maintenance are paramount. Its token-oriented design makes it straightforward to parse and generate, while its object-oriented structure maps naturally to modern programming paradigms.

---

**Document Version**: 1.0  
**Last Updated**: November 2025  
**License**: Creative Commons Attribution 4.0 International
