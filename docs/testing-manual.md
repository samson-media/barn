# Manual Testing

This document provides step-by-step procedures for manually testing Barn functionality.

---

## Overview

Manual testing is essential for:

- Verifying user-facing behavior
- Testing platform-specific features
- Exploratory testing
- Release validation

---

## Test Environment Setup

### Prerequisites

1. Build the barn binary:
   ```bash
   ./gradlew nativeCompile
   ```

2. Add to PATH (optional):
   ```bash
   export PATH=$PATH:$(pwd)/build/native/nativeCompile
   ```

3. Create a clean test directory:
   ```bash
   rm -rf /tmp/barn
   mkdir -p /tmp/barn
   ```

### Verify Installation

```bash
barn --version
# Expected: barn 1.x.x

barn --help
# Expected: Usage information
```

---

## Service Management Tests

### Test: Start Service

**Steps:**
1. Ensure service is not running:
   ```bash
   barn service status
   ```
2. Start the service:
   ```bash
   barn service start
   ```
3. Verify service is running:
   ```bash
   barn service status
   ```

**Expected:**
- Status shows "running"
- Process ID is displayed
- No error messages

**Verify:**
```bash
# Check process exists
ps aux | grep barn

# Check IPC socket created (Unix)
ls -la /tmp/barn/barn.sock

# Check logs
cat /tmp/barn/logs/barn.log
```

---

### Test: Stop Service

**Steps:**
1. Ensure service is running
2. Stop the service:
   ```bash
   barn service stop
   ```
3. Verify service stopped:
   ```bash
   barn service status
   ```

**Expected:**
- Status shows "stopped" or "not running"
- Process no longer exists

---

### Test: Restart Service

**Steps:**
1. Start the service
2. Note the PID:
   ```bash
   barn service status
   ```
3. Restart:
   ```bash
   barn service restart
   ```
4. Verify new PID:
   ```bash
   barn service status
   ```

**Expected:**
- Service restarts successfully
- New PID is different from old PID

---

### Test: Reload Configuration

**Steps:**
1. Start the service
2. Modify configuration:
   ```bash
   echo "[cleanup]" >> /etc/barn/barn.conf
   echo "max_age_hours = 24" >> /etc/barn/barn.conf
   ```
3. Reload:
   ```bash
   barn service reload
   ```
4. Check logs for reload confirmation

**Expected:**
- Service continues running (same PID)
- Logs show "Configuration reloaded"

---

## Job Execution Tests

### Test: Run Simple Command

**Steps:**
1. Run a command:
   ```bash
   barn run echo "hello world"
   ```
2. Note the job ID from output
3. Check job status:
   ```bash
   barn status
   ```

**Expected:**
- Job ID is returned
- Status shows job as "succeeded"
- Exit code is 0

---

### Test: Run Command (JSON Output)

**Steps:**
```bash
barn run --output=json echo "hello world"
```

**Expected Output:**
```json
{
  "job_id": "job-xxxxx",
  "state": "queued",
  "tag": null,
  "created_at": "2026-01-21T10:00:00Z",
  "exit_code": null
}
```

**Verify JSON is valid:**
```bash
barn run --output=json echo "hello" | jq .
```

---

### Test: Run Command with Tag

**Steps:**
```bash
barn run --tag=download curl -o /tmp/test.txt https://example.com
barn status --tag=download
```

**Expected:**
- Job is tagged with "download"
- Filtering by tag shows only matching jobs

---

### Test: Run Long-Running Command

**Steps:**
1. Start a long command:
   ```bash
   barn run sleep 30
   ```
2. Immediately check status:
   ```bash
   barn status
   ```
3. Verify heartbeat updates:
   ```bash
   watch -n 1 'cat /tmp/barn/jobs/job-*/heartbeat'
   ```

**Expected:**
- Job shows as "running"
- Heartbeat timestamp updates every few seconds

---

### Test: Run Failing Command

**Steps:**
```bash
barn run --output=json sh -c "exit 1"
barn status
```

**Expected:**
- Job state is "failed"
- Exit code is 1
- Error file contains failure reason

**Verify files:**
```bash
cat /tmp/barn/jobs/job-*/exit_code
cat /tmp/barn/jobs/job-*/error
```

---

### Test: Run Command in Offline Mode

**Steps:**
1. Stop service if running:
   ```bash
   barn service stop
   ```
2. Run with offline flag:
   ```bash
   barn run --offline echo "offline test"
   ```

**Expected:**
- Command executes without service
- Job files created in /tmp/barn/jobs/

---

## Job Inspection Tests

### Test: Describe Job

**Steps:**
1. Run a command and note job ID
2. Get job details:
   ```bash
   barn describe job-xxxxx
   ```

**Expected Output:**
```
Job ID:      job-xxxxx
State:       succeeded
Tag:         -
Created:     2026-01-21T10:00:00Z
Started:     2026-01-21T10:00:01Z
Finished:    2026-01-21T10:00:02Z
Exit Code:   0
PID:         12345

Command:
  echo hello world

Logs:
  stdout: /tmp/barn/jobs/job-xxxxx/logs/stdout.log
  stderr: /tmp/barn/jobs/job-xxxxx/logs/stderr.log
```

