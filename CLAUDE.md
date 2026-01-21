# Claude AI Instructions for Barn

> **FIRST STEP - MANDATORY**: Before writing ANY code, read these files:
> 1. `README.md` - Project overview
> 2. `docs/coding-standards.md` - Code style and conventions
> 3. `docs/jobs.md` - Filesystem state layout
> 4. `docs/config.md` - Configuration options

## Critical Requirements

### 1. ALWAYS Write Tests First

**Every code change MUST include automated tests.** No exceptions.

```
Code without tests = Incomplete work
```

Before implementing any feature or fix:
1. Write failing tests that define the expected behavior
2. Implement the code to make tests pass
3. Run `mvn verify` to ensure all checks pass
4. Only then consider the work complete

### 2. ALWAYS Validate with Tests

After writing code, ALWAYS run:

```bash
mvn verify
```

This runs:
- Compilation with `-Werror` (warnings are errors)
- Checkstyle (code formatting)
- Unit tests
- SpotBugs (bug detection)
- PMD (static analysis)
- JaCoCo (coverage check - must be 80%+)

**Do NOT consider code complete until `mvn verify` passes.**

### 3. ALWAYS Follow Coding Standards

Read `docs/coding-standards.md` before writing code. Key rules:
- 4 spaces indentation, 120 char line limit
- No wildcard imports
- Use `Optional<T>` instead of returning `null`
- Use `Objects.requireNonNull()` for constructor params
- Use `final` for fields that don't change

---

## Project Overview

Barn is a cross-platform job daemon for managing long-running media processing tasks (FFmpeg, WebDAV transfers). It runs as an OS service on Windows, macOS, and Linux.

## Technology Stack

- **Language**: Java 21+
- **Build**: Maven
- **Native Image**: GraalVM Native Image
- **Code Style**: Checkstyle (Google Java Style)
- **Static Analysis**: SpotBugs, PMD
- **Testing**: JUnit 5, Mockito, AssertJ
- **Coverage**: JaCoCo (80% minimum)

---

## Testing Requirements (MANDATORY)

### Test Coverage Requirements

| Type | Minimum | Target |
|------|---------|--------|
| Line coverage | 80% | 90%+ |
| Branch coverage | 70% | 80%+ |
| New code | 100% | 100% |

### Test Naming Convention

```java
// Pattern: methodName_condition_expectedResult
@Test
void createJob_withValidCommand_shouldReturnJobWithUniqueId() { }

@Test
void createJob_withNullCommand_shouldThrowNullPointerException() { }

@Test
void processJob_whenServiceNotRunning_shouldThrowIllegalStateException() { }
```

### Test Structure (AAA Pattern)

```java
@Test
void findJob_withExistingId_shouldReturnJob() {
    // Arrange
    var manager = new JobManager(testConfig);
    var job = manager.createJob(new Command("echo", "test"));

    // Act
    var result = manager.findJob(job.getId());

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(job.getId());
}
```

### What to Test

For EVERY class you create or modify:

1. **Happy path** - Normal successful operation
2. **Edge cases** - Empty inputs, boundary values
3. **Error cases** - Null inputs, invalid states
4. **Integration** - Component interactions

### Test File Location

```
src/
  main/java/com/samsonmedia/barn/
    jobs/
      JobManager.java
  test/java/com/samsonmedia/barn/
    jobs/
      JobManagerTest.java        # Unit tests
      JobManagerIT.java          # Integration tests
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=JobManagerTest

# Run with coverage report
mvn test jacoco:report
# View: target/site/jacoco/index.html

# Run full verification (ALWAYS do this before committing)
mvn verify
```

---

## Workflow for Every Code Change

### Step 1: Understand the Requirement
- Read relevant docs
- Understand existing code patterns

### Step 2: Write Tests FIRST
```java
// Write test that defines expected behavior
@Test
void newFeature_shouldDoExpectedThing() {
    // Arrange
    var component = new Component();

    // Act
    var result = component.newFeature();

    // Assert
    assertThat(result).isEqualTo(expected);
}
```

### Step 3: Run Tests (Should Fail)
```bash
mvn test -Dtest=ComponentTest#newFeature_shouldDoExpectedThing
# Expected: FAILURE (test written, code not yet implemented)
```

### Step 4: Implement the Code
Write minimal code to make the test pass.

### Step 5: Run Tests (Should Pass)
```bash
mvn test -Dtest=ComponentTest#newFeature_shouldDoExpectedThing
# Expected: SUCCESS
```

