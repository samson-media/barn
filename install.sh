#!/bin/sh
# Barn Installer Script
# Usage: curl -fsSL https://raw.githubusercontent.com/samson-media/barn/main/install.sh | sh
#
# This script installs the Barn job daemon and sets it up to run on startup.
# Supports: Linux (systemd), macOS (launchd)

set -e

# Configuration
REPO="samson-media/barn"
INSTALL_DIR="/usr/local/bin"
BARN_DIR=""  # Will be set based on OS

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Print functions
info() {
    printf "${GREEN}[INFO]${NC} %s\n" "$1"
}

warn() {
    printf "${YELLOW}[WARN]${NC} %s\n" "$1"
}

error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1"
    exit 1
}

# Detect OS
detect_os() {
    case "$(uname -s)" in
        Linux*)  OS="linux" ;;
        Darwin*) OS="macos" ;;
        *)       error "Unsupported operating system: $(uname -s)" ;;
    esac
}

# Detect architecture
detect_arch() {
    case "$(uname -m)" in
        x86_64|amd64)  ARCH="x64" ;;
        aarch64|arm64) ARCH="arm64" ;;
        *)             error "Unsupported architecture: $(uname -m)" ;;
    esac
}

# Get latest release version
get_latest_version() {
    VERSION=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')
    if [ -z "$VERSION" ]; then
        error "Failed to get latest version"
    fi
    info "Latest version: $VERSION"
}

# Download and install binary
install_binary() {
    BINARY_NAME="barn-${OS}-${ARCH}"
    DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${VERSION}/${BINARY_NAME}"

    info "Downloading $BINARY_NAME..."

    # Create temp directory
    TMP_DIR=$(mktemp -d)
    trap "rm -rf $TMP_DIR" EXIT

    # Download binary
    if ! curl -fsSL "$DOWNLOAD_URL" -o "$TMP_DIR/barn"; then
        error "Failed to download binary from $DOWNLOAD_URL"
    fi

    # Make executable
    chmod +x "$TMP_DIR/barn"

    # Verify it works
    if ! "$TMP_DIR/barn" --version > /dev/null 2>&1; then
        error "Downloaded binary is not executable or corrupted"
    fi

    # Install to destination
    if [ -w "$INSTALL_DIR" ]; then
        mv "$TMP_DIR/barn" "$INSTALL_DIR/barn"
    else
        info "Installing to $INSTALL_DIR requires sudo..."
        sudo mv "$TMP_DIR/barn" "$INSTALL_DIR/barn"
    fi

    info "Installed barn to $INSTALL_DIR/barn"
}

# Setup systemd service (Linux)
setup_systemd() {
    info "Setting up systemd service..."

    # Determine if we should use user or system service
    if [ "$(id -u)" -eq 0 ]; then
        # Running as root - install system service
        SERVICE_FILE="/etc/systemd/system/barn.service"
        BARN_DIR="/var/lib/barn"
        USER_MODE=""
        SYSTEMCTL_CMD="systemctl"
    else
        # Running as user - install user service
        SERVICE_FILE="$HOME/.config/systemd/user/barn.service"
        BARN_DIR="$HOME/.local/share/barn"
        USER_MODE="--user"
        SYSTEMCTL_CMD="systemctl --user"

        # Create directory if needed
        mkdir -p "$(dirname "$SERVICE_FILE")"
    fi

    # Create barn data directory
    mkdir -p "$BARN_DIR"

    # Generate service file
    if [ -n "$USER_MODE" ]; then
        # User service
        cat > "$SERVICE_FILE" << EOF
[Unit]
Description=Barn Job Daemon (User)
Documentation=https://github.com/samson-media/barn

[Service]
Type=simple
ExecStart=$INSTALL_DIR/barn service start --foreground --barn-dir $BARN_DIR
ExecStop=$INSTALL_DIR/barn service stop --barn-dir $BARN_DIR
Restart=on-failure
RestartSec=5

[Install]
WantedBy=default.target
EOF
    else
        # System service
        sudo tee "$SERVICE_FILE" > /dev/null << EOF
[Unit]
Description=Barn Job Daemon
Documentation=https://github.com/samson-media/barn
After=network.target

[Service]
Type=simple
ExecStart=$INSTALL_DIR/barn service start --foreground --barn-dir $BARN_DIR
ExecStop=$INSTALL_DIR/barn service stop --barn-dir $BARN_DIR
ExecReload=/bin/kill -HUP \$MAINPID
Restart=on-failure
RestartSec=5

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=read-only
PrivateTmp=false
ReadWritePaths=$BARN_DIR

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=barn

[Install]
WantedBy=multi-user.target
EOF
    fi

    info "Created service file: $SERVICE_FILE"

    # Reload systemd
    $SYSTEMCTL_CMD daemon-reload

    # Enable service
    $SYSTEMCTL_CMD enable barn
    info "Enabled barn service to start on boot"

    # Start service
    $SYSTEMCTL_CMD start barn
    info "Started barn service"

    # Show status
    $SYSTEMCTL_CMD status barn --no-pager || true
}

