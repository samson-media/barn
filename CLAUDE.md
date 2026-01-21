# Claude AI Instructions for Barn

> **FIRST STEP - MANDATORY**: Before writing ANY code, read these files:
> 1. `README.md` - Project overview
> 2. `docs/coding-standards.md` - Code style and conventions
> 3. `docs/jobs.md` - Filesystem state layout
> 4. `docs/config.md` - Configuration options

---

## Ticket Implementation Process (MANDATORY)

When assigned a GitHub issue/ticket, follow this **exact process**. Do NOT skip steps.

### Phase 1: READ (Do Not Write Any Code Yet)

```
┌─────────────────────────────────────────────────────────────┐
│  STOP! Read everything before writing a single line of code │
└─────────────────────────────────────────────────────────────┘
```

**Step 1.1: Fetch and Read the Ticket**
```bash
gh issue view <issue-number>
```

Read the ENTIRE ticket including:
- Overview and Context
- Requirements (every numbered item)
- Acceptance Criteria (every checkbox)
- Implementation Notes
- Tests Required

**Step 1.2: Check Dependencies**
```bash
# List dependency issues
gh issue view <issue-number> --json body | grep -o '#[0-9]*'

# Check if dependencies are closed
gh issue view <dep-number> --json state
```

If dependencies are NOT closed: **STOP**. Work on dependencies first or inform the user.

**Step 1.3: Read Referenced Documentation**

The ticket's "Context" section lists docs to read. Read ALL of them:
```bash
# Example: if ticket says "Read first: docs/jobs.md"
cat docs/jobs.md
```

**Step 1.4: Read Related Existing Code**

If the ticket depends on other issues, read the code they produced:
```bash
# Find relevant files
find src -name "*.java" | xargs grep -l "RelatedClass"
```

### Phase 2: PLAN (Still No Implementation Code)

**Step 2.1: List All Deliverables**

Extract from the ticket:
- [ ] Classes/files to create
- [ ] Methods to implement
- [ ] Tests to write
- [ ] Acceptance criteria to satisfy

**Step 2.2: Identify Test Cases**

From the "Tests Required" section, list every test:
```
Example:
- JobManagerTest.java
  - createJob_withValidCommand_shouldReturnJobWithUniqueId
  - createJob_withNullCommand_shouldThrowNullPointerException
  - findJob_withExistingId_shouldReturnJob
  - findJob_withMissingId_shouldReturnEmpty
```

### Phase 3: TEST (Write Tests FIRST)

```
┌─────────────────────────────────────────────────────────┐
│  Write ALL tests before writing ANY implementation code  │
└─────────────────────────────────────────────────────────┘
```

**Step 3.1: Create Test File**
```java
// src/test/java/com/samsonmedia/barn/<package>/<Class>Test.java
class JobManagerTest {
    @Test
    void methodName_condition_expectedResult() {
        // Arrange
        // Act
        // Assert - use AssertJ
    }
}
```

**Step 3.2: Write ALL Test Cases**

Write tests for:
1. Happy path (normal success)
2. Edge cases (empty, boundary values)
3. Error cases (null, invalid state)
4. Every acceptance criterion

**Step 3.3: Run Tests (Must FAIL)**
```bash
mvn test -Dtest=<TestClass>
# Expected: FAILURE (code not implemented yet)
```

If tests pass before implementation, the tests are wrong.

### Phase 4: IMPLEMENT (Now Write Code)

**Step 4.1: Implement Minimum Code**

Write the minimum code to make ONE test pass at a time:
```bash
# After each small change
mvn test -Dtest=<TestClass>#<testMethod>
```

**Step 4.2: Follow Coding Standards**

Every file must:
- Have no wildcard imports
- Use `Optional<T>` not null returns
- Use `Objects.requireNonNull()` for parameters
- Use `final` for immutable fields
- Have proper Javadoc on public methods

**Step 4.3: Iterate Until All Tests Pass**
```bash
mvn test -Dtest=<TestClass>
# Expected: SUCCESS for all tests
```

### Phase 5: VERIFY (Full Quality Check)

**Step 5.1: Run Full Verification**
```bash
mvn verify
```

This MUST pass:
- ✓ Checkstyle (formatting)
- ✓ Compilation (no warnings)
- ✓ All unit tests
- ✓ SpotBugs (no bugs)
- ✓ PMD (no violations)
- ✓ JaCoCo (80%+ coverage)

**Step 5.2: Check Acceptance Criteria**

Go through EVERY checkbox in the ticket:
```
- [ ] Criterion 1 → Verify manually or with test
- [ ] Criterion 2 → Verify manually or with test
...
```

**Step 5.3: Fix Any Issues**

If `mvn verify` fails:
1. Read the error message
2. Fix the issue
3. Run `mvn verify` again
4. Repeat until clean

