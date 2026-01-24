# Barn Configuration

This document describes the configuration file format and options for the Barn job daemon.

---

## Configuration File Location

Barn uses system-wide configuration only:

| Platform | Path |
|----------|------|
| Linux    | `/etc/barn/barn.conf` |
| macOS    | `/etc/barn/barn.conf` |
| Windows  | `%PROGRAMDATA%\barn\barn.conf` |

You can also specify a config file explicitly:

```bash
barn --config /path/to/barn.conf service start
```

---

## Configuration Format

Barn uses a simple key-value format (TOML-like):

```toml
[service]
log_level = "info"
heartbeat_interval_seconds = 5
ipc_socket = "/tmp/barn/barn.sock"

[load_levels]
max_high_jobs = 2
max_medium_jobs = 8
max_low_jobs = 32

[jobs]
default_timeout_seconds = 3600
max_retries = 3
retry_delay_seconds = 30
retry_backoff_multiplier = 2.0

[cleanup]
enabled = true
max_age_hours = 72
cleanup_interval_minutes = 60
keep_failed_jobs = true

[storage]
base_dir = "/tmp/barn"
max_disk_usage_gb = 50
```

---

## Service Settings

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `log_level` | string | `"info"` | Logging verbosity: `debug`, `info`, `warn`, `error` |
| `max_concurrent_jobs` | integer | `4` | **Deprecated.** Use `[load_levels]` instead. |
| `heartbeat_interval_seconds` | integer | `5` | How often running jobs update their heartbeat |
| `ipc_socket` | string | platform-specific | Path to the IPC socket for CLI communication |
| `stale_heartbeat_threshold_seconds` | integer | `30` | Heartbeat age before a job is considered orphaned |

---

## Load Level Settings

Barn supports **per-level job limits** to allow concurrent execution of different types of jobs. For example, you can run multiple downloads (LOW load) while transcoding (HIGH load) without overwhelming the system.

### Configuration

```toml
[load_levels]
max_high_jobs = 2
max_medium_jobs = 8
max_low_jobs = 32
```

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `max_high_jobs` | integer | `2` | Maximum concurrent HIGH load jobs (CPU/GPU intensive) |
| `max_medium_jobs` | integer | `8` | Maximum concurrent MEDIUM load jobs |
| `max_low_jobs` | integer | `32` | Maximum concurrent LOW load jobs (network/IO intensive) |

### Load Level Classification

Jobs are automatically classified into a load level based on **whitelist files** in the config directory. These files use a gitignore-style format.

#### Whitelist File Locations

| Platform | Directory |
|----------|-----------|
| Linux    | `/etc/barn/` |
| macOS    | `/etc/barn/` |
| Windows  | `%PROGRAMDATA%\barn\` |

#### Whitelist Files

| File | Purpose |
|------|---------|
| `high.load` | CPU/GPU intensive commands (transcoding, encoding) |
| `medium.load` | General purpose commands |
| `low.load` | Network/IO intensive commands (downloads, uploads) |

#### File Format

```
# Comments start with #
# Empty lines are ignored

# Match by executable name (any path)
ffmpeg
curl

# Match by full path
/usr/local/bin/custom-transcoder

# Match any executable in a directory (trailing slash)
/opt/encoders/
```

#### Example `high.load`

```
# High load commands - CPU/GPU intensive (transcoding, encoding)
# Maximum concurrent: 2 (default)
ffmpeg
ffprobe
handbrake
HandBrakeCLI
x264
x265
av1an
svt-av1
```

#### Example `low.load`

```
# Low load commands - Network/IO intensive (downloads, uploads)
# Maximum concurrent: 32 (default)
curl
wget
rclone
rsync
aria2c
scp
sftp
cadaver
```

#### Classification Priority

1. Commands are checked against HIGH whitelist first
2. Then MEDIUM whitelist
3. Then LOW whitelist
4. If no match, defaults to **MEDIUM**

### Manual Load Level Override

You can override the auto-detected load level per job:

```bash
# Force a command to run as LOW load
barn run --load-level=low -- ffmpeg -i input.mp4 output.mp4

