package org.apache.utlx.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import org.apache.utlx.core.codegen.CodeGenerator
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.optimizer.Optimizer

/**
 * Compile command - Compile UTL-X to bytecode/JavaScript/native
 */
class CompileCommand : CliktCommand(
    name = "compile",
    help = "Compile UTL-X transformation to optimized bytecode"
) {
    
    private val transform by argument(
        name = "TRANSFORM",
        help = "UTL-X transformation file to compile"
    ).file(mustExist = true, mustBeReadable = true)
    
    private val output by option(
        "-o", "--output",
        help = "Output file"
    ).file()
    
    private val target by option(
        "-t", "--target",
        help = "Compilation target"
    ).choice("jvm", "js", "native").default("jvm")
    
    private val optimize by option(
        "-O", "--optimize",
        help = "Optimization level (0-3)"
    ).choice("0", "1", "2", "3").default("2")
    
    override fun run() {
        echo("Compiling: ${transform.absolutePath}")
        echo("  Target: $target")
        echo("  Optimization level: $optimize")
        
        try {
            // Parse
            val parser = Parser(transform.readText())
            val ast = parser.parse()
            
            // Optimize
            val optimizer = Optimizer(optimize.toInt())
            val optimizedAst = optimizer.optimize(ast)
            
            // Generate code
            val generator = CodeGenerator.forTarget(target)
            val bytecode = generator.generate(optimizedAst)
            
            // Write output
            val outputFile = output ?: transform.resolveSibling(
                "${transform.nameWithoutExtension}.${getExtension(target)}"
            )
            outputFile.writeBytes(bytecode)
            
            echo("\n✓ Compiled successfully")
            echo("  Output: ${outputFile.absolutePath}")
            echo("  Size: ${bytecode.size} bytes")
            
        } catch (e: Exception) {
            echo("\n✗ Compilation failed: ${e.message}", err = true)
            throw e
        }
    }
    
    private fun getExtension(target: String): String = when (target) {
        "jvm" -> "class"
        "js" -> "js"
        "native" -> "o"
        else -> "out"
    }
}
