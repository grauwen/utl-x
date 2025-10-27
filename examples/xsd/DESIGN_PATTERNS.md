# XSD Design Patterns

This directory contains examples demonstrating the four major XML Schema design patterns. Each pattern represents a different approach to structuring XSD schemas, with different trade-offs in terms of reusability, maintainability, and flexibility.

## The Four XSD Design Patterns

### 1. Russian Doll Pattern

**Structure:**
- Only the root element is defined globally
- All other elements and types are nested inline (anonymous)
- Maximum nesting, minimum reusability

**Characteristics:**
- ✅ Simple to understand for small schemas
- ✅ Self-contained (all definitions in one place)
- ❌ No type reusability
- ❌ Difficult to maintain for large schemas
- ❌ Cannot use elements independently

**When to Use:**
- Very small, simple schemas
- Schemas that won't evolve or grow
- Quick prototyping

**Example Structure:**
```xml
<xs:element name="Root">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="Child">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="GrandChild" type="xs:string"/>
          </xs:sequence>
        </xs:complexType>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

---

### 2. Salami Slice Pattern

**Structure:**
- All elements are defined globally
- Types are defined inline (anonymous)
- Elements can be referenced from anywhere

**Characteristics:**
- ✅ Maximum granularity
- ✅ Elements can be used independently
- ✅ Good for data binding
- ✅ Supports partial document validation
- ❌ No type reusability
- ❌ Can lead to naming conflicts
- ❌ Verbose schemas

**When to Use:**
- Data binding scenarios (JAXB, XMLBeans)
- Partial document validation
- When elements need to be used independently
- Schema composition (importing elements from multiple schemas)

**Example Structure:**
```xml
<xs:element name="Root">
  <xs:complexType>
    <xs:sequence>
      <xs:element ref="Child"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>

<xs:element name="Child">
  <xs:complexType>
    <xs:sequence>
      <xs:element ref="GrandChild"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>

<xs:element name="GrandChild" type="xs:string"/>
```

---

### 3. Venetian Blind Pattern ⭐ RECOMMENDED

**Structure:**
- Only the root element is defined globally
- All complex types are defined globally (named types)
- Elements are defined locally (within types)

**Characteristics:**
- ✅ **Excellent type reusability**
- ✅ **Easy to maintain and extend**
- ✅ **Best balance of reusability and structure**
- ✅ Clean, organized schemas
- ✅ Avoids naming conflicts
- ❌ Cannot use elements independently (only root)

**When to Use:**
- **MOST ENTERPRISE APPLICATIONS** (recommended default)
- Large, complex schemas
- Schemas that will evolve over time
- When type reusability is important
- Team development (clear structure)

**Example Structure:**
```xml
<xs:element name="Root" type="RootType"/>

<xs:complexType name="RootType">
  <xs:sequence>
    <xs:element name="Child" type="ChildType"/>
  </xs:sequence>
</xs:complexType>

<xs:complexType name="ChildType">
  <xs:sequence>
    <xs:element name="GrandChild" type="xs:string"/>
  </xs:sequence>
</xs:complexType>
```

---

### 4. Garden of Eden Pattern

**Structure:**
- All elements are defined globally
- All types are defined globally (named types)
- Maximum flexibility

**Characteristics:**
- ✅ Maximum reusability (both elements and types)
- ✅ Excellent for schema composition
- ✅ Supports complex inheritance scenarios
- ❌ Risk of creating invalid document structures
- ❌ Can be complex to understand
- ❌ Naming conflicts more likely

**When to Use:**
- Schema composition and inheritance
- Multiple schema imports/includes
- When maximum flexibility is required
- Framework/library schemas that will be extended

**Example Structure:**
```xml
<xs:element name="Root" type="RootType"/>
<xs:element name="Child" type="ChildType"/>
<xs:element name="GrandChild" type="xs:string"/>

<xs:complexType name="RootType">
  <xs:sequence>
    <xs:element ref="Child"/>
  </xs:sequence>
</xs:complexType>

<xs:complexType name="ChildType">
  <xs:sequence>
    <xs:element ref="GrandChild"/>
  </xs:sequence>
