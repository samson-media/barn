# Automated Testing

This document describes the automated testing strategy, test structure, and how to run tests for Barn.

---

## Overview

| Test Type | Purpose | Location |
|-----------|---------|----------|
| Unit Tests | Test individual functions and classes | `src/test/kotlin/unit/` |
| Integration Tests | Test component interactions | `src/test/kotlin/integration/` |
| End-to-End Tests | Test full CLI workflows | `src/test/kotlin/e2e/` |
| Native Image Tests | Verify native binary behavior | `src/test/kotlin/native/` |

---

## Running Tests

### All Tests

```bash
./gradlew test
```

### Specific Test Types

```bash
# Unit tests only
./gradlew test --tests "com.samsonmedia.barn.unit.*"

# Integration tests only
./gradlew test --tests "com.samsonmedia.barn.integration.*"

# End-to-end tests only
./gradlew test --tests "com.samsonmedia.barn.e2e.*"
```

### Single Test Class

```bash
./gradlew test --tests "com.samsonmedia.barn.unit.JobManagerTest"
```

### Single Test Method

```bash
./gradlew test --tests "com.samsonmedia.barn.unit.JobManagerTest.shouldCreateJobDirectory"
```

### With Verbose Output

```bash
./gradlew test --info
```

### Continuous Testing (Watch Mode)

```bash
./gradlew test --continuous
```

---

## Test Structure

```
src/test/kotlin/
  com/samsonmedia/barn/
    unit/
      JobManagerTest.kt
      ConfigParserTest.kt
      StateFileTest.kt
      RetryPolicyTest.kt
      CleanupServiceTest.kt
    integration/
      ServiceIntegrationTest.kt
      IpcCommunicationTest.kt
      FilesystemStateTest.kt
      ProcessManagementTest.kt
    e2e/
      CliRunCommandTest.kt
      CliStatusCommandTest.kt
      CliServiceCommandTest.kt
      CliCleanCommandTest.kt
    native/
      NativeBinaryTest.kt
      NativeServiceTest.kt
    fixtures/
      TestFixtures.kt
      MockProcessExecutor.kt
    testutil/
      TempDirectoryExtension.kt
      ServiceTestExtension.kt
```

---

## Unit Tests

Unit tests verify individual components in isolation.

### Characteristics

- Fast execution (milliseconds)
- No filesystem or network access
- Dependencies are mocked
- Run on every commit

### Example: JobManagerTest

```kotlin
class JobManagerTest {

    @Test
    fun `should generate unique job IDs`() {
        val manager = JobManager(MockFileSystem())

        val job1 = manager.createJob(Command("echo hello"))
        val job2 = manager.createJob(Command("echo world"))

        assertNotEquals(job1.id, job2.id)
    }

    @Test
    fun `should set initial state to queued`() {
        val manager = JobManager(MockFileSystem())

        val job = manager.createJob(Command("echo hello"))

        assertEquals(JobState.QUEUED, job.state)
    }

    @Test
    fun `should record creation timestamp`() {
        val clock = MockClock(Instant.parse("2026-01-21T10:00:00Z"))
        val manager = JobManager(MockFileSystem(), clock)

        val job = manager.createJob(Command("echo hello"))

        assertEquals("2026-01-21T10:00:00Z", job.createdAt)
    }
}
```

### Example: RetryPolicyTest

```kotlin
class RetryPolicyTest {

    @Test
    fun `should calculate exponential backoff delay`() {
        val policy = RetryPolicy(
            maxRetries = 3,
            initialDelay = Duration.ofSeconds(30),
            backoffMultiplier = 2.0
        )

        assertEquals(Duration.ofSeconds(30), policy.delayFor(retryCount = 0))
        assertEquals(Duration.ofSeconds(60), policy.delayFor(retryCount = 1))
        assertEquals(Duration.ofSeconds(120), policy.delayFor(retryCount = 2))
    }

    @Test
    fun `should not retry when max retries exceeded`() {
        val policy = RetryPolicy(maxRetries = 3)

        assertTrue(policy.shouldRetry(retryCount = 2))
        assertFalse(policy.shouldRetry(retryCount = 3))
    }
}
```

---

## Integration Tests

Integration tests verify component interactions with real filesystem operations.

### Characteristics

- Uses temporary directories
- Tests actual file I/O
- May spawn subprocesses
- Slower than unit tests (seconds)

### Example: FilesystemStateTest

