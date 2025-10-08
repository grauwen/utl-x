# Contributing to UTL-X

Thank you for your interest in contributing to UTL-X! We welcome contributions from everyone, whether you're fixing a typo or implementing a major feature.

---

## 📋 Table of Contents

- [Before You Contribute](#before-you-contribute)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Making Changes](#making-changes)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Types of Contributions](#types-of-contributions)
- [Community Guidelines](#community-guidelines)
- [Getting Help](#getting-help)

---

## Before You Contribute

### Contributor License Agreement (CLA)

**Before we can accept your contributions, you must sign our Contributor License Agreement (CLA).**

This is a **one-time process** that takes about 2 minutes.

#### Why do we need a CLA?

UTL-X is dual-licensed under AGPL-3.0 (open source) and a commercial license. The CLA allows Glomidco B.V. to offer UTL-X under both licenses while ensuring all contributions remain properly licensed and available as open source.

#### What does the CLA mean for you?

✅ **You retain copyright** to your contributions  
✅ **Your code is always open source** under AGPL-3.0  
✅ You grant Glomidco B.V. the right to also license under commercial terms  
✅ You grant a patent license for your contributions  
✅ Simple, fair terms based on industry-standard CLAs

#### How to sign the CLA

1. **Read the CLA:** [CLA.md](./CLA.md)
2. **Sign electronically:** https://cla-assistant.io/grauwen/utl-x
3. You'll receive confirmation via email
4. Your GitHub username will be added to the CLA registry

The CLA bot will automatically check your signature when you open a pull request.

#### For corporate contributors

If you're contributing on behalf of your employer, your company must sign a Corporate CLA.  
Contact: cla@glomidco.com

---

## Getting Started

### 1. Check Existing Issues

Before starting work, check if someone else is already working on it:

- Browse [open issues](https://github.com/grauwen/utl-x/issues)
- Check [pull requests](https://github.com/grauwen/utl-x/pulls)
- Search [discussions](https://github.com/grauwen/utl-x/discussions)

### 2. Claim an Issue or Create One

**For existing issues:**
- Comment "I'd like to work on this"
- Wait for maintainer approval (usually within 1-2 business days)
- Maintainers will assign the issue to you

**For new features/bugs:**
- Open an issue first to discuss the approach
- Get feedback before spending significant time coding
- Reference the issue number in your pull request

**Good first issues:**
- Look for the `good-first-issue` label
- These are well-defined, smaller tasks
- Great for newcomers to the project

### 3. Fork the Repository

1. Click the **"Fork"** button at the top of the [repository page](https://github.com/grauwen/utl-x)
2. This creates a copy under your GitHub account

### 4. Clone Your Fork

```bash
# Clone your fork
git clone https://github.com/YOUR-USERNAME/utl-x.git
cd utl-x

# Add upstream remote
git remote add upstream https://github.com/grauwen/utl-x.git

# Verify remotes
git remote -v
```

---

## Development Setup

### Prerequisites

- **Java Development Kit (JDK) 17** or higher
- **Kotlin 1.9+** (included via Gradle)
- **Gradle 8.0+** (use included wrapper: `./gradlew`)
- **Git**
- **Text editor or IDE** (IntelliJ IDEA recommended)

### Initial Build

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Check code style
./gradlew ktlintCheck
```

If everything passes, you're ready to start developing! ✅

### IDE Setup

#### IntelliJ IDEA (Recommended)

1. **Open Project:**
   - `File` → `Open` → Select `utlx` folder
   - IntelliJ will automatically detect it's a Gradle project
   - Wait for Gradle sync to complete (may take 1-2 minutes first time)

2. **Enable Kotlin:**
   - Kotlin plugin should be enabled by default
   - If not: `Settings` → `Plugins` → Search "Kotlin" → Enable

3. **Configure Code Style:**
   - `Settings` → `Editor` → `Code Style` → `Kotlin`
   - Set "Use tab character": OFF
   - Indentation: 4 spaces
   - Or import: `config/intellij-codestyle.xml` (if provided)

4. **Enable ktlint:**
   - Gradle will run ktlint automatically
   - To run manually: `./gradlew ktlintFormat`

#### VS Code

1. **Install Extensions:**
   - Kotlin Language (by fwcd)
   - Gradle for Java
   - Test Runner for Java

2. **Open Project:**
   - `File` → `Open Folder` → Select `utlx`

3. **Configure:**
   - VS Code will detect Gradle project
   - Use integrated terminal for Gradle commands

#### Eclipse

1. **Import Project:**
   - `File` → `Import` → `Gradle` → `Existing Gradle Project`
   - Select `utlx` folder

2. **Install Kotlin Plugin:**
   - `Help` → `Eclipse Marketplace`
   - Search "Kotlin" → Install

---

## Making Changes

### 1. Create a Feature Branch

```bash
# Make sure you're on main and up to date
git checkout main
git pull upstream main

# Create a new branch
git checkout -b feature/my-awesome-feature

# Or for bug fixes
git checkout -b fix/bug-description
```

**Branch naming conventions:**
- `feature/feature-name` - New features
- `fix/bug-name` - Bug fixes
- `docs/topic` - Documentation changes
- `refactor/component` - Code refactoring
- `test/test-name` - Test additions/fixes
- `chore/task` - Maintenance tasks

### 2. Make Your Changes

**Guidelines:**
- Write clean, readable code
- Follow the [coding standards](#coding-standards)
- Add tests for new functionality
- Update documentation as needed
- Keep commits focused and atomic

**Testing your changes:**
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "ParserTest"

# Run tests with verbose output
./gradlew test --info

# Check code coverage
./gradlew test jacocoTestReport
```

### 3. Write Good Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

**Types:**
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation only
- `style` - Code style (formatting, no logic changes)
- `refactor` - Code refactoring
- `test` - Adding or updating tests
- `chore` - Maintenance tasks
- `perf` - Performance improvements

**Examples:**

```bash
# Good commit messages
git commit -m "feat(parser): add support for CSV input format"
git commit -m "fix(compiler): resolve null pointer in type inference"
git commit -m "docs(readme): update installation instructions"
git commit -m "test(stdlib): add tests for string manipulation functions"
git commit -m "refactor(core): simplify UDM construction logic"

# Bad commit messages (avoid these)
git commit -m "fixed stuff"
git commit -m "updates"
git commit -m "wip"
```

**For larger changes:**
```bash
git commit -m "feat(transforms): add template matching support

Implements XSLT-style template matching for declarative transformations.

- Add TemplateNode AST type
- Implement pattern matching algorithm
- Add 'match' and 'apply' keywords
- Include 15 unit tests

Closes #42"
```

### 4. Keep Your Fork Updated

```bash
# Fetch latest changes from upstream
git fetch upstream

# Merge upstream main into your branch
git checkout main
git merge upstream/main

# Rebase your feature branch on latest main
git checkout feature/my-awesome-feature
git rebase main
```

### 5. Push Your Changes

```bash
# Push to your fork
git push origin feature/my-awesome-feature

# If you rebased, you might need force push (be careful!)
git push --force-with-lease origin feature/my-awesome-feature
```

---

## Pull Request Process

### 1. Open a Pull Request

1. Go to the [UTL-X repository](https://github.com/grauwen/utl-x)
2. Click **"Pull requests"** → **"New pull request"**
3. Click **"compare across forks"**
4. Select:
   - **Base repository:** `grauwen/utl-x`
   - **Base branch:** `main`
   - **Head repository:** `YOUR-USERNAME/utl-x`
   - **Compare branch:** `feature/my-awesome-feature`
5. Click **"Create pull request"**
6. Fill out the PR template (see below)
7. Click **"Create pull request"**

### 2. PR Template

When you create a PR, fill out this information:

```markdown
## Description

Brief description of what this PR does and why.

## Related Issue

Fixes #123
Closes #456
Related to #789

## Type of Change

- [ ] 🐛 Bug fix (non-breaking change which fixes an issue)
- [ ] ✨ New feature (non-breaking change which adds functionality)
- [ ] 💥 Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] 📝 Documentation update
- [ ] ♻️ Code refactoring
- [ ] ✅ Test additions or updates

## How Has This Been Tested?

Describe the tests you ran and how to reproduce them.

**Test Configuration:**
- OS: [e.g., macOS 14.0]
- Java version: [e.g., JDK 17]
- Kotlin version: [e.g., 1.9.21]

## Checklist

- [ ] I have signed the CLA
- [ ] My code follows the project's coding standards
- [ ] I have performed a self-review of my code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes
- [ ] Any dependent changes have been merged and published
- [ ] My commit messages follow the Conventional Commits format

## Screenshots (if applicable)

Add screenshots to help explain your changes.

## Additional Context

Add any other context about the PR here.
```

### 3. PR Review Process

**What happens after you submit:**

1. **Automated Checks (1-5 minutes):**
   - ✅ CLA signature verified
   - ✅ Tests must pass
   - ✅ Code style must pass (ktlint)
   - ✅ Build must succeed
   - ✅ No merge conflicts

2. **Code Review (1-7 days):**
   - At least one maintainer approval required
   - Reviewers may request changes
   - Address feedback by pushing new commits
   - Discussion happens in PR comments

3. **Approval & Merge:**
   - Once approved and all checks pass
   - A maintainer will merge your PR
   - Your contribution is now part of UTL-X! 🎉

**Response Times:**
- Initial response: Within 2-3 business days
- Simple PRs: Reviewed within 1 week
- Complex PRs: May take longer, we'll keep you updated
- If no response after 1 week, ping in the PR comments

### 4. After Your PR is Merged

**Congratulations! 🎉**

- Your contribution is live in the `main` branch
- You'll be listed in the contributors
- Close the issue if your PR fixed it
- Consider tackling another issue!

**Update your fork:**
```bash
git checkout main
git pull upstream main
git push origin main
```

---

## Coding Standards

### Kotlin Style Guide

Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).

**Key Guidelines:**

**Indentation:**
- Use **4 spaces** for indentation (no tabs)
- Continuation indent: 4 spaces

**Line Length:**
- Maximum **120 characters** per line
- Break long lines logically

**Naming:**
```kotlin
// Classes: PascalCase
class TransformEngine

// Functions: camelCase
fun parseInput()

// Properties: camelCase
val inputFormat: Format

// Constants: UPPER_SNAKE_CASE
const val MAX_RECURSION_DEPTH = 100

// Private properties: prefix with underscore (optional)
private val _cache = mutableMapOf<String, Any>()
```

**Code Organization:**
```kotlin
class MyClass {
    // 1. Companion object
    companion object {
        const val DEFAULT_VALUE = 42
    }
    
    // 2. Properties
    private val helper = Helper()
    var publicProperty: String = ""
    
    // 3. Init blocks
    init {
        // Initialization code
    }
    
    // 4. Public methods
    fun publicMethod() { }
    
    // 5. Private methods
    private fun privateMethod() { }
}
```

**Type Inference:**
```kotlin
// Prefer type inference where clear
val name = "UTL-X"  // ✅ Good
val name: String = "UTL-X"  // ❌ Unnecessary

// Use explicit types when not obvious
val result: TransformResult = complexOperation()  // ✅ Good
```

**Immutability:**
```kotlin
// Prefer val over var
val immutable = "Cannot change"  // ✅ Preferred
var mutable = "Can change"  // ⚠️ Use only when necessary
```

### Automated Formatting

**Before committing, always run:**

```bash
# Auto-fix formatting issues
./gradlew ktlintFormat

# Check for remaining issues
./gradlew ktlintCheck
```

**Configure your IDE:**
- IntelliJ IDEA: Use Kotlin plugin's default formatter
- Enable "Reformat code" on save (optional but helpful)

### Documentation Standards

**All public APIs must have KDoc comments:**

```kotlin
/**
 * Transforms input data from one format to another.
 *
 * This function takes a Universal Data Model (UDM) representation of input data
 * and converts it to the specified output format. The transformation is performed
 * according to the compiled transformation rules.
 *
 * @param input Universal Data Model representation of input data
 * @param outputFormat Target output format (JSON, XML, CSV, YAML, etc.)
 * @param options Optional transformation options
 * @return Serialized output in the target format
 * @throws UnsupportedFormatException if the output format is not supported
 * @throws TransformationException if the transformation fails
 *
 * @sample com.glomidco.utlx.samples.basicTransformation
 * @see UDM
 * @see Format
 * @since 0.1.0
 */
fun transform(
    input: UDM,
    outputFormat: Format,
    options: TransformOptions = TransformOptions()
): String {
    // Implementation
}
```

**Documentation Guidelines:**
- First sentence is a brief summary (shown in IDE hints)
- Describe what the function does, not how
- Document all parameters and return values
- List possible exceptions
- Include `@since` for new APIs
- Add usage examples when helpful

**Internal functions can have simpler docs:**
```kotlin
// Validates that the input node is not null
private fun validateNode(node: Node?) = 
    requireNotNull(node) { "Node cannot be null" }
```

### Testing Standards

**Test Coverage Goals:**
- New features: **80%+ coverage**
- Bug fixes: Include regression test
- Refactoring: Maintain or improve coverage

**Test Structure:**
```kotlin
class TransformEngineTest {
    
    @Test
    fun `should transform XML to JSON correctly`() {
        // Arrange (Given)
        val inputXml = """<root><item>value</item></root>"""
        val expectedJson = """{"root":{"item":"value"}}"""
        val engine = TransformEngine()
        
        // Act (When)
        val result = engine.transform(inputXml, Format.JSON)
        
        // Assert (Then)
        assertEquals(expectedJson, result)
    }
    
    @Test
    fun `should throw exception for invalid XML`() {
        // Arrange
        val invalidXml = "<root><unclosed>"
        val engine = TransformEngine()
        
        // Act & Assert
        val exception = assertThrows<ParseException> {
            engine.transform(invalidXml, Format.JSON)
        }
        
        assertTrue(exception.message!!.contains("unclosed tag"))
    }
    
    @ParameterizedTest
    @CsvSource(
        "XML, JSON",
        "JSON, XML",
        "CSV, JSON"
    )
    fun `should support multiple format combinations`(input: Format, output: Format) {
        // Test with different format combinations
    }
}
```

**Test Naming:**
- Use descriptive names with backticks
- Follow pattern: `should <expected behavior> when <condition>`
- Examples:
  - ✅ `should parse valid XML input`
  - ✅ `should throw exception for empty input`
  - ✅ `should return cached result on second call`
  - ❌ `test1`
  - ❌ `testParse`

**Test Organization:**
```
utlx-core/
└── src/
    ├── main/kotlin/com/glomidco/utlx/
    │   └── Parser.kt
    └── test/kotlin/com/glomidco/utlx/
        ├── ParserTest.kt          // Unit tests
        ├── ParserIntegrationTest.kt  // Integration tests
        └── fixtures/              // Test data
            └── sample.xml
```

---

## Types of Contributions

### 🐛 Bug Reports

Found a bug? Help us fix it!

**Before reporting:**
- Search existing issues to avoid duplicates
- Try to reproduce with the latest version
- Check if it's already fixed in main branch

**What to include:**
```markdown
**Bug Description**
Clear description of what's wrong.

**Steps to Reproduce**
1. Step one
2. Step two
3. Step three

**Expected Behavior**
What you expected to happen.

**Actual Behavior**
What actually happened.

**Environment**
- UTL-X version: [e.g., 0.1.0]
- OS: [e.g., macOS 14.0, Ubuntu 22.04, Windows 11]
- Java version: [e.g., OpenJDK 17.0.2]
- Kotlin version: [e.g., 1.9.21]

**Sample Code**
```kotlin
// Code that reproduces the bug
```

**Error Messages**
```
Stack trace or error output
```

**Additional Context**
Any other relevant information.
```

**Open a bug report:** https://github.com/grauwen/utl-x/issues/new?template=bug_report.md

### ✨ Feature Requests

Have an idea for a new feature?

**Start a discussion first:**
1. Go to [Discussions](https://github.com/grauwen/utl-x/discussions)
2. Create a new discussion in "Ideas" category
3. Describe:
   - The problem you're trying to solve
   - Your proposed solution
   - Why it benefits the community
   - Any alternative solutions considered

**After discussion, create a feature request issue**

### 📝 Documentation

Documentation improvements **don't require CLA** for small changes (<10 lines).

**Ways to help:**
- Fix typos or grammar
- Improve clarity and readability
- Add missing documentation
- Create tutorials or guides
- Add code examples
- Translate documentation
- Update outdated information

**Documentation types:**
- Code comments (KDoc)
- README.md
- User guides
- API reference
- Architecture docs
- Contributing guidelines

### 🧪 Tests

Help us improve test coverage!

**Areas needing tests:**
- Edge cases
- Error handling
- Integration tests
- Performance tests
- End-to-end tests

**To find areas needing tests:**
```bash
# Generate coverage report
./gradlew test jacocoTestReport

# Open report
open build/reports/jacoco/test/html/index.html
```

### 💻 Code Contributions

**Good first issues:**
- Look for `good-first-issue` label
- Look for `help-wanted` label
- Check [Project Board](https://github.com/grauwen/utl-x/projects) for planned work

**Areas needing help:**
- Parser improvements
- Standard library functions
- Format support (CSV, YAML, etc.)
- Performance optimizations
- Error messages and diagnostics
- CLI improvements
- IDE integration

### 🌍 Translations

Help make UTL-X accessible worldwide:
- Translate documentation
- Translate error messages
- Translate website
- Create localized examples

---

## Community Guidelines

### Code of Conduct

We follow the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).

**Summary:**
- ✅ Be respectful and inclusive
- ✅ Welcome newcomers
- ✅ Be patient with questions
- ✅ Give constructive feedback
- ✅ Respect different viewpoints
- ✅ Accept criticism gracefully
- ❌ No harassment or discrimination
- ❌ No trolling or inflammatory comments
- ❌ No personal attacks

**Violations:**
Report to community@glomidco.com. All reports will be reviewed confidentially.

### Communication Channels

**GitHub:**
- 💬 [Discussions](https://github.com/grauwen/utl-x/discussions) - Questions, ideas, general chat
- 🐛 [Issues](https://github.com/grauwen/utl-x/issues) - Bug reports, feature requests
- 📝 [Pull Requests](https://github.com/grauwen/utl-x/pulls) - Code contributions

**Email:**
- community@glomidco.com - General questions
- dev@glomidco.com - Technical discussions
- cla@glomidco.com - CLA questions

**Social Media:**
- 🐦 [@UTLXLang](https://twitter.com/UTLXLang) - Updates and announcements
- 💼 [LinkedIn](https://linkedin.com/company/utlx) - Professional updates

### Asking Questions

**Good places to ask:**
1. **GitHub Discussions** (preferred for most questions)
2. **Issue comments** (for questions about specific issues)
3. **Email** (for private or complex questions)

**How to ask good questions:**
- Search first to see if it's already answered
- Be specific and provide context
- Include code examples when relevant
- Show what you've already tried
- Be patient and respectful

**Example of a good question:**
> I'm trying to transform XML to JSON using UTL-X, but I'm getting a ParseException with the message "unexpected token". Here's my code:
> 
> ```kotlin
> val xml = "<root><item>value</item></root>"
> val result = engine.transform(xml, Format.JSON)
> ```
>
> I'm using UTL-X 0.1.0 on Java 17. What am I doing wrong?

---

## Getting Help

### Stuck on Something?

**Resources:**
1. 📚 **Documentation** - Check [docs/](docs/) folder
2. 💬 **Discussions** - Ask in [GitHub Discussions](https://github.com/grauwen/utl-x/discussions)
3. 📧 **Email** - dev@glomidco.com for technical questions
4. 🔍 **Search** - Search issues and PRs for similar problems

### Development Issues

**Build problems:**
```bash
# Clean and rebuild
./gradlew clean build

# Check Java version
java -version  # Should be 11+

# Check Gradle version
./gradlew --version
```

**Test failures:**
```bash
# Run tests with verbose output
./gradlew test --info

# Run specific test
./gradlew test --tests "ClassName.testMethod"

# Debug tests in IntelliJ
# Right-click test → Debug
```

**Git problems:**
```bash
# Undo uncommitted changes
git checkout .

# Reset to upstream main
git fetch upstream
git reset --hard upstream/main

# Fix merge conflicts
git mergetool
```

---

## Recognition

### Contributors

All contributors are recognized:
- 👥 [GitHub contributors page](https://github.com/grauwen/utl-x/graphs/contributors)
- 📝 Release notes
- 🌐 Project website (coming soon)
- 🏆 Special recognition for significant contributions

### Becoming a Maintainer

Active contributors may be invited to become maintainers with:
- ✅ Commit access to repository
- ✅ Issue triage permissions
- ✅ PR review authority
- ✅ Voice in project direction
- ✅ Listed as core team member

**Typical path:**
1. Make several quality contributions
2. Show good judgment in reviews
3. Help other contributors
4. Demonstrate commitment to project
5. Invitation from project lead

---

## License

By contributing to UTL-X, you agree that your contributions will be licensed under both:
- **AGPL-3.0** (open source), and
- **Glomidco B.V.'s commercial license terms**

as specified in the [Contributor License Agreement](CLA.md).

**Important:** Your contributions will always be available as open source under AGPL-3.0. The dual licensing allows Glomidco B.V. to offer commercial licenses to companies that cannot comply with AGPL requirements, which helps fund continued development.

---

## Questions?

**General:** community@glomidco.com  
**Technical:** dev@glomidco.com  
**CLA issues:** cla@glomidco.com  
**Commercial:** licensing@glomidco.com

---

## Thank You! 🎉

Every contribution, no matter how small, helps make UTL-X better for everyone.

Thank you for being part of the UTL-X community!

---

**Glomidco B.V.**  
Website: https://glomidco.com  
UTL-X Project: https://utl-x.com  
GitHub: https://github.com/grauwen/utl-x