</xs:complexType>
```

---

## Pattern Comparison Matrix

| Pattern | Type Reusability | Element Reusability | Maintainability | Complexity | Best For |
|---------|-----------------|---------------------|-----------------|------------|----------|
| **Russian Doll** | ❌ None | ❌ None | ❌ Poor (large schemas) | 🟢 Low | Small, simple schemas |
| **Salami Slice** | ❌ None | ✅ High | 🟡 Medium | 🟡 Medium | Data binding, partial validation |
| **Venetian Blind** ⭐ | ✅ High | ❌ None (except root) | ✅ Excellent | 🟢 Low | **Enterprise applications** |
| **Garden of Eden** | ✅ High | ✅ High | 🟡 Medium | 🔴 High | Schema composition, inheritance |

---

## Business Examples

This directory contains 3 real-world business examples, each implemented in all 4 design patterns:

### Example A: Product Order (13_product_order)

**Business Domain:** E-commerce order processing

**Data Model:**
- Order information (ID, date, status)
- Customer details (ID, name, email, address)
- Line items (product, quantity, price)
- Order total

**Files:**
- `13a_product_order_russian_doll.xsd` - Russian Doll pattern
- `13b_product_order_salami_slice.xsd` - Salami Slice pattern
- `13c_product_order_venetian_blind.xsd` - Venetian Blind pattern ⭐
- `13d_product_order_garden_of_eden.xsd` - Garden of Eden pattern
- `13_product_order.xml` - Sample data (validates against ALL 4 schemas)

---

### Example B: Employee Profile (14_employee_profile)

**Business Domain:** Human Resources (HR) employee management

**Data Model:**
- Employee ID
- Personal information (name, DOB, contact)
- Employment details (department, job title, hire date, manager)
- Compensation (salary, currency, bonus eligibility)
- Skills (name, proficiency level, years of experience)

**Files:**
- `14a_employee_profile_russian_doll.xsd` - Russian Doll pattern
- `14b_employee_profile_salami_slice.xsd` - Salami Slice pattern
- `14c_employee_profile_venetian_blind.xsd` - Venetian Blind pattern ⭐
- `14d_employee_profile_garden_of_eden.xsd` - Garden of Eden pattern
- `14_employee_profile.xml` - Sample data (validates against ALL 4 schemas)

---

### Example C: Insurance Policy (15_insurance_policy)

**Business Domain:** Insurance policy management

**Data Model:**
- Policy number and type (auto, home, life, health)
- Effective and expiration dates
- Policy holder information (name, DOB, license, address)
- Coverage items (type, limit, deductible)
- Premium details (annual amount, payment frequency)
- Policy status

**Files:**
- `15a_insurance_policy_russian_doll.xsd` - Russian Doll pattern
- `15b_insurance_policy_salami_slice.xsd` - Salami Slice pattern
- `15c_insurance_policy_venetian_blind.xsd` - Venetian Blind pattern ⭐
- `15d_insurance_policy_garden_of_eden.xsd` - Garden of Eden pattern
- `15_insurance_policy.xml` - Sample data (validates against ALL 4 schemas)

---

## Validating the Examples

Each XML file validates against all 4 schema variants for that example:

### Using xmllint (Linux/macOS)

```bash
# Product Order example
xmllint --schema 13a_product_order_russian_doll.xsd 13_product_order.xml --noout
xmllint --schema 13b_product_order_salami_slice.xsd 13_product_order.xml --noout
xmllint --schema 13c_product_order_venetian_blind.xsd 13_product_order.xml --noout
xmllint --schema 13d_product_order_garden_of_eden.xsd 13_product_order.xml --noout

# Employee Profile example
xmllint --schema 14a_employee_profile_russian_doll.xsd 14_employee_profile.xml --noout
xmllint --schema 14b_employee_profile_salami_slice.xsd 14_employee_profile.xml --noout
xmllint --schema 14c_employee_profile_venetian_blind.xsd 14_employee_profile.xml --noout
xmllint --schema 14d_employee_profile_garden_of_eden.xsd 14_employee_profile.xml --noout

# Insurance Policy example
xmllint --schema 15a_insurance_policy_russian_doll.xsd 15_insurance_policy.xml --noout
xmllint --schema 15b_insurance_policy_salami_slice.xsd 15_insurance_policy.xml --noout
xmllint --schema 15c_insurance_policy_venetian_blind.xsd 15_insurance_policy.xml --noout
xmllint --schema 15d_insurance_policy_garden_of_eden.xsd 15_insurance_policy.xml --noout
```

### Validation Script

Create a script to validate all patterns:

```bash
#!/bin/bash

echo "Validating Product Order..."
for pattern in a b c d; do
  echo "  Pattern: 13${pattern}"
  xmllint --schema 13${pattern}_product_order_*.xsd 13_product_order.xml --noout
