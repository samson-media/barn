# Barn Job Daemon (Cross-Platform)

A **cross-platform service** for managing long-running jobs that involve
downloading and uploading media via WebDAV, using FFmpeg to analyse and transcode video, with strong guarantees around durability,
observability, and crash recovery.

This project is designed to run as a **real OS service / daemon** on:

- ✅ Windows (Windows Service)
- ✅ macOS (launchd)
- ✅ Linux (systemd)

The daemon is **not HTTP-bound**.  
It runs independently in the background and exposes control via a local CLI / IPC interface.

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

