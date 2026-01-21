# Development Guide

This document covers local development setup and building Barn from source.

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | 21+ | Required for building |
| Maven | 3.9+ | Build tool |
| GraalVM CE | 21+ | Required for native image builds (optional) |
| Git | Any recent | For version control |

### Installing JDK 21

**macOS (Homebrew):**

```bash
brew install openjdk@21
```

**Linux (SDKMAN):**

```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 21-tem
```

**Windows:**

Download from [Adoptium](https://adoptium.net/) and add to PATH.

### Installing Maven

**macOS (Homebrew):**

```bash
brew install maven
```

**Linux:**

```bash
sudo apt install maven  # Debian/Ubuntu
sudo dnf install maven  # Fedora
```

**Windows:**

Download from [Apache Maven](https://maven.apache.org/download.cgi) and add to PATH.

### Installing GraalVM (Optional)

GraalVM is required for building native images.

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
      java/                # Application source code
      resources/           # Configuration and resource files
    test/
      java/                # Unit and integration tests
  config/
    checkstyle/            # Checkstyle configuration
    spotbugs/              # SpotBugs exclusions
    pmd/                   # PMD ruleset
  docs/                    # Documentation
  .githooks/               # Git hooks
  .github/workflows/       # CI/CD workflows
  pom.xml                  # Maven build configuration
```

---

## Building

### Build the JAR

```bash
mvn package
```

This compiles the source, runs tests, and produces a JAR in `target/`.

### Skip Tests

```bash
mvn package -DskipTests
```

### Build with All Quality Checks

```bash
mvn verify
```

### Build without Quality Checks (Fast)

```bash
mvn package -Pfast
```

### Clean Build

```bash
mvn clean package
```

---

## Running Tests

### Run All Tests

```bash
mvn test
```

### Run Specific Test Class

```bash
mvn test -Dtest=JobManagerTest
```

### Run Specific Test Method

```bash
mvn test -Dtest=JobManagerTest#createJob_shouldGenerateUniqueId
```

### Run with Verbose Output

```bash
mvn test -X
```

### Run Integration Tests

```bash
mvn verify
```

### Generate Coverage Report

```bash
mvn test jacoco:report
# Open target/site/jacoco/index.html
```

---

## Code Quality

### Run Checkstyle

```bash
mvn checkstyle:check
```

### Run SpotBugs

```bash
mvn compile spotbugs:check

# View in GUI
mvn compile spotbugs:gui
```

### Run PMD

```bash
mvn compile pmd:check
```

### Run All Quality Checks

```bash
mvn verify
```

### Skip Quality Checks

```bash
mvn package -Pfast
# Or individually:
mvn package -Dcheckstyle.skip=true
mvn package -Dspotbugs.skip=true
mvn package -Dpmd.skip=true
```

---

## Native Image Build

Building a native executable requires GraalVM:

```bash
# Ensure GraalVM is active
java -version  # Should show GraalVM

# Build native image
mvn package -Pnative

# The binary is output to target/barn
```

---

## Running Locally

### From JAR

```bash
java -jar target/barn-0.1.0-SNAPSHOT.jar --help
```

### From Native Binary

```bash
./target/barn --help
```

### Development Mode

Use the `--offline` flag to test commands without running the service:

```bash
java -jar target/barn-0.1.0-SNAPSHOT.jar run --offline echo hello
```

---

## IDE Setup

### IntelliJ IDEA (Recommended)

1. Open IntelliJ IDEA
2. Select **File > Open**
3. Navigate to the `barn` directory and open it
4. IntelliJ will detect the Maven project and import it
5. Wait for indexing to complete

**Install Plugins:**

- CheckStyle-IDEA
- SpotBugs

**Import Code Style:**

1. Settings → Editor → Code Style → Java
2. Click gear icon → Import Scheme → CheckStyle Configuration
3. Select `config/checkstyle/checkstyle.xml`

**Run Configuration:**

- Create Application configuration
- Main class: `com.samsonmedia.barn.Main`
- Program arguments: `--help` (or any command)

### VS Code

1. Install extensions: Java Extension Pack, Checkstyle for Java
2. Open the `barn` folder
3. The Java extension will detect the Maven project

**Configure Checkstyle:**

Settings → Checkstyle: Configuration → set to `config/checkstyle/checkstyle.xml`

### Eclipse

1. Import as Maven project
2. Install Checkstyle plugin from Marketplace
3. Configure to use `config/checkstyle/checkstyle.xml`

---

## Git Hooks

Install the pre-commit hook to run checks before each commit:

```bash
git config core.hooksPath .githooks
```

The hook runs:
- Checkstyle
- Compilation check
- SpotBugs
- PMD

To bypass (use sparingly):

```bash
git commit --no-verify
```

---

## Common Tasks

### Format Code

IntelliJ IDEA: **Code → Reformat Code** (Ctrl+Alt+L / Cmd+Alt+L)

VS Code: Right-click → Format Document

### Update Dependencies

```bash
mvn versions:display-dependency-updates
mvn versions:display-plugin-updates
```

### Generate Project Reports

```bash
mvn site
# Open target/site/index.html
```

---

## Troubleshooting

### Compilation Fails with Lint Errors

The project uses `-Werror` to treat warnings as errors. Fix all warnings:

```bash
# See detailed warnings
mvn compile -X
```

### Tests Fail on Windows

Ensure line endings are consistent:

```bash
git config core.autocrlf input
```

### Checkstyle Violations

View details:

```bash
mvn checkstyle:checkstyle
# Open target/site/checkstyle.html
```

### SpotBugs Issues

View in GUI:

```bash
mvn compile spotbugs:gui
```

### Out of Memory During Build

Increase Maven heap:

```bash
export MAVEN_OPTS="-Xmx2g"
mvn package
```

### Native Image Build Fails

Common causes:

1. **Reflection configuration missing**: Add to `src/main/resources/META-INF/native-image/reflect-config.json`
2. **Resource not included**: Add to `resource-config.json`
3. **Insufficient memory**: Increase heap with `-J-Xmx8g`

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `JAVA_HOME` | Path to JDK installation |
| `GRAALVM_HOME` | Path to GraalVM installation |
| `MAVEN_OPTS` | JVM options for Maven |
| `BARN_LOG_LEVEL` | Override log level during development |

---

## Useful Maven Commands

```bash
# Clean everything
mvn clean

# Compile only
mvn compile

# Run tests
mvn test

# Package JAR
mvn package

# Install to local repo
mvn install

# Full verification
mvn verify

# Generate site/reports
mvn site

# Dependency tree
mvn dependency:tree

# Effective POM
mvn help:effective-pom
```

---

## Next Steps

- [Coding Standards](coding-standards.md) - Code style and conventions
- [Releasing](releasing.md) - How to create releases
- [CI/CD](ci-cd.md) - Automated build and release workflows
