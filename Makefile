# Makefile for UTL-X project

.PHONY: all build test clean setup install docs

# Default target
all: build

# Setup project structure
setup:
	@echo "Setting up UTL-X project..."
	@chmod +x scripts/*.sh
	@./scripts/setup-project.sh
	@./scripts/init-modules.sh
	@./scripts/create-sample-files.sh
	@echo "✅ Setup complete!"

# Download Gradle wrapper
gradle-wrapper:
	@gradle wrapper --gradle-version 8.4

# Build all modules
build:
	@echo "Building UTL-X..."
	@./gradlew build

# Build specific module
build-jvm:
	@./gradlew :modules:jvm:build

build-cli:
	@./gradlew :modules:cli:build

build-js:
	@cd modules/javascript && npm run build

# Run tests
test:
	@echo "Running tests..."
	@./gradlew test

test-jvm:
	@./gradlew :modules:jvm:test

test-js:
	@cd modules/javascript && npm test

# Clean build artifacts
clean:
	@echo "Cleaning..."
	@./gradlew clean
	@rm -rf build/ dist/
	@find . -name "node_modules" -type d -exec rm -rf {} +

# Install dependencies
install:
	@echo "Installing dependencies..."
	@cd modules/javascript && npm install

# Run CLI
run:
	@./gradlew :modules:cli:run --args="$(ARGS)"

# Create distribution packages
dist:
	@echo "Creating distribution packages..."
	@./gradlew assembleDist

# Generate documentation
docs:
	@echo "Generating documentation..."
	@./gradlew dokkaHtml

# Run benchmarks
benchmark:
	@./gradlew :tools:benchmarks:jmh

# Format code
format:
	@./gradlew ktlintFormat
	@cd modules/javascript && npm run format

# Lint code
lint:
	@./gradlew ktlintCheck
	@cd modules/javascript && npm run lint

# Help
help:
	@echo "UTL-X Build System"
	@echo ""
	@echo "Available targets:"
	@echo "  make setup         - Set up project structure"
	@echo "  make build         - Build all modules"
	@echo "  make test          - Run all tests"
	@echo "  make clean         - Clean build artifacts"
	@echo "  make install       - Install dependencies"
	@echo "  make run ARGS='...'- Run CLI with arguments"
	@echo "  make dist          - Create distribution packages"
	@echo "  make docs          - Generate documentation"
	@echo "  make format        - Format code"
	@echo "  make lint          - Lint code"
