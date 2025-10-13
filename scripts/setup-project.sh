#!/bin/bash
# Setup script for UTL-X project structure

set -e

echo "🚀 Setting up UTL-X project structure..."

# Create main directories
mkdir -p modules/{core,jvm,javascript,native,cli}
mkdir -p formats/{xml,json,csv,yaml,plugin}
mkdir -p stdlib
mkdir -p tools/{vscode-extension,intellij-plugin,maven-plugin,gradle-plugin,benchmarks}
mkdir -p scripts
mkdir -p build
mkdir -p dist
mkdir -p .vscode

# Create core module structure
echo "📦 Creating core module..."
mkdir -p modules/core/src/{main,test}/kotlin/org/apache/utlx/core/{ast,lexer,parser,types,optimizer,codegen,udm}

# Create JVM module structure
echo "☕ Creating JVM module..."
mkdir -p modules/jvm/src/{main,test}/kotlin/org/apache/utlx/jvm/{runtime,compiler,api,integration/{spring,camel,kafka}}

# Create JavaScript module structure
echo "📜 Creating JavaScript module..."
mkdir -p modules/javascript/src/{runtime,compiler,api,browser,node}
mkdir -p modules/javascript/test

# Create Native module structure
echo "⚡ Creating Native module..."
mkdir -p modules/native/src/{main,test}/kotlin/org/apache/utlx/native/{compiler,runtime,ffi}

# Create CLI module structure
echo "🖥️  Creating CLI module..."
mkdir -p modules/cli/src/{main,test}/kotlin/org/apache/utlx/cli/{commands,options,utils}

# Create format modules
echo "📄 Creating format modules..."
for format in xml json csv yaml; do
    mkdir -p formats/$format/src/{main,test}/kotlin/org/apache/utlx/formats/$format
done
mkdir -p formats/plugin/src/{main,test}/kotlin/org/apache/utlx/formats/plugin

# Create stdlib structure
echo "📚 Creating standard library..."
mkdir -p stdlib/src/{main,test}/kotlin/org/apache/utlx/stdlib/{string,array,math,date,object,type}

# Create tool structures
echo "🔧 Creating tools..."
mkdir -p tools/vscode-extension/src/{language,debugger}
mkdir -p tools/vscode-extension/syntaxes
mkdir -p tools/intellij-plugin/src/main/{kotlin,resources}
mkdir -p tools/maven-plugin/src/main/java/org/apache/utlx/maven
mkdir -p tools/gradle-plugin/src/main/kotlin/org/apache/utlx/gradle
mkdir -p tools/benchmarks/src/main/kotlin/org/apache/utlx/benchmarks

# Create Gradle wrapper directory
mkdir -p gradle/wrapper

echo "✅ Directory structure created!"

# Create placeholder README files
echo "📝 Creating README files..."
find modules -type d -maxdepth 1 -mindepth 1 -exec sh -c 'echo "# $(basename {}) Module" > {}/README.md' \;
find formats -type d -maxdepth 1 -mindepth 1 -exec sh -c 'echo "# $(basename {}) Format Module" > {}/README.md' \;
find tools -type d -maxdepth 1 -mindepth 1 -exec sh -c 'echo "# $(basename {}) Tool" > {}/README.md' \;

echo "✅ README files created!"

# Create .gitignore
cat > .gitignore << 'EOF'
# Build outputs
build/
dist/
out/
target/
*.class
*.jar
*.war
*.ear

# IDE
.idea/
*.iml
.vscode/
*.swp
.DS_Store

# Gradle
.gradle/
gradle-app.setting
!gradle-wrapper.jar

# Node
node_modules/
npm-debug.log
yarn-error.log

# Logs
*.log
logs/

# Test
coverage/
.nyc_output/
test-results/

# Temporary
*.tmp
*.temp
*~

# Distribution
*.tar.gz
*.zip
EOF

echo "✅ .gitignore created!"

# Create .editorconfig
cat > .editorconfig << 'EOF'
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.{kt,kts}]
indent_style = space
indent_size = 4

[*.{ts,js,json}]
indent_style = space
indent_size = 2

[*.{yml,yaml}]
indent_style = space
indent_size = 2

[*.md]
trim_trailing_whitespace = false
EOF

echo "✅ .editorconfig created!"

# Create root build.gradle.kts
cat > build.gradle.kts << 'EOF'
plugins {
    kotlin("jvm") version "1.9.20" apply false
    id("org.jetbrains.dokka") version "1.9.10" apply false
}