```kotlin
@ExtendWith(TempDirectoryExtension::class)
class FilesystemStateTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should persist job state to filesystem`() {
        val stateManager = FilesystemStateManager(tempDir)
        val jobId = "job-12345"

        stateManager.writeState(jobId, JobState.RUNNING)

        val stateFile = tempDir.resolve("jobs/$jobId/state")
        assertTrue(stateFile.exists())
        assertEquals("running", stateFile.readText())
    }

    @Test
    fun `should atomically update state files`() {
        val stateManager = FilesystemStateManager(tempDir)
        val jobId = "job-12345"

        // Simulate concurrent updates
        val threads = (1..10).map { i ->
            thread {
                stateManager.writeState(jobId, JobState.RUNNING)
            }
        }
        threads.forEach { it.join() }

        // File should be valid (not corrupted)
        val state = stateManager.readState(jobId)
        assertEquals(JobState.RUNNING, state)
    }
}
```

### Example: ProcessManagementTest

```kotlin
@ExtendWith(TempDirectoryExtension::class)
class ProcessManagementTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should capture stdout to log file`() {
        val executor = ProcessExecutor(tempDir)
        val jobId = "job-12345"

        val result = executor.run(jobId, Command("echo", "hello world"))

        val stdout = tempDir.resolve("jobs/$jobId/logs/stdout.log")
        assertEquals("hello world\n", stdout.readText())
    }

    @Test
    fun `should record exit code`() {
        val executor = ProcessExecutor(tempDir)
        val jobId = "job-12345"

        executor.run(jobId, Command("sh", "-c", "exit 42"))

        val exitCode = tempDir.resolve("jobs/$jobId/exit_code")
        assertEquals("42", exitCode.readText())
    }

    @Test
    fun `should update heartbeat during execution`() {
        val executor = ProcessExecutor(tempDir, heartbeatInterval = Duration.ofMillis(100))
        val jobId = "job-12345"

        executor.run(jobId, Command("sleep", "0.5"))

        val heartbeat = tempDir.resolve("jobs/$jobId/heartbeat")
        // Heartbeat should have been updated multiple times
        assertTrue(heartbeat.exists())
    }
}
```

---

## End-to-End Tests

E2E tests verify complete CLI workflows.

### Characteristics

- Tests the actual `barn` binary or JAR
- Simulates real user commands
- Validates output format (human, JSON, XML)
- Slowest tests (seconds to minutes)

### Example: CliRunCommandTest

