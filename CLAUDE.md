# Claude AI Instructions for Barn

> **First Step**: Always read `README.md` and relevant files in `docs/` before making changes.

## Project Overview

Barn is a cross-platform job daemon for managing long-running media processing tasks (FFmpeg, WebDAV transfers). It runs as an OS service on Windows, macOS, and Linux.

**Key documentation to read first:**

1. `README.md` - Project overview and CLI commands
2. `docs/jobs.md` - Filesystem-based job state layout
3. `docs/config.md` - Configuration options
4. `docs/coding-standards.md` - **CRITICAL**: Code style and conventions

## Technology Stack

- **Language**: Java 21+
- **Build**: Maven
- **Native Image**: GraalVM Native Image
- **Code Style**: Checkstyle (Google Java Style)
- **Static Analysis**: SpotBugs, PMD
- **Testing**: JUnit 5, Mockito, AssertJ

## Code Standards (Mandatory)

Before writing any code, read `docs/coding-standards.md`. Key points:

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
- Use `final` for fields that don't change
- Use `Optional<T>` for return types that may be absent
- Use `Objects.requireNonNull()` for constructor parameters
- Use records for value objects
- Use sealed interfaces for state machines
- Use switch expressions (not statements)
- Use `var` for local variables with obvious types

### Error Handling
```java
// Good: Use Optional for methods that may not find a result
public Optional<Job> findJob(String id) {
    return Optional.ofNullable(jobs.get(id));
}

// Good: Validate preconditions
public void processJob(Job job) {
    Objects.requireNonNull(job, "job must not be null");
    if (job.getState() != JobState.QUEUED) {
        throw new IllegalStateException("Job must be in QUEUED state");
    }
}
```

### Testing
- Use descriptive names: `methodName_condition_expectedResult()`
- Follow AAA pattern: Arrange, Act, Assert
- Minimum 80% line coverage

## Architecture Principles

### Filesystem as Database
- Job state is stored in `/tmp/barn/jobs/<job-id>/`
- Use atomic file operations
- Human-readable plain text files
- No external database dependencies

### Cross-Platform
- Use `System.getProperty("java.io.tmpdir")` for temp paths
- Avoid platform-specific commands
- Test on all three platforms (Windows, macOS, Linux)

### GraalVM Native Image
- Avoid reflection where possible
- Register any reflection in `reflect-config.json`
- Test native image builds

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
    test/java/
      unit/                # Unit tests
      integration/         # Integration tests
      e2e/                 # End-to-end tests
  docs/                    # Documentation
  config/
    checkstyle/            # Checkstyle configuration
    spotbugs/              # SpotBugs exclusions
    pmd/                   # PMD ruleset
  .githooks/               # Git hooks
  .github/workflows/       # CI/CD
  pom.xml                  # Maven build
```

## Common Tasks

### Before Committing
```bash
mvn checkstyle:check     # Check code style
mvn spotbugs:check       # Bug detection
mvn pmd:check            # Static analysis
mvn test                 # Run tests
mvn verify               # Run all checks
```

### Commit Message Format
Follow Conventional Commits:
```
feat(jobs): add retry mechanism for failed jobs
fix(cli): handle spaces in file paths
docs(readme): update installation instructions
```

### Adding a New CLI Command
1. Create command class in `src/main/java/.../cli/`
2. Annotate with picocli `@Command`
3. Add to main command group
4. Add tests in `src/test/java/.../e2e/`
5. Update `README.md` and `docs/`

### Adding a Job State File
1. Update `docs/jobs.md` with the new file
2. Add read/write methods in `StateManager`
3. Add tests for persistence
4. Consider crash recovery implications

## Security Considerations

- Never log passwords, tokens, or credentials
- Validate job IDs to prevent path traversal
- Sanitize command inputs (no shell injection)
- Use atomic writes for state files

## What NOT to Do

- Don't return `null` - use `Optional<T>` instead
- Don't use raw types - always specify generics
- Don't use wildcard imports
- Don't skip tests for "simple" changes
- Don't use reflection without registering for native image
- Don't use platform-specific code without abstractions
- Don't commit without running `mvn verify`
- Don't catch `Exception` - catch specific exceptions
- Don't use `System.out.println` - use SLF4J logging

## Questions to Ask

Before implementing a feature, consider:

1. Does this work on Windows, macOS, AND Linux?
2. Will this survive a crash/restart?
3. Can the state be manually inspected in the filesystem?
4. Is this covered by tests?
5. Does this follow the existing patterns in the codebase?
6. Have I run `mvn verify` before committing?
