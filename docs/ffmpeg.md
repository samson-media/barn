# FFmpeg Integration Guide

This guide covers FFmpeg-specific behavior when running jobs with Barn.

## Programmatic Workflow

This section shows the complete process for running FFmpeg jobs programmatically (e.g., from a script or application over SSH).

### Step 1: Submit the Job

```bash
barn run --output=json -- ffmpeg -y -progress pipe:1 -threads 0 -i input.mkv -c:v libx264 output.mp4
```

Response:
```json
{
  "id" : "job-cbe3e07f",
  "state" : "QUEUED",
  "command" : [ "ffmpeg", "-y", "-progress", "pipe:1", "-threads", "0", "-i", "input.mkv", "-c:v", "libx264", "output.mp4" ],
  "tag" : null,
  "createdAt" : "2026-01-22T10:13:34.121846Z",
  "startedAt" : null,
  "finishedAt" : null,
  "exitCode" : null,
  "error" : null,
  "pid" : null,
  "heartbeat" : null,
  "retryCount" : 0,
  "retryAt" : null
}
```

Save the `id` field for subsequent commands.

### Step 2: Poll for Status

Check if the job is still running:

```bash
barn describe job-cbe3e07f --output=json
```

Response while running:
```json
{
  "id" : "job-cbe3e07f",
  "state" : "running",
  "command" : [ "ffmpeg", "-y", "-progress", "pipe:1", "-threads", "0", "-i", "input.mkv", "-c:v", "libx264", "output.mp4" ],
  "createdAt" : "2026-01-22T10:13:34.121180Z",
  "startedAt" : "2026-01-22T10:13:34.347543Z",
  "finishedAt" : null,
  "pid" : 70923,
  "exitCode" : null,
  "error" : null,
  "heartbeat" : "2026-01-22T10:13:34.347543Z",
  "retryCount" : 0,
  "retryAt" : null,
  "paths" : {
    "jobDir" : "/tmp/barn/jobs/job-cbe3e07f",
    "workDir" : "/tmp/barn/jobs/job-cbe3e07f/work",
    "logsDir" : "/tmp/barn/jobs/job-cbe3e07f/logs"
  }
}
```

Response when complete:
```json
{
  "id" : "job-cbe3e07f",
  "state" : "succeeded",
  "command" : [ "ffmpeg", "-y", "-progress", "pipe:1", "-threads", "0", "-i", "input.mkv", "-c:v", "libx264", "output.mp4" ],
  "createdAt" : "2026-01-22T10:13:34.121180Z",
  "startedAt" : "2026-01-22T10:13:34.347543Z",
  "finishedAt" : "2026-01-22T10:13:34.394959Z",
  "pid" : 70923,
  "exitCode" : 0,
  "error" : null,
  "heartbeat" : "2026-01-22T10:13:34.347543Z",
  "retryCount" : 0,
  "retryAt" : null,
  "paths" : {
    "jobDir" : "/tmp/barn/jobs/job-cbe3e07f",
    "workDir" : "/tmp/barn/jobs/job-cbe3e07f/work",
    "logsDir" : "/tmp/barn/jobs/job-cbe3e07f/logs"
  }
}
```

### Step 3: Get Progress (While Running)

Use `--logs` to include stdout/stderr in the response:

```bash
barn describe job-cbe3e07f --logs --output=json
```

Response:
```json
{
  "id" : "job-cbe3e07f",
  "state" : "running",
  "pid" : 70923,
  "exitCode" : null,
  "paths" : {
    "jobDir" : "/tmp/barn/jobs/job-cbe3e07f",
    "workDir" : "/tmp/barn/jobs/job-cbe3e07f/work",
    "logsDir" : "/tmp/barn/jobs/job-cbe3e07f/logs"
  },
  "logs" : {
    "stdout" : "frame=90\nfps=0.00\nstream_0_0_q=-1.0\nbitrate=48.9kbits/s\ntotal_size=17935\nout_time_us=2933333\nout_time_ms=2933333\nout_time=00:00:02.933333\ndup_frames=0\ndrop_frames=0\nspeed=94.9x\nprogress=continue",
    "stderr" : "ffmpeg version 8.0.1 Copyright (c) 2000-2025 the FFmpeg developers..."
  }
}
```

Parse the `logs.stdout` field to extract progress. Key fields:
- `frame=N` - current frame number
- `fps=N` - encoding speed in frames per second
- `speed=Nx` - encoding speed relative to realtime
- `out_time=HH:MM:SS` - current output timestamp
- `bitrate=N` - current bitrate
- `progress=continue` - job still running
- `progress=end` - job complete

### Step 4: Handle Completion

Check `state` and `exitCode`:

| State | exitCode | Meaning |
|-------|----------|---------|
| `succeeded` | 0 | Job completed successfully |
| `failed` | non-zero | Job failed (check `error` and `logs.stderr`) |
| `killed` | null | Job was killed by user |