done

echo "Validating Employee Profile..."
for pattern in a b c d; do
  echo "  Pattern: 14${pattern}"
  xmllint --schema 14${pattern}_employee_profile_*.xsd 14_employee_profile.xml --noout
done

echo "Validating Insurance Policy..."
for pattern in a b c d; do
  echo "  Pattern: 15${pattern}"
  xmllint --schema 15${pattern}_insurance_policy_*.xsd 15_insurance_policy.xml --noout
done

echo "All validations complete!"
```

---

## Pattern Selection Guide

### Choose **Russian Doll** if:
- Schema is very small (< 10 elements)
- Schema will not evolve
- Quick prototype or proof of concept
- No reusability needed

### Choose **Salami Slice** if:
- Using data binding tools (JAXB, XMLBeans)
- Need partial document validation
- Elements must be independently referenceable
- Building a schema library for composition

### Choose **Venetian Blind** if: ⭐
- **Building enterprise applications** (RECOMMENDED)
- Schema will grow and evolve
- Team development
- Need type reusability
- Want clean, maintainable structure
- **DEFAULT CHOICE for most projects**

### Choose **Garden of Eden** if:
- Building framework/library schemas
- Complex schema composition required
- Multiple schema imports/includes
- Need maximum flexibility
- Building extensible schemas for third parties

---

## Pattern Migration

### Migrating from Russian Doll to Venetian Blind

**Before (Russian Doll):**
```xml
<xs:element name="Order">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="Customer">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="Name" type="xs:string"/>
          </xs:sequence>
        </xs:complexType>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

**After (Venetian Blind):**
```xml
<xs:element name="Order" type="OrderType"/>

<xs:complexType name="OrderType">
  <xs:sequence>
    <xs:element name="Customer" type="CustomerType"/>
  </xs:sequence>
</xs:complexType>

<xs:complexType name="CustomerType">
  <xs:sequence>
    <xs:element name="Name" type="xs:string"/>
  </xs:sequence>
</xs:complexType>
```

### Migrating from Salami Slice to Venetian Blind

**Before (Salami Slice):**
```xml
<xs:element name="Order">
  <xs:complexType>
    <xs:sequence>
      <xs:element ref="Customer"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>

<xs:element name="Customer">
  <xs:complexType>
    <xs:sequence>
      <xs:element ref="Name"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>

<xs:element name="Name" type="xs:string"/>
```

**After (Venetian Blind):**
```xml
<xs:element name="Order" type="OrderType"/>

<xs:complexType name="OrderType">
  <xs:sequence>
    <xs:element name="Customer" type="CustomerType"/>
  </xs:sequence>
</xs:complexType>

<xs:complexType name="CustomerType">
  <xs:sequence>
    <xs:element name="Name" type="xs:string"/>
  </xs:sequence>
</xs:complexType>
```

---

## Best Practices by Pattern

### Russian Doll Best Practices

1. **Keep it small** - Only use for schemas with < 10 elements
2. **Document thoroughly** - Inline documentation is critical
3. **Avoid deep nesting** - More than 3 levels becomes unmaintainable
4. **Consider refactoring** - Migrate to Venetian Blind as schema grows

### Salami Slice Best Practices

1. **Use namespaces** - Avoid naming conflicts with prefixes
2. **Group related elements** - Use consistent naming conventions
3. **Document element relationships** - Not obvious from schema structure
4. **Validate carefully** - Easy to create invalid structures

### Venetian Blind Best Practices ⭐

1. **Name types clearly** - Use descriptive type names (e.g., `CustomerType`, `AddressType`)
2. **Reuse types** - Don't duplicate type definitions
3. **Extract common types** - Create reusable types for common structures (Address, Money, etc.)
4. **Use simple types** - Define global simple types for reusable constraints
5. **Document types** - Add annotations to type definitions
6. **Consistent naming** - Use `Type` suffix for complex types

### Garden of Eden Best Practices

1. **Namespace everything** - Essential to avoid conflicts
2. **Document extensively** - Complexity requires good documentation
3. **Version carefully** - Changes can break dependent schemas
4. **Use prefixes** - Make element references clear
5. **Validate extensively** - Easy to create invalid structures
6. **Control access** - Consider which elements should be global

---

## Common Patterns Within Each Style

### Reusable Address Type (Venetian Blind)

