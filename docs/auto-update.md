# Updating Barn

This document describes how to keep Barn updated on different platforms.

---

## Overview

| Platform | Update Method | Automatic |
|----------|---------------|-----------|
| macOS (Homebrew) | `brew upgrade barn` | Yes |
| macOS (manual) | Download from GitHub Releases | No |
| Windows | Download setup installer from GitHub Releases | No |
| Linux | Download binary from GitHub Releases | No |

---

## macOS (Homebrew)

If you installed Barn via Homebrew, updates are handled automatically:

```bash
# Check for updates
brew outdated barn

# Update Barn
brew upgrade barn

# Restart the service after updating
brew services restart barn
```

Homebrew will automatically fetch the latest version from the Homebrew tap when you run `brew upgrade`.

---

## macOS (Manual Installation)

If you installed Barn manually (not via Homebrew):

1. Download the latest binary from [GitHub Releases](https://github.com/samson-media/barn/releases/latest)
2. Stop the service: `barn service stop`
3. Replace the binary:
   ```bash
   chmod +x barn-macos-arm64
   sudo mv barn-macos-arm64 /usr/local/bin/barn
   ```
4. Verify: `barn --version`
5. Start the service: `barn service start`

---

## Windows

To update Barn on Windows:

1. Download the latest `setup-barn-vX.X.X-windows-x64.exe` from [GitHub Releases](https://github.com/samson-media/barn/releases/latest)
2. Run the installer - it will automatically:
   - Stop the Barn service
   - Update the binary
   - Restart the service

Alternatively, for silent updates:

```powershell
# Download the installer
Invoke-WebRequest -Uri "https://github.com/samson-media/barn/releases/latest/download/setup-barn-vX.X.X-windows-x64.exe" -OutFile "setup-barn.exe"

# Run silent installation
.\setup-barn.exe /S
```

---

## Linux

To update Barn on Linux:

1. Download the latest binary from [GitHub Releases](https://github.com/samson-media/barn/releases/latest)
2. Stop the service:
   ```bash
   sudo systemctl stop barn
   ```
3. Replace the binary:
   ```bash
   chmod +x barn-linux-x64
   sudo mv barn-linux-x64 /usr/local/bin/barn
   ```
4. Verify: `barn --version`
5. Start the service:
   ```bash
   sudo systemctl start barn
   ```

---

## Checking Your Current Version

```bash
barn --version
```

---

## Release Notifications

To be notified of new releases, watch the [GitHub repository](https://github.com/samson-media/barn):

1. Go to https://github.com/samson-media/barn
2. Click "Watch" → "Custom" → Select "Releases"

---

## Next Steps

- [Releasing](releasing.md) - How versions are published
- [Distribution](distribution.md) - Installation methods by platform
