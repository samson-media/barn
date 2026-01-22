# Distribution

This document describes package manager setup and installer creation for all supported platforms.

---

## Overview

| Platform | Package Format | Distribution Channel |
|----------|---------------|---------------------|
| Windows | Setup installer (.exe) | GitHub Releases |
| Linux | Native binary | GitHub Releases |
| macOS | Formula | Homebrew tap |

---

## Windows Setup Installer

### Download

Download the latest setup installer from GitHub Releases:

```
https://github.com/samson-media/barn/releases/download/v1.2.3/setup-barn-v1.2.3-windows-x64.exe
```

### Installation

1. Download `setup-barn-vX.X.X-windows-x64.exe` from the [releases page](https://github.com/samson-media/barn/releases/latest)
2. Run the installer (requires Administrator privileges)
3. The installer will:
   - Install `barn.exe` to `C:\Program Files\Barn\`
   - Add Barn to the system PATH
   - Install and start the Barn Windows Service

### Silent Installation

For automated deployments:

```powershell
.\setup-barn-v1.2.3-windows-x64.exe /S
```

### Uninstallation

Use Windows Settings → Apps → Barn, or run:

```powershell
"C:\Program Files\Barn\uninstall.exe" /S
```

### Updating

To update Barn on Windows:

1. Download the latest setup installer
2. Run the installer - it will automatically stop the service, update the binary, and restart the service

---

## Linux

Linux users install Barn by downloading the native binary directly from GitHub Releases.

### Download

Download the appropriate binary for your architecture:

- **x86_64**: `barn-linux-x64`
- **ARM64**: `barn-linux-arm64`

### Installation

```bash
# Download (x86_64 example)
curl -LO https://github.com/samson-media/barn/releases/download/v1.2.3/barn-linux-x64

# Verify checksum
curl -LO https://github.com/samson-media/barn/releases/download/v1.2.3/SHA256SUMS
sha256sum -c SHA256SUMS --ignore-missing

# Install
chmod +x barn-linux-x64
sudo mv barn-linux-x64 /usr/local/bin/barn

# Verify
barn --version
```

### Setting Up the Service

After installing the binary, set up the systemd service:

```bash
# Install the service
barn service install

# Enable and start the service
sudo systemctl enable barn
sudo systemctl start barn

# Verify
barn service status
```

### Updating

To update Barn on Linux:

1. Download the new binary from GitHub Releases
2. Stop the service: `sudo systemctl stop barn`
3. Replace the binary: `sudo mv barn-linux-x64 /usr/local/bin/barn`
4. Start the service: `sudo systemctl start barn`

---

## macOS Homebrew

### Homebrew Tap Setup

Create a tap repository: `samson-media/homebrew-barn`

```bash
# Create repository structure
mkdir homebrew-barn
cd homebrew-barn
git init
mkdir Formula
```

### Formula File

File: `Formula/barn.rb`

```ruby
class Barn < Formula
  desc "Cross-platform job daemon for media processing"
  homepage "https://github.com/samson-media/barn"
  version "1.2.3"
  license "MIT"

  on_macos do
    on_arm do
      url "https://github.com/samson-media/barn/releases/download/v1.2.3/barn-macos-arm64"
      sha256 "SHA256_HASH_HERE"

      def install
        bin.install "barn-macos-arm64" => "barn"
      end
    end

    on_intel do
      url "https://github.com/samson-media/barn/releases/download/v1.2.3/barn-macos-x64"
      sha256 "SHA256_HASH_HERE"

      def install
        bin.install "barn-macos-x64" => "barn"
      end
    end
  end

  service do
    run [opt_bin/"barn", "service", "run"]
    keep_alive true
    log_path var/"log/barn.log"
    error_log_path var/"log/barn.error.log"
  end

  test do
    assert_match "barn", shell_output("#{bin}/barn --version")
  end
end
```

### User Installation

```bash
# Add tap
brew tap samson-media/barn

# Install
brew install barn

# Start service
brew services start barn
```

### Formula vs Cask

Use **Formula** (not Cask) because:
- Barn is a CLI tool
- No GUI components
- Single binary installation
- Service integration via `brew services`

### Auto-Updating Formula

Create a GitHub Action to update the formula on release:

File: `.github/workflows/update-homebrew.yml`

```yaml
name: Update Homebrew

on:
  release:
    types: [published]

jobs:
  update:
    runs-on: ubuntu-latest
    steps:
      - name: Update Homebrew formula
        uses: mislav/bump-homebrew-formula-action@v3
        with:
          formula-name: barn
          homebrew-tap: samson-media/homebrew-barn
          download-url: https://github.com/samson-media/barn/releases/download/${{ github.ref_name }}/barn-macos-arm64
        env:
          COMMITTER_TOKEN: ${{ secrets.HOMEBREW_TAP_TOKEN }}
```

---

## Direct Binary Downloads

For users who prefer manual installation:

### Download URLs

```
https://github.com/samson-media/barn/releases/download/v1.2.3/barn-linux-x64
https://github.com/samson-media/barn/releases/download/v1.2.3/barn-linux-arm64
https://github.com/samson-media/barn/releases/download/v1.2.3/barn-macos-x64
https://github.com/samson-media/barn/releases/download/v1.2.3/barn-macos-arm64
https://github.com/samson-media/barn/releases/download/v1.2.3/barn-windows-x64.exe
https://github.com/samson-media/barn/releases/download/v1.2.3/SHA256SUMS
```

### Manual Installation (Unix)

```bash
# Download
curl -LO https://github.com/samson-media/barn/releases/download/v1.2.3/barn-linux-x64

# Verify checksum
curl -LO https://github.com/samson-media/barn/releases/download/v1.2.3/SHA256SUMS
sha256sum -c SHA256SUMS --ignore-missing

# Install
chmod +x barn-linux-x64
sudo mv barn-linux-x64 /usr/local/bin/barn

# Verify
barn --version
```

---

## Release Checklist for Distribution

After publishing a GitHub Release:

1. **Homebrew**: Formula auto-updates via GitHub Actions workflow
2. **Windows/Linux**: Binaries and setup installer available on GitHub Releases (manual download)

---

## Next Steps

- [Auto-Update](auto-update.md) - Built-in update mechanism
- [CI/CD](ci-cd.md) - Automated packaging workflows
