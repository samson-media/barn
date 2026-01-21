# Releasing

This document describes how releases are versioned, tagged, and published.

---

## Versioning

Barn follows [Semantic Versioning](https://semver.org/):

```
MAJOR.MINOR.PATCH
```

| Component | When to Increment |
|-----------|-------------------|
| MAJOR | Breaking changes to CLI or config format |
| MINOR | New features, backward compatible |
| PATCH | Bug fixes, backward compatible |

### Pre-release Versions

For pre-release testing:

- Alpha: `1.2.0-alpha.1`
- Beta: `1.2.0-beta.1`
- Release candidate: `1.2.0-rc.1`

---

## Version Location

The version is defined in `gradle.properties`:

```properties
version=1.2.3
```

This version is embedded in the binary and available via `barn --version`.

---

## Changelog

Maintain `CHANGELOG.md` in the repository root using [Keep a Changelog](https://keepachangelog.com/) format:

```markdown
# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- New feature description

### Changed
- Modified behavior description

### Fixed
- Bug fix description

### Removed
- Removed feature description

## [1.2.3] - 2026-01-21

### Added
- Initial release features

[Unreleased]: https://github.com/samson-media/barn/compare/v1.2.3...HEAD
[1.2.3]: https://github.com/samson-media/barn/releases/tag/v1.2.3
```

### Changelog Categories

- **Added**: New features
- **Changed**: Changes in existing functionality
- **Deprecated**: Features that will be removed
- **Removed**: Features that were removed
- **Fixed**: Bug fixes
- **Security**: Security-related changes

---

## Release Checklist

### Prerequisites (First Release Only)

Before the first release, ensure the following are set up:

- [ ] **AUR SSH Key**: Generate and add to GitHub Secrets as `AUR_SSH_KEY` (see [CI/CD](ci-cd.md#aur-ssh-key-setup))
- [ ] **AUR Package**: Create initial package at https://aur.archlinux.org (see [Distribution](distribution.md#initial-aur-setup))
- [ ] **Homebrew Tap**: Create `samson-media/homebrew-barn` repository
- [ ] **Homebrew Token**: Add `HOMEBREW_TAP_TOKEN` to GitHub Secrets
- [ ] **Code Signing** (optional): Add `SIGNING_KEY` and `SIGNING_KEY_PASSWORD` to GitHub Secrets

### 1. Prepare the Release

Ensure all changes are merged to `main` and tests pass:

```bash
git checkout main
git pull origin main
./gradlew clean test
```

### 2. Update Version

Edit `gradle.properties`:

```properties
version=1.2.3
```

### 3. Update Changelog

Move items from `[Unreleased]` to the new version section:

```markdown
## [1.2.3] - 2026-01-21

### Added
- New feature X
- Support for Y

### Fixed
- Bug in Z
```

### 4. Commit the Release

```bash
git add gradle.properties CHANGELOG.md
git commit -m "Release v1.2.3"
```

### 5. Tag the Release

```bash
git tag v1.2.3
```

### 6. Push to Remote

```bash
git push origin main --tags
```

This triggers the release workflow in GitHub Actions.

---

## Automated Release Pipeline

When a tag is pushed, the following happens automatically:

```
git push --tags
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│  release.yml - Build & Publish                          │
│  ├── Build native binaries (Linux, macOS, Windows)      │
│  ├── Build packages (.deb, .msi)                        │
│  ├── Generate SHA256SUMS                                │
│  └── Create GitHub Release with artifacts               │
└─────────────────────────────────────────────────────────┘
    │
    ▼ (on: release published)
┌─────────────────────────────────────────────────────────┐
│  update-aur.yml - Arch Linux                            │
│  ├── Fetch checksums from GitHub Release                │
│  ├── Generate PKGBUILD with new version                 │
│  ├── Generate .SRCINFO                                  │
│  └── Push to AUR via SSH                                │
└─────────────────────────────────────────────────────────┘
    │
    ▼ (on: release published)
┌─────────────────────────────────────────────────────────┐
│  update-homebrew.yml - macOS                            │
│  ├── Fetch checksums from GitHub Release                │
│  ├── Update formula with new version/SHA                │
│  └── Push to homebrew-barn tap                          │
└─────────────────────────────────────────────────────────┘
```

### Manual Steps Required

| Package Manager | Automated | Manual Action Required |
|-----------------|-----------|------------------------|
| GitHub Releases | Yes | None |
| AUR (Arch Linux) | Yes | None (verify after) |
| Homebrew (macOS) | Yes | None (verify after) |
| WinGet (Windows) | No | Submit manifest PR |
| apt (Debian/Ubuntu) | Partial | .deb on GitHub, PPA requires manual upload |

---

## GitHub Release

The CI/CD pipeline automatically creates a GitHub Release when a tag is pushed. The release includes:

### Artifacts

| Artifact | Description |
|----------|-------------|
| `barn-linux-x64` | Linux x86_64 binary |
| `barn-linux-arm64` | Linux ARM64 binary |
| `barn-macos-x64` | macOS Intel binary |
| `barn-macos-arm64` | macOS Apple Silicon binary |
| `barn-windows-x64.exe` | Windows x86_64 binary |
| `barn-windows-x64.msi` | Windows installer |
| `barn-linux-x64.deb` | Debian/Ubuntu package |
| `SHA256SUMS` | Checksums for all artifacts |

### Release Notes

The release workflow extracts the changelog section for the released version and uses it as release notes.

---

## Hotfix Releases

For urgent fixes to a released version:

### 1. Create Hotfix Branch

```bash
git checkout -b hotfix/v1.2.4 v1.2.3
```

### 2. Apply Fix

Make the necessary changes and commit:

```bash
git commit -m "Fix critical bug in job scheduler"
```

### 3. Update Version and Changelog

```bash
# Edit gradle.properties: version=1.2.4
# Update CHANGELOG.md
git commit -m "Release v1.2.4"
```

### 4. Tag and Push

```bash
git tag v1.2.4
git push origin hotfix/v1.2.4 --tags
```

### 5. Merge Back to Main

```bash
git checkout main
git merge hotfix/v1.2.4
git push origin main
```

---

## Post-Release Tasks

After a release is published:

### 1. Verify GitHub Release

```bash
# Check release page
open https://github.com/samson-media/barn/releases/latest

# Verify all artifacts are present
curl -s https://api.github.com/repos/samson-media/barn/releases/latest | jq '.assets[].name'

# Expected artifacts:
# - barn-linux-x64
# - barn-linux-arm64
# - barn-macos-x64
# - barn-macos-arm64
# - barn-windows-x64.exe
# - barn-windows-x64.msi
# - barn-linux-x64.deb
# - SHA256SUMS
```

### 2. Verify Package Manager Updates

#### AUR (Arch Linux) - Automated

Check the GitHub Actions workflow status:

```bash
open https://github.com/samson-media/barn/actions/workflows/update-aur.yml
```

Verify the AUR package:

```bash
# Check AUR page shows new version
open https://aur.archlinux.org/packages/barn

# Test installation (on Arch Linux)
yay -Sy barn
barn --version
```

If the workflow failed, manually update:

```bash
git clone ssh://aur@aur.archlinux.org/barn.git aur-barn
cd aur-barn
# Update pkgver and checksums in PKGBUILD
makepkg --printsrcinfo > .SRCINFO
git add -A && git commit -m "Update to vX.Y.Z" && git push
```

#### Homebrew (macOS) - Automated

Check the GitHub Actions workflow status:

```bash
open https://github.com/samson-media/barn/actions/workflows/update-homebrew.yml
```

Verify the Homebrew formula:

```bash
# Update tap and check version
brew update
brew info samson-media/barn/barn

# Test installation (on macOS)
brew upgrade barn
barn --version
```

#### WinGet (Windows) - Manual

Submit a PR to winget-pkgs:

```bash
# Fork and clone microsoft/winget-pkgs
# Create manifests/s/SamsonMedia/Barn/X.Y.Z/
# Add version, installer, and locale manifests
# See Distribution docs for manifest format
```

Validate and submit:

```powershell
winget validate manifests/s/SamsonMedia/Barn/X.Y.Z/
# Create PR to microsoft/winget-pkgs
```

### 3. Announce Release

- Post to relevant channels (Discord, Twitter, blog, etc.)
- Update project website if applicable

### 4. Monitor for Issues

- Watch GitHub Issues for regression reports
- Monitor AUR comments: https://aur.archlinux.org/packages/barn
- Check Homebrew issues if any

### Troubleshooting Failed Updates

| Package Manager | Check Logs | Manual Fix |
|-----------------|------------|------------|
| AUR | Actions → update-aur.yml | See [Distribution](distribution.md#updating-aur-package) |
| Homebrew | Actions → update-homebrew.yml | See [Distribution](distribution.md#homebrew-tap-setup) |
| WinGet | N/A (manual) | See [Distribution](distribution.md#winget-submission) |

---

## Reverting a Release

If a critical issue is found after release:

### Option 1: Hotfix Release

Create a patch release with the fix (preferred).

### Option 2: Yank the Release

Mark the GitHub Release as a pre-release or delete it:

```bash
# Mark as pre-release via GitHub UI
# Or delete the release (artifacts remain)
```

Never delete tags that have been pushed publicly.

---

## Version Embedding

The version is embedded in the binary at build time. To verify:

```bash
barn --version
# Output: barn 1.2.3
```

The version is also available programmatically:

```bash
barn version --output=json
# Output: {"version": "1.2.3", "commit": "abc123", "built_at": "2026-01-21T10:00:00Z"}
```

---

## Next Steps

- [CI/CD](ci-cd.md) - Automated release workflow details
- [Distribution](distribution.md) - Package manager updates
