package org.apache.utlx.formats.osch

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EDMXParserTest {

    // ==================== Basic Parsing ====================

    @Nested
    @DisplayName("Basic EDMX Parsing")
    inner class BasicParsing {

        @Test
        fun `parse minimal EDMX with single entity type`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test.Model" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Product">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                        <Property Name="Name" Type="Edm.String"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse()

            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result

            // Version
            (obj.properties["%version"] as UDM.Scalar).value shouldBe "4.0"

            // Namespace
            (obj.properties["%namespace"] as UDM.Scalar).value shouldBe "Test.Model"

            // Types
            val types = obj.properties["%types"] as UDM.Object
            types.properties.containsKey("Product") shouldBe true

            // Product entity type
            val product = types.properties["Product"] as UDM.Object
            (product.properties["%entityType"] as UDM.Scalar).value shouldBe true

            // Fields
            val fields = product.properties["%fields"] as UDM.Array
            fields.elements.size shouldBe 2

            // ID field is key and required
            val idField = fields.elements[0] as UDM.Object
            (idField.properties["%name"] as UDM.Scalar).value shouldBe "ID"
            (idField.properties["%type"] as UDM.Scalar).value shouldBe "integer"
            (idField.properties["%key"] as UDM.Scalar).value shouldBe true
            (idField.properties["%required"] as UDM.Scalar).value shouldBe true

            // Name field
            val nameField = fields.elements[1] as UDM.Object
            (nameField.properties["%name"] as UDM.Scalar).value shouldBe "Name"
            (nameField.properties["%type"] as UDM.Scalar).value shouldBe "string"
            nameField.properties.containsKey("%key") shouldBe false
            (nameField.properties["%required"] as UDM.Scalar).value shouldBe false
        }

        @Test
        fun `parse EDMX with schema alias`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="My.Namespace" Alias="MN" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Item">
                        <Key>
                          <PropertyRef Name="ItemID"/>
                        </Key>
                        <Property Name="ItemID" Type="Edm.Int32" Nullable="false"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object

            (result.properties["%namespace"] as UDM.Scalar).value shouldBe "My.Namespace"
            (result.properties["%alias"] as UDM.Scalar).value shouldBe "MN"
        }

        @Test
        fun `reject non-EDMX XML`() {
            val xml = """
                <?xml version="1.0"?>
                <root><child>data</child></root>
            """.trimIndent()

            assertThrows<IllegalArgumentException> {
                EDMXParser(xml).parse()
            }
        }
    }

    // ==================== Edm Type Mapping ====================

    @Nested
    @DisplayName("Edm Type Mapping")
    inner class TypeMapping {

        @Test
        fun `map all Edm primitive types to UTLX types`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="AllTypes">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                        <Property Name="StringField" Type="Edm.String"/>
                        <Property Name="GuidField" Type="Edm.Guid"/>
                        <Property Name="BoolField" Type="Edm.Boolean"/>
                        <Property Name="ByteField" Type="Edm.Byte"/>
                        <Property Name="Int16Field" Type="Edm.Int16"/>
                        <Property Name="Int64Field" Type="Edm.Int64"/>
                        <Property Name="SingleField" Type="Edm.Single"/>
                        <Property Name="DoubleField" Type="Edm.Double"/>
                        <Property Name="DecimalField" Type="Edm.Decimal"/>
                        <Property Name="DateField" Type="Edm.Date"/>
                        <Property Name="TimeField" Type="Edm.TimeOfDay"/>
                        <Property Name="DateTimeField" Type="Edm.DateTimeOffset"/>
                        <Property Name="BinaryField" Type="Edm.Binary"/>
                        <Property Name="StreamField" Type="Edm.Stream"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val allTypes = types.properties["AllTypes"] as UDM.Object
            val fields = allTypes.properties["%fields"] as UDM.Array

            // Build a map of field name → UTLX type
            val typeMap = fields.elements.filterIsInstance<UDM.Object>().associate { field ->
                val name = (field.properties["%name"] as UDM.Scalar).value as String
                val type = (field.properties["%type"] as UDM.Scalar).value as String
                name to type
            }

            typeMap["ID"] shouldBe "integer"
            typeMap["StringField"] shouldBe "string"
            typeMap["GuidField"] shouldBe "string"
            typeMap["BoolField"] shouldBe "boolean"
            typeMap["ByteField"] shouldBe "integer"
            typeMap["Int16Field"] shouldBe "integer"
            typeMap["Int64Field"] shouldBe "integer"
            typeMap["SingleField"] shouldBe "number"
            typeMap["DoubleField"] shouldBe "number"
            typeMap["DecimalField"] shouldBe "number"
            typeMap["DateField"] shouldBe "date"
            typeMap["TimeField"] shouldBe "time"
            typeMap["DateTimeField"] shouldBe "datetime"
            typeMap["BinaryField"] shouldBe "binary"
            typeMap["StreamField"] shouldBe "binary"
        }

        @Test
        fun `preserve original Edm schema type`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Item">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int64" Nullable="false"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val item = types.properties["Item"] as UDM.Object
            val fields = item.properties["%fields"] as UDM.Array
            val idField = fields.elements[0] as UDM.Object

            // UTLX type is "integer"
            (idField.properties["%type"] as UDM.Scalar).value shouldBe "integer"
            // Original schema type preserved
            (idField.properties["%schemaType"] as UDM.Scalar).value shouldBe "Edm.Int64"
        }
    }

    // ==================== Property Constraints ====================

    @Nested
    @DisplayName("Property Constraints")
    inner class PropertyConstraints {

        @Test
        fun `extract MaxLength, Precision, and Scale constraints`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Item">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                        <Property Name="Name" Type="Edm.String" MaxLength="100"/>
                        <Property Name="Price" Type="Edm.Decimal" Precision="10" Scale="2"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val item = types.properties["Item"] as UDM.Object
            val fields = item.properties["%fields"] as UDM.Array

            // Name field has MaxLength
            val nameField = fields.elements[1] as UDM.Object
            (nameField.properties["%maxLength"] as UDM.Scalar).value shouldBe 100.0

            // Price field has Precision and Scale
            val priceField = fields.elements[2] as UDM.Object
            (priceField.properties["%precision"] as UDM.Scalar).value shouldBe 10.0
            (priceField.properties["%scale"] as UDM.Scalar).value shouldBe 2.0
        }

        @Test
        fun `extract compound key with multiple PropertyRef`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="OrderDetail">
                        <Key>
                          <PropertyRef Name="OrderID"/>
                          <PropertyRef Name="ProductID"/>
                        </Key>
                        <Property Name="OrderID" Type="Edm.Int32" Nullable="false"/>
                        <Property Name="ProductID" Type="Edm.Int32" Nullable="false"/>
                        <Property Name="Quantity" Type="Edm.Int16"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val orderDetail = types.properties["OrderDetail"] as UDM.Object
            val fields = orderDetail.properties["%fields"] as UDM.Array

            val orderIdField = fields.elements[0] as UDM.Object
            (orderIdField.properties["%key"] as UDM.Scalar).value shouldBe true

            val productIdField = fields.elements[1] as UDM.Object
            (productIdField.properties["%key"] as UDM.Scalar).value shouldBe true

            val quantityField = fields.elements[2] as UDM.Object
            quantityField.properties.containsKey("%key") shouldBe false
        }
    }

    // ==================== Complex Types ====================

    @Nested
    @DisplayName("Complex Type Extraction")
    inner class ComplexTypeExtraction {

        @Test
        fun `extract ComplexType as non-entity type`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <ComplexType Name="Address">
                        <Property Name="Street" Type="Edm.String"/>
                        <Property Name="City" Type="Edm.String"/>
                        <Property Name="PostalCode" Type="Edm.String" MaxLength="10"/>
                      </ComplexType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val address = types.properties["Address"] as UDM.Object

            // ComplexType → %entityType: false
            (address.properties["%entityType"] as UDM.Scalar).value shouldBe false

            val fields = address.properties["%fields"] as UDM.Array
            fields.elements.size shouldBe 3

            val streetField = fields.elements[0] as UDM.Object
            (streetField.properties["%name"] as UDM.Scalar).value shouldBe "Street"
            (streetField.properties["%type"] as UDM.Scalar).value shouldBe "string"
        }
    }

    // ==================== Enum Types ====================

    @Nested
    @DisplayName("Enum Type Extraction")
    inner class EnumTypeExtraction {

        @Test
        fun `extract EnumType with members and values`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EnumType Name="Color">
                        <Member Name="Red" Value="0"/>
                        <Member Name="Green" Value="1"/>
                        <Member Name="Blue" Value="2"/>
                      </EnumType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val color = types.properties["Color"] as UDM.Object

            (color.properties["%kind"] as UDM.Scalar).value shouldBe "enum"

            val members = color.properties["%members"] as UDM.Array
            members.elements.size shouldBe 3

            val red = members.elements[0] as UDM.Object
            (red.properties["%name"] as UDM.Scalar).value shouldBe "Red"
            (red.properties["%value"] as UDM.Scalar).value shouldBe 0.0

            val blue = members.elements[2] as UDM.Object
            (blue.properties["%name"] as UDM.Scalar).value shouldBe "Blue"
            (blue.properties["%value"] as UDM.Scalar).value shouldBe 2.0
        }

        @Test
        fun `extract flags enum with IsFlags attribute`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EnumType Name="Permission" IsFlags="true">
                        <Member Name="Read" Value="1"/>
                        <Member Name="Write" Value="2"/>
                        <Member Name="Execute" Value="4"/>
                      </EnumType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val permission = types.properties["Permission"] as UDM.Object

            (permission.properties["%kind"] as UDM.Scalar).value shouldBe "enum"
            (permission.properties["%isFlags"] as UDM.Scalar).value shouldBe true
        }
    }

    // ==================== Navigation Properties ====================

    @Nested
    @DisplayName("Navigation Property Extraction")
    inner class NavigationPropertyExtraction {

        @Test
        fun `extract single-valued navigation property`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test.Model" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Product">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                        <NavigationProperty Name="Category" Type="Test.Model.Category" Partner="Products"/>
                      </EntityType>
                      <EntityType Name="Category">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                        <NavigationProperty Name="Products" Type="Collection(Test.Model.Product)" Partner="Category"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val product = types.properties["Product"] as UDM.Object
            val navigation = product.properties["%navigation"] as UDM.Object

            // Single-valued navigation → cardinality "1"
            val categoryNav = navigation.properties["Category"] as UDM.Object
            (categoryNav.properties["%target"] as UDM.Scalar).value shouldBe "Category"
            (categoryNav.properties["%cardinality"] as UDM.Scalar).value shouldBe "1"
            (categoryNav.properties["%partner"] as UDM.Scalar).value shouldBe "Products"
        }

        @Test
        fun `extract collection navigation property`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test.Model" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Category">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                        <NavigationProperty Name="Products" Type="Collection(Test.Model.Product)" Partner="Category"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val category = types.properties["Category"] as UDM.Object
            val navigation = category.properties["%navigation"] as UDM.Object

            // Collection navigation → cardinality "*"
            val productsNav = navigation.properties["Products"] as UDM.Object
            (productsNav.properties["%target"] as UDM.Scalar).value shouldBe "Product"
            (productsNav.properties["%cardinality"] as UDM.Scalar).value shouldBe "*"
        }

        @Test
        fun `extract navigation property with referential constraint`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test.Model" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Product">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                        <Property Name="CategoryID" Type="Edm.Int32"/>
                        <NavigationProperty Name="Category" Type="Test.Model.Category" Partner="Products">
                          <ReferentialConstraint Property="CategoryID" ReferencedProperty="CategoryID"/>
                        </NavigationProperty>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val product = types.properties["Product"] as UDM.Object
            val navigation = product.properties["%navigation"] as UDM.Object
            val categoryNav = navigation.properties["Category"] as UDM.Object

            val constraints = categoryNav.properties["%referentialConstraints"] as UDM.Array
            constraints.elements.size shouldBe 1

            val rc = constraints.elements[0] as UDM.Object
            (rc.properties["%property"] as UDM.Scalar).value shouldBe "CategoryID"
            (rc.properties["%referencedProperty"] as UDM.Scalar).value shouldBe "CategoryID"
        }
    }

    // ==================== Entity Container ====================

    @Nested
    @DisplayName("Entity Container Extraction")
    inner class EntityContainerExtraction {

        @Test
        fun `extract entity container with entity sets and navigation bindings`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Product">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                      </EntityType>
                      <EntityContainer Name="MyContainer">
                        <EntitySet Name="Products" EntityType="Test.Product">
                          <NavigationPropertyBinding Path="Category" Target="Categories"/>
                        </EntitySet>
                        <EntitySet Name="Categories" EntityType="Test.Category"/>
                      </EntityContainer>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val container = result.properties["%entityContainer"] as UDM.Object

            (container.properties["%name"] as UDM.Scalar).value shouldBe "MyContainer"

            val entitySets = container.properties["%entitySets"] as UDM.Object
            entitySets.properties.size shouldBe 2

            // Products entity set
            val products = entitySets.properties["Products"] as UDM.Object
            (products.properties["%entityType"] as UDM.Scalar).value shouldBe "Test.Product"

            // Navigation bindings
            val bindings = products.properties["%navigationPropertyBindings"] as UDM.Array
            bindings.elements.size shouldBe 1
            val binding = bindings.elements[0] as UDM.Object
            (binding.properties["%path"] as UDM.Scalar).value shouldBe "Category"
            (binding.properties["%target"] as UDM.Scalar).value shouldBe "Categories"
        }
    }

    // ==================== Auto-Detection ====================

    @Nested
    @DisplayName("Auto-Detection")
    inner class AutoDetection {

        @Test
        fun `detects EDMX by edmx Edmx element`() {
            val edmx = """<edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx"></edmx:Edmx>"""
            EDMXParser.looksLikeEDMX(edmx) shouldBe true
        }

        @Test
        fun `detects EDMX by OASIS namespace`() {
            val edmx = """<Edmx xmlns="http://docs.oasis-open.org/odata/ns/edmx"></Edmx>"""
            EDMXParser.looksLikeEDMX(edmx) shouldBe true
        }

        @Test
        fun `detects EDMX by Microsoft namespace`() {
            val edmx = """<edmx:Edmx xmlns:edmx="http://schemas.microsoft.com/ado/2007/06/edmx"></edmx:Edmx>"""
            EDMXParser.looksLikeEDMX(edmx) shouldBe true
        }

        @Test
        fun `plain XML is not detected as EDMX`() {
            val xml = """<?xml version="1.0"?><root><child>data</child></root>"""
            EDMXParser.looksLikeEDMX(xml) shouldBe false
        }

        @Test
        fun `JSON is not detected as EDMX`() {
            val json = """{ "name": "Widget" }"""
            EDMXParser.looksLikeEDMX(json) shouldBe false
        }
    }

    // ==================== Advanced Features ====================

    @Nested
    @DisplayName("Advanced Features")
    inner class AdvancedFeatures {

        @Test
        fun `parse abstract entity type`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="BaseEntity" Abstract="true">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val baseEntity = types.properties["BaseEntity"] as UDM.Object

            (baseEntity.properties["%abstract"] as UDM.Scalar).value shouldBe true
        }

        @Test
        fun `parse entity type with base type inheritance`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="BaseEntity" Abstract="true">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                      </EntityType>
                      <EntityType Name="Product" BaseType="Test.BaseEntity">
                        <Property Name="Name" Type="Edm.String"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val product = types.properties["Product"] as UDM.Object

            (product.properties["%baseType"] as UDM.Scalar).value shouldBe "Test.BaseEntity"
        }

        @Test
        fun `parse mixed entity types, complex types, and enums`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Product">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                      </EntityType>
                      <ComplexType Name="Address">
                        <Property Name="Street" Type="Edm.String"/>
                      </ComplexType>
                      <EnumType Name="Status">
                        <Member Name="Active" Value="1"/>
                      </EnumType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object

            types.properties.size shouldBe 3
            types.properties.containsKey("Product") shouldBe true
            types.properties.containsKey("Address") shouldBe true
            types.properties.containsKey("Status") shouldBe true

            // Verify type discrimination
            val product = types.properties["Product"] as UDM.Object
            (product.properties["%entityType"] as UDM.Scalar).value shouldBe true

            val address = types.properties["Address"] as UDM.Object
            (address.properties["%entityType"] as UDM.Scalar).value shouldBe false

            val status = types.properties["Status"] as UDM.Object
            (status.properties["%kind"] as UDM.Scalar).value shouldBe "enum"
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        fun `entity type with no properties`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Empty">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val empty = types.properties["Empty"] as UDM.Object

            (empty.properties["%entityType"] as UDM.Scalar).value shouldBe true
            // No %fields key when there are no properties
            empty.properties.containsKey("%fields") shouldBe false
        }

        @Test
        fun `property with DefaultValue`() {
            val edmx = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Config">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                        <Property Name="Active" Type="Edm.Boolean" DefaultValue="true"/>
                      </EntityType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val result = EDMXParser(edmx).parse() as UDM.Object
            val types = result.properties["%types"] as UDM.Object
            val config = types.properties["Config"] as UDM.Object
            val fields = config.properties["%fields"] as UDM.Array
            val activeField = fields.elements[1] as UDM.Object

            (activeField.properties["%defaultValue"] as UDM.Scalar).value shouldBe "true"
        }
    }
}
