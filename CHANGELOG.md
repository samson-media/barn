# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [0.1.4] - 2026-01-22

### Added
- Intel Mac (x86_64) support

## [0.1.3] - 2026-01-22

### Fixed
- Debug logs no longer break JSON/XML CLI output (#30)
- Replace SLF4J/logback with custom BarnLogger (disabled for CLI, enabled for service)

### Changed
- Update documentation: remove .deb and AUR references (no longer applicable)
- Update documentation: add Windows setup.exe installation instructions
- Update documentation: clarify auto-update only works with Homebrew

## [0.1.2] - 2026-01-21

### Fixed
- IPC communication fails in native binary due to missing Jackson reflection config

## [0.1.1] - 2026-01-21

### Fixed
- Native binary crashes on startup due to missing picocli reflection config

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

[0.1.4]: https://github.com/samson-media/barn/releases/tag/v0.1.4
[0.1.3]: https://github.com/samson-media/barn/releases/tag/v0.1.3
[0.1.2]: https://github.com/samson-media/barn/releases/tag/v0.1.2
[0.1.1]: https://github.com/samson-media/barn/releases/tag/v0.1.1
[0.1.0]: https://github.com/samson-media/barn/releases/tag/v0.1.0