# Force a command to run as HIGH load
barn run --load-level=high -- curl -O https://example.com/file.zip
```

### Installing Default Config Files

Use `barn config install` to create default configuration files:

```bash
# Interactive installation (prompts for confirmation)
barn config install

# Force installation without prompts
barn config install --force

# Show what would be installed without writing
barn config install --show
```

This creates:
- `barn.conf` - Main configuration file
- `high.load` - High load whitelist (ffmpeg, etc.)
- `medium.load` - Medium load whitelist (empty)
- `low.load` - Low load whitelist (curl, wget, etc.)

### Backward Compatibility

When `[load_levels]` is not configured:
- The deprecated `max_concurrent_jobs` value is distributed proportionally across levels
- Ratio: 1 HIGH : 4 MEDIUM : 16 LOW
- Example: `max_concurrent_jobs = 21` → HIGH=1, MEDIUM=4, LOW=16

---

## Job Settings

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `default_timeout_seconds` | integer | `3600` | Maximum time a job can run before being killed |
| `max_retries` | integer | `3` | Number of retry attempts for failed jobs |
| `retry_delay_seconds` | integer | `30` | Initial delay before first retry |
| `retry_backoff_multiplier` | float | `2.0` | Multiplier applied to delay after each retry |
| `retry_on_exit_codes` | array | `[]` | Only retry on these exit codes (empty = retry all failures) |

---

## Retry Behavior

Barn implements **exponential backoff** for job retries.

### How Retries Work

1. When a job fails, Barn checks if retries are allowed
2. If `retry_count < max_retries`, the job is re-queued
3. The retry delay increases exponentially:
   ```
   delay = retry_delay_seconds * (retry_backoff_multiplier ^ retry_count)
   ```

### Example Timeline

With default settings (`retry_delay_seconds=30`, `retry_backoff_multiplier=2.0`, `max_retries=3`):

| Attempt | Delay Before Retry |
|---------|-------------------|
| 1st retry | 30 seconds |
| 2nd retry | 60 seconds |
| 3rd retry | 120 seconds |
| (no more retries) | job marked `failed` |

### Retry State Tracking

Retry information is stored in the job directory:

```
/tmp/barn/jobs/<job-id>/
  retry_count      → current retry attempt (0, 1, 2, ...)
  retry_at         → timestamp for next retry attempt
  retry_history    → log of previous attempt exit codes and errors
```

### Per-Job Retry Override

You can override retry settings per job:

```bash
barn run --max-retries=5 --retry-delay=60 ffmpeg -i input.mp4 output.mp4
```

### Disabling Retries

To disable retries for a specific job:

```bash
barn run --max-retries=0 ffmpeg -i input.mp4 output.mp4
```

Or globally in config:

```toml
[jobs]
max_retries = 0
```

---

## Cleanup Settings

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | boolean | `true` | Enable automatic cleanup |
| `max_age_hours` | integer | `72` | Remove jobs older than this |
| `cleanup_interval_minutes` | integer | `60` | How often the cleanup task runs |
| `keep_failed_jobs` | boolean | `true` | Preserve failed jobs for debugging |
| `keep_failed_jobs_hours` | integer | `168` | How long to keep failed jobs (1 week) |

---

## Cleanup Behavior

Barn provides both **automatic** and **manual** cleanup mechanisms.

### Automatic Cleanup

When `cleanup.enabled = true`, the daemon periodically:

1. Scans all jobs in `/tmp/barn/jobs/`
2. Identifies jobs eligible for removal based on:
   - Job state (`succeeded`, `failed`, `canceled`)
   - Age (based on `finished_at` timestamp)
   - Configured retention policies

### What Gets Cleaned

| Job State | Cleanup Rule |
|-----------|--------------|
| `succeeded` | Removed after `max_age_hours` |
| `canceled` | Removed after `max_age_hours` |
| `failed` | Kept for `keep_failed_jobs_hours` if `keep_failed_jobs=true`, otherwise `max_age_hours` |
| `running` | Never automatically removed |
| `queued` | Never automatically removed |

### Manual Cleanup

Run cleanup immediately:

```bash
barn clean
```

With options:

```bash
# Remove all completed jobs regardless of age
barn clean --all