### Step 6: Run Full Verification
```bash
mvn verify
# Must pass ALL checks:
# - Checkstyle
# - Compilation
# - All tests
# - SpotBugs
# - PMD
# - Coverage thresholds
```

### Step 7: Only THEN Consider Complete
If `mvn verify` fails, fix the issues and repeat.

---

## Code Standards Summary

### Formatting
- 4 spaces indentation (no tabs)
- 120 character line limit
- K&R brace style (opening brace on same line)
- Static imports first, then regular imports, alphabetically

### Naming
- Packages: `lowercase`
- Classes/Interfaces: `PascalCase`
- Methods/Variables: `camelCase`
- Constants: `SCREAMING_SNAKE_CASE`

### Java Idioms
```java
// Use final for immutable fields
private final Config config;

// Use Optional for nullable returns
public Optional<Job> findJob(String id) {
    return Optional.ofNullable(jobs.get(id));
}

// Validate constructor params
public JobManager(Config config) {
    this.config = Objects.requireNonNull(config, "config must not be null");
}

// Use records for value objects
public record Job(String id, JobState state, Instant createdAt) {}

// Use switch expressions
String status = switch (state) {
    case QUEUED -> "Waiting";
    case RUNNING -> "Executing";
    case FAILED -> "Failed";
    case SUCCEEDED -> "Done";
};
```

### Error Handling
```java
// Use Optional, not null
public Optional<Job> findJob(String id);

// Validate preconditions
public void processJob(Job job) {
    Objects.requireNonNull(job, "job");
    if (job.getState() != JobState.QUEUED) {
        throw new IllegalStateException("Job must be QUEUED");
    }
}

// Create domain exceptions
public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String jobId) {
        super("Job not found: " + jobId);
    }
}
```

---

## Architecture Principles

### Filesystem as Database
- Job state stored in `/tmp/barn/jobs/<job-id>/`
- Use atomic file operations
- Human-readable plain text files
- No external database dependencies

### Cross-Platform
- Use `System.getProperty("java.io.tmpdir")` for temp paths
- Avoid platform-specific commands
- Test on Windows, macOS, and Linux

### GraalVM Native Image
- Avoid reflection where possible
- Register any reflection in `reflect-config.json`

---

## File Structure

```
barn/
  src/
    main/java/com/samsonmedia/barn/
      Main.java            # Entry point
      cli/                 # CLI commands (picocli)
      config/              # Configuration parsing
      jobs/                # Job management
      service/             # Daemon/service logic
      state/               # Filesystem state management
    test/java/com/samsonmedia/barn/
      # Mirror structure with Test suffix
  config/
    checkstyle/            # Checkstyle rules
    spotbugs/              # SpotBugs exclusions
    pmd/                   # PMD ruleset
  docs/                    # Documentation
  pom.xml                  # Maven build
```

---

## Commit Checklist

Before EVERY commit:

- [ ] Tests written for new/changed code
- [ ] `mvn verify` passes
- [ ] No `TODO` or `FIXME` without tracking issue
- [ ] Javadoc for public API
- [ ] Commit message follows Conventional Commits format

```bash
# Commit message format
feat(jobs): add retry mechanism for failed jobs
fix(cli): handle spaces in file paths
test(jobs): add integration tests for job lifecycle
```

---

## What NOT to Do

- **Don't skip tests** - Every change needs tests
- **Don't commit without `mvn verify`** - Ensures all checks pass
- **Don't return `null`** - Use `Optional<T>` instead
- **Don't catch `Exception`** - Catch specific exceptions
- **Don't use `System.out.println`** - Use SLF4J logging
- **Don't use wildcard imports** - Import specific classes
- **Don't use raw types** - Always specify generics
- **Don't ignore warnings** - Fix them (they're errors with `-Werror`)

---

## Quick Reference

```bash
# Build
mvn package

# Test
mvn test

# Full verification (ALWAYS before commit)
mvn verify

# Coverage report
mvn test jacoco:report

# Fast build (skip checks - use sparingly)
mvn package -Pfast

# Specific test
mvn test -Dtest=JobManagerTest

# View SpotBugs in GUI
mvn compile spotbugs:gui
```

---

## Questions to Ask Before Implementing

1. Have I read the relevant documentation?
2. Have I written tests that define the expected behavior?
3. Does this work on Windows, macOS, AND Linux?
4. Will this survive a crash/restart?
5. Can the state be manually inspected in the filesystem?
6. Does `mvn verify` pass?
