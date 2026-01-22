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

### Complete Example Script

```bash
#!/bin/bash
set -e

INPUT="/path/to/input.mkv"
OUTPUT="/path/to/output.mp4"

# Submit job
RESPONSE=$(barn run --output=json -- ffmpeg -y -progress pipe:1 -threads 0 -i "$INPUT" -c:v libx264 "$OUTPUT")
JOB_ID=$(echo "$RESPONSE" | jq -r '.id')
echo "Started job: $JOB_ID"

# Poll until complete
while true; do
  STATUS=$(barn describe "$JOB_ID" --output=json)
  STATE=$(echo "$STATUS" | jq -r '.state')

  case "$STATE" in
    "queued"|"running")
      # Get progress
      PROGRESS=$(barn describe "$JOB_ID" --logs --output=json | jq -r '.logs.stdout' | grep -o 'speed=[0-9.]*x' | tail -1)
      echo "Status: $STATE $PROGRESS"
      sleep 2
      ;;
    "succeeded")
      echo "Job completed successfully"
      exit 0
      ;;
    "failed")
      ERROR=$(echo "$STATUS" | jq -r '.error')
      echo "Job failed: $ERROR"
      exit 1
      ;;
    *)
      echo "Job ended with state: $STATE"
      exit 1
      ;;
  esac
done
```

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

To watch FFmpeg progress in real-time:

```bash
# Watch the stdout log (with -progress pipe:1)
tail -f /tmp/barn/jobs/<job-id>/logs/stdout.log

# Or use barn describe with --logs
watch -n 2 'barn describe <job-id> --logs --output=json | jq -r .logs.stdout'
```

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