# Remove jobs older than 24 hours
barn clean --older-than=24h

# Dry run - show what would be removed
barn clean --dry-run

# Include failed jobs in cleanup
barn clean --include-failed

# Clean a specific job
barn clean --job-id=job-9f83c
```

### Cleanup Process

For each job being cleaned:

1. Acquire job lock (`/tmp/barn/locks/job-<job-id>.lock`)
2. Verify job is in terminal state
3. Remove job directory (`/tmp/barn/jobs/<job-id>/`)
4. Release and remove lock file
5. Log cleanup action to `/tmp/barn/logs/barn.log`

### Disk Space Protection

When `max_disk_usage_gb` is set, Barn will:

1. Calculate total disk usage of `/tmp/barn/`
2. If usage exceeds limit, trigger aggressive cleanup
3. Remove oldest completed jobs first (FIFO)
4. Log warnings if unable to free sufficient space

---

## Storage Settings

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `base_dir` | string | `/tmp/barn` | Root directory for all Barn data |
| `max_disk_usage_gb` | integer | `50` | Maximum disk space Barn can use |
| `preserve_work_dir` | boolean | `false` | Keep work/input and work/output after job completion |

---

## Environment Variable Overrides

Any config option can be overridden via environment variables:

```bash
BARN_SERVICE_LOG_LEVEL=debug
BARN_JOBS_MAX_RETRIES=5
BARN_CLEANUP_MAX_AGE_HOURS=24
```

Pattern: `BARN_<SECTION>_<KEY>` (uppercase, underscores)

---

## Example Configurations

### Development (Aggressive Cleanup)

```toml
[service]
log_level = "debug"

[load_levels]
max_high_jobs = 1
max_medium_jobs = 2
max_low_jobs = 4

[jobs]
max_retries = 1
default_timeout_seconds = 300

[cleanup]
enabled = true
max_age_hours = 1
cleanup_interval_minutes = 5
keep_failed_jobs = false
```

### Production (Conservative)

```toml
[service]
log_level = "warn"
heartbeat_interval_seconds = 10

[load_levels]
max_high_jobs = 4
max_medium_jobs = 16
max_low_jobs = 64

[jobs]
max_retries = 5
retry_delay_seconds = 60
default_timeout_seconds = 7200

[cleanup]
enabled = true
max_age_hours = 168
keep_failed_jobs = true
keep_failed_jobs_hours = 336

[storage]
max_disk_usage_gb = 200
```

### CI/CD (No Retries, Fast Cleanup)

```toml
[service]
log_level = "info"

[load_levels]
max_high_jobs = 1
max_medium_jobs = 1
max_low_jobs = 1

[jobs]
max_retries = 0
default_timeout_seconds = 600

[cleanup]
enabled = true
max_age_hours = 1
keep_failed_jobs = false
```

---

## Reloading Configuration

To apply config changes without restarting:

```bash
barn service reload
```

This will:

1. Re-read the configuration file
2. Apply changes to cleanup and job settings immediately
3. New jobs will use updated settings
4. Running jobs are not affected

Settings that require a full restart:

- `ipc_socket`
- `base_dir`

---

## Configuration Commands

### Install Default Configuration

Create default configuration files in the system config directory:

```bash
# Interactive installation (prompts for confirmation)
barn config install

# Install without prompts
barn config install --force

# Preview what would be installed
barn config install --show
```

### Validate Configuration

Check your config file for errors:

```bash
barn config validate
barn config validate --config /path/to/barn.conf
```

### Show Configuration

Show effective configuration (with defaults and overrides):

```bash
barn config show
```
