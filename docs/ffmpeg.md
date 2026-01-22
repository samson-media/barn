# FFmpeg Integration Guide

This guide covers FFmpeg-specific behavior when running jobs with Barn.

## Output Streams (stdout vs stderr)

**Important:** FFmpeg outputs progress and status information to **stderr**, not stdout. This is by design.

### Why FFmpeg Uses stderr

FFmpeg is designed to support piping video data through stdout:

```bash
# Example: pipe video to another command
ffmpeg -i input.mp4 -f mp4 - | another_command
```

To make this work, FFmpeg sends:
- **stdout**: Raw video/audio data (when outputting to `-` or pipe)
- **stderr**: All informational messages (version, configuration, progress, warnings, errors)

### What This Means for Barn

When you run an FFmpeg job with Barn:

```bash
barn run -- ffmpeg -i input.mkv -c:v libx264 output.mp4
```

The logs will show:
- `stdout.log`: Empty (video goes to output file)
- `stderr.log`: All FFmpeg output (version info, progress, statistics)

Example `barn describe --logs` output:
```json
{
  "logs": {
    "stdout": "",
    "stderr": "ffmpeg version 7.0...\nframe=1000 fps=120 q=28.0 size=50000kB..."
  }
}
```

This is **correct behavior**, not a bug.

### Common FFmpeg Patterns

#### Transcoding to File

```bash
barn run --tag=transcode -- ffmpeg -y -i input.mkv -c:v libx264 -crf 23 output.mp4
```
- stdout: empty
- stderr: progress and statistics

#### Hardware-Accelerated Encoding (macOS)

```bash
barn run --tag=transcode -- ffmpeg -y -i input.mkv -c:v h264_videotoolbox -b:v 4M output.mp4
```

#### Analyzing Media with ffprobe

```bash
barn run --tag=analyze -- ffprobe -v quiet -print_format json -show_format -show_streams input.mp4
```
- stdout: JSON output (when using `-print_format json`)
- stderr: errors only (when using `-v quiet`)

### Monitoring FFmpeg Jobs

To watch FFmpeg progress in real-time:

```bash
# Watch the stderr log
tail -f /tmp/barn/jobs/<job-id>/logs/stderr.log

# Or use barn describe with --logs
watch -n 2 'barn describe <job-id> --logs --output=json | jq .logs.stderr'
```

### FFmpeg Exit Codes

| Exit Code | Meaning |
|-----------|---------|
| 0 | Success |
| 1 | Generic error (check stderr for details) |
| 69 | Invalid data in input |
| 183 | Output file already exists (use `-y` to overwrite) |

### Recommended FFmpeg Flags for Barn

```bash
ffmpeg \
  -y                    # Overwrite output without asking
  -threads 0            # Auto-detect optimal thread count
  -progress pipe:2      # Send progress to stderr (default)
  -stats                # Show encoding statistics
  -loglevel info        # Show info level logs (default)
  input.mp4 output.mp4
```

### Suppressing FFmpeg Output

If you want minimal output:

```bash
ffmpeg -y -loglevel error -i input.mp4 output.mp4
```

Or for completely silent operation:

```bash
ffmpeg -y -loglevel quiet -i input.mp4 output.mp4
```

Note: Even with `-loglevel quiet`, fatal errors still go to stderr.