allprojects {
    group = "org.apache.utlx"
    version = "1.0.0-SNAPSHOT"
    
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")
    
    dependencies {
        val implementation by configurations
        val testImplementation by configurations
        
        implementation(kotlin("stdlib"))
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
        testImplementation("io.mockk:mockk:1.13.8")
    }
    
    tasks.withType<Test> {
        useJUnitPlatform()
    }
    
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }
}
EOF

echo "✅ Root build.gradle.kts created!"

# Create settings.gradle.kts
cat > settings.gradle.kts << 'EOF'
rootProject.name = "utl-x"

// Core modules
include("modules:core")
include("modules:jvm")
include("modules:javascript")
include("modules:native")
include("modules:cli")

// Format modules
include("formats:xml")
include("formats:json")
include("formats:csv")
include("formats:yaml")
include("formats:plugin")

// Standard library
include("stdlib")

// Tools
include("tools:intellij-plugin")
include("tools:maven-plugin")
include("tools:gradle-plugin")
include("tools:benchmarks")
EOF

echo "✅ settings.gradle.kts created!"

# Create gradle.properties
cat > gradle.properties << 'EOF'
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m
org.gradle.parallel=true
org.gradle.caching=true
EOF

echo "✅ gradle.properties created!"

# Create package.json for JavaScript tooling
cat > modules/javascript/package.json << 'EOF'
{
  "name": "@apache/utlx",
  "version": "1.0.0",
  "description": "Universal Transformation Language Extended - JavaScript Runtime",
  "main": "dist/index.js",
  "types": "dist/index.d.ts",
  "scripts": {
    "build": "tsc",
    "test": "jest",
    "lint": "eslint src/**/*.ts",
    "format": "prettier --write src/**/*.ts"
  },
  "keywords": ["transformation", "xml", "json", "data"],
  "author": "UTL-X Contributors",
  "license": "AGPL-3.0",
  "repository": {
    "type": "git",
    "url": "https://github.com/grauwen/utl-x.git"
  },
  "devDependencies": {
    "@types/node": "^20.0.0",
    "typescript": "^5.2.0",
    "jest": "^29.6.0",
    "@typescript-eslint/eslint-plugin": "^6.0.0",
    "@typescript-eslint/parser": "^6.0.0",
    "eslint": "^8.47.0",
    "prettier": "^3.0.0"
  }
}
EOF

echo "✅ JavaScript package.json created!"

# Create tsconfig.json
cat > modules/javascript/tsconfig.json << 'EOF'
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "commonjs",
    "lib": ["ES2020"],
    "declaration": true,
    "outDir": "./dist",
    "rootDir": "./src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "moduleResolution": "node",
    "resolveJsonModule": true,
    "sourceMap": true
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist", "test"]
}
EOF

echo "✅ TypeScript config created!"

# Create VS Code settings
cat > .vscode/settings.json << 'EOF'
{
  "editor.formatOnSave": true,
  "editor.codeActionsOnSave": {
    "source.organizeImports": true
  },
  "files.exclude": {
    "**/.gradle": true,
    "**/build": true,
    "**/node_modules": true,
    "**/.idea": true
  },
  "[kotlin]": {
    "editor.tabSize": 4
  },
  "[typescript]": {
    "editor.tabSize": 2,
    "editor.defaultFormatter": "esbenp.prettier-vscode"
  },
  "[json]": {
    "editor.tabSize": 2
  }
}
EOF

echo "✅ VS Code settings created!"

# Create VS Code launch configuration
cat > .vscode/launch.json << 'EOF'
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "kotlin",
      "request": "launch",
      "name": "Run CLI",
      "projectName": "utl-x",
      "mainClass": "org.apache.utlx.cli.MainKt",
      "args": "transform test.utlx input.json"
    },
    {
      "type": "node",
      "request": "launch",
      "name": "Run JS Tests",
      "program": "${workspaceFolder}/modules/javascript/node_modules/.bin/jest",
      "args": ["--runInBand"],
      "console": "integratedTerminal",
      "internalConsoleOptions": "neverOpen"
    }
  ]
}
EOF

echo "✅ VS Code launch config created!"

echo ""
echo "🎉 Project setup complete!"
echo ""
echo "Next steps:"
echo "1. Initialize git: git init"
echo "2. Download Gradle wrapper: gradle wrapper"
echo "3. Build the project: ./gradlew build"
echo "4. Install JS dependencies: cd modules/javascript && npm install"
echo "5. Start coding! 🚀"
