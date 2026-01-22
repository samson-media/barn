# FFmpeg Integration Guide

This guide covers FFmpeg-specific behavior when running jobs with Barn.

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