### Phase 6: COMPLETE (Commit and Close)

**Step 6.1: Commit with Conventional Message**
```bash
git add .
git commit -m "feat(<scope>): <description>

Implements #<issue-number>

- Added <component>
- Implemented <feature>
- Tests for <functionality>

Co-Authored-By: Claude <noreply@anthropic.com>"
```

**Step 6.2: Verify Commit**
```bash
# Ensure nothing is missed
git status
mvn verify  # Run one final time
```

**Step 6.3: Close the Ticket**
```bash
gh issue close <issue-number> --comment "Implemented in commit <sha>"
```

---

## Ticket Process Checklist

Copy this checklist for each ticket:

```markdown
## Ticket #XX: <Title>

### Phase 1: READ
- [ ] Fetched ticket with `gh issue view`
- [ ] Read entire ticket description
- [ ] Checked all dependencies are closed
- [ ] Read all referenced documentation
- [ ] Read related existing code

### Phase 2: PLAN
- [ ] Listed all classes/files to create
- [ ] Listed all methods to implement
- [ ] Listed all test cases from ticket

### Phase 3: TEST
- [ ] Created test file(s)
- [ ] Wrote all test cases
- [ ] Tests fail (code not implemented)

### Phase 4: IMPLEMENT
- [ ] Implemented code to pass tests
- [ ] All tests pass
- [ ] Followed coding standards

### Phase 5: VERIFY
- [ ] `mvn verify` passes
- [ ] All acceptance criteria checked
- [ ] No TODO/FIXME without issue

### Phase 6: COMPLETE
- [ ] Committed with conventional message
- [ ] Closed ticket with `gh issue close`
```

---

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

---

## Working with GitHub Issues

### Viewing the Backlog

```bash
# List all open issues
gh issue list

# List issues by milestone
gh issue list --milestone "M1: Foundation"

# List issues by label
gh issue list --label "foundation"

# View specific issue
gh issue view 1
```

### Dependency Graph

Issues must be completed in dependency order. The milestones define the general order:

```
M1: Foundation (#1, #2, #3, #4, #22)
       ↓
M2: Job Execution (#5, #6, #7, #20)
       ↓
M3: CLI Commands (#8, #9, #10, #11, #12, #13, #21)
       ↓
M4: Service Infrastructure (#14, #15, #16)
       ↓
M5: Platform Integration (#17, #18, #19)
```

**Recommended order within M1:**
```
#1 (project structure)
  → #22 (logging)
  → #2 (configuration)
    → #3 (filesystem state)
      → #4 (job model)
```

### Starting a New Ticket

```bash
# 1. Check what's ready to work on (no open dependencies)
gh issue list --milestone "M1: Foundation" --state open

# 2. Pick the next ticket with closed dependencies
gh issue view 1

# 3. Follow the Ticket Implementation Process above
```

### Progress Tracking

After completing a ticket:
```bash
# Close with comment
gh issue close <number> --comment "Implemented in commit abc123"

# Verify milestone progress
gh issue list --milestone "M1: Foundation"
```

---

## Emergency Procedures

### If `mvn verify` Fails

1. **Read the error** - Don't guess, read the actual message
2. **Identify the phase** - Checkstyle? Compile? Test? SpotBugs? PMD? JaCoCo?
3. **Fix incrementally** - Run just that check until it passes:
   ```bash
   mvn checkstyle:check     # Formatting issues
   mvn compile              # Compilation errors
   mvn test                 # Test failures
   mvn spotbugs:check       # Bug detection
   mvn pmd:check            # Static analysis
   mvn jacoco:check         # Coverage issues
   ```
4. **Run full verify again** - `mvn verify`

### If Tests Won't Pass

1. **Check the test is correct** - Is the expected behavior right?
2. **Check dependencies** - Is required code from other tickets present?
3. **Check test isolation** - Does the test depend on external state?
4. **Run single test** - `mvn test -Dtest=ClassName#methodName`

### If Stuck on a Ticket

1. **Re-read the ticket** - Often the answer is in the description
2. **Check Implementation Notes** - Specific guidance is provided
3. **Check referenced docs** - Context section lists what to read
4. **Look at similar code** - How did other tickets solve similar problems?

---

## Ticket Template Reference

Every ticket follows this structure:

```markdown
## Overview
Brief description of what to implement.

## Context
- **Read first**: docs to read before starting
- **Depends on**: prerequisite tickets

## Requirements
### 1. Component Name
Detailed requirements with code examples.

### 2. Another Component
More requirements...

## Tests Required
- TestClass1.java: description
- TestClass2.java: description

## Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2
- [ ] `mvn verify` passes
- [ ] Tests have 80%+ coverage

## Implementation Notes
Specific guidance, code patterns, edge cases.

## Definition of Done
Summary of completion requirements.
```

Use this structure to know exactly what's expected.
