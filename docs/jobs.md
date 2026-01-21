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
```

### Notes

- Append-only
- Human-readable
- `progress.log` contains FFmpeg `-progress` output

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
