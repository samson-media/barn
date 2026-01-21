# CI/CD

This document describes the GitHub Actions workflows for building and releasing Barn.

---

## Workflow Overview

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `build.yml` | Push, PR | Build and test on all platforms |
| `release.yml` | Tag push | Build, package, and publish release |
| `update-aur.yml` | Release published | Update AUR package |
| `update-homebrew.yml` | Release published | Update Homebrew formula |

---

## Build Workflow

File: `.github/workflows/build.yml`

```yaml
name: Build

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up GraalVM
        uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: 'graalvm-community'
          cache: 'gradle'

      - name: Run tests
        run: ./gradlew test

      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: build/reports/tests/

  build-native:
    needs: test
    strategy:
      matrix:
        include:
          - os: ubuntu-latest
            arch: x64
            artifact: barn-linux-x64
          - os: ubuntu-24.04-arm
            arch: arm64
            artifact: barn-linux-arm64
          - os: macos-latest
            arch: arm64
            artifact: barn-macos-arm64
          - os: macos-13
            arch: x64
            artifact: barn-macos-x64
          - os: windows-latest
            arch: x64
            artifact: barn-windows-x64.exe

    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4

      - name: Set up GraalVM
        uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: 'graalvm-community'
          cache: 'gradle'

      - name: Build native image
        run: ./gradlew nativeCompile

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: ${{ matrix.artifact }}
          path: build/native/nativeCompile/barn*
```

---

## Release Workflow

File: `.github/workflows/release.yml`

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

permissions:
  contents: write

