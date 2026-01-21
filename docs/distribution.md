# Distribution

This document describes package manager setup and installer creation for all supported platforms.

---

## Overview

| Platform | Package Format | Distribution Channel |
|----------|---------------|---------------------|
| Windows | MSI installer | WinGet |
| Linux (Debian/Ubuntu) | .deb | GitHub Releases, optional PPA |
| Linux (Arch) | PKGBUILD | AUR |
| macOS | Formula | Homebrew tap |

---

## Windows MSI

### WiX Toolset Setup

Barn uses [WiX Toolset v4](https://wixtoolset.org/) to create MSI installers.

Install WiX:

```powershell
dotnet tool install --global wix
```

### Package Structure

```
packaging/wix/
  barn.wxs           # WiX source file
  license.rtf        # License for installer
  banner.png         # Installer banner (optional)
  dialog.png         # Installer dialog background (optional)
```

### WiX Source File

File: `packaging/wix/barn.wxs`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Wix xmlns="http://wixtoolset.org/schemas/v4/wxs">
  <Package Name="Barn"
           Version="!(bind.FileVersion.BarnExe)"
           Manufacturer="Samson Media"
           UpgradeCode="PUT-GUID-HERE"
           Scope="perMachine">

    <MajorUpgrade DowngradeErrorMessage="A newer version of Barn is already installed." />
    <MediaTemplate EmbedCab="yes" />

    <Feature Id="ProductFeature" Title="Barn" Level="1">
      <ComponentGroupRef Id="ProductComponents" />
      <ComponentGroupRef Id="ServiceComponents" />
    </Feature>

    <StandardDirectory Id="ProgramFiles64Folder">
      <Directory Id="INSTALLFOLDER" Name="Barn">
        <Component Id="BarnExe" Guid="PUT-GUID-HERE">
          <File Id="BarnExe" Source="barn-windows-x64.exe" Name="barn.exe" KeyPath="yes" />
        </Component>

        <Component Id="PathEntry" Guid="PUT-GUID-HERE">
          <Environment Id="PATH" Name="PATH" Value="[INSTALLFOLDER]"
                       Permanent="no" Part="last" Action="set" System="yes" />
        </Component>
      </Directory>
    </StandardDirectory>

    <ComponentGroup Id="ProductComponents">
      <ComponentRef Id="BarnExe" />
      <ComponentRef Id="PathEntry" />
    </ComponentGroup>

    <ComponentGroup Id="ServiceComponents">
      <Component Id="BarnService" Directory="INSTALLFOLDER" Guid="PUT-GUID-HERE">
        <ServiceInstall Id="BarnServiceInstall"
                        Type="ownProcess"
                        Name="BarnService"
                        DisplayName="Barn Job Daemon"
                        Description="Background service for managing media processing jobs"
                        Start="auto"
                        Account="LocalSystem"
                        ErrorControl="normal" />
        <ServiceControl Id="StartBarnService"
                        Start="install"
                        Stop="both"
                        Remove="uninstall"
                        Name="BarnService"
                        Wait="yes" />
      </Component>
    </ComponentGroup>
  </Package>
</Wix>
```

### Building the MSI

```powershell
cd packaging/wix
wix build -o barn-windows-x64.msi barn.wxs
```

### WinGet Submission

#### Manifest Structure

Create a manifest directory:

```
manifests/s/SamsonMedia/Barn/1.2.3/
  SamsonMedia.Barn.yaml
  SamsonMedia.Barn.installer.yaml
  SamsonMedia.Barn.locale.en-US.yaml
```

#### Version Manifest

File: `SamsonMedia.Barn.yaml`

```yaml
PackageIdentifier: SamsonMedia.Barn
PackageVersion: 1.2.3
DefaultLocale: en-US
ManifestType: version
ManifestVersion: 1.6.0
```

#### Installer Manifest

File: `SamsonMedia.Barn.installer.yaml`

```yaml
PackageIdentifier: SamsonMedia.Barn
PackageVersion: 1.2.3
Platform:
  - Windows.Desktop
MinimumOSVersion: 10.0.0.0
InstallerType: msi
Scope: machine
InstallModes:
  - silent
  - silentWithProgress
Installers:
  - Architecture: x64
    InstallerUrl: https://github.com/samson-media/barn/releases/download/v1.2.3/barn-windows-x64.msi
    InstallerSha256: <SHA256_HASH>
ManifestType: installer
ManifestVersion: 1.6.0
```

#### Locale Manifest

File: `SamsonMedia.Barn.locale.en-US.yaml`

```yaml
PackageIdentifier: SamsonMedia.Barn
PackageVersion: 1.2.3
PackageLocale: en-US
Publisher: Samson Media
PackageName: Barn
PackageUrl: https://github.com/samson-media/barn
License: MIT
ShortDescription: Cross-platform job daemon for media processing
Description: A cross-platform service for managing long-running jobs involving media download, upload, and FFmpeg processing.
Tags:
  - cli
  - ffmpeg
  - media
  - daemon
  - job-scheduler
ManifestType: defaultLocale
ManifestVersion: 1.6.0
```

#### Submitting to WinGet

1. Fork [microsoft/winget-pkgs](https://github.com/microsoft/winget-pkgs)
2. Create branch: `barn-1.2.3`
3. Add manifest files to `manifests/s/SamsonMedia/Barn/1.2.3/`
4. Validate: `winget validate manifests/s/SamsonMedia/Barn/1.2.3/`
5. Create pull request

---

## Linux .deb (apt)

### Package Structure

```
packaging/deb/
  DEBIAN/
    control
    postinst
    prerm
    conffiles
  usr/
    bin/
      barn
  lib/
    systemd/
      system/
        barn.service
  etc/
    barn/
      barn.conf
```

### Control File

File: `packaging/deb/DEBIAN/control`

```
Package: barn
Version: 1.2.3
Section: utils
Priority: optional
Architecture: amd64
Maintainer: Samson Media <support@samson-media.com>
Description: Cross-platform job daemon for media processing
 A cross-platform service for managing long-running jobs involving
 media download, upload, and FFmpeg processing with strong guarantees
 around durability, observability, and crash recovery.
Homepage: https://github.com/samson-media/barn
Depends: libc6 (>= 2.31)
```

### Post-Install Script

File: `packaging/deb/DEBIAN/postinst`

```bash
#!/bin/bash
set -e

# Reload systemd
systemctl daemon-reload

# Enable and start service
systemctl enable barn.service
systemctl start barn.service

echo "Barn installed successfully. Service is running."
echo "Run 'barn status' to check job status."
```

### Pre-Remove Script

File: `packaging/deb/DEBIAN/prerm`

```bash
#!/bin/bash
set -e

# Stop and disable service
systemctl stop barn.service || true
systemctl disable barn.service || true

echo "Barn service stopped."
```

### Systemd Service File

File: `packaging/deb/lib/systemd/system/barn.service`

```ini
[Unit]
Description=Barn Job Daemon
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/barn service run
ExecReload=/bin/kill -HUP $MAINPID
Restart=on-failure
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
```

### Building the .deb

```bash
# Set permissions
chmod 755 packaging/deb/DEBIAN/postinst
chmod 755 packaging/deb/DEBIAN/prerm
chmod 755 packaging/deb/usr/bin/barn

# Build package
dpkg-deb --build packaging/deb barn-linux-x64.deb

# Verify
dpkg-deb --info barn-linux-x64.deb
```

### Installation

Users install directly from GitHub Releases:

```bash
# Download
wget https://github.com/samson-media/barn/releases/download/v1.2.3/barn-linux-x64.deb

# Install
sudo dpkg -i barn-linux-x64.deb

# Or with apt (handles dependencies)
sudo apt install ./barn-linux-x64.deb
```

### Optional: Launchpad PPA

For automatic updates via apt:

1. Create account at [Launchpad](https://launchpad.net/)
2. Create PPA: `ppa:samson-media/barn`
3. Upload source package with `dput`
4. Users add: `sudo add-apt-repository ppa:samson-media/barn`

---

## Linux PKGBUILD (pacman/AUR)

### PKGBUILD File

File: `packaging/aur/PKGBUILD`

```bash
# Maintainer: Samson Media <support@samson-media.com>
pkgname=barn
pkgver=1.2.3
pkgrel=1
pkgdesc="Cross-platform job daemon for media processing"
arch=('x86_64' 'aarch64')
url="https://github.com/samson-media/barn"
license=('MIT')
depends=('glibc')
provides=('barn')
conflicts=('barn-git')
source_x86_64=("${pkgname}-${pkgver}-x86_64::https://github.com/samson-media/barn/releases/download/v${pkgver}/barn-linux-x64")
source_aarch64=("${pkgname}-${pkgver}-aarch64::https://github.com/samson-media/barn/releases/download/v${pkgver}/barn-linux-arm64")
sha256sums_x86_64=('SKIP')  # Replace with actual hash
sha256sums_aarch64=('SKIP')  # Replace with actual hash

package() {
    install -Dm755 "${srcdir}/${pkgname}-${pkgver}-${CARCH}" "${pkgdir}/usr/bin/barn"
    install -Dm644 "${srcdir}/barn.service" "${pkgdir}/usr/lib/systemd/system/barn.service"
}
```

### Install Script

File: `packaging/aur/barn.install`

```bash
post_install() {
    systemctl daemon-reload
    echo ":: Run 'systemctl enable --now barn.service' to start Barn"
}

post_upgrade() {
    systemctl daemon-reload
    systemctl try-restart barn.service
}

pre_remove() {
    systemctl stop barn.service || true
    systemctl disable barn.service || true
}

post_remove() {
    systemctl daemon-reload
}
```

### AUR Submission

1. Create account at [AUR](https://aur.archlinux.org/)
2. Add SSH key to account
3. Clone AUR package base:
   ```bash
   git clone ssh://aur@aur.archlinux.org/barn.git
   ```
4. Add PKGBUILD and .SRCINFO:
   ```bash
   makepkg --printsrcinfo > .SRCINFO
   ```
5. Commit and push:
   ```bash
   git add PKGBUILD .SRCINFO barn.install
   git commit -m "Update to version 1.2.3"
   git push
   ```

### Updating AUR Package

AUR updates are **automated** via GitHub Actions. See [CI/CD](ci-cd.md#aur-update-workflow) for the workflow details.

If manual update is needed:

1. Update `pkgver` in PKGBUILD
2. Update SHA256 checksums
3. Regenerate .SRCINFO
4. Commit and push

```bash
# Clone the AUR repo
git clone ssh://aur@aur.archlinux.org/barn.git aur-barn
cd aur-barn

# Update version
sed -i "s/pkgver=.*/pkgver=1.2.4/" PKGBUILD

# Get new checksums from GitHub release
curl -sL https://github.com/samson-media/barn/releases/download/v1.2.4/SHA256SUMS

# Update checksums in PKGBUILD
# Edit sha256sums_x86_64 and sha256sums_aarch64

# Regenerate .SRCINFO
makepkg --printsrcinfo > .SRCINFO

# Commit and push
git add PKGBUILD .SRCINFO
git commit -m "Update to version 1.2.4"
git push
```

### Initial AUR Setup

Before the first release, create the AUR package:

1. **Create AUR Account**
   - Register at https://aur.archlinux.org/register/
   - Verify email and log in

2. **Add SSH Key**
   ```bash
   ssh-keygen -t ed25519 -C "aur" -f ~/.ssh/aur
   ```
   - Add public key at https://aur.archlinux.org/account/
   - Configure SSH:
     ```
     # ~/.ssh/config
     Host aur.archlinux.org
       IdentityFile ~/.ssh/aur
       User aur
     ```

3. **Create Package Base**
   ```bash
   git clone ssh://aur@aur.archlinux.org/barn.git
   cd barn
   # Directory will be empty for new packages
   ```

4. **Add Package Files**
   ```bash
   # Copy PKGBUILD, barn.install, barn.service from packaging/aur/
   cp /path/to/packaging/aur/* .

   # Generate .SRCINFO
   makepkg --printsrcinfo > .SRCINFO

   # Initial commit
   git add PKGBUILD .SRCINFO barn.install barn.service
   git commit -m "Initial package submission"
   git push
   ```

5. **Verify Package**
   - Check https://aur.archlinux.org/packages/barn
   - Test installation:
     ```bash
     yay -S barn  # or paru -S barn
     ```

### AUR Best Practices

- **Test locally** before pushing: `makepkg -si`
- **Bump pkgrel** for packaging changes (same upstream version)
- **Reset pkgrel to 1** when pkgver changes
- **Respond to comments** on AUR package page
- **Flag out-of-date** packages if automation fails

### barn-git Package (Optional)

For users who want the latest development version, consider maintaining a `-git` package:

File: `packaging/aur-git/PKGBUILD`

```bash
# Maintainer: Samson Media <support@samson-media.com>
pkgname=barn-git
pkgver=r123.abc1234
pkgrel=1
pkgdesc="Cross-platform job daemon for media processing (development version)"
arch=('x86_64' 'aarch64')
url="https://github.com/samson-media/barn"
license=('MIT')
depends=('glibc')
makedepends=('git' 'graalvm-jdk' 'gradle')
provides=('barn')
conflicts=('barn')
source=("git+https://github.com/samson-media/barn.git")
sha256sums=('SKIP')

pkgver() {
    cd barn
    printf "r%s.%s" "$(git rev-list --count HEAD)" "$(git rev-parse --short HEAD)"
}

build() {
    cd barn
    ./gradlew nativeCompile
}

package() {
    cd barn
    install -Dm755 "build/native/nativeCompile/barn" "${pkgdir}/usr/bin/barn"
}
```

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

1. **Windows**: Submit WinGet manifest PR
2. **Arch Linux**: Update AUR PKGBUILD
3. **Homebrew**: Formula auto-updates via workflow
4. **Debian**: .deb available on GitHub Releases

---

## Next Steps

- [Auto-Update](auto-update.md) - Built-in update mechanism
- [CI/CD](ci-cd.md) - Automated packaging workflows
