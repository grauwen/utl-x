In Kotlin, the best way to handle uniform file paths across platforms is to use the **`java.nio.file.Path`** API or **`java.io.File`** class, which automatically handle platform-specific separators:

## Recommended Approach: Use Path API

```kotlin
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

// Creating paths - automatically uses correct separator
val path = Path("data", "files", "document.txt")
// or
val path2 = Paths.get("data", "files", "document.txt")

// Reading
val content = path.readText()

// Writing
path.writeText("Hello, World!")

// Building paths programmatically
val baseDir = Path("project")
val filePath = baseDir / "src" / "main" / "resources" / "config.json"
```

## Alternative: Using File

```kotlin
import java.io.File

// Automatically uses correct separator
val file = File("data", "files", "document.txt")
// or
val file2 = File("data" + File.separator + "files" + File.separator + "document.txt")

// Reading
val content = file.readText()

// Writing
file.writeText("Hello, World!")
```

## Key Points

**Don’t hardcode separators** - Avoid using `"data/files/file.txt"` or `"data\\files\\file.txt"` directly. Instead:

- Use the constructor with multiple path segments: `File("data", "files", "file.txt")`
- Use `File.separator` constant if you need to build strings
- Use the `/` operator with Path for cleaner syntax

**Path normalization** - Convert paths to absolute or normalize them:

```kotlin
val normalized = path.normalize()
val absolute = path.toAbsolutePath()
```

**Cross-platform best practice**:

```kotlin
import kotlin.io.path.*

fun getConfigFile(): Path {
    return Path(System.getProperty("user.home"))
        .resolve(".myapp")
        .resolve("config.json")
}
```

The Path API is more modern and idiomatic for Kotlin, while File is still widely used and perfectly acceptable. Both handle Windows (`\`), Linux/Mac (`/`) separators transparently.​​​​​​​​​​​​​​​​
