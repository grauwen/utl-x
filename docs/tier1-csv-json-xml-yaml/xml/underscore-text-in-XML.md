# Understanding `_text` in XML Parsing

## Behavior in UTLX: XML -> JSON

- Internal representation: XML parser creates _text property internally to store text content
- Automatic unwrapping: When you access an element, the interpreter automatically unwraps it 
- User-facing API: You should just use $input.Order.Customer, NOT $input.Order.Customer._text


## Overview

`_text` is a special property name used by XML parsing libraries to store the text content of XML elements when converting XML to object/JSON format.

## Why `_text` is Used

When parsing XML into objects or JSON, there needs to be a way to distinguish between:
- Element text content
- Attributes
- Child elements

The `_text` property provides a standardized way to represent text content alongside other structured data.

## Example 1: Basic XML Structure

### XML Input:
```xml
<person>
  <name>John Doe</name>
  <age>30</age>
  <bio>
    Software developer with 10 years of experience.
    <emphasis>Loves coding!</emphasis>
  </bio>
</person>
```

### Converted to Object/JSON with `_text`:
```json
{
  "person": {
    "name": {
      "_text": "John Doe"
    },
    "age": {
      "_text": "30"
    },
    "bio": {
      "_text": "Software developer with 10 years of experience.",
      "emphasis": {
        "_text": "Loves coding!"
      }
    }
  }
}
```

## Example 2: Mixed Content

### XML Input:
```xml
<message>Hello <strong>world</strong>!</message>
```

### Representation with `_text`:
```json
{
  "message": {
    "_text": "Hello ",
    "strong": {
      "_text": "world"
    },
    "_text_after": "!"
  }
}
```

## Common Libraries Using `_text`

This convention is used by many XML parsing libraries across different programming languages:

- **Node.js**: xml2js
- **PHP**: SimpleXML
- **Python**: Various XML to dict converters
- **Java**: Some XML binding frameworks

## Key Benefits

1. **Clarity**: Clearly separates text content from structural elements
2. **Consistency**: Provides a predictable pattern for accessing text data
3. **Flexibility**: Allows text content to coexist with attributes and child elements
4. **Compatibility**: Widely adopted across different parsing libraries and languages

## Conclusion

The `_text` property is an essential convention in XML parsing that ensures text content can be properly represented when converting XML to object-based formats like JSON, making it easier to work with XML data programmatically.
