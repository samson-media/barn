# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [0.1.0] - 2026-01-21

### Added
- Initial release
- Job daemon with filesystem-based state management
- Cross-platform support (Windows, macOS, Linux)
- Service management (install, start, stop, status)
- Job management (run, status, describe, kill, clean)
- Resource usage monitoring
- IPC communication between CLI and service
- Shell autocompletion (bash, zsh)
- Retry mechanism with exponential backoff

### Fixed
- Jobs that fail to start now properly transition to FAILED state
- Startup errors are written to stderr.log for visibility

[0.1.0]: https://github.com/samson-media/barn/releases/tag/v0.1.0