---

### Test: Describe Job (JSON)

**Steps:**
```bash
barn describe --output=json job-xxxxx
```

**Expected:**
- Valid JSON with all job fields
- Includes manifest, state, timestamps, logs

---

### Test: View Job Logs

**Steps:**
1. Run a command that produces output:
   ```bash
   barn run sh -c "echo stdout; echo stderr >&2"
   ```
2. View logs:
   ```bash
   cat /tmp/barn/jobs/job-xxxxx/logs/stdout.log
   cat /tmp/barn/jobs/job-xxxxx/logs/stderr.log
   ```

**Expected:**
- stdout.log contains "stdout"
- stderr.log contains "stderr"

---

## Job Control Tests

### Test: Kill Running Job

**Steps:**
1. Start a long-running job:
   ```bash
   barn run sleep 300
   ```
2. Note the job ID
3. Kill the job:
   ```bash
   barn kill job-xxxxx
   ```
4. Verify:
   ```bash
   barn describe job-xxxxx
   ```

**Expected:**
- Job state is "canceled"
- Process is terminated
- finished_at is recorded

---

### Test: Kill Non-Existent Job

**Steps:**
```bash
barn kill job-nonexistent
```

**Expected:**
- Error message: "Job not found: job-nonexistent"
- Exit code is non-zero

---

## Cleanup Tests

### Test: Clean Completed Jobs

**Steps:**
1. Run several jobs:
   ```bash
   for i in 1 2 3; do barn run --offline echo "test $i"; done
   ```
2. Verify jobs exist:
   ```bash
   ls /tmp/barn/jobs/
   barn status
   ```
3. Clean up:
   ```bash
   barn clean
   ```
4. Verify cleaned:
   ```bash
   ls /tmp/barn/jobs/
   barn status
   ```

**Expected:**
- Completed jobs are removed
- Job directories deleted

---

### Test: Clean with Dry Run

**Steps:**
```bash
barn clean --dry-run
```

**Expected:**
- Shows what would be deleted
- No actual deletion occurs
- Job directories still exist

---

### Test: Clean Jobs Older Than

**Steps:**
1. Create jobs with old timestamps (manual file manipulation):
   ```bash
   # Create a job dir with old timestamp
   mkdir -p /tmp/barn/jobs/job-old
   echo "succeeded" > /tmp/barn/jobs/job-old/state
   touch -d "2 days ago" /tmp/barn/jobs/job-old/finished_at
   ```
2. Clean with age filter:
   ```bash
   barn clean --older-than=24h
   ```

**Expected:**
- Old jobs are removed
- Recent jobs are kept

---

### Test: Clean Including Failed Jobs

**Steps:**
1. Create a failed job:
   ```bash
   barn run --offline sh -c "exit 1"
   ```
2. Default clean (should keep failed):
   ```bash
   barn clean
   barn status
   ```
3. Clean with include-failed:
   ```bash
   barn clean --include-failed
   barn status
   ```

**Expected:**
- Default clean preserves failed jobs
- --include-failed removes them

---

## Retry Behavior Tests

### Test: Automatic Retry on Failure

**Setup:**
Configure retries in barn.conf:
```toml
[jobs]
max_retries = 3
retry_delay_seconds = 5
```

**Steps:**
1. Create a script that fails twice then succeeds:
   ```bash
   cat > /tmp/flaky.sh << 'EOF'
   #!/bin/bash
   COUNT_FILE="/tmp/flaky_count"
   COUNT=$(cat $COUNT_FILE 2>/dev/null || echo 0)
   COUNT=$((COUNT + 1))
   echo $COUNT > $COUNT_FILE
   if [ $COUNT -lt 3 ]; then
     echo "Attempt $COUNT - failing"
     exit 1
   fi
   echo "Attempt $COUNT - success"
   exit 0
   EOF
   chmod +x /tmp/flaky.sh
   rm -f /tmp/flaky_count
   ```
2. Run the flaky command:
   ```bash
   barn run /tmp/flaky.sh
   ```
3. Wait for retries and check status:
   ```bash
   watch barn status
   ```

**Expected:**
- Job retries automatically
- Eventually succeeds on 3rd attempt
- retry_count file shows retry history

---

### Test: Max Retries Exceeded

**Steps:**
1. Run a command that always fails:
   ```bash
   barn run sh -c "exit 1"
   ```
2. Wait for all retries to exhaust
3. Check final status:
   ```bash
   barn describe job-xxxxx
   ```

**Expected:**
- Job state is "failed"
- retry_count equals max_retries
- No more retry attempts

---

## Output Format Tests

### Test: Human-Readable Output (Default)

**Steps:**
```bash
barn status
```

**Expected:**
- Formatted table or list
- Human-readable timestamps
- Clear column headers

---

### Test: JSON Output

**Steps:**
```bash
barn status --output=json | jq .
```

