package org.apache.utlx.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import org.apache.utlx.jvm.api.UTLXEngine
import org.apache.utlx.jvm.api.Format
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Transform command - Main transformation operation
 */
class TransformCommand : CliktCommand(
    name = "transform",
    help = "Transform data from one format to another using UTL-X"
) {
    
    private val input by argument(
        name = "INPUT",
        help = "Input file (or - for stdin)"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true).optional()
    
    private val transform by argument(
        name = "TRANSFORM",
        help = "UTL-X transformation file"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    private val output by option(
        "-o", "--output",
        help = "Output file (default: stdout)"
    ).file(canBeDir = false)
    
    private val inputFormat by option(
        "-i", "--input-format",
        help = "Input format (auto-detected if not specified)"
    ).enum<Format>()
    
    private val outputFormat by option(
        "-f", "--output-format",
        help = "Output format"
    ).enum<Format>().default(Format.JSON)
    
    private val pretty by option(
        "-p", "--pretty",
        help = "Pretty-print output"
    ).flag(default = true)
    
    private val watch by option(
        "-w", "--watch",
        help = "Watch for changes and re-transform"
    ).flag()
    
    private val benchmark by option(
        "-b", "--benchmark",
        help = "Run benchmark (multiple iterations)"
    ).flag()
    
    override fun run() {
        val inputFile = input ?: readStdin()
        
        if (benchmark) {
            runBenchmark(inputFile, transform)
            return
        }
        
        if (watch) {
            watchMode(inputFile, transform)
            return
        }
        
        executeTransform(inputFile, transform, output)
    }
    
    private fun executeTransform(inputFile: File, transformFile: File, outputFile: File?) {
        val startTime = System.currentTimeMillis()
        
        // Compile transformation
        val engine = UTLXEngine.builder()
            .compile(transformFile)
            .build()
        
        val compileTime = System.currentTimeMillis() - startTime
        logVerbose("Compilation time: ${compileTime}ms")
        
        // Execute transformation
        val transformStartTime = System.currentTimeMillis()
        val result = engine.transform(
            input = inputFile.readText(),
            inputFormat = inputFormat ?: detectFormat(inputFile),
            outputFormat = outputFormat,
            pretty = pretty
        )
        
        val transformTime = System.currentTimeMillis() - transformStartTime
        logVerbose("Transformation time: ${transformTime}ms")
        
        // Write output
        if (outputFile != null) {
            outputFile.writeText(result)
            echo("✓ Output written to: ${outputFile.absolutePath}")
        } else {
            echo(result)
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        logVerbose("Total time: ${totalTime}ms")
    }
    
    private fun runBenchmark(inputFile: File, transformFile: File) {
        echo("Running benchmark...")
        
        val warmupIterations = 10
        val benchmarkIterations = 100
        
        // Compile once
        val engine = UTLXEngine.builder()
            .compile(transformFile)
            .build()
        
        val inputText = inputFile.readText()
        val inputFmt = inputFormat ?: detectFormat(inputFile)
        
        // Warmup
        echo("Warmup: $warmupIterations iterations...")
        repeat(warmupIterations) {
            engine.transform(inputText, inputFmt, outputFormat, false)
        }
        
        // Benchmark
        echo("Benchmarking: $benchmarkIterations iterations...")
        val times = mutableListOf<Long>()
        
        repeat(benchmarkIterations) {
            val time = measureTimeMillis {
                engine.transform(inputText, inputFmt, outputFormat, false)
            }
            times.add(time)
        }
        
        // Statistics
        times.sort()
        val min = times.first()
        val max = times.last()
        val avg = times.average()
        val median = times[times.size / 2]
        val p95 = times[(times.size * 0.95).toInt()]
        val p99 = times[(times.size * 0.99).toInt()]
        
        echo("\nResults:")
        echo("  Min:      ${min}ms")
        echo("  Max:      ${max}ms")
        echo("  Average:  ${"%.2f".format(avg)}ms")
        echo("  Median:   ${median}ms")
        echo("  P95:      ${p95}ms")
        echo("  P99:      ${p99}ms")
        echo("  Throughput: ${"%.2f".format(1000.0 / avg)} transforms/sec")
    }
    
    private fun watchMode(inputFile: File, transformFile: File) {
        echo("Watching for changes... (Ctrl+C to stop)")
        
        var lastInputModified = inputFile.lastModified()
        var lastTransformModified = transformFile.lastModified()
        
        while (true) {
            Thread.sleep(1000)
            
            val inputModified = inputFile.lastModified()
            val transformModified = transformFile.lastModified()
            
            if (inputModified != lastInputModified || transformModified != lastTransformModified) {
                echo("\n🔄 Changes detected, re-transforming...")
                try {
                    executeTransform(inputFile, transformFile, output)
                    echo("✓ Transform complete")
                } catch (e: Exception) {
                    echo("✗ Error: ${e.message}", err = true)
                }
                
                lastInputModified = inputModified
                lastTransformModified = transformModified
            }
        }
    }
    
    private fun readStdin(): File {
        val tempFile = File.createTempFile("utlx-stdin-", ".tmp")
        tempFile.deleteOnExit()
        tempFile.writeText(System.`in`.bufferedReader().readText())
        return tempFile
    }
    
    private fun detectFormat(file: File): Format {
        return when (file.extension.lowercase()) {
            "xml" -> Format.XML
            "json" -> Format.JSON
            "csv" -> Format.CSV
            "yaml", "yml" -> Format.YAML
            else -> Format.AUTO
        }
    }
    
    private fun logVerbose(message: String) {
        if (System.getProperty("utlx.verbose") == "true") {
            echo("  $message")
        }
    }
}
