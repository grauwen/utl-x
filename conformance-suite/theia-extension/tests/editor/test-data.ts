/**
 * Test Data for Format Strategy and Detection Tests
 *
 * Provides sample content for each format to use in tests
 */

export const TestData = {
  xml: {
    simple: `<?xml version="1.0" encoding="UTF-8"?>
<Order id="12345">
  <Customer>John Doe</Customer>
  <Items>
    <Item sku="A001">Widget</Item>
    <Item sku="A002">Gadget</Item>
  </Items>
  <Total>99.99</Total>
</Order>`,

    withNamespaces: `<?xml version="1.0" encoding="UTF-8"?>
<Order xmlns="http://example.com/order" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" id="12345">
  <Customer>John Doe</Customer>
  <Items>
    <Item sku="A001">Widget</Item>
  </Items>
</Order>`,

    withComment: `<!-- This is a comment -->
<?xml version="1.0" encoding="UTF-8"?>
<Order id="12345">
  <Customer>John Doe</Customer>
</Order>`,

    withDoctype: `<!DOCTYPE html>
<html>
  <head><title>Test</title></head>
  <body><p>Content</p></body>
</html>`,
  },

  xsd: {
    simple: `<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="Order">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="Customer" type="xs:string"/>
        <xs:element name="Total" type="xs:decimal"/>
      </xs:sequence>
      <xs:attribute name="id" type="xs:string" use="required"/>
    </xs:complexType>
  </xs:element>
</xs:schema>`,
  },

  json: {
    simple: `{
  "order": {
    "id": "12345",
    "customer": "John Doe",
    "items": [
      { "sku": "A001", "name": "Widget", "price": 19.99 },
      { "sku": "A002", "name": "Gadget", "price": 29.99 }
    ],
    "total": 99.99,
    "paid": true,
    "notes": null
  }
}`,

    withTypes: `{
  "integer": 42,
  "float": 3.14,
  "boolean": true,
  "null": null,
  "string": "hello",
  "array": [1, 2, 3],
  "object": { "nested": "value" }
}`,
  },

  jsch: {
    simple: `{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Order",
  "type": "object",
  "properties": {
    "id": { "type": "string" },
    "customer": { "type": "string" },
    "total": { "type": "number" }
  },
  "required": ["id", "customer", "total"]
}`,
  },

  yaml: {
    simple: `order:
  id: "12345"
  customer: John Doe
  items:
    - sku: A001
      name: Widget
      price: 19.99
    - sku: A002
      name: Gadget
      price: 29.99
  total: 99.99
  paid: true
  notes: null
`,

    withComments: `# This is a comment
order:
  id: "12345"  # Inline comment
  customer: John Doe
`,

    multiline: `description: |
  This is a multi-line string
  that spans multiple lines
  and preserves line breaks
`,
  },

  csv: {
    comma: `EmployeeID,FirstName,LastName,Department,Salary
E001,John,Smith,Engineering,95000
E002,Sarah,Johnson,Marketing,78000
E003,Michael,Williams,Engineering,102000`,

    semicolon: `EmployeeID;FirstName;LastName;Department;Salary
E001;John;Smith;Engineering;95000
E002;Sarah;Johnson;Marketing;78000`,

    tab: `EmployeeID\tFirstName\tLastName\tDepartment\tSalary
E001\tJohn\tSmith\tEngineering\t95000
E002\tSarah\tJohnson\tMarketing\t78000`,

    pipe: `EmployeeID|FirstName|LastName|Department|Salary
E001|John|Smith|Engineering|95000
E002|Sarah|Johnson|Marketing|78000`,
  },

  avro: {
    simple: `{
  "type": "record",
  "name": "Order",
  "namespace": "com.example",
  "fields": [
    { "name": "id", "type": "string" },
    { "name": "customer", "type": "string" },
    { "name": "total", "type": "double" }
  ]
}`,
  },

  proto: {
    proto3: `syntax = "proto3";

package example;

message Order {
  string id = 1;
  string customer = 2;
  repeated Item items = 3;
  double total = 4;
}

message Item {
  string sku = 1;
  string name = 2;
  double price = 3;
}`,

    proto2: `syntax = "proto2";

package example;

message Order {
  required string id = 1;
  required string customer = 2;
  optional double total = 3;
}`,
  },
};

/**
 * Expected format detection results for each test case
 */
export const ExpectedDetection = {
  xml: {
    simple: 'xml',
    withNamespaces: 'xml',
    withComment: 'xml',
    withDoctype: 'xml',
  },
  xsd: {
    simple: 'xsd',
  },
  json: {
    simple: 'json',
    withTypes: 'json',
  },
  jsch: {
    simple: 'jsch',
  },
  yaml: {
    simple: 'yaml',
    withComments: 'yaml',
    multiline: 'yaml',
  },
  csv: {
    comma: 'csv',
    semicolon: 'csv',
    tab: 'csv',
    pipe: 'csv',
  },
  avro: {
    simple: 'avro',
  },
  proto: {
    proto3: 'proto',
    proto2: 'proto',
  },
};