# Setup launchd service (macOS)
setup_launchd() {
    info "Setting up launchd service..."

    # Always install as user agent on macOS
    PLIST_FILE="$HOME/Library/LaunchAgents/com.samsonmedia.barn.plist"
    BARN_DIR="$HOME/Library/Application Support/barn"
    LOGS_DIR="$BARN_DIR/logs"

    # Create directories
    mkdir -p "$(dirname "$PLIST_FILE")"
    mkdir -p "$BARN_DIR"
    mkdir -p "$LOGS_DIR"

    # Generate plist file
    cat > "$PLIST_FILE" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.samsonmedia.barn</string>

    <key>ProgramArguments</key>
    <array>
        <string>$INSTALL_DIR/barn</string>
        <string>service</string>
        <string>start</string>
        <string>--foreground</string>
        <string>--barn-dir</string>
        <string>$BARN_DIR</string>
    </array>

    <key>RunAtLoad</key>
    <true/>

    <key>KeepAlive</key>
    <true/>

    <key>StandardOutPath</key>
    <string>$LOGS_DIR/barn.log</string>

    <key>StandardErrorPath</key>
    <string>$LOGS_DIR/barn.log</string>

    <key>WorkingDirectory</key>
    <string>$BARN_DIR</string>
</dict>
</plist>
EOF

    info "Created plist file: $PLIST_FILE"

    # Unload if already loaded (ignore errors)
    launchctl unload "$PLIST_FILE" 2>/dev/null || true

    # Load the service
    launchctl load "$PLIST_FILE"
    info "Loaded barn service into launchd"

    # Start the service
    launchctl start com.samsonmedia.barn
    info "Started barn service"

    # Verify it's running
    sleep 2
    if launchctl list | grep -q "com.samsonmedia.barn"; then
        info "Barn service is running"
    else
        warn "Barn service may not have started correctly. Check logs at $LOGS_DIR/barn.log"
    fi
}

# Main installation function
main() {
    echo "========================================"
    echo "       Barn Installer"
    echo "========================================"
    echo ""

    detect_os
    detect_arch
    info "Detected: $OS-$ARCH"

    get_latest_version
    install_binary

    # Setup service based on OS
    case "$OS" in
        linux)
            if [ -d "/run/systemd/system" ]; then
                setup_systemd
            else
                warn "systemd not detected. Barn installed but not configured to run on startup."
                warn "You can manually run: barn service start"
            fi
            ;;
        macos)
            setup_launchd
            ;;
    esac

    echo ""
    echo "========================================"
    info "Barn installation complete!"
    echo ""
    echo "Commands:"
    echo "  barn status           - Show job status"
    echo "  barn run -- <cmd>     - Run a command as a job"
    echo "  barn service status   - Show service status"
    echo ""
    echo "Data directory: $BARN_DIR"
    echo "========================================"
}

# Run main
main