jobs:
  build-native:
    strategy:
      matrix:
        include:
          - os: ubuntu-latest
            arch: x64
            artifact: barn-linux-x64
          - os: ubuntu-24.04-arm
            arch: arm64
            artifact: barn-linux-arm64
          - os: macos-latest
            arch: arm64
            artifact: barn-macos-arm64
          - os: macos-13
            arch: x64
            artifact: barn-macos-x64
          - os: windows-latest
            arch: x64
            artifact: barn-windows-x64

    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4

      - name: Set up GraalVM
        uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: 'graalvm-community'
          cache: 'gradle'

      - name: Build native image
        run: ./gradlew nativeCompile

      - name: Rename artifact (Unix)
        if: runner.os != 'Windows'
        run: mv build/native/nativeCompile/barn build/native/nativeCompile/${{ matrix.artifact }}

      - name: Rename artifact (Windows)
        if: runner.os == 'Windows'
        run: mv build/native/nativeCompile/barn.exe build/native/nativeCompile/${{ matrix.artifact }}.exe

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: ${{ matrix.artifact }}
          path: build/native/nativeCompile/${{ matrix.artifact }}*

  build-deb:
    needs: build-native
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Download Linux x64 binary
        uses: actions/download-artifact@v4
        with:
          name: barn-linux-x64
          path: packaging/deb/usr/bin/

      - name: Build .deb package
        run: |
          chmod +x packaging/deb/usr/bin/barn-linux-x64
          mv packaging/deb/usr/bin/barn-linux-x64 packaging/deb/usr/bin/barn
          dpkg-deb --build packaging/deb barn-linux-x64.deb

      - name: Upload .deb
        uses: actions/upload-artifact@v4
        with:
          name: barn-linux-x64.deb
          path: barn-linux-x64.deb

  build-msi:
    needs: build-native
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4

      - name: Download Windows binary
        uses: actions/download-artifact@v4
        with:
          name: barn-windows-x64
          path: packaging/wix/

      - name: Install WiX Toolset
        run: dotnet tool install --global wix

      - name: Build MSI
        run: |
          cd packaging/wix
          wix build -o barn-windows-x64.msi barn.wxs

      - name: Upload MSI
        uses: actions/upload-artifact@v4
        with:
          name: barn-windows-x64.msi
          path: packaging/wix/barn-windows-x64.msi

  release:
    needs: [build-native, build-deb, build-msi]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Download all artifacts
        uses: actions/download-artifact@v4
        with:
          path: artifacts/

      - name: Flatten artifacts
        run: |
          mkdir release
          find artifacts -type f -exec mv {} release/ \;

      - name: Generate checksums
        run: |
          cd release
          sha256sum * > SHA256SUMS

      - name: Extract changelog
        id: changelog
        run: |
          VERSION=${GITHUB_REF#refs/tags/v}
          sed -n "/## \[$VERSION\]/,/## \[/p" CHANGELOG.md | head -n -1 > release_notes.md

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          body_path: release_notes.md
          files: release/*
```

---

## AUR Update Workflow

File: `.github/workflows/update-aur.yml`

This workflow automatically updates the AUR package when a new release is published.

```yaml
name: Update AUR

on:
  release:
    types: [published]

jobs:
  update-aur:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Get release info
        id: release
        run: |
          VERSION=${GITHUB_REF#refs/tags/v}
          echo "version=$VERSION" >> $GITHUB_OUTPUT

          # Get checksums from release
          curl -sL "https://github.com/samson-media/barn/releases/download/v${VERSION}/SHA256SUMS" -o SHA256SUMS

          X64_SHA=$(grep barn-linux-x64 SHA256SUMS | awk '{print $1}')
          ARM64_SHA=$(grep barn-linux-arm64 SHA256SUMS | awk '{print $1}')

          echo "x64_sha=$X64_SHA" >> $GITHUB_OUTPUT
          echo "arm64_sha=$ARM64_SHA" >> $GITHUB_OUTPUT

      - name: Update PKGBUILD
        run: |
          cat > PKGBUILD << 'EOF'
          # Maintainer: Samson Media <support@samson-media.com>
          pkgname=barn
          pkgver=${{ steps.release.outputs.version }}
          pkgrel=1
          pkgdesc="Cross-platform job daemon for media processing"
          arch=('x86_64' 'aarch64')
          url="https://github.com/samson-media/barn"
          license=('MIT')
          depends=('glibc')
          provides=('barn')
          conflicts=('barn-git')
          install=barn.install
          source_x86_64=("${pkgname}-${pkgver}-x86_64::https://github.com/samson-media/barn/releases/download/v${pkgver}/barn-linux-x64")
          source_aarch64=("${pkgname}-${pkgver}-aarch64::https://github.com/samson-media/barn/releases/download/v${pkgver}/barn-linux-arm64")
          source=("barn.service" "barn.install")
          sha256sums_x86_64=('${{ steps.release.outputs.x64_sha }}')
          sha256sums_aarch64=('${{ steps.release.outputs.arm64_sha }}')
          sha256sums=('SKIP' 'SKIP')

          package() {
              install -Dm755 "${srcdir}/${pkgname}-${pkgver}-${CARCH}" "${pkgdir}/usr/bin/barn"
              install -Dm644 "${srcdir}/barn.service" "${pkgdir}/usr/lib/systemd/system/barn.service"
          }
          EOF

      - name: Generate .SRCINFO
        uses: hapakaien/archlinux-package-action@v2
        with:
          pkgbuild: PKGBUILD
          flags: '--printsrcinfo > .SRCINFO'

      - name: Publish to AUR
        uses: KSXGitHub/github-actions-deploy-aur@v2
        with:
          pkgname: barn
          pkgbuild: ./PKGBUILD
          commit_username: 'Samson Media Bot'
          commit_email: 'bot@samson-media.com'
          ssh_private_key: ${{ secrets.AUR_SSH_KEY }}
          commit_message: "Update to version ${{ steps.release.outputs.version }}"
          assets: |
            barn.service
            barn.install
```

### AUR SSH Key Setup

To enable automated AUR publishing:

1. Generate an SSH key pair:
   ```bash
   ssh-keygen -t ed25519 -C "barn-aur-deploy" -f barn-aur-key -N ""
   ```

2. Add the **public key** to your AUR account:
   - Go to https://aur.archlinux.org/account/
   - Add the contents of `barn-aur-key.pub` to SSH Keys

3. Add the **private key** to GitHub Secrets:
   - Go to repository Settings > Secrets > Actions
   - Create `AUR_SSH_KEY` with contents of `barn-aur-key`

### AUR Package Files

The workflow requires these files in the repository:

**File: `packaging/aur/barn.service`**

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

[Install]
WantedBy=multi-user.target
```

**File: `packaging/aur/barn.install`**

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

### Manual AUR Update

If the automated workflow fails, update manually:

```bash
# Clone the AUR repo
git clone ssh://aur@aur.archlinux.org/barn.git aur-barn
cd aur-barn

# Update PKGBUILD version and checksums
# Edit pkgver, sha256sums_x86_64, sha256sums_aarch64

# Regenerate .SRCINFO
makepkg --printsrcinfo > .SRCINFO

# Commit and push
git add PKGBUILD .SRCINFO
git commit -m "Update to version X.Y.Z"
git push
```

---

## Homebrew Update Workflow

File: `.github/workflows/update-homebrew.yml`

```yaml
name: Update Homebrew

on:
  release:
    types: [published]

jobs:
  update-homebrew:
    runs-on: ubuntu-latest
    steps:
      - name: Get release info
        id: release
        run: |
          VERSION=${GITHUB_REF#refs/tags/v}
          echo "version=$VERSION" >> $GITHUB_OUTPUT

          # Get checksums
          curl -sL "https://github.com/samson-media/barn/releases/download/v${VERSION}/SHA256SUMS" -o SHA256SUMS

          MACOS_ARM64_SHA=$(grep barn-macos-arm64 SHA256SUMS | awk '{print $1}')
          MACOS_X64_SHA=$(grep barn-macos-x64 SHA256SUMS | awk '{print $1}')

          echo "macos_arm64_sha=$MACOS_ARM64_SHA" >> $GITHUB_OUTPUT
          echo "macos_x64_sha=$MACOS_X64_SHA" >> $GITHUB_OUTPUT

      - name: Update Homebrew formula
        uses: mislav/bump-homebrew-formula-action@v3
        with:
          formula-name: barn
          formula-path: Formula/barn.rb
          homebrew-tap: samson-media/homebrew-barn
          base-branch: main
          download-url: https://github.com/samson-media/barn/releases/download/v${{ steps.release.outputs.version }}/barn-macos-arm64
          download-sha256: ${{ steps.release.outputs.macos_arm64_sha }}
        env:
          COMMITTER_TOKEN: ${{ secrets.HOMEBREW_TAP_TOKEN }}
```

---

## Build Matrix

| Runner | Architecture | Output |
|--------|--------------|--------|
| `ubuntu-latest` | x86_64 | `barn-linux-x64` |
| `ubuntu-24.04-arm` | ARM64 | `barn-linux-arm64` |
| `macos-latest` | ARM64 | `barn-macos-arm64` |
| `macos-13` | x86_64 | `barn-macos-x64` |
| `windows-latest` | x86_64 | `barn-windows-x64.exe` |

---

## GraalVM Setup

The `graalvm/setup-graalvm` action handles installation:

```yaml
- name: Set up GraalVM
  uses: graalvm/setup-graalvm@v1
  with:
    java-version: '21'
    distribution: 'graalvm-community'
    cache: 'gradle'
```

Key options:

| Option | Value | Purpose |
|--------|-------|---------|
| `java-version` | `21` | JDK version |
| `distribution` | `graalvm-community` | GraalVM CE |
| `cache` | `gradle` | Cache Gradle dependencies |

---

## Caching Strategy

### Gradle Cache

The GraalVM action caches Gradle dependencies automatically when `cache: 'gradle'` is set.

### Manual Caching

For more control:

```yaml
- name: Cache Gradle packages
  uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
    restore-keys: |
      ${{ runner.os }}-gradle-
```

### Native Image Cache

GraalVM native image builds can be cached:

```yaml
- name: Cache native image
  uses: actions/cache@v4
  with:
    path: build/native
    key: ${{ runner.os }}-native-${{ hashFiles('src/**', 'build.gradle.kts') }}
```

---

## Secrets Management

Required secrets in GitHub repository settings:

| Secret | Purpose |
|--------|---------|
| `SIGNING_KEY` | Code signing private key (optional) |
| `SIGNING_KEY_PASSWORD` | Signing key passphrase (optional) |
| `HOMEBREW_TAP_TOKEN` | Token for updating Homebrew tap |
| `AUR_SSH_KEY` | SSH key for AUR updates |

### Using Secrets

```yaml
- name: Sign binary
  env:
    SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
    SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
  run: ./scripts/sign.sh release/barn-linux-x64
```

---

## Artifact Upload

Artifacts are uploaded per platform and downloaded in the release job:

```yaml
# Upload in build job
- name: Upload artifact
  uses: actions/upload-artifact@v4
  with:
    name: barn-linux-x64
    path: build/native/nativeCompile/barn
    retention-days: 7

# Download in release job
- name: Download all artifacts
  uses: actions/download-artifact@v4
  with:
    path: artifacts/
```

---

## Checksums

SHA256 checksums are generated for all release artifacts:

```yaml
- name: Generate checksums
  run: |
    cd release
    sha256sum * > SHA256SUMS
```

The `SHA256SUMS` file is included in the release for verification.

---

## Triggering Workflows

### Build Workflow

Triggered automatically on:
- Push to `main`
- Pull request to `main`

### Release Workflow

Triggered by pushing a version tag:

```bash
git tag v1.2.3
git push origin v1.2.3
```

---

## Monitoring Workflows

View workflow runs at:

```
https://github.com/samson-media/barn/actions
```

### Status Badges

Add to README:

```markdown
![Build](https://github.com/samson-media/barn/actions/workflows/build.yml/badge.svg)
![Release](https://github.com/samson-media/barn/actions/workflows/release.yml/badge.svg)
```

---

## Troubleshooting

### Native Build Fails

Check GraalVM version compatibility:

```yaml
- name: Check GraalVM
  run: |
    java -version
    native-image --version
```

### Artifact Not Found

Ensure artifact names match between upload and download steps.

### Release Already Exists

Delete the existing release via GitHub UI before re-running.

### Windows Long Paths

Enable long path support:

```yaml
- name: Enable long paths
  run: git config --system core.longpaths true
```

---

## Next Steps

- [Releasing](releasing.md) - How to create releases
- [Distribution](distribution.md) - Package manager setup