```kotlin
@ExtendWith(ServiceTestExtension::class)
class CliRunCommandTest {

    @Test
    fun `should run command and return job ID`() {
        val result = barn("run", "echo", "hello")

        assertEquals(0, result.exitCode)
        assertContains(result.stdout, "job-")
        assertContains(result.stdout, "queued")
    }

    @Test
    fun `should support JSON output`() {
        val result = barn("run", "--output=json", "echo", "hello")

        val json = Json.parseToJsonElement(result.stdout)
        assertTrue(json.jsonObject.containsKey("job_id"))
        assertEquals("queued", json.jsonObject["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should apply custom tag`() {
        val result = barn("run", "--tag=download", "--output=json", "echo", "hello")

        val json = Json.parseToJsonElement(result.stdout)
        assertEquals("download", json.jsonObject["tag"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should work in offline mode without service`() {
        val result = barn("run", "--offline", "echo", "hello")

        assertEquals(0, result.exitCode)
        assertContains(result.stdout, "job-")
    }

    private fun barn(vararg args: String): ProcessResult {
        return ProcessBuilder("./build/libs/barn.jar", *args)
            .redirectErrorStream(true)
            .start()
            .waitFor(30, TimeUnit.SECONDS)
    }
}
```

### Example: CliStatusCommandTest

```kotlin
@ExtendWith(ServiceTestExtension::class)
class CliStatusCommandTest {

    @Test
    fun `should list all jobs`() {
        // Create some jobs
        barn("run", "--offline", "echo", "job1")
        barn("run", "--offline", "echo", "job2")

        val result = barn("status")

        assertEquals(0, result.exitCode)
        assertContains(result.stdout, "job-")
    }

    @Test
    fun `should filter by tag`() {
        barn("run", "--offline", "--tag=download", "echo", "job1")
        barn("run", "--offline", "--tag=transcode", "echo", "job2")

        val result = barn("status", "--tag=download", "--output=json")

        val json = Json.parseToJsonElement(result.stdout)
        val jobs = json.jsonObject["jobs"]?.jsonArray
        assertEquals(1, jobs?.size)
    }

    @Test
    fun `should show empty state gracefully`() {
        val result = barn("status")

        assertEquals(0, result.exitCode)
        assertContains(result.stdout, "No jobs found")
    }
}
```

---

## Native Image Tests

Tests that verify the GraalVM native binary works correctly.

### Characteristics

- Requires native image to be built first
- Tests startup time
- Verifies reflection configuration
- Platform-specific

### Running Native Tests

```bash
# Build native image first
./gradlew nativeCompile

# Run native-specific tests
./gradlew nativeTest
```

### Example: NativeBinaryTest

```kotlin
class NativeBinaryTest {

    private val nativeBinary = Path.of("build/native/nativeCompile/barn")

    @BeforeAll
    fun checkBinaryExists() {
        assumeTrue(nativeBinary.exists(), "Native binary not found. Run ./gradlew nativeCompile first.")
    }

    @Test
    fun `should start within 100ms`() {
        val start = System.currentTimeMillis()

        val process = ProcessBuilder(nativeBinary.toString(), "--version")
            .start()
        process.waitFor(5, TimeUnit.SECONDS)

        val elapsed = System.currentTimeMillis() - start
        assertTrue(elapsed < 100, "Startup took ${elapsed}ms, expected < 100ms")
    }

    @Test
    fun `should display version`() {
        val result = runNative("--version")

        assertEquals(0, result.exitCode)
        assertMatches(Regex("barn \\d+\\.\\d+\\.\\d+"), result.stdout)
    }

    @Test
    fun `should handle JSON serialization`() {
        val result = runNative("status", "--output=json", "--offline")

        assertEquals(0, result.exitCode)
        // Verify JSON is valid (reflection config is correct)
        assertDoesNotThrow { Json.parseToJsonElement(result.stdout) }
    }

    private fun runNative(vararg args: String): ProcessResult {
        return ProcessBuilder(nativeBinary.toString(), *args)
            .redirectErrorStream(true)
            .start()
            .let { process ->
                process.waitFor(30, TimeUnit.SECONDS)
                ProcessResult(
                    exitCode = process.exitValue(),
                    stdout = process.inputStream.bufferedReader().readText()
                )
            }
    }
}
```

---

## Test Fixtures

### TestFixtures.kt

```kotlin
object TestFixtures {

    fun createTestJob(
        id: String = "job-${UUID.randomUUID().toString().take(5)}",
        state: JobState = JobState.QUEUED,
        tag: String? = null,
        createdAt: Instant = Instant.now()
    ): Job {
        return Job(
            id = id,
            state = state,
            tag = tag,
            createdAt = createdAt.toString(),
            command = Command("echo", "test")
        )
    }

    fun createTestConfig(
        maxConcurrentJobs: Int = 4,
        maxRetries: Int = 3,
        cleanupMaxAgeHours: Int = 72
    ): Config {
        return Config(
            service = ServiceConfig(maxConcurrentJobs = maxConcurrentJobs),
            jobs = JobsConfig(maxRetries = maxRetries),
            cleanup = CleanupConfig(maxAgeHours = cleanupMaxAgeHours)
        )
    }

    fun createJobDirectory(baseDir: Path, jobId: String, state: JobState = JobState.QUEUED): Path {
        val jobDir = baseDir.resolve("jobs/$jobId")
        jobDir.createDirectories()
        jobDir.resolve("state").writeText(state.name.lowercase())
        jobDir.resolve("created_at").writeText(Instant.now().toString())
        return jobDir
    }
}
```

### MockProcessExecutor.kt

```kotlin
class MockProcessExecutor : ProcessExecutor {

    private val executions = mutableListOf<Execution>()
    private val results = mutableMapOf<String, ProcessResult>()

    fun whenCommand(pattern: String, result: ProcessResult) {
        results[pattern] = result
    }

    override fun execute(command: Command): ProcessResult {
        executions.add(Execution(command, Instant.now()))

        val matchingResult = results.entries.find { (pattern, _) ->
            command.toString().contains(pattern)
        }?.value

        return matchingResult ?: ProcessResult(exitCode = 0, stdout = "", stderr = "")
    }

    fun verifyExecuted(pattern: String) {
        assertTrue(
            executions.any { it.command.toString().contains(pattern) },
            "Expected command matching '$pattern' to be executed"
        )
    }

    data class Execution(val command: Command, val timestamp: Instant)
}
```

---

## Test Extensions

### TempDirectoryExtension

```kotlin
class TempDirectoryExtension : BeforeEachCallback, AfterEachCallback {

    private val tempDirs = mutableMapOf<ExtensionContext, Path>()

    override fun beforeEach(context: ExtensionContext) {
        val tempDir = Files.createTempDirectory("barn-test-")
        tempDirs[context] = tempDir

        // Inject into test instance
        context.testInstance.ifPresent { instance ->
            instance.javaClass.declaredFields
                .filter { it.isAnnotationPresent(TempDir::class.java) }
                .forEach { field ->
                    field.isAccessible = true
                    field.set(instance, tempDir)
                }
        }
    }

    override fun afterEach(context: ExtensionContext) {
        tempDirs.remove(context)?.let { dir ->
            dir.toFile().deleteRecursively()
        }
    }
}
```

### ServiceTestExtension

```kotlin
class ServiceTestExtension : BeforeAllCallback, AfterAllCallback {

    private var serviceProcess: Process? = null

    override fun beforeAll(context: ExtensionContext) {
        // Start barn service in test mode
        serviceProcess = ProcessBuilder("./gradlew", "run", "--args=service start --test-mode")
            .start()

        // Wait for service to be ready
        retry(maxAttempts = 10, delay = Duration.ofMillis(500)) {
            val result = ProcessBuilder("./gradlew", "run", "--args=service status")
                .start()
                .waitFor(5, TimeUnit.SECONDS)
            result == 0
        }
    }

    override fun afterAll(context: ExtensionContext) {
        serviceProcess?.destroyForcibly()
    }
}
```

---

## CI Integration

### Test Reporting

Test results are published as artifacts in CI:

```yaml
- name: Run tests
  run: ./gradlew test

- name: Upload test results
  uses: actions/upload-artifact@v4
  if: always()
  with:
    name: test-results
    path: |
      build/reports/tests/
      build/test-results/
```

### Code Coverage

```bash
# Generate coverage report
./gradlew jacocoTestReport

# Check coverage thresholds
./gradlew jacocoTestCoverageVerification
```

Coverage thresholds in `build.gradle.kts`:

```kotlin
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()  // 80% line coverage
            }
        }
    }
}
```

### Test Matrix

Tests run on all supported platforms in CI:

| Platform | JVM Tests | Native Tests |
|----------|-----------|--------------|
| Linux x64 | Yes | Yes |
| Linux ARM64 | Yes | Yes |
| macOS x64 | Yes | Yes |
| macOS ARM64 | Yes | Yes |
| Windows x64 | Yes | Yes |

---

## Best Practices

### Naming Conventions

```kotlin
// Use backticks with descriptive names
@Test
fun `should create job directory when running command`() { }

@Test
fun `should fail gracefully when service unavailable`() { }

@Test
fun `should retry failed job up to max retries`() { }
```

### Test Organization

```kotlin
class JobManagerTest {

    // Group related tests
    @Nested
    inner class `when creating jobs` {
        @Test
        fun `should generate unique ID`() { }

        @Test
        fun `should set initial state to queued`() { }
    }

    @Nested
    inner class `when job fails` {
        @Test
        fun `should record exit code`() { }

        @Test
        fun `should schedule retry if policy allows`() { }
    }
}
```

### Assertions

```kotlin
// Prefer specific assertions
assertEquals(JobState.RUNNING, job.state)
assertTrue(job.startedAt != null)
assertContains(output, "job-")

// Avoid generic assertions
assertTrue(job.state == JobState.RUNNING)  // Less informative on failure
```

---

## Troubleshooting

### Tests Fail on Windows

Check line endings:

```bash
git config core.autocrlf input
```

### Flaky Tests

Use retry for inherently flaky operations:

```kotlin
@Test
@RetryingTest(3)
fun `should handle concurrent access`() { }
```

### Slow Tests

Tag slow tests and exclude from fast feedback loop:

```kotlin
@Tag("slow")
@Test
fun `should handle large file processing`() { }
```

```bash
# Run without slow tests
./gradlew test -PexcludeTags=slow
```

---

## Next Steps

- [Manual Testing](testing-manual.md) - Manual test procedures
- [Development Guide](development.md) - Setting up dev environment
