# Barn Job Daemon (Cross-Platform)

A **cross-platform service** for managing long-running jobs that involve
downloading and uploading media via WebDAV, using FFmpeg to analyse and transcode video, with strong guarantees around durability,
observability, and crash recovery.

This project is designed to run as a **real OS service / daemon** on:

- ✅ Windows (Windows Service)
- ✅ macOS (launchd)
- ✅ Linux (systemd)

The daemon is **not HTTP-bound**.  
It runs independently in the background and exposes control via a local CLI / RPC interface.

---

## Why This Exists

Running FFmpeg jobs over SSH or ad-hoc scripts quickly breaks down when you need:

- Multiple concurrent jobs
- Reliable handling of failures
- Recovery after crashes or restarts
- Cross-platform support
- Programmatic job inspection and control

This project treats FFmpeg jobs as **first-class managed units of work**, not shell commands.

---

## Core Goals

- Run FFmpeg jobs reliably in the background
- Survive SSH disconnects, restarts, and crashes
- Explicitly model job state and failure reasons
- Provide durable, inspectable job state
- Work consistently on Windows, Linux, and macOS
- Avoid coupling to HTTP or web frameworks

---

## Installation

### Quick Install (Recommended)

#### MacOS - Homebrew

```bash
brew install samson-media/tap/barn
```


#### Linux / MacOS 

The installer scripts download the latest release, install the binary, and set up barn to run as a service on startup.

```bash
curl -fsSL https://raw.githubusercontent.com/samson-media/barn/main/install.sh | sh
```

#### Windows Installer (setup.exe)

