# Start developing in your IDE
# IntelliJ IDEA: Open as Gradle project
# VS Code: Open folder

# Run tests while developing
make test

# Build specific module
make build-jvm
make build-js

# Run CLI locally
make run ARGS="transform test.utlx input.json"
