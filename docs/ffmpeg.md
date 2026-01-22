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
  "id": "job-a1b2c3d4",
  "state": "queued",
  "command": ["ffmpeg", "-y", "-progress", "pipe:1", "-threads", "0", "-i", "input.mkv", "-c:v", "libx264", "output.mp4"],
  "createdAt": "2026-01-22T10:00:00.000Z",
  "pid": null,
  "exitCode": null
}
```

Save the `id` field for subsequent commands.

### Step 2: Poll for Status

Check if the job is still running:

```bash
barn describe job-a1b2c3d4 --output=json
```

Response while running:
```json
{
  "id": "job-a1b2c3d4",
  "state": "running",
  "pid": 12345,
  "exitCode": null,
  "startedAt": "2026-01-22T10:00:01.000Z",
  "finishedAt": null
}
```

Response when complete:
```json
{
  "id": "job-a1b2c3d4",
  "state": "succeeded",
  "pid": 12345,
  "exitCode": 0,
  "startedAt": "2026-01-22T10:00:01.000Z",
  "finishedAt": "2026-01-22T10:05:30.000Z"
}
```

### Step 3: Get Progress (While Running)

Use `--logs` to include stdout/stderr in the response:

```bash
barn describe job-a1b2c3d4 --logs --output=json
```

Response:
```json
{
  "id": "job-a1b2c3d4",
  "state": "running",
  "logs": {
    "stdout": "frame=5000\nfps=120.5\nspeed=2.5x\nprogress=continue\n",
    "stderr": "ffmpeg version 7.0..."
  }
}
```

Parse the `logs.stdout` field to extract progress. Look for:
- `frame=N` - current frame number
- `fps=N` - encoding speed in frames per second
- `speed=Nx` - encoding speed relative to realtime
- `out_time=HH:MM:SS` - current output timestamp
- `progress=continue` or `progress=end`

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
  "id": "job-a1b2c3d4",
  "state": "failed",
  "exitCode": 1,
  "error": "Process exited with code 1",
  "logs": {
    "stdout": "",
    "stderr": "input.mkv: No such file or directory"
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