Example error response:
```json
{
  "id" : "job-12860505",
  "state" : "failed",
  "command" : [ "ffmpeg", "-y", "-progress", "pipe:1", "-i", "/invalid/path.mkv", "output.mp4" ],
  "pid" : 70875,
  "exitCode" : 1,
  "error" : "Process exited with code 1",
  "paths" : {
    "jobDir" : "/tmp/barn/jobs/job-12860505",
    "workDir" : "/tmp/barn/jobs/job-12860505/work",
    "logsDir" : "/tmp/barn/jobs/job-12860505/logs"
  },
  "logs" : {
    "stdout" : "",
    "stderr" : "ffmpeg version 8.0.1...\n/invalid/path.mkv: No such file or directory"
  }
}
```

### Step 5: Kill a Running Job (Optional)

To stop a running job:

**Command:**
```bash
barn kill job-cbe3e07f --output=json
```

**Output:**
```json
{
  "id" : "job-cbe3e07f",
  "state" : "killed",
  "pid" : 70923,
  "exitCode" : null,
  "error" : "Job killed by user"
}
```

---

### Summary

| Step | Command | Key Output Fields |
|------|---------|-------------------|
| Submit | `barn run --output=json -- ffmpeg ...` | `id`, `state` |
| Poll | `barn describe <id> --output=json` | `state`, `exitCode` |
| Progress | `barn describe <id> --logs --output=json` | `logs.stdout`, `logs.stderr` |
| Kill | `barn kill <id> --output=json` | `state` |

**State values:** `queued` → `running` → `succeeded` / `failed` / `killed`

---

## Recommended FFmpeg Command

Use `-progress pipe:1` to output machine-readable progress to stdout:

```bash
barn run -- ffmpeg -y -progress pipe:1 -threads 0 -i input.mkv -c:v libx264 output.mp4
```

This outputs structured key=value pairs to `stdout.log`:

```
frame=100
fps=120.5
stream_0_0_q=28.0
bitrate=4500.0kbits/s
total_size=5000000
out_time_us=4000000
out_time_ms=4000000
out_time=00:00:04.000000
dup_frames=0
drop_frames=0
speed=3.5x
progress=continue
```

The final block will have `progress=end` when complete.

Human-readable output (version info, warnings, errors) still goes to `stderr.log`.

### Recommended Flags

| Flag | Purpose |
|------|---------|
| `-y` | Overwrite output without asking |
| `-progress pipe:1` | Output machine-readable progress to stdout |
| `-threads 0` | Auto-detect optimal thread count |

### Common Patterns

#### Transcoding to File

```bash
barn run --tag=transcode -- ffmpeg -y -progress pipe:1 -threads 0 -i input.mkv -c:v libx264 -crf 23 output.mp4
```

#### Hardware-Accelerated Encoding (macOS)

```bash
barn run --tag=transcode -- ffmpeg -y -progress pipe:1 -threads 0 -i input.mkv -c:v h264_videotoolbox -b:v 4M output.mp4
```

#### Analyzing Media with ffprobe

```bash
barn run --tag=analyze -- ffprobe -v quiet -print_format json -show_format -show_streams input.mp4
```
- stdout: JSON output (when using `-print_format json`)
- stderr: errors only (when using `-v quiet`)

## Output Streams (stdout vs stderr)

By default (without `-progress pipe:1`), FFmpeg outputs progress to **stderr**, not stdout. This is by design to allow piping video data through stdout:

```bash
# FFmpeg can pipe video to another command via stdout
ffmpeg -i input.mp4 -f mp4 - | another_command
```

Without `-progress pipe:1`:
- `stdout.log`: Empty (video goes to output file)
- `stderr.log`: All FFmpeg output (version info, progress, statistics)

This is correct behavior, not a bug. Use `-progress pipe:1` to get progress in stdout.

## Monitoring FFmpeg Jobs

To check FFmpeg progress:

```bash
# Get current progress (human-readable)
barn describe <job-id> --logs

# Get current progress (JSON for parsing)
barn describe <job-id> --logs --output=json
```

The `logs.stdout` field contains the `-progress pipe:1` output with fields like `frame=`, `fps=`, `speed=`, and `progress=`.

## FFmpeg Exit Codes

| Exit Code | Meaning |
|-----------|---------|
| 0 | Success |
| 1 | Generic error (check stderr for details) |
| 69 | Invalid data in input |
| 183 | Output file already exists (use `-y` to overwrite) |

## Suppressing FFmpeg Output

If you want minimal output:

```bash
ffmpeg -y -progress pipe:1 -loglevel error -i input.mp4 output.mp4
```

Or for completely silent operation (progress only, no errors):

```bash
ffmpeg -y -progress pipe:1 -loglevel quiet -i input.mp4 output.mp4
```

Note: Even with `-loglevel quiet`, fatal errors still go to stderr.
