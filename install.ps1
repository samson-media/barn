# Barn Installer Script for Windows
# Usage: iwr -useb https://raw.githubusercontent.com/samson-media/barn/main/install.ps1 | iex
#
# This script installs the Barn job daemon and sets it up to run on startup.
# Requires: PowerShell 5.1+ and Administrator privileges (for Windows Service)

param(
    [switch]$NoService,
    [string]$InstallDir = "$env:ProgramFiles\barn",
    [string]$BarnDir = "$env:LOCALAPPDATA\barn"
)

$ErrorActionPreference = "Stop"

# Configuration
$Repo = "samson-media/barn"
$ServiceName = "barn"
$ServiceDisplayName = "Barn Job Daemon"

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] " -ForegroundColor Green -NoNewline
    Write-Host $Message
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[WARN] " -ForegroundColor Yellow -NoNewline
    Write-Host $Message
}

function Write-Err {
    param([string]$Message)
    Write-Host "[ERROR] " -ForegroundColor Red -NoNewline
    Write-Host $Message
    exit 1
}

function Test-Administrator {
    $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentUser)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-LatestVersion {
    Write-Info "Getting latest version..."
    try {
        $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest"
        $version = $release.tag_name
        Write-Info "Latest version: $version"
        return $version
    }
    catch {
        Write-Err "Failed to get latest version: $_"
    }
}

function Install-Binary {
    param([string]$Version)

    $binaryName = "barn-windows-x64.exe"
    $downloadUrl = "https://github.com/$Repo/releases/download/$Version/$binaryName"

    Write-Info "Downloading $binaryName..."

    # Create install directory
    if (-not (Test-Path $InstallDir)) {
        New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
    }

    $destPath = Join-Path $InstallDir "barn.exe"

    try {
        # Download with progress
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $downloadUrl -OutFile $destPath -UseBasicParsing
        $ProgressPreference = 'Continue'
    }
    catch {
        Write-Err "Failed to download binary: $_"
    }

    # Verify it works
    try {
        $versionOutput = & $destPath --version 2>&1
        Write-Info "Installed: $versionOutput"
    }
    catch {
        Write-Err "Downloaded binary is not executable or corrupted"
    }

    Write-Info "Installed barn to $destPath"
    return $destPath
}

function Add-ToPath {
    param([string]$Directory)

    $currentPath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    if ($currentPath -notlike "*$Directory*") {
        Write-Info "Adding $Directory to system PATH..."
        $newPath = "$currentPath;$Directory"
        [Environment]::SetEnvironmentVariable("Path", $newPath, "Machine")
        $env:Path = "$env:Path;$Directory"
        Write-Info "Added to PATH. You may need to restart your terminal."
    }
    else {
        Write-Info "$Directory is already in PATH"
    }
}

function Install-WindowsService {
    param([string]$BinaryPath)

    Write-Info "Setting up Windows Service..."

    # Create barn data directory
    if (-not (Test-Path $BarnDir)) {
        New-Item -ItemType Directory -Path $BarnDir -Force | Out-Null
    }

    # Check if service already exists
    $existingService = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if ($existingService) {
        Write-Info "Stopping existing service..."
        Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
        Write-Info "Removing existing service..."
        sc.exe delete $ServiceName | Out-Null
        Start-Sleep -Seconds 2
    }

    # Create the service using sc.exe
    $binPath = "`"$BinaryPath`" service start --foreground --barn-dir `"$BarnDir`""

    Write-Info "Creating service..."
    $result = sc.exe create $ServiceName binPath= $binPath DisplayName= $ServiceDisplayName start= auto
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Failed to create service: $result"
    }

    # Set description
    sc.exe description $ServiceName "Cross-platform job daemon for media processing" | Out-Null

    # Configure failure recovery (restart after 5 seconds)
    sc.exe failure $ServiceName reset= 86400 actions= restart/5000/restart/5000/restart/5000 | Out-Null

    Write-Info "Service created: $ServiceName"

    # Start the service
    Write-Info "Starting service..."
    Start-Service -Name $ServiceName

    # Verify it's running
    Start-Sleep -Seconds 2
    $service = Get-Service -Name $ServiceName
    if ($service.Status -eq "Running") {
        Write-Info "Service is running"
    }
    else {
        Write-Warn "Service may not have started correctly. Status: $($service.Status)"
    }
}

function Install-ScheduledTask {
    param([string]$BinaryPath)

    Write-Info "Setting up Scheduled Task (user mode)..."

    # Create barn data directory
    if (-not (Test-Path $BarnDir)) {
        New-Item -ItemType Directory -Path $BarnDir -Force | Out-Null
    }

    $taskName = "BarnJobDaemon"

    # Remove existing task if present
    $existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($existingTask) {
        Write-Info "Removing existing scheduled task..."
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
    }

    # Create action
    $action = New-ScheduledTaskAction -Execute $BinaryPath -Argument "service start --foreground --barn-dir `"$BarnDir`""

    # Create trigger (at logon)
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME

    # Create settings
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)

    # Register task
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description "Barn Job Daemon" | Out-Null

    Write-Info "Scheduled task created: $taskName"

    # Start the task now
    Write-Info "Starting barn..."
    Start-ScheduledTask -TaskName $taskName

    Start-Sleep -Seconds 2
    Write-Info "Barn is running"
}

function Main {
    Write-Host "========================================"
    Write-Host "       Barn Installer for Windows"
    Write-Host "========================================"
    Write-Host ""

    $isAdmin = Test-Administrator

    if (-not $isAdmin) {
        Write-Warn "Not running as Administrator."
        Write-Warn "Will install for current user only (using Scheduled Task instead of Windows Service)."
        Write-Host ""

        # Adjust install directory for non-admin
        $InstallDir = Join-Path $env:LOCALAPPDATA "barn"
    }

    Write-Info "Install directory: $InstallDir"
    Write-Info "Data directory: $BarnDir"
    Write-Host ""

    $version = Get-LatestVersion
    $binaryPath = Install-Binary -Version $version

    if ($isAdmin) {
        Add-ToPath -Directory $InstallDir
    }
    else {
        # Add to user PATH
        $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($currentPath -notlike "*$InstallDir*") {
            Write-Info "Adding $InstallDir to user PATH..."
            $newPath = "$currentPath;$InstallDir"
            [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
            $env:Path = "$env:Path;$InstallDir"
        }
    }

    if (-not $NoService) {
        if ($isAdmin) {
            Install-WindowsService -BinaryPath $binaryPath
        }
        else {
            Install-ScheduledTask -BinaryPath $binaryPath
        }
    }
    else {
        Write-Info "Skipping service setup (use -NoService to disable)"
    }

    Write-Host ""
    Write-Host "========================================"
    Write-Info "Barn installation complete!"
    Write-Host ""
    Write-Host "Commands:"
    Write-Host "  barn status           - Show job status"
    Write-Host "  barn run -- <cmd>     - Run a command as a job"
    Write-Host "  barn service status   - Show service status"
    Write-Host ""
    Write-Host "Data directory: $BarnDir"
    Write-Host "========================================"
}

# Run main
Main
