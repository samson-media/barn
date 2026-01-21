# Development Guide

This document covers local development setup and building Barn from source.

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | 21+ | Required for building |
| GraalVM CE | 21+ | Required for native image builds |
| Gradle | 8.x | Wrapper included in project |
| Git | Any recent | For version control |

### Installing GraalVM

**macOS (Homebrew):**

```bash
brew install --cask graalvm-jdk
```

**Linux:**

```bash
# Download from https://www.graalvm.org/downloads/
# Extract and set GRAALVM_HOME
export GRAALVM_HOME=/path/to/graalvm
export PATH=$GRAALVM_HOME/bin:$PATH
```

**Windows:**

Download from [GraalVM Downloads](https://www.graalvm.org/downloads/) and add to PATH.

---

## Project Structure

```
barn/
  src/
    main/
      kotlin/           # Application source code
      resources/        # Configuration and resource files
    test/
      kotlin/           # Unit and integration tests
  build.gradle.kts      # Build configuration
  gradle.properties     # Version and build properties
  settings.gradle.kts   # Project settings
  docs/                 # Documentation
```

---

## Building

### Build the JAR

```bash
./gradlew build
```

This compiles the source, runs tests, and produces a JAR in `build/libs/`.

### Run Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.samsonmedia.barn.JobManagerTest"

# Run with verbose output
./gradlew test --info
```

### Build Native Image

Building a native executable requires GraalVM with Native Image support:

```bash
# Ensure GraalVM is active
java -version  # Should show GraalVM

# Build native image
./gradlew nativeCompile
```

The native binary is output to `build/native/nativeCompile/barn`.

### Clean Build

```bash
./gradlew clean build
```

---

## Running Locally

### From JAR

```bash
java -jar build/libs/barn.jar --help
```

### From Native Binary

```bash
./build/native/nativeCompile/barn --help
```

### Development Mode

Use the `--offline` flag to test commands without running the service:

```bash
./gradlew run --args="run --offline echo hello"
```

---

## IDE Setup

### IntelliJ IDEA (Recommended)

1. Open IntelliJ IDEA
2. Select **File > Open**
3. Navigate to the `barn` directory and open it
4. IntelliJ will detect the Gradle project and import it
5. Wait for Gradle sync to complete

**Recommended plugins:**

- Kotlin
- Gradle

**Run configurations:**

- Create a Kotlin Application configuration
- Main class: `com.samsonmedia.barn.MainKt`
- Program arguments: `--help` (or any command)

### VS Code

1. Install the Kotlin and Gradle extensions
2. Open the `barn` folder
3. The Gradle extension will detect the project

---

## Common Tasks

### Format Code

```bash
./gradlew ktlintFormat
```

### Check Code Style

```bash
./gradlew ktlintCheck
```

### Generate Documentation

```bash
./gradlew dokkaHtml
```

Output is in `build/dokka/html/`.

### Dependency Updates

Check for outdated dependencies:

```bash
./gradlew dependencyUpdates
```

---

## Troubleshooting

### GraalVM Not Found

Ensure `GRAALVM_HOME` is set and GraalVM is on your PATH:

```bash
export GRAALVM_HOME=/path/to/graalvm
export PATH=$GRAALVM_HOME/bin:$PATH
```

### Native Image Build Fails

Common causes:

1. **Reflection configuration missing**: Add classes to `reflect-config.json`
2. **Resource not included**: Add to `resource-config.json`
3. **Insufficient memory**: Increase heap with `-J-Xmx8g`

```bash
./gradlew nativeCompile -Pnative.heap=8g
```

### Tests Fail on Windows

Ensure line endings are consistent:

```bash
git config core.autocrlf input
```

### Gradle Wrapper Issues

Regenerate the wrapper:

```bash
gradle wrapper --gradle-version 8.5
```

### Out of Memory During Build

Increase Gradle daemon memory in `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4g
```

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `GRAALVM_HOME` | Path to GraalVM installation |
| `JAVA_HOME` | Path to JDK (can point to GraalVM) |
| `BARN_LOG_LEVEL` | Override log level during development |

---

## Next Steps

- [Releasing](releasing.md) - How to create releases
- [CI/CD](ci-cd.md) - Automated build and release workflows
