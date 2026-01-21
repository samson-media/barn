# Coding Standards

This document defines the coding standards and conventions for the Barn project.

---

## Overview

Barn is written in **Java 21+** and follows industry best practices for code quality, maintainability, and consistency. We use automated tools to enforce these standards.

### Enforcement Tools

| Tool | Purpose | Configuration |
|------|---------|---------------|
| [Checkstyle](https://checkstyle.org/) | Code formatting & style | `config/checkstyle/checkstyle.xml` |
| [SpotBugs](https://spotbugs.github.io/) | Bug detection | `config/spotbugs/exclude.xml` |
| [PMD](https://pmd.github.io/) | Static analysis | `config/pmd/ruleset.xml` |
| [JaCoCo](https://www.jacoco.org/) | Code coverage | Maven plugin |
| Pre-commit hooks | Local enforcement | `.githooks/pre-commit` |
| GitHub Actions | CI enforcement | `.github/workflows/lint.yml` |

---

## Code Style

We follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) with the following specifics:

### Formatting

- **Indentation**: 4 spaces (no tabs)
- **Max line length**: 120 characters
- **Braces**: K&R style (opening brace on same line)
- **Blank lines**: Single blank line between methods, two before class members
- **Import ordering**: Static imports first, then regular imports, alphabetically

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Packages | lowercase, no underscores | `com.samsonmedia.barn.jobs` |
| Classes | PascalCase | `JobManager`, `ConfigParser` |
| Interfaces | PascalCase | `StateManager`, `Executable` |
| Methods | camelCase | `createJob()`, `parseConfig()` |
| Variables | camelCase | `jobId`, `maxRetries` |
| Constants | SCREAMING_SNAKE_CASE | `MAX_RETRIES`, `DEFAULT_TIMEOUT` |
| Type parameters | Single uppercase letter | `T`, `K`, `V` |

### File Organization

```java
// 1. Package statement
package com.samsonmedia.barn.jobs;

// 2. Imports (static first, then regular, alphabetically)
import static java.util.Objects.requireNonNull;

import com.samsonmedia.barn.config.Config;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Manages job lifecycle and state.
 *
 * <p>Jobs are persisted to the filesystem and can survive service restarts.
 * See docs/jobs.md for the filesystem layout.
 */
public class JobManager {

    // 3. Constants
    private static final String JOB_ID_PREFIX = "job-";

    // 4. Static fields
    private static final Logger LOG = LoggerFactory.getLogger(JobManager.class);

    // 5. Instance fields
    private final Config config;
    private final StateManager stateManager;
    private final Map<String, Job> jobs;

    // 6. Constructors
    public JobManager(Config config, StateManager stateManager) {
        this.config = requireNonNull(config, "config");
        this.stateManager = requireNonNull(stateManager, "stateManager");
        this.jobs = new ConcurrentHashMap<>();
    }

    // 7. Public methods
    public Job createJob(Command command) {
        // ...
    }

    // 8. Private methods
    private String generateJobId() {
        // ...
    }

    // 9. Nested classes
    private static class JobIdGenerator {
        // ...
    }
}
```

---

## Java Best Practices

### Null Safety

```java
// Good: Use Optional for return types that may be absent
public Optional<Job> findJob(String id) {
    return Optional.ofNullable(jobs.get(id));
}

// Good: Use requireNonNull for constructor parameters
public JobManager(Config config) {
    this.config = Objects.requireNonNull(config, "config must not be null");
}

// Good: Use @Nullable/@NonNull annotations
public void setTag(@Nullable String tag) {
    this.tag = tag;
}

// Avoid: Returning null from methods
public Job findJob(String id) {
    return jobs.get(id);  // Bad - returns null if not found
}
```

### Immutability

```java
// Good: Use final fields
private final String id;
private final JobState state;

// Good: Use unmodifiable collections
public List<Job> getJobs() {
    return Collections.unmodifiableList(jobs);
}

// Good: Use records for value objects (Java 16+)
public record Job(
    String id,
    JobState state,
    Instant createdAt
) {}

// Good: Use builder pattern for complex objects
Job job = Job.builder()
    .id(generateId())
    .state(JobState.QUEUED)
    .createdAt(Instant.now())
    .build();
```

### Modern Java Features (21+)

```java
// Good: Use var for local variables with obvious types
var jobs = new ArrayList<Job>();
var config = loadConfig();

// Good: Use switch expressions
String status = switch (state) {
    case QUEUED -> "Waiting to run";
    case RUNNING -> "Currently executing";
    case FAILED -> "Failed: " + errorMessage;
    case SUCCEEDED -> "Completed successfully";
    case CANCELED -> "Canceled by user";
};

// Good: Use pattern matching
if (obj instanceof Job job) {
    processJob(job);
}

// Good: Use text blocks for multi-line strings
String json = """
    {
        "jobId": "%s",
        "state": "%s"
    }
    """.formatted(jobId, state);

// Good: Use sealed classes for state machines
public sealed interface JobState
    permits Queued, Running, Failed, Succeeded, Canceled {
}
```

### Resource Management

```java
// Good: Use try-with-resources
try (var reader = Files.newBufferedReader(path)) {
    return reader.lines().toList();
}

// Good: Use try-with-resources for multiple resources
try (var in = Files.newInputStream(source);
     var out = Files.newOutputStream(target)) {
    in.transferTo(out);
}
```

---

## Error Handling

### Use Exceptions Appropriately

```java
// Good: Use checked exceptions for recoverable errors
public Config loadConfig(Path path) throws ConfigurationException {
    try {
        return parseConfig(Files.readString(path));
    } catch (IOException e) {
        throw new ConfigurationException("Failed to load config: " + path, e);
    }
}

// Good: Use unchecked exceptions for programming errors
public void processJob(Job job) {
    if (job == null) {
        throw new IllegalArgumentException("job must not be null");
    }
    if (job.getState() != JobState.QUEUED) {
        throw new IllegalStateException("Job must be in QUEUED state");
    }
}

// Good: Create domain-specific exceptions
public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String jobId) {
        super("Job not found: " + jobId);
    }
}
```

### Logging

```java
// Use SLF4J
private static final Logger LOG = LoggerFactory.getLogger(JobManager.class);

// Good: Use appropriate log levels
LOG.debug("Processing job: {}", jobId);      // Development details
LOG.info("Job completed: {}", jobId);        // Normal operations
LOG.warn("Retry attempt {} for job: {}", attempt, jobId);  // Potential issues
LOG.error("Job failed", exception);           // Errors

// Good: Use parameterized logging (not string concatenation)
LOG.info("Job {} state changed from {} to {}", jobId, oldState, newState);

// Bad: String concatenation (evaluates even if level disabled)
LOG.debug("Job " + jobId + " processing");  // Avoid
```

---

## Testing Standards

### Test Naming

```java
// Use descriptive method names with underscores
@Test
void createJob_shouldGenerateUniqueId() { }

@Test
void createJob_whenServiceNotRunning_shouldThrowException() { }

@Test
void processJob_withMaxRetriesExceeded_shouldMarkAsFailed() { }
```

### Test Structure (AAA Pattern)

```java
@Test
void completeJob_shouldTransitionToSucceeded() {
    // Arrange
    var job = createTestJob(JobState.RUNNING);
    var manager = new JobManager(testConfig);

    // Act
    manager.completeJob(job.getId(), 0);

    // Assert
    var updated = manager.getJob(job.getId());
    assertThat(updated).isPresent();
    assertThat(updated.get().getState()).isEqualTo(JobState.SUCCEEDED);
    assertThat(updated.get().getFinishedAt()).isNotNull();
}
```

### Test Coverage Requirements

| Type | Minimum Coverage |
|------|-----------------|
| Line coverage | 80% |
| Branch coverage | 70% |
| Critical paths | 100% |

---

## Documentation

### Javadoc Comments

```java
/**
 * Manages job lifecycle including creation, execution, and cleanup.
 *
 * <p>Jobs are persisted to the filesystem and can survive service restarts.
 * See <a href="docs/jobs.md">jobs.md</a> for the filesystem layout.
 *
 * <p>This class is thread-safe.
 *
 * @see Job
 * @see StateManager
 */
public class JobManager {

    /**
     * Creates a new job for the given command.
     *
     * <p>The job is assigned a unique ID and placed in the {@link JobState#QUEUED}
     * state. It will be picked up by the scheduler based on available capacity.
     *
     * @param command the command to execute, must not be null
     * @param tag optional tag for filtering jobs, may be null
     * @return the created job, never null
     * @throws IllegalStateException if the service is not running
     * @throws NullPointerException if command is null
     */
    public Job createJob(Command command, @Nullable String tag) {
        // ...
    }
}
```

### When to Document

- **Always**: Public API, interfaces, complex algorithms
- **Sometimes**: Private methods with complex logic
- **Never**: Self-explanatory code, obvious getters/setters

```java
// Good: Document non-obvious behavior
/**
 * Calculates retry delay using exponential backoff.
 * The delay doubles with each attempt, capped at {@code maxDelay}.
 *
 * @param attempt the current attempt number (0-based)
 * @return the delay before the next retry
 */
Duration calculateRetryDelay(int attempt);

// Bad: Unnecessary documentation
/** Returns the job ID. */
String getJobId();  // Obvious - no doc needed
```

---

## Git Conventions

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style (formatting, no logic change)
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `perf`: Performance improvement
- `test`: Adding or fixing tests
- `chore`: Build process, dependencies, tooling

Examples:
```
feat(jobs): add retry mechanism for failed jobs

fix(cli): handle spaces in file paths correctly

docs(readme): update installation instructions

refactor(config): extract parsing logic to separate class

test(jobs): add integration tests for job lifecycle
```

### Branch Naming

```
<type>/<description>

feature/add-retry-mechanism
fix/handle-spaces-in-paths
docs/update-installation
refactor/extract-config-parser
```

---

## Running Quality Checks

### Local Development

```bash
# Format code (via IDE or Checkstyle)
mvn checkstyle:check

# Run static analysis
mvn spotbugs:check
mvn pmd:check

# Run all checks
mvn verify

# Run tests with coverage
mvn test jacoco:report
```

### Pre-commit Hook

The pre-commit hook runs automatically on `git commit`:

```bash
# Install hooks (run once after cloning)
git config core.hooksPath .githooks

# Or manually
cp .githooks/pre-commit .git/hooks/
chmod +x .git/hooks/pre-commit
```

### CI Checks

All pull requests must pass:
- Checkstyle formatting check
- SpotBugs analysis
- PMD analysis
- Unit tests
- Integration tests
- Code coverage thresholds (80%)

---

## IDE Setup

### IntelliJ IDEA

1. Import the project as a Maven project
2. Install plugins: CheckStyle-IDEA, SpotBugs
3. Import code style:
   - Settings → Editor → Code Style → Java
   - Import from `config/checkstyle/checkstyle.xml`
4. Enable EditorConfig: Settings → Editor → Code Style → Enable EditorConfig

### VS Code

1. Install extensions: Java Extension Pack, Checkstyle for Java
2. EditorConfig is automatically respected
3. Configure Checkstyle extension to use project config

### Eclipse

1. Import as Maven project
2. Install Checkstyle plugin
3. Configure to use `config/checkstyle/checkstyle.xml`

---

## Dependency Guidelines

### Adding Dependencies

1. Check if functionality exists in JDK first
2. Prefer well-maintained, widely-used libraries
3. Consider native image compatibility (GraalVM)
4. Document why the dependency is needed in commit message

### Banned Patterns

- No reflection-heavy libraries without GraalVM config
- No dependencies with known security vulnerabilities
- No transitive dependency conflicts

### Approved Libraries

| Category | Library | Notes |
|----------|---------|-------|
| CLI parsing | picocli | Native image compatible |
| JSON | Jackson or Gson | Configure for native image |
| Logging | SLF4J + Logback | Standard logging |
| Testing | JUnit 5 | Standard testing |
| Assertions | AssertJ | Fluent assertions |
| Mocking | Mockito | Standard mocking |

---

## Security

### Sensitive Data

```java
// Never log sensitive data
LOG.info("Authenticating user: {}", username);  // Good
LOG.info("Password: {}", password);              // Bad!

// Mask sensitive values
public static String maskToken(String token) {
    if (token.length() <= 8) return "****";
    return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
}
```

### Input Validation

```java
// Validate all external input
public void executeCommand(String command) {
    Objects.requireNonNull(command, "command");
    if (command.isBlank()) {
        throw new IllegalArgumentException("Command cannot be blank");
    }
    if (command.contains(";") || command.contains("&&")) {
        throw new SecurityException("Command injection detected");
    }
    // ...
}
```

### File Operations

```java
// Use secure file operations
public void writeState(String jobId, String state) throws IOException {
    // Validate job ID to prevent path traversal
    if (!jobId.matches("[a-zA-Z0-9-]+")) {
        throw new IllegalArgumentException("Invalid job ID: " + jobId);
    }

    Path path = baseDir.resolve("jobs").resolve(jobId).resolve("state");
    // Use atomic write
    Files.writeString(path, state, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
}
```

---

## Summary

1. **Format**: Use Checkstyle, follow Google Java Style
2. **Analyze**: Use SpotBugs and PMD, fix all warnings
3. **Test**: 80% coverage, meaningful tests
4. **Document**: Javadoc for public API
5. **Commit**: Conventional commits, descriptive messages
6. **Review**: All PRs require review and passing CI

When in doubt, prioritize **readability** and **maintainability** over cleverness.
