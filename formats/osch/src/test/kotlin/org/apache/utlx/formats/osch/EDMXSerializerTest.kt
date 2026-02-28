package org.apache.utlx.formats.osch

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EDMXSerializerTest {

    // ==================== USDL Mode Serialization ====================

    @Nested
    @DisplayName("USDL Mode Serialization")
    inner class USDLModeSerialization {

        @Test
        fun `serialize simple entity type to EDMX`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%version" to UDM.Scalar.string("4.0"),
                "%namespace" to UDM.Scalar.string("Test.Model"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Product" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("ID"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%schemaType" to UDM.Scalar.string("Edm.Int32"),
                                "%key" to UDM.Scalar.boolean(true),
                                "%required" to UDM.Scalar.boolean(true)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Name"),
                                "%type" to UDM.Scalar.string("string"),
                                "%schemaType" to UDM.Scalar.string("Edm.String"),
                                "%required" to UDM.Scalar.boolean(false)
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer(namespace = "Test.Model").serialize(udm)

            output shouldContain "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            output shouldContain "<edmx:Edmx Version=\"4.0\""
            output shouldContain "xmlns:edmx=\"http://docs.oasis-open.org/odata/ns/edmx\""
            output shouldContain "<edmx:DataServices>"
            output shouldContain "Namespace=\"Test.Model\""
            output shouldContain "<EntityType Name=\"Product\">"
            output shouldContain "<Key>"
            output shouldContain "<PropertyRef Name=\"ID\"/>"
            output shouldContain "Name=\"ID\" Type=\"Edm.Int32\" Nullable=\"false\""
            output shouldContain "Name=\"Name\" Type=\"Edm.String\""
            output shouldContain "</EntityType>"
            output shouldContain "</edmx:Edmx>"
        }

        @Test
        fun `serialize complex type to EDMX`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Address" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(false),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Street"),
                                "%type" to UDM.Scalar.string("string"),
                                "%schemaType" to UDM.Scalar.string("Edm.String"),
                                "%required" to UDM.Scalar.boolean(false)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("City"),
                                "%type" to UDM.Scalar.string("string"),
                                "%schemaType" to UDM.Scalar.string("Edm.String"),
                                "%required" to UDM.Scalar.boolean(false)
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "<ComplexType Name=\"Address\">"
            output shouldContain "Name=\"Street\" Type=\"Edm.String\""
            output shouldContain "Name=\"City\" Type=\"Edm.String\""
            output shouldContain "</ComplexType>"
            output shouldNotContain "<EntityType"
        }

        @Test
        fun `serialize enum type to EDMX`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Color" to UDM.Object(properties = linkedMapOf(
                        "%kind" to UDM.Scalar.string("enum"),
                        "%members" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Red"),
                                "%value" to UDM.Scalar.number(0.0)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Green"),
                                "%value" to UDM.Scalar.number(1.0)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Blue"),
                                "%value" to UDM.Scalar.number(2.0)
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "<EnumType Name=\"Color\">"
            output shouldContain "<Member Name=\"Red\" Value=\"0\"/>"
            output shouldContain "<Member Name=\"Green\" Value=\"1\"/>"
            output shouldContain "<Member Name=\"Blue\" Value=\"2\"/>"
            output shouldContain "</EnumType>"
        }

        @Test
        fun `serialize flags enum with IsFlags attribute`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Permission" to UDM.Object(properties = linkedMapOf(
                        "%kind" to UDM.Scalar.string("enum"),
                        "%isFlags" to UDM.Scalar.boolean(true),
                        "%members" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Read"),
                                "%value" to UDM.Scalar.number(1.0)
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "IsFlags=\"true\""
        }
    }

    // ==================== Navigation Properties ====================

    @Nested
    @DisplayName("Navigation Property Serialization")
    inner class NavigationPropertySerialization {

        @Test
        fun `serialize single-valued navigation property`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test.Model"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Product" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("ID"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%key" to UDM.Scalar.boolean(true),
                                "%required" to UDM.Scalar.boolean(true)
                            ))
                        )),
                        "%navigation" to UDM.Object(properties = linkedMapOf(
                            "Category" to UDM.Object(properties = linkedMapOf(
                                "%target" to UDM.Scalar.string("Category"),
                                "%cardinality" to UDM.Scalar.string("1"),
                                "%partner" to UDM.Scalar.string("Products")
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "NavigationProperty Name=\"Category\" Type=\"Test.Model.Category\""
            output shouldContain "Partner=\"Products\""
            output shouldNotContain "Collection("
        }

        @Test
        fun `serialize collection navigation property`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test.Model"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Category" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("ID"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%key" to UDM.Scalar.boolean(true),
                                "%required" to UDM.Scalar.boolean(true)
                            ))
                        )),
                        "%navigation" to UDM.Object(properties = linkedMapOf(
                            "Products" to UDM.Object(properties = linkedMapOf(
                                "%target" to UDM.Scalar.string("Product"),
                                "%cardinality" to UDM.Scalar.string("*"),
                                "%partner" to UDM.Scalar.string("Category")
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "Type=\"Collection(Test.Model.Product)\""
        }

        @Test
        fun `serialize navigation property with referential constraints`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test.Model"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Product" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("ID"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%key" to UDM.Scalar.boolean(true),
                                "%required" to UDM.Scalar.boolean(true)
                            ))
                        )),
                        "%navigation" to UDM.Object(properties = linkedMapOf(
                            "Category" to UDM.Object(properties = linkedMapOf(
                                "%target" to UDM.Scalar.string("Category"),
                                "%cardinality" to UDM.Scalar.string("1"),
                                "%referentialConstraints" to UDM.Array(listOf(
                                    UDM.Object(properties = linkedMapOf(
                                        "%property" to UDM.Scalar.string("CategoryID"),
                                        "%referencedProperty" to UDM.Scalar.string("CategoryID")
                                    ))
                                ))
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "<NavigationProperty Name=\"Category\""
            output shouldContain "<ReferentialConstraint Property=\"CategoryID\" ReferencedProperty=\"CategoryID\"/>"
            output shouldContain "</NavigationProperty>"
        }
    }

    // ==================== Entity Container ====================

    @Nested
    @DisplayName("Entity Container Serialization")
    inner class EntityContainerSerialization {

        @Test
        fun `serialize explicit entity container`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Product" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("ID"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%key" to UDM.Scalar.boolean(true),
                                "%required" to UDM.Scalar.boolean(true)
                            ))
                        ))
                    ))
                )),
                "%entityContainer" to UDM.Object(properties = linkedMapOf(
                    "%name" to UDM.Scalar.string("MyContainer"),
                    "%entitySets" to UDM.Object(properties = linkedMapOf(
                        "Products" to UDM.Object(properties = linkedMapOf(
                            "%entityType" to UDM.Scalar.string("Test.Product"),
                            "%navigationPropertyBindings" to UDM.Array(listOf(
                                UDM.Object(properties = linkedMapOf(
                                    "%path" to UDM.Scalar.string("Category"),
                                    "%target" to UDM.Scalar.string("Categories")
                                ))
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "<EntityContainer Name=\"MyContainer\">"
            output shouldContain "EntitySet Name=\"Products\" EntityType=\"Test.Product\""
            output shouldContain "<NavigationPropertyBinding Path=\"Category\" Target=\"Categories\"/>"
            output shouldContain "</EntityContainer>"
        }

        @Test
        fun `auto-generate entity container when not provided`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Product" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("ID"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%key" to UDM.Scalar.boolean(true),
                                "%required" to UDM.Scalar.boolean(true)
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            // Auto-generated container with pluralized entity set name
            output shouldContain "<EntityContainer Name=\"Container\">"
            output shouldContain "EntitySet Name=\"Products\" EntityType=\"Test.Product\""
        }
    }

    // ==================== Property Constraints ====================

    @Nested
    @DisplayName("Property Constraint Serialization")
    inner class PropertyConstraintSerialization {

        @Test
        fun `serialize MaxLength, Precision, and Scale constraints`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Product" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("ID"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%key" to UDM.Scalar.boolean(true),
                                "%required" to UDM.Scalar.boolean(true)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Name"),
                                "%type" to UDM.Scalar.string("string"),
                                "%required" to UDM.Scalar.boolean(false),
                                "%maxLength" to UDM.Scalar.number(100.0)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Price"),
                                "%type" to UDM.Scalar.string("number"),
                                "%schemaType" to UDM.Scalar.string("Edm.Decimal"),
                                "%required" to UDM.Scalar.boolean(false),
                                "%precision" to UDM.Scalar.number(10.0),
                                "%scale" to UDM.Scalar.number(2.0)
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "MaxLength=\"100\""
            output shouldContain "Precision=\"10\""
            output shouldContain "Scale=\"2\""
        }

        @Test
        fun `serialize DefaultValue`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Config" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("ID"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%key" to UDM.Scalar.boolean(true),
                                "%required" to UDM.Scalar.boolean(true)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Active"),
                                "%type" to UDM.Scalar.string("boolean"),
                                "%required" to UDM.Scalar.boolean(false),
                                "%defaultValue" to UDM.Scalar.string("true")
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "DefaultValue=\"true\""
        }
    }

    // ==================== Type Mapping ====================

    @Nested
    @DisplayName("Type Mapping Serialization")
    inner class TypeMappingSerialization {

        @Test
        fun `preserve original Edm schema type in serialization`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Item" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("ID"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%schemaType" to UDM.Scalar.string("Edm.Int64"),
                                "%key" to UDM.Scalar.boolean(true),
                                "%required" to UDM.Scalar.boolean(true)
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            // Should use original Edm.Int64, not default Edm.Int32
            output shouldContain "Type=\"Edm.Int64\""
            output shouldNotContain "Type=\"Edm.Int32\""
        }

        @Test
        fun `map UTLX types to Edm types when no schema type preserved`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Test"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Item" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Name"),
                                "%type" to UDM.Scalar.string("string"),
                                "%required" to UDM.Scalar.boolean(false)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Count"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%required" to UDM.Scalar.boolean(false)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Price"),
                                "%type" to UDM.Scalar.string("number"),
                                "%required" to UDM.Scalar.boolean(false)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Active"),
                                "%type" to UDM.Scalar.string("boolean"),
                                "%required" to UDM.Scalar.boolean(false)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Created"),
                                "%type" to UDM.Scalar.string("date"),
                                "%required" to UDM.Scalar.boolean(false)
                            )),
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("Modified"),
                                "%type" to UDM.Scalar.string("datetime"),
                                "%required" to UDM.Scalar.boolean(false)
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "Name=\"Name\" Type=\"Edm.String\""
            output shouldContain "Name=\"Count\" Type=\"Edm.Int32\""
            output shouldContain "Name=\"Price\" Type=\"Edm.Decimal\""
            output shouldContain "Name=\"Active\" Type=\"Edm.Boolean\""
            output shouldContain "Name=\"Created\" Type=\"Edm.Date\""
            output shouldContain "Name=\"Modified\" Type=\"Edm.DateTimeOffset\""
        }
    }

    // ==================== Namespace Handling ====================

    @Nested
    @DisplayName("Namespace Handling")
    inner class NamespaceHandling {

        @Test
        fun `constructor namespace overrides UDM namespace`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("Original.Namespace"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Item" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true),
                        "%fields" to UDM.Array(listOf(
                            UDM.Object(properties = linkedMapOf(
                                "%name" to UDM.Scalar.string("ID"),
                                "%type" to UDM.Scalar.string("integer"),
                                "%key" to UDM.Scalar.boolean(true),
                                "%required" to UDM.Scalar.boolean(true)
                            ))
                        ))
                    ))
                ))
            ))

            val output = EDMXSerializer(namespace = "Override.Namespace").serialize(udm)

            output shouldContain "Namespace=\"Override.Namespace\""
            output shouldNotContain "Original.Namespace"
        }

        @Test
        fun `default namespace used when none provided`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Item" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true)
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "Namespace=\"Default.Namespace\""
        }

        @Test
        fun `serialize schema alias`() {
            val udm = UDM.Object(properties = linkedMapOf(
                "%namespace" to UDM.Scalar.string("My.Namespace"),
                "%alias" to UDM.Scalar.string("MN"),
                "%types" to UDM.Object(properties = linkedMapOf(
                    "Item" to UDM.Object(properties = linkedMapOf(
                        "%entityType" to UDM.Scalar.boolean(true)
                    ))
                ))
            ))

            val output = EDMXSerializer().serialize(udm)

            output shouldContain "Alias=\"MN\""
        }
    }

    // ==================== Round-Trip ====================

    @Nested
    @DisplayName("Round-Trip: Parse then Serialize")
    inner class RoundTrip {

        @Test
        fun `parse and reserialize preserves entity types and properties`() {
            val input = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test.Model" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Product">
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

            // Parse EDMX → UDM
            val udm = EDMXParser(input).parse()

            // Serialize UDM → EDMX
            val output = EDMXSerializer().serialize(udm)

            // Structural equivalence
            output shouldContain "<EntityType Name=\"Product\">"
            output shouldContain "<PropertyRef Name=\"ID\"/>"
            output shouldContain "Name=\"ID\" Type=\"Edm.Int32\" Nullable=\"false\""
            output shouldContain "Name=\"Name\" Type=\"Edm.String\""
            output shouldContain "MaxLength=\"100\""
            output shouldContain "Name=\"Price\" Type=\"Edm.Decimal\""
            output shouldContain "Precision=\"10\""
            output shouldContain "Scale=\"2\""
        }

        @Test
        fun `round-trip preserves complex type`() {
            val input = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <ComplexType Name="Address">
                        <Property Name="Street" Type="Edm.String"/>
                        <Property Name="City" Type="Edm.String"/>
                      </ComplexType>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
            """.trimIndent()

            val udm = EDMXParser(input).parse()
            val output = EDMXSerializer().serialize(udm)

            output shouldContain "<ComplexType Name=\"Address\">"
            output shouldContain "Name=\"Street\" Type=\"Edm.String\""
            output shouldContain "Name=\"City\" Type=\"Edm.String\""
        }

        @Test
        fun `round-trip preserves enum type`() {
            val input = """
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

            val udm = EDMXParser(input).parse()
            val output = EDMXSerializer().serialize(udm)

            output shouldContain "<EnumType Name=\"Color\">"
            output shouldContain "<Member Name=\"Red\" Value=\"0\"/>"
            output shouldContain "<Member Name=\"Green\" Value=\"1\"/>"
            output shouldContain "<Member Name=\"Blue\" Value=\"2\"/>"
        }

        @Test
        fun `round-trip preserves navigation properties and referential constraints`() {
            val input = """
                <?xml version="1.0" encoding="utf-8"?>
                <edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="Test.Model" xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="Product">
                        <Key>
                          <PropertyRef Name="ID"/>
                        </Key>
                        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
                        <NavigationProperty Name="Category" Type="Test.Model.Category" Partner="Products">
                          <ReferentialConstraint Property="CategoryID" ReferencedProperty="CategoryID"/>
                        </NavigationProperty>
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

            val udm = EDMXParser(input).parse()
            val output = EDMXSerializer().serialize(udm)

            // Single-valued nav
            output shouldContain "NavigationProperty Name=\"Category\" Type=\"Test.Model.Category\""
            output shouldContain "Partner=\"Products\""
            output shouldContain "<ReferentialConstraint Property=\"CategoryID\" ReferencedProperty=\"CategoryID\"/>"

            // Collection nav
            output shouldContain "NavigationProperty Name=\"Products\" Type=\"Collection(Test.Model.Product)\""
            output shouldContain "Partner=\"Category\""
        }
    }

    // ==================== Error Handling ====================

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {

        @Test
        fun `reject non-object UDM`() {
            val udm = UDM.Scalar.string("not an object")

            assertThrows<IllegalArgumentException> {
                EDMXSerializer().serialize(udm)
            }
        }

        @Test
        fun `reject array UDM`() {
            val udm = UDM.Array(listOf(UDM.Scalar.string("item")))

            assertThrows<IllegalArgumentException> {
                EDMXSerializer().serialize(udm)
            }
        }
    }
}
