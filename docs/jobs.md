# Barn Temporary Directory Layout

This document describes the **filesystem-only job state layout** used by the Barn job daemon.

The design intentionally avoids databases and relies entirely on **atomic filesystem operations**
to provide durability, observability, and crash recovery.

This layout works consistently across:

- Linux
- macOS
- Windows

All paths are rooted under the OS temporary directory.

---

## Base Directory

```
/tmp/barn/
```

> On Windows this resolves to:
>
> ```
> %TEMP%\barn\
> ```
>
> In Java, always resolve this via:
>
> ```java
> Paths.get(System.getProperty("java.io.tmpdir"), "barn");
> ```

---

## Top-Level Structure

```
/tmp/barn/
  jobs/
  locks/
  logs/
```

### Purpose

| Directory | Purpose |
|---------|--------|
| `jobs/` | Job definitions, state, logs, and artifacts |
| `locks/` | Scheduler and per-job locks |
| `logs/` | Daemon-level logs |

---

## Jobs Directory

Each job is fully self-contained.

```
/tmp/barn/jobs/
  <job-id>/
```

Example:

```
/tmp/barn/jobs/job-9f83c/
```

---

## Per-Job Directory Layout

```
/tmp/barn/jobs/<job-id>/
  manifest.json
  state
  stage
  load_level
  pid
  heartbeat
  exit_code
  error
  created_at
  started_at
  finished_at

  work/
    input/
    output/

  logs/
    stdout.log
    stderr.log
    progress.log
    upload.log
```

---

## Job Metadata Files

### `manifest.json`
Immutable job definition.

Contains:
- Input WebDAV URL
- Output WebDAV URL
- Job command line arguments arguments
- Retry policy
- Optional metadata

Written once at job creation.

---

### Lifecycle State Files

| File          | Description                                            |
|---------------|--------------------------------------------------------|
| `state`       | `queued`, `running`, `succeeded`, `failed`, `canceled` |
| `tag`         | user defined string                                    |
| `load_level`  | `HIGH`, `MEDIUM`, or `LOW` (for scheduling priority)   |
| `created_at`  | Job creation timestamp                                 |
| `started_at`  | Execution start timestamp                              |
| `finished_at` | Execution end timestamp                                |
| `exit_code`   | `0` or symbolic error code                             |
| `error`       | Human-readable failure reason                          |
| `pid`         | OS process ID (best-effort)                            |
| `heartbeat`   | Last liveness update                                   |
| `retry_count` | Current retry attempt (0, 1, 2, ...)                   |
| `retry_at`    | Timestamp for next retry attempt                       |
| `retry_history` | Log of previous attempt exit codes and errors        |

All values are stored as **plain text**.

---

## Working Directory

```
work/
  input/
    source.tmp
  output/
    result.mp4
```

- Temporary artifacts only
- Safe to delete after completion
- Never used for scheduling decisions

---

## Logs Directory

```
logs/
  stdout.log
  stderr.log
  progress.log
  upload.log
  usage.csv
```

### Notes

- Append-only
- Human-readable
- `progress.log` contains FFmpeg `-progress` output
- `usage.csv` contains resource usage samples (see below)

---

## Resource Usage Monitoring

During job execution, Barn monitors and logs resource usage metrics.

### Usage Log File

```
logs/usage.csv
```

### CSV Format

```csv
timestamp,cpu_percent,memory_bytes,disk_bytes,gpu_percent,gpu_memory_bytes
2026-01-21T18:42:05Z,45.20,134217728,52428800,,
2026-01-21T18:42:10Z,52.30,142606336,58720256,,
```

### Metrics Collected

| Metric | Description |
|--------|-------------|
| `timestamp` | ISO-8601 timestamp of the sample |
| `cpu_percent` | Process CPU usage as percentage |
| `memory_bytes` | Process resident memory in bytes |
| `disk_bytes` | Working directory disk usage in bytes |
| `gpu_percent` | GPU utilization (if nvidia-smi available) |
| `gpu_memory_bytes` | GPU memory usage (if nvidia-smi available) |

### Collection Interval

Metrics are sampled every 5 seconds by default.

### Viewing Usage Data

Use the `barn usage` command to view resource usage:

```bash
# Show usage summary and recent samples
barn usage <job-id> --offline

# Show summary statistics only
barn usage <job-id> --offline --summary

# Export raw CSV data
barn usage <job-id> --offline --csv

# Limit to last N samples
barn usage <job-id> --offline --limit 100

# Output as JSON
barn usage <job-id> --offline --output json
```

### Example Output

```
Resource Usage for Job: job-9f83c
================================================================================

Summary (120 samples):
  CPU:     avg=45.2%  max=98.5%  min=2.1%
  Memory:  avg=128.5 MB  max=256.0 MB  min=64.0 MB
  Disk:    max=1.2 GB

Recent Samples:
TIMESTAMP                  CPU %       MEMORY          DISK
2026-01-21 18:40:05        45.2       128.5 MB        50.0 MB
2026-01-21 18:40:10        52.3       135.8 MB        52.4 MB
...
```

---

## Locks Directory

```
/tmp/barn/locks/
  scheduler.lock
  job-<job-id>.lock
```

### Usage

- `scheduler.lock` prevents multiple schedulers
- `job-<job-id>.lock` prevents duplicate execution
- Created via atomic filesystem operations

---

## Daemon Logs

```
/tmp/barn/logs/
  barn.log
```

Contains daemon-level events, not job output.

---

## Example: Running Job

```
/tmp/barn/jobs/job-9f83c/
  manifest.json
  state        → running
  stage        → transcode
  load_level   → HIGH
  pid          → 12345
  heartbeat    → 2026-01-21T18:42:10Z
  started_at   → 2026-01-21T18:40:01Z

  work/
    input/source.tmp
    output/result.mp4

  logs/
    download.log
    ffmpeg.stdout.log
    ffmpeg.stderr.log
    progress.log
    usage.csv
```

---

## Crash Recovery Strategy

On daemon startup:

1. Scan `/tmp/barn/jobs/*`
2. Identify jobs where:
   - `state = running`
   - `heartbeat` is stale
3. Verify process liveness (best-effort)
4. Mark orphaned jobs as:
   - `state = failed`
   - `exit_code = orphaned_process`
   - write `finished_at`

No database is required.

---

## Design Principles

- Filesystem is the source of truth
- Atomic writes over in-place mutation
- Human-readable state
- Portable across operating systems
- Easy manual inspection and recovery

---

## Summary

This directory structure provides:

- Durable job state
- Clear lifecycle tracking
- Crash-safe recovery
- Cross-platform compatibility
- Zero external dependencies

It forms the backbone of the Barn job execution model.
