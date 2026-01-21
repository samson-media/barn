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

## Barn command

This project creates a simple CLI commandlet with sub-comands.

- `barn service {start|stop|resart|reload|status|logs"}"` - manages the barn service
- `barn run {command}` - starts job
- `barn status` - gets the status of all jobs running
- `barn describe {jobId}` - collects all the info from /tmp and display it
- `barn kill {jobId}` - kills a job
- `barn clean` - performs clean up

barn is just one binary for both the service and the client. the client uses IPC to communicate with the service.

clients typically access barn over ssh:
- fetch from webdav server: `barn run --output=json curl -u username:password -o /tmp/barn/input/video.mp4 https://example.com/dav/remote-file.ext`
- analyse file with ffmpeg: `barn run --output=json curl ffprobe -i /tmp/barn/input/video.mp4`
- get status of the jobs `bar status --output=json`

barn's output defaults to a human-readable format, but also supports xml or json via the --output={json|xml} cli option.

### Managing the service

There needs to be a barn service in order to run jobs (unless you use --offline). You manage the service using the `barn service` sub command.
- `barn service status"` - check the status of the service. up/down, process id etc.
- `barn service start"` - starts barn service process
- `barn service stop"` - stops/kills the barn service
- `barn service restart"` - restarts the barn service
- `barn service reload"` - reloads the configuration and soft restarts the barn service

### Adding jobs

`barn run --output=json --tag=downloading <cmd>}` takes in a command to run, starts a new process/thread, outputs the status of that job:

| File          | Description                                            |
|---------------|--------------------------------------------------------|
| `job_id`      | the job of the job                                     |
| `state`       | `queued`, `running`, `succeeded`, `failed`, `canceled` |
| `tag`         | user defined string                                    |
| `created_at`  | Job creation timestamp                                 |
| `started_at`  | Execution start timestamp                              |
| `finished_at` | Execution end timestamp                                |
| `exit_code`   | '-' if still running, `0` or symbolic error code       |
| `error`       | Human-readable failure reason                          |
| `pid`         | OS process ID (best-effort)                            |
| `heartbeat`   | Last liveness update                                   |

Users can tag job for the purpose of filtering using the --tag=string CLI option.

Developers can use the --offline command line option to test how the service behaves eg creating tmp files etc. This runs the same code as the service but without having to run the service

> barn's output defaults to a human-readable format, but also supports xml or json via the --output={json|xml} cli option.

### Get status of jobs

`bar status` gets the status of all jobs found in /tmp.

> barn's output defaults to a human-readable format, but also supports xml or json via the --output={json|xml} cli option.


### Get Details of a job
`barn describe {jobId}` - collects all the info from /tmp and display it

> barn's output defaults to a human-readable format, but also supports xml or json via the --output={json|xml} cli option.

---


### Stop a job
`barn kill {jobId}` - kills a job process

> barn's output defaults to a human-readable format, but also supports xml or json via the --output={json|xml} cli option.


### Stop a job
`barn kill {jobId}` - kills a job

> barn's output defaults to a human-readable format, but also supports xml or json via the --output={json|xml} cli option.


### Clean up jobs
`barn clean` - removes completed jobs and cleans up old jobs (older than max_age config) with errors from /tmp

> barn's output defaults to a human-readable format, but also supports xml or json via the --output={json|xml} cli option.


## Documentation

- [Barn Temporary Directory Layout](docs/jobs.md) describes how barn tracks jobs and storage