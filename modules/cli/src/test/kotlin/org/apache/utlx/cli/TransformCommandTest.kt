// modules/cli/src/test/kotlin/org/apache/utlx/cli/TransformCommandTest.kt
package org.apache.utlx.cli

import org.apache.utlx.cli.commands.TransformCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformCommandTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    @Test
    fun `test simple XML to JSON transformation`() {
        // Create input XML
        val inputXml = tempDir.resolve("input.xml").toFile()
        inputXml.writeText("""
            <person>
                <name>John Doe</name>
                <age>30</age>
            </person>
        """.trimIndent())
        
        // Create UTL-X script
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input xml
            output json
            ---
            {
              name: input.person.name,
              age: input.person.age
            }
        """.trimIndent())
        
        // Output file
        val output = tempDir.resolve("output.json").toFile()
        
        // Execute transformation
        val args = arrayOf(
            script.absolutePath,
            inputXml.absolutePath,
            "-o", output.absolutePath
        )
        
        TransformCommand.execute(args)
        
        // Verify output
        assertTrue(output.exists(), "Output file should exist")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("\"name\""), "Should contain name field")
        assertTrue(outputContent.contains("John Doe"), "Should contain name value")
    }
    
    @Test
    fun `test JSON to XML transformation`() {
        val inputJson = tempDir.resolve("input.json").toFile()
        inputJson.writeText("""
            {
              "order": {
                "id": "ORD-001",
                "customer": "Alice"
              }
            }
        """.trimIndent())
        
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output xml
            ---
            {
              Order: {
                @id: input.order.id,
                Customer: input.order.customer
              }
            }
        """.trimIndent())
        
        val output = tempDir.resolve("output.xml").toFile()
        
        val args = arrayOf(
            script.absolutePath,
            inputJson.absolutePath,
            "-o", output.absolutePath
        )
        
        TransformCommand.execute(args)
        
        assertTrue(output.exists())
        val outputContent = output.readText()
        assertTrue(outputContent.contains("<Order"))
        assertTrue(outputContent.contains("id=\"ORD-001\""))
    }
    
    @Test
    fun `test CSV to JSON transformation`() {
        val inputCsv = tempDir.resolve("input.csv").toFile()
        inputCsv.writeText("""
            Name,Age,City
            Alice,25,New York
            Bob,30,San Francisco
        """.trimIndent())
        
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input csv
            output json
            ---
            {
              people: input |> map(row => {
                name: row.Name,
                age: row.Age,
                city: row.City
              })
            }
        """.trimIndent())
        
        val output = tempDir.resolve("output.json").toFile()
        
        val args = arrayOf(
            script.absolutePath,
            inputCsv.absolutePath,
            "-o", output.absolutePath
        )
        
        TransformCommand.execute(args)
        
        assertTrue(output.exists())
        val outputContent = output.readText()
        assertTrue(outputContent.contains("\"people\""))
        assertTrue(outputContent.contains("Alice"))
        assertTrue(outputContent.contains("New York"))
    }
    
    @Test
    fun `test auto format detection`() {
        val inputXml = tempDir.resolve("input.xml").toFile()
        inputXml.writeText("<data><value>42</value></data>")
        
        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input auto
            output json
            ---
            { value: input.data.value }
        """.trimIndent())
        
        val output = tempDir.resolve("output.json").toFile()
        
        val args = arrayOf(
            script.absolutePath,
            inputXml.absolutePath,
            "-o", output.absolutePath
        )

        TransformCommand.execute(args)
        
        assertTrue(output.exists())
    }
    
    @Test
    fun `test verbose mode`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("{\"test\": true}")

        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            { result: input.test }
        """.trimIndent())

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            script.absolutePath,
            input.absolutePath,
            "-o", output.absolutePath,
            "--verbose"
        )

        // Should not throw exception
        TransformCommand.execute(args)

        assertTrue(output.exists())
    }

    @Test
    fun `test type checking default mode - type error produces warning but succeeds`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("{\"value\": 42}")

        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            (let x: Number = "this is a string" in { result: x })
        """.trimIndent())

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            script.absolutePath,
            input.absolutePath,
            "-o", output.absolutePath
        )

        // Should succeed - default mode shows warnings but doesn't fail
        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Should succeed in default mode")
        assertTrue(output.exists(), "Output should be created even with type errors in default mode")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("this is a string"), "Should execute despite type mismatch")
    }

    @Test
    fun `test strict-types mode - type error causes failure`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("{\"value\": 42}")

        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            (let x: Number = "this is a string" in { result: x })
        """.trimIndent())

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            script.absolutePath,
            input.absolutePath,
            "-o", output.absolutePath,
            "--strict-types"
        )

        // Should fail - strict mode rejects type errors
        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Failure, "Should fail with type errors in strict mode")
        assertEquals(1, (result as CommandResult.Failure).exitCode, "Exit code should be 1")
        assertTrue(result.message.contains("Type checking failed"), "Error message should mention type checking")
        assertTrue(!output.exists(), "Output should not be created when type checking fails")
    }

    @Test
    fun `test strict-types mode - valid types succeed`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("{\"value\": 42}")

        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            (let x: Number = 123 in { result: x })
        """.trimIndent())

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            script.absolutePath,
            input.absolutePath,
            "-o", output.absolutePath,
            "--strict-types"
        )

        // Should succeed - types are valid
        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Should succeed with valid types in strict mode")
        assertTrue(output.exists(), "Output should be created")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("123"), "Should contain the correct value")
    }

    @Test
    fun `test strict-types with multiple type errors`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("{\"value\": 42}")

        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            {
              error1: (let x: Number = "string" in x),
              error2: (let y: String = 123 in y),
              error3: (let z: Boolean = null in z)
            }
        """.trimIndent())

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            script.absolutePath,
            input.absolutePath,
            "-o", output.absolutePath,
            "--strict-types"
        )

        // Should fail with multiple errors
        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Failure, "Should fail with multiple type errors")
        assertTrue(!output.exists(), "Output should not be created")
    }

    // =========================================================================
    // Identity Mode Tests (passthrough with smart format flip)
    // =========================================================================

    @Test
    fun `test identity mode - XML to JSON smart flip`() {
        val inputXml = tempDir.resolve("input.xml").toFile()
        inputXml.writeText("<person><name>Alice</name><age>30</age></person>")

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            "-i", inputXml.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args, identityMode = true)

        assertTrue(result is CommandResult.Success, "Identity XML→JSON should succeed")
        assertTrue(output.exists(), "Output file should exist")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("Alice"), "Should contain data from XML input")
        assertTrue(outputContent.contains("\"name\"") || outputContent.contains("\"person\""),
            "Output should be JSON format")
    }

    @Test
    fun `test identity mode - JSON to XML smart flip`() {
        val inputJson = tempDir.resolve("input.json").toFile()
        inputJson.writeText("""{"order": {"id": "ORD-001", "customer": "Bob"}}""")

        val output = tempDir.resolve("output.xml").toFile()

        val args = arrayOf(
            "-i", inputJson.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args, identityMode = true)

        assertTrue(result is CommandResult.Success, "Identity JSON→XML should succeed")
        assertTrue(output.exists(), "Output file should exist")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("<"), "Output should be XML format")
        assertTrue(outputContent.contains("Bob"), "Should contain data from JSON input")
    }

    @Test
    fun `test identity mode - CSV to JSON smart flip`() {
        val inputCsv = tempDir.resolve("input.csv").toFile()
        inputCsv.writeText("Name,Age\nAlice,25\nBob,30")

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            "-i", inputCsv.absolutePath,
            "-o", output.absolutePath,
            "--from", "csv"
        )

        val result = TransformCommand.execute(args, identityMode = true)

        assertTrue(result is CommandResult.Success, "Identity CSV→JSON should succeed")
        assertTrue(output.exists(), "Output file should exist")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("Alice"), "Should contain CSV data")
    }

    @Test
    fun `test identity mode - explicit --to overrides smart flip`() {
        val inputXml = tempDir.resolve("input.xml").toFile()
        inputXml.writeText("<data><value>42</value></data>")

        val output = tempDir.resolve("output.yaml").toFile()

        val args = arrayOf(
            "-i", inputXml.absolutePath,
            "-o", output.absolutePath,
            "--to", "yaml"
        )

        val result = TransformCommand.execute(args, identityMode = true)

        assertTrue(result is CommandResult.Success, "Identity XML→YAML should succeed")
        assertTrue(output.exists(), "Output file should exist")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("42"), "Should contain data from input")
    }

    @Test
    fun `test identity mode - no args reads stdin`() {
        // Identity mode with no args (would read stdin in real usage)
        // Here we test that parseOptions returns identity mode options
        val options = TransformCommand.parseOptions(emptyArray(), allowIdentityMode = true)
        assertTrue(options.identityMode, "Should be in identity mode")
        assertTrue(options.scriptFile == null, "Script file should be null in identity mode")
    }

    @Test
    fun `test identity mode - --to and --from aliases work`() {
        val inputJson = tempDir.resolve("input.json").toFile()
        inputJson.writeText("""{"name": "test"}""")

        val output = tempDir.resolve("output.yaml").toFile()

        val args = arrayOf(
            "-i", inputJson.absolutePath,
            "-o", output.absolutePath,
            "--from", "json",
            "--to", "yaml"
        )

        val result = TransformCommand.execute(args, identityMode = true)

        assertTrue(result is CommandResult.Success, "Identity JSON→YAML with --from/--to should succeed")
        assertTrue(output.exists(), "Output file should exist")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("test"), "Should contain data from input")
    }

    // =========================================================================
    // --to / --from aliases with normal transform mode (backward compat)
    // =========================================================================

    @Test
    fun `test --to alias works with script-based transform`() {
        val inputXml = tempDir.resolve("input.xml").toFile()
        inputXml.writeText("<data><value>hello</value></data>")

        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input xml
            output xml
            ---
            { result: input.data.value }
        """.trimIndent())

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            script.absolutePath,
            inputXml.absolutePath,
            "-o", output.absolutePath,
            "--to", "json"
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Should succeed with --to alias")
        assertTrue(output.exists(), "Output file should exist")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("hello"), "Should contain transformed data")
        assertTrue(outputContent.contains("{"), "Output should be JSON (overridden by --to)")
    }

    @Test
    fun `test --from alias works with script-based transform`() {
        val inputXml = tempDir.resolve("input.xml").toFile()
        inputXml.writeText("<data><value>world</value></data>")

        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input auto
            output json
            ---
            { result: input.data.value }
        """.trimIndent())

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            script.absolutePath,
            inputXml.absolutePath,
            "-o", output.absolutePath,
            "--from", "xml"
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Should succeed with --from alias")
        assertTrue(output.exists(), "Output file should exist")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("world"), "Should contain data parsed with --from xml")
    }

    // =========================================================================
    // Implicit transform (no 'transform' subcommand, .utlx file as first arg)
    // =========================================================================

    @Test
    fun `test implicit transform with utlx file`() {
        val inputJson = tempDir.resolve("input.json").toFile()
        inputJson.writeText("""{"greeting": "hello"}""")

        val script = tempDir.resolve("script.utlx").toFile()
        script.writeText("""
            %utlx 1.0
            input json
            output json
            ---
            { msg: input.greeting }
        """.trimIndent())

        val output = tempDir.resolve("output.json").toFile()

        // Simulate what Main.kt does: pass full args including the .utlx file
        val args = arrayOf(
            script.absolutePath,
            inputJson.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Implicit transform should succeed")
        assertTrue(output.exists(), "Output file should exist")
        val outputContent = output.readText()
        assertTrue(outputContent.contains("hello"), "Should contain transformed data")
    }

    // =========================================================================
    // Expression Mode (-e) Tests
    // =========================================================================

    @Test
    fun `test expression mode - basic field extraction`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"name":"Alice","age":30}""")

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            "-e", "\$input.name",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Expression mode should succeed")
        assertTrue(output.exists(), "Output file should exist")
        val content = output.readText().trim()
        assertTrue(content.contains("Alice"), "Should contain extracted value")
    }

    @Test
    fun `test expression mode - default output is JSON`() {
        val input = tempDir.resolve("input.xml").toFile()
        input.writeText("<person><name>Alice</name></person>")

        val output = tempDir.resolve("output.txt").toFile()

        val args = arrayOf(
            "-e", "\$input.person.name",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Expression from XML should succeed")
        assertTrue(output.exists(), "Output file should exist")
        val content = output.readText().trim()
        assertTrue(content.contains("Alice"), "Should contain extracted name")
        // JSON output: should have quotes
        assertTrue(content.startsWith("\"") || content.contains("{"), "Output should be JSON format")
    }

    @Test
    fun `test expression mode - with --to yaml override`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"name":"Alice","age":30}""")

        val output = tempDir.resolve("output.yaml").toFile()

        val args = arrayOf(
            "-e", "\$input",
            "-i", input.absolutePath,
            "-o", output.absolutePath,
            "--to", "yaml"
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Expression with --to yaml should succeed")
        assertTrue(output.exists(), "Output file should exist")
        val content = output.readText()
        assertTrue(content.contains("name:"), "Output should be YAML format")
    }

    @Test
    fun `test expression mode - function call`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"items":[{"price":10},{"price":20},{"price":30}]}""")

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            "-e", "sum(\$input.items |> map(i => i.price))",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Expression with function should succeed")
        assertTrue(output.exists(), "Output file should exist")
        val content = output.readText().trim()
        assertTrue(content.contains("60"), "Should contain sum result")
    }

    @Test
    fun `test expression mode - long form --expression`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"value":42}""")

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            "--expression", "\$input.value",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "--expression long form should succeed")
        val content = output.readText().trim()
        assertTrue(content.contains("42"), "Should contain value")
    }

    // =========================================================================
    // Raw Output (-r) Tests
    // =========================================================================

    @Test
    fun `test raw output - strips quotes from string`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"name":"Alice"}""")

        val output = tempDir.resolve("output.txt").toFile()

        val args = arrayOf(
            "-e", "\$input.name",
            "-r",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Raw output should succeed")
        val content = output.readText().trim()
        assertEquals("Alice", content, "Raw output should strip quotes")
    }

    @Test
    fun `test raw output - long form --raw-output`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"name":"Bob"}""")

        val output = tempDir.resolve("output.txt").toFile()

        val args = arrayOf(
            "-e", "\$input.name",
            "--raw-output",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "--raw-output long form should succeed")
        val content = output.readText().trim()
        assertEquals("Bob", content, "Raw output should strip quotes")
    }

    @Test
    fun `test raw output - number unchanged`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"value":42}""")

        val output = tempDir.resolve("output.txt").toFile()

        val args = arrayOf(
            "-e", "\$input.value",
            "-r",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Raw output with number should succeed")
        val content = output.readText().trim()
        assertEquals("42", content, "Number should be unchanged in raw mode")
    }

    @Test
    fun `test raw output - boolean unchanged`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"active":true}""")

        val output = tempDir.resolve("output.txt").toFile()

        val args = arrayOf(
            "-e", "\$input.active",
            "-r",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Raw output with boolean should succeed")
        val content = output.readText().trim()
        assertEquals("true", content, "Boolean should be unchanged in raw mode")
    }

    // =========================================================================
    // Dot Shorthand Expansion Tests
    // =========================================================================

    @Test
    fun `test dot expansion - parseOptions recognizes -e`() {
        val options = TransformCommand.parseOptions(arrayOf("-e", ".name"), allowIdentityMode = true)
        assertEquals(".name", options.expression, "Expression should be captured")
        assertTrue(options.scriptFile == null, "No script file in expression mode")
    }

    @Test
    fun `test dot expansion - dot alone becomes identity`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"a":1,"b":2}""")

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            "-e", ".",
            "-i", input.absolutePath,
            "-o", output.absolutePath,
            "--no-pretty"
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Dot identity should succeed")
        val content = output.readText().trim()
        assertTrue(content.contains("\"a\"") && content.contains("\"b\""), "Should contain all fields")
    }

    @Test
    fun `test dot expansion - dot with function`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"name":"alice"}""")

        val output = tempDir.resolve("output.txt").toFile()

        val args = arrayOf(
            "-e", "upper(.name)",
            "-r",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Dot with function should succeed")
        val content = output.readText().trim()
        assertEquals("ALICE", content, "Should uppercase via dot shorthand")
    }

    @Test
    fun `test dot expansion - explicit dollar input still works`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"x":99}""")

        val output = tempDir.resolve("output.json").toFile()

        val args = arrayOf(
            "-e", "\$input.x",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success, "Explicit \$input should still work")
        val content = output.readText().trim()
        assertTrue(content.contains("99"), "Should contain value")
    }

    // =========================================================================
    // Expression + Raw combined
    // =========================================================================

    @Test
    fun `test expression with raw and function`() {
        val input = tempDir.resolve("input.json").toFile()
        input.writeText("""{"msg":"hello world"}""")

        val output = tempDir.resolve("output.txt").toFile()

        val args = arrayOf(
            "-e", "upper(.msg)",
            "-r",
            "-i", input.absolutePath,
            "-o", output.absolutePath
        )

        val result = TransformCommand.execute(args)

        assertTrue(result is CommandResult.Success)
        val content = output.readText().trim()
        assertEquals("HELLO WORLD", content)
    }
}
