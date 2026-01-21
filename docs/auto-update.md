# Auto-Update

This document describes Barn's built-in self-update mechanism.

---

## Overview

Barn includes a `barn update` command that can check for and install new versions directly from GitHub Releases without requiring a package manager.

---

## Commands

### Check for Updates

```bash
barn update --check
```

Output:

```
Current version: 1.2.3
Latest version:  1.3.0
Update available: yes

Run 'barn update' to install the latest version.
```

### Install Latest Version

```bash
barn update
```

Output:

```
Current version: 1.2.3
Latest version:  1.3.0

Downloading barn-macos-arm64...
Verifying checksum...
Installing...
Restarting service...

Successfully updated to version 1.3.0
```

### Install Specific Version

```bash
barn update --version=1.2.0
```

### Force Update (Reinstall Current Version)

```bash
barn update --force
```

### JSON Output

```bash
barn update --check --output=json
```

```json
{
  "current_version": "1.2.3",
  "latest_version": "1.3.0",
  "update_available": true,
  "release_url": "https://github.com/samson-media/barn/releases/tag/v1.3.0"
}
```

---

## Update Flow

### 1. Query GitHub Releases API

Barn queries the GitHub Releases API to find the latest version:

```
GET https://api.github.com/repos/samson-media/barn/releases/latest
```

Response includes:
- Version tag
- Asset download URLs
- Checksums

### 2. Compare Versions

Versions are compared using semantic versioning:

```
1.3.0 > 1.2.3  → Update available
1.2.3 = 1.2.3  → Up to date
1.2.0 < 1.2.3  → Downgrade (requires --force)
```

### 3. Download Binary

The appropriate binary is downloaded based on OS and architecture:

| OS | Architecture | Asset |
|----|--------------|-------|
| Linux | x86_64 | `barn-linux-x64` |
| Linux | ARM64 | `barn-linux-arm64` |
| macOS | x86_64 | `barn-macos-x64` |
| macOS | ARM64 | `barn-macos-arm64` |
| Windows | x86_64 | `barn-windows-x64.exe` |

### 4. Verify Checksum

The SHA256 checksum is verified against `SHA256SUMS`:

```bash
# Downloaded automatically and verified
sha256sum -c SHA256SUMS
```

If verification fails, the update is aborted.

### 5. Replace Binary

The old binary is replaced with the new one.

### 6. Verify Installation

After replacement, Barn verifies the new binary works:

```bash
barn --version
```

---

## Platform-Specific Behavior

### Windows

Windows cannot replace a running executable. The update process:

1. Download new binary to temp location
2. Stop the Barn service
3. Rename current `barn.exe` to `barn.exe.old`
4. Move new binary to `barn.exe`
5. Start the Barn service
6. Delete `barn.exe.old` on next run

```
C:\Program Files\Barn\
  barn.exe          ← New version
  barn.exe.old      ← Previous version (deleted on next run)
```

### Linux / macOS

Direct replacement if permissions allow:

```bash
# Requires write permission to binary location
sudo barn update
```

If running without sudo and binary is in a protected location:

```
Error: Cannot update binary at /usr/local/bin/barn
Permission denied. Run with sudo or use your package manager.
```

### Package Manager Installations

If Barn detects it was installed via a package manager, it warns the user:

```
Warning: Barn appears to be installed via Homebrew.
Using 'barn update' may conflict with your package manager.

Recommended: Run 'brew upgrade barn' instead.

Continue anyway? [y/N]
```

Detection methods:

| Package Manager | Detection |
|-----------------|-----------|
| Homebrew | Binary in `/opt/homebrew/` or `/usr/local/Cellar/` |
| apt/dpkg | Binary in `/usr/bin/` with dpkg ownership |
| pacman | Binary in `/usr/bin/` with pacman ownership |
| WinGet | Registry entries present |

---

## Security

### HTTPS Only

All downloads use HTTPS. HTTP is never used.

### Checksum Verification

Every download is verified against the published SHA256 checksum:

```
SHA256SUMS content:
a1b2c3d4... barn-linux-x64
e5f6g7h8... barn-linux-arm64
...
```

If the checksum doesn't match, the update fails:

```
Error: Checksum verification failed
Expected: a1b2c3d4...
Got:      xxxxxxxx...

The download may be corrupted or tampered with.
Update aborted.
```

### Signature Verification (Optional)

If GPG signatures are published, Barn can verify them:

```bash
barn update --verify-signature
```

This requires the Samson Media public key to be imported.

---

## Configuration

Update behavior can be configured in `barn.conf`:

```toml
[update]
# Disable update checks
enabled = true

# Check for updates automatically on service start
check_on_start = true

# Notify about updates without auto-installing
auto_install = false

# GitHub API endpoint (for enterprise GitHub)
github_api = "https://api.github.com"

# Proxy settings
proxy = ""
```

### Environment Variables

```bash
# Disable update functionality
BARN_UPDATE_ENABLED=false

# Use proxy for downloads
BARN_UPDATE_PROXY=http://proxy.example.com:8080
```

---

## Rollback

Barn keeps the previous version as a backup.

### Automatic Backup

During update, the previous binary is preserved:

| Platform | Backup Location |
|----------|-----------------|
| Linux/macOS | `/usr/local/bin/barn.backup` or alongside binary |
| Windows | `barn.exe.backup` |

### Manual Rollback

```bash
barn update --rollback
```

Or manually:

```bash
# Linux/macOS
sudo mv /usr/local/bin/barn.backup /usr/local/bin/barn

# Windows
move "C:\Program Files\Barn\barn.exe.backup" "C:\Program Files\Barn\barn.exe"
```

### Rollback Retention

Only the most recent previous version is kept. Older backups are deleted.

---

## Offline Mode

If network is unavailable:

```bash
barn update --check
```

```
Error: Cannot reach GitHub API
Check your network connection or proxy settings.

For offline installation, download the binary manually from:
https://github.com/samson-media/barn/releases
```

### Manual Offline Update

1. Download binary on another machine
2. Transfer to target machine
3. Replace binary manually:

```bash
chmod +x barn-linux-x64
sudo mv barn-linux-x64 /usr/local/bin/barn
```

---

## Update Notifications

When `check_on_start = true`, Barn checks for updates when the service starts and logs:

```
[INFO] Update available: 1.3.0 (current: 1.2.3)
[INFO] Run 'barn update' to install
```

This check is non-blocking and doesn't affect service startup.

---

## Troubleshooting

### Update Check Fails

```
Error: Failed to check for updates
GitHub API rate limit exceeded.
```

Solution: Wait an hour or authenticate with a GitHub token:

```bash
GITHUB_TOKEN=xxx barn update --check
```

### Permission Denied

```
Error: Cannot write to /usr/local/bin/barn
```

Solution: Run with elevated privileges:

```bash
sudo barn update
```

### Binary Corrupted After Update

```
Error: barn: cannot execute binary file
```

Solution: Roll back and report the issue:

```bash
sudo mv /usr/local/bin/barn.backup /usr/local/bin/barn
barn --version  # Verify rollback worked
```

### Service Won't Start After Update

1. Check logs: `barn service logs`
2. Roll back: `barn update --rollback`
3. Start service: `barn service start`

---

## Implementation Details

### Version Detection

Barn determines its own version at compile time:

```kotlin
object Version {
    const val CURRENT = "1.2.3"  // Injected at build
    val commit = System.getenv("BARN_COMMIT") ?: "unknown"
}
```

### API Endpoint

```
GET https://api.github.com/repos/samson-media/barn/releases/latest
Accept: application/vnd.github.v3+json
```

### Download with Progress

Downloads show progress for large binaries:

```
Downloading barn-linux-x64...
[████████████████████░░░░░░░░░░] 67% (12.3 MB / 18.4 MB)
```

---

## Next Steps

- [Releasing](releasing.md) - How versions are published
- [Distribution](distribution.md) - Package manager alternatives