Download the latest `setup-barn-vX.X.X-windows-x64.exe` from the [releases page](https://github.com/samson-media/barn/releases/latest).



### Alternative Installation Methods

### Windows (PowerShell as Administrator)

The installer scripts download the latest release, install the binary, and set up barn to run as a service on startup.

```powershell
iwr -useb https://raw.githubusercontent.com/samson-media/barn/main/install.ps1 | iex
```

### Manual Download

**MacOS (ARM64):**
```bash
curl -L -o barn https://github.com/samson-media/barn/releases/latest/download/barn-macos-arm64
chmod +x barn
sudo mv barn /usr/local/bin/
```

**Linux (x64):**
```bash
curl -L -o barn https://github.com/samson-media/barn/releases/latest/download/barn-linux-x64
chmod +x barn
sudo mv barn /usr/local/bin/
```

**Linux (ARM64):**
```bash
curl -L -o barn https://github.com/samson-media/barn/releases/latest/download/barn-linux-arm64
chmod +x barn
sudo mv barn /usr/local/bin/
```

**Windows (PowerShell):**
```powershell
New-Item -ItemType Directory -Force -Path "$env:LOCALAPPDATA\Programs\barn"
Invoke-WebRequest -Uri "https://github.com/samson-media/barn/releases/latest/download/barn-windows-x64.exe" -OutFile "$env:LOCALAPPDATA\Programs\barn\barn.exe"
$path = [Environment]::GetEnvironmentVariable("Path", "User")
if ($path -notlike "*$env:LOCALAPPDATA\Programs\barn*") {
    [Environment]::SetEnvironmentVariable("Path", "$path;$env:LOCALAPPDATA\Programs\barn", "User")
}
```

### Setting Up the Service (Manual Install)

If you installed manually, you need to set up the service to run on startup:

**Linux (systemd):**
```bash
barn service install
sudo systemctl enable barn
sudo systemctl start barn
```

**macOS (launchd):**
```bash
barn service install
```

**Windows (as Administrator):**
```powershell
barn service install
barn service start
```

### Verify Installation

```bash
barn --version
barn service status
```

---

## Barn command

This project creates a simple CLI commandlet with sub-comands.

- `barn service {start|stop|restart|reload|status|logs}` - manages the barn service
- `barn run {command}` - starts job
- `barn status` - gets the status of all jobs running
- `barn describe {jobId}` - collects all the info from /tmp and display it
- `barn usage {jobId}` - shows resource usage (CPU, memory, disk) for a job
- `barn kill {jobId}` - kills a job
- `barn clean` - performs clean up
- `barn config install` - installs default configuration files
- `barn config show` - shows effective configuration

barn is just one binary for both the service and the client. the client uses IPC to communicate with the service.

clients typically access barn over ssh:
- fetch from webdav server: `barn run --output=json curl -u username:password -o /tmp/barn/input/video.mp4 https://example.com/dav/remote-file.ext`
- analyse file with ffmpeg: `barn run --output=json ffprobe -i /tmp/barn/input/video.mp4`
- get status of the jobs `barn status --output=json`

barn's output defaults to a human-readable format, but also supports xml or json via the --output={json|xml} cli option.

### Managing the service

There needs to be a barn service in order to run jobs (unless you use --offline). You manage the service using the `barn service` sub command.
- `barn service status` - check the status of the service. up/down, process id etc.
- `barn service start` - starts barn service process
- `barn service stop` - stops/kills the barn service
- `barn service restart` - restarts the barn service
- `barn service reload` - reloads the configuration and soft restarts the barn service

**Example: `barn service status --output=json`**
```json
{
  "status" : "running",
  "running" : true,
  "pid" : 17474,
  "uptime_seconds" : 16,
  "started_at" : "2026-01-22T01:48:41.869Z",
  "jobs" : {
    "running" : 0,
    "queued" : 1,
    "succeeded" : 1,
    "failed" : 0,
    "canceled" : 0,
    "killed" : 0,
    "total" : 2
  },
  "data_dir" : "/tmp/barn"
}
```

### Adding jobs

`barn run --output=json --tag=downloading <cmd>` takes in a command to run, starts a new process/thread, outputs the status of that job:

| Field         | Description                                            |
|---------------|--------------------------------------------------------|
| `job_id`      | the job of the job                                     |
| `state`       | `queued`, `running`, `succeeded`, `failed`, `canceled` |
| `load_level`  | `HIGH`, `MEDIUM`, or `LOW` (auto-detected or manual)   |
| `tag`         | user defined string                                    |
| `created_at`  | Job creation timestamp                                 |
| `started_at`  | Execution start timestamp                              |
| `finished_at` | Execution end timestamp                                |
| `exit_code`   | '-' if still running, `0` or symbolic error code       |
| `error`       | Human-readable failure reason                          |
| `pid`         | OS process ID (best-effort)                            |
| `heartbeat`   | Last liveness update                                   |

Users can tag jobs for the purpose of filtering using the `--tag=string` CLI option.

Jobs are automatically assigned a **load level** based on the command being run (see [Load Levels](#load-levels)). You can override this with `--load-level={high|medium|low}`.

Developers can use the `--offline` command line option to test how the service behaves (e.g., creating tmp files). This runs the same code as the service but without having to run the service.

**Example: `barn run --output=json -- echo "Hello World"`**
```json
{
  "id" : "job-6d368040",
  "state" : "QUEUED",
  "loadLevel" : "MEDIUM",
  "command" : [ "echo", "Hello World" ],
  "tag" : null,
  "createdAt" : "2026-01-22T01:48:46.749629Z",
  "startedAt" : null,
  "finishedAt" : null,
  "exitCode" : null,
  "error" : null,
  "pid" : null,
  "heartbeat" : null,
  "retryCount" : 0,
  "retryAt" : null,
  "terminal" : false,
  "running" : false,
  "queued" : true
}
```

### Get status of jobs

`barn status` gets the status of all jobs found in /tmp.

**Example: `barn status`**
```
ID            STATE      LOAD    TAG  CREATED              EXIT  PID
job-6d368040  succeeded  medium  -    2026-01-22 01:48:46  0     17486

Total: 1 job (1 succeeded)
```

**Example: `barn status --output=json`**
```json
{
  "jobs" : [ {
    "id" : "job-6d368040",
    "state" : "SUCCEEDED",
    "loadLevel" : "MEDIUM",
    "command" : [ "echo", "Hello World" ],
    "tag" : null,
    "createdAt" : "2026-01-22T01:48:46.739979Z",
    "startedAt" : "2026-01-22T01:48:47.161895Z",
    "finishedAt" : "2026-01-22T01:48:47.184605Z",
    "exitCode" : 0,
    "error" : null,
    "pid" : 17486,
    "heartbeat" : "2026-01-22T01:48:47.161895Z",
    "retryCount" : 0,
    "retryAt" : null,
    "running" : false,
    "terminal" : true,
    "queued" : false
  } ],
  "summary" : {
    "total" : 1,
    "queued" : 0,
    "running" : 0,
    "succeeded" : 1,
    "failed" : 0,
    "canceled" : 0,
    "killed" : 0
  }
}
```


### Get Details of a job
`barn describe {jobId}` - collects all the info from /tmp and display it

**Example: `barn describe job-6d368040 --output=json`**
```json
{
  "id" : "job-6d368040",
  "state" : "succeeded",
  "tag" : null,
  "command" : [ "echo", "Hello World" ],
  "createdAt" : "2026-01-22T01:48:46.739979Z",
  "startedAt" : "2026-01-22T01:48:47.161895Z",
  "finishedAt" : "2026-01-22T01:48:47.184605Z",
  "pid" : 17486,
  "exitCode" : 0,
  "error" : null,
  "heartbeat" : "2026-01-22T01:48:47.161895Z",
  "retryCount" : 0,
  "retryAt" : null,
  "paths" : {
    "jobDir" : "/tmp/barn/jobs/job-6d368040",
    "workDir" : "/tmp/barn/jobs/job-6d368040/work",
    "logsDir" : "/tmp/barn/jobs/job-6d368040/logs"
  }
}
```

---


### Stop a job
`barn kill {jobId}` - kills a job process

### Clean up jobs
`barn clean` - removes completed jobs and cleans up old jobs (older than max_age config) from /tmp

---

## Load Levels

Barn supports **per-level job limits** to allow concurrent execution of different types of jobs. For example, you can run multiple downloads while transcoding without overwhelming the system.

### Load Level Types

| Level | Default Max | Use Case |
|-------|-------------|----------|
| HIGH | 2 | CPU/GPU intensive (transcoding, encoding) |
| MEDIUM | 8 | General purpose commands |
| LOW | 32 | Network/IO intensive (downloads, uploads) |

### Auto-Classification

Jobs are automatically classified based on whitelist files in `/etc/barn/`:

- `high.load` - ffmpeg, ffprobe, handbrake, x264, x265, etc.
- `medium.load` - general commands (default if not matched)
- `low.load` - curl, wget, rclone, rsync, aria2c, etc.

### Manual Override

Force a specific load level for a job:

```bash
# Run ffmpeg as LOW load (bypass normal HIGH classification)
barn run --load-level=low -- ffmpeg -i input.mp4 output.mp4

# Run curl as HIGH load (override normal LOW classification)
barn run --load-level=high -- curl -O https://example.com/file.zip
```

### Installing Default Config

Install default configuration files including load level whitelists:

```bash
# Preview what would be installed
barn config install --show

# Install to system config directory (requires sudo on Linux/macOS)
sudo barn config install --force
```

See [Configuration](docs/config.md) for detailed load level configuration options.

---

## Documentation

- [FFmpeg Integration Guide](docs/ffmpeg.md) explains FFmpeg-specific behavior (stdout/stderr, exit codes, recommended flags)
- [Barn Temporary Directory Layout](docs/jobs.md) describes how barn tracks jobs and storage
- [Barn Configuration](docs/config.md) describes configuration options, retry behavior, and cleanup policies
- [Development Guide](docs/development.md) covers local development setup and building from source
- [Coding Standards](docs/coding-standards.md) defines code style, conventions, and quality enforcement
- [Releasing](docs/releasing.md) describes version management and the release process
- [CI/CD](docs/ci-cd.md) documents the GitHub Actions workflows for building and releasing
- [Distribution](docs/distribution.md) covers installation methods (Windows setup, Linux binary, Homebrew)
- [Updating](docs/auto-update.md) explains how to update Barn on each platform
- [Automated Testing](docs/testing-automated.md) covers the test suite, running tests, and CI integration
- [Manual Testing](docs/testing-manual.md) provides step-by-step manual test procedures
