package org.apache.utlx.cli.commands

import com.github.ajalt.clikt.core.CliktCommand

/**
 * Version command - Display version information
 */
class VersionCommand : CliktCommand(
    name = "version",
    help = "Display version information"
) {
    
    override fun run() {
        echo("""
            UTL-X (Universal Transformation Language Extended)
            
            Version:        1.0.0-SNAPSHOT
            Build:          native
            Runtime:        ${System.getProperty("java.version")}
            OS:             ${System.getProperty("os.name")} ${System.getProperty("os.version")}
            Architecture:   ${System.getProperty("os.arch")}
            
            License:        Dual-licensed (AGPL-3.0 / Commercial)
            Project Lead:   Ir. Marcel A. Grauwen
            Website:        https://github.com/grauwen/utl-x
            
            For commercial licensing: info@utlx-lang.org
        """.trimIndent())
    }
}