```xml
<xs:complexType name="AddressType">
  <xs:sequence>
    <xs:element name="Street" type="xs:string"/>
    <xs:element name="City" type="xs:string"/>
    <xs:element name="State" type="xs:string"/>
    <xs:element name="ZipCode" type="xs:string"/>
  </xs:sequence>
</xs:complexType>

<!-- Used multiple times -->
<xs:element name="ShippingAddress" type="AddressType"/>
<xs:element name="BillingAddress" type="AddressType"/>
```

### Reusable Money Type (Venetian Blind)

```xml
<xs:simpleType name="MoneyType">
  <xs:restriction base="xs:decimal">
    <xs:fractionDigits value="2"/>
    <xs:minInclusive value="0"/>
  </xs:restriction>
</xs:simpleType>

<!-- Used multiple times -->
<xs:element name="Price" type="MoneyType"/>
<xs:element name="Total" type="MoneyType"/>
<xs:element name="Discount" type="MoneyType"/>
```

### Enumeration Pattern (All Patterns)

```xml
<xs:simpleType name="OrderStatusType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="pending"/>
    <xs:enumeration value="processing"/>
    <xs:enumeration value="shipped"/>
    <xs:enumeration value="delivered"/>
  </xs:restriction>
</xs:simpleType>
```

---

## UTL-X Integration

All patterns work identically with UTL-X transformations:

```utlx
%utlx 1.0
input xml
output json
---
{
  // Works with ANY pattern - Russian Doll, Salami Slice, Venetian Blind, or Garden of Eden
  order: {
    orderId: $input.ProductOrder.OrderID,
    customer: {
      name: $input.ProductOrder.Customer.Name,
      email: $input.ProductOrder.Customer.Email
    },
    items: $input.ProductOrder.Items.Item |> map(item => {
      product: item.ProductName,
      quantity: toNumber(item.Quantity),
      price: toNumber(item.Price)
    }),
    total: toNumber($input.ProductOrder.Total)
  }
}
```

The pattern choice affects **schema design and maintenance**, not **XML processing**!

---

## Industry Recommendations

### Financial Services
**Pattern:** Venetian Blind ⭐
- Regulatory compliance requires maintainability
- Schemas evolve frequently
- Type reusability is critical

### Healthcare (HL7, FHIR)
**Pattern:** Garden of Eden
- Complex schema composition
- Multiple standard imports
- Extension and customization required

### E-commerce
**Pattern:** Venetian Blind ⭐
- Evolving product catalogs
- Integration with multiple systems
- Team development

### Government/Standards Bodies
**Pattern:** Garden of Eden
- Maximum reusability across agencies
- Schema composition and extension
- Long-term maintenance

### Enterprise Integration
**Pattern:** Venetian Blind ⭐
- B2B data exchange
- Clear structure for teams
- Evolution over time

---

## References

### W3C XML Schema Resources
- [W3C XML Schema Primer](https://www.w3.org/TR/xmlschema-0/)
- [XML Schema Part 1: Structures](https://www.w3.org/TR/xmlschema-1/)
- [XML Schema Part 2: Datatypes](https://www.w3.org/TR/xmlschema-2/)

### Design Pattern References
- [Russian Doll Design](https://www.ibm.com/docs/en/integration-bus/10.0?topic=schemas-russian-doll-design)
- [Salami Slice Design](https://www.ibm.com/docs/en/integration-bus/10.0?topic=schemas-salami-slice-design)
- [Venetian Blind Design](https://www.ibm.com/docs/en/integration-bus/10.0?topic=schemas-venetian-blind-design)
- [Garden of Eden Design](https://www.ibm.com/docs/en/integration-bus/10.0?topic=schemas-garden-eden-design)

### Books
- *Definitive XML Schema* by Priscilla Walmsley (O'Reilly)
- *XML Schema: The W3C's Object-Oriented Descriptions for XML* (Sams)

---

## Summary

**Quick Decision Tree:**

```
Is your schema < 10 elements and won't grow?
  └─ YES → Russian Doll
  └─ NO → Continue...

Do you need data binding or partial validation?
  └─ YES → Salami Slice
  └─ NO → Continue...

Do you need complex schema composition/inheritance?
  └─ YES → Garden of Eden
  └─ NO → Venetian Blind ⭐ (DEFAULT CHOICE)
```

**Remember:** Venetian Blind is the recommended pattern for most enterprise applications!

---

## License

These examples are provided as part of the UTL-X project and follow the project's licensing terms (AGPL-3.0 / Commercial).