**Expected:**
- Valid JSON
- Array of job objects
- ISO 8601 timestamps

---

### Test: XML Output

**Steps:**
```bash
barn status --output=xml | xmllint --format -
```

**Expected:**
- Valid XML
- Proper element structure
- XML declaration present

---

## Platform-Specific Tests

### Linux (systemd)

**Test: Service Integration**
```bash
# After installing .deb
sudo systemctl status barn
sudo systemctl start barn
sudo systemctl stop barn
sudo journalctl -u barn
```

### macOS (launchd)

**Test: Service Integration**
```bash
# After brew install
brew services list
brew services start barn
brew services stop barn
cat /usr/local/var/log/barn.log
```

### Windows (Service)

**Test: Service Integration**
```powershell
# After MSI install
Get-Service BarnService
Start-Service BarnService
Stop-Service BarnService
Get-EventLog -LogName Application -Source Barn
```

---

## Crash Recovery Tests

### Test: Service Crash Recovery

**Steps:**
1. Start service and create a long-running job:
   ```bash
   barn service start
   barn run sleep 300
   ```
2. Force-kill the service:
   ```bash
   pkill -9 -f "barn service"
   ```
3. Restart service:
   ```bash
   barn service start
   ```
4. Check orphaned job status:
   ```bash
   barn status
   ```

**Expected:**
- Orphaned job detected
- State changed to "failed"
- exit_code set to "orphaned_process"

---

### Test: System Reboot Recovery

**Steps:**
1. Create a running job
2. Reboot the system
3. After reboot, start service and check status

**Expected:**
- Previous running jobs marked as failed/orphaned
- Service recovers cleanly

---

## Concurrent Access Tests

### Test: Multiple Simultaneous Jobs

**Steps:**
```bash
# Run 10 jobs in parallel
for i in $(seq 1 10); do
  barn run --tag=batch sleep 5 &
done
wait

# Check status
barn status --tag=batch
```

**Expected:**
- All jobs execute (respecting max_concurrent_jobs)
- No file corruption
- All jobs complete successfully

---

### Test: Multiple CLI Clients

**Steps:**
1. Open two terminals
2. In terminal 1:
   ```bash
   watch -n 0.5 barn status
   ```
3. In terminal 2:
   ```bash
   for i in $(seq 1 20); do barn run echo $i; sleep 0.5; done
   ```

**Expected:**
- Status updates in real-time
- No IPC errors
- All jobs visible

---

## Update Tests

### Test: Check for Updates

**Steps:**
```bash
barn update --check
```

**Expected:**
- Shows current version
- Shows latest available version
- Indicates if update available

---

### Test: Perform Update

**Steps:**
```bash
sudo barn update
```

**Expected:**
- Downloads new binary
- Verifies checksum
- Replaces binary
- Restarts service if running

---

### Test: Rollback Update

**Steps:**
```bash
sudo barn update --rollback
barn --version
```

**Expected:**
- Previous version restored
- Service continues working

---

## Error Handling Tests

### Test: Invalid Command

**Steps:**
```bash
barn invalidcommand
```

**Expected:**
- Error message with suggestion
- Non-zero exit code

---

### Test: Missing Arguments

**Steps:**
```bash
barn run
```

**Expected:**
- Error: "Missing command to run"
- Usage hint displayed

---

### Test: Permission Denied

**Steps:**
```bash
# As non-root user
barn run --offline touch /root/test.txt
```

**Expected:**
- Job runs and fails
- Error captured in job's error file
- Exit code is non-zero

---

## Test Checklist

Use this checklist before each release:

### Basic Functionality
- [ ] `barn --version` shows correct version
- [ ] `barn --help` displays usage
- [ ] `barn service start/stop/status` works
- [ ] `barn run <cmd>` executes commands
- [ ] `barn status` lists jobs
- [ ] `barn describe <id>` shows job details
- [ ] `barn kill <id>` terminates jobs
- [ ] `barn clean` removes old jobs

### Output Formats
- [ ] Human-readable output is formatted correctly
- [ ] JSON output is valid
- [ ] XML output is valid

### Platform-Specific
- [ ] Linux: systemd integration works
- [ ] macOS: launchd integration works
- [ ] Windows: Service Control Manager works

### Error Handling
- [ ] Invalid commands show helpful errors
- [ ] Missing service shows clear message
- [ ] Permission errors are handled gracefully

### Recovery
- [ ] Service recovers from crash
- [ ] Orphaned jobs are detected
- [ ] State files are not corrupted

---

## Reporting Issues

When reporting bugs found during manual testing:

1. **Environment**: OS, version, architecture
2. **Steps to reproduce**: Exact commands run
3. **Expected behavior**: What should happen
4. **Actual behavior**: What actually happened
5. **Logs**: Relevant output and log files
6. **Screenshots**: If applicable

Submit issues at: https://github.com/samson-media/barn/issues

---

## Next Steps

- [Automated Testing](testing-automated.md) - Automated test suite
- [Development Guide](development.md) - Development setup
