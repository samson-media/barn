package com.samsonmedia.barn.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import com.samsonmedia.barn.config.OperatingSystem;
import com.samsonmedia.barn.logging.BarnLogger;
import com.samsonmedia.barn.platform.linux.SystemdManager;
import com.samsonmedia.barn.platform.macos.LaunchdManager;
import com.samsonmedia.barn.platform.windows.WindowsServiceManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to uninstall Barn system service.
 *
 * <p>Removes platform-specific service registration:
 * <ul>
 *   <li>macOS: launchd plist files</li>
 *   <li>Linux: systemd unit files</li>
 *   <li>Windows: Windows Service registration</li>
 * </ul>
 *
 * <p>System-wide uninstallation requires root/administrator privileges.
 */
@Command(
    name = "uninstall",
    mixinStandardHelpOptions = true,
    description = "Uninstall Barn system service"
)
public class ServiceUninstallCommand extends BaseCommand {

    private static final BarnLogger LOG = BarnLogger.getLogger(ServiceUninstallCommand.class);

    @Option(names = {"--force"}, description = "Force uninstall even if service is running")
    private boolean force;

    @Override
    public Integer call() {
        OperatingSystem os = OperatingSystem.current();

        return switch (os) {
            case MACOS -> uninstallMacOs();
            case LINUX -> uninstallLinux();
            case WINDOWS -> uninstallWindows();
        };
    }

    private int uninstallMacOs() {
        LaunchdManager launchd = new LaunchdManager();

        Path plistPath = launchd.getSystemDaemonPath();

        // Check if installed
        if (!Files.exists(plistPath)) {
            outputError("Barn service is not installed");
            return EXIT_ERROR;
        }

        // Check if we need sudo for system-wide uninstallation
        if (!canWriteToPath(plistPath)) {
            outputError("System-wide uninstallation requires root privileges. Run with sudo.");
            return EXIT_ERROR;
        }

        try {
            // Stop the service first if it's running
            if (launchd.isLoaded()) {
                try {
                    launchd.unload(plistPath);
                } catch (LaunchdManager.LaunchdException e) {
                    if (!force) {
                        outputError("Failed to stop service: " + e.getMessage()
                            + ". Use --force to uninstall anyway.");
                        return EXIT_ERROR;
                    }
                    LOG.warn("Force uninstalling despite error: {}", e.getMessage());
                }
            }

            // Remove the plist file
            if (!launchd.deletePlist(plistPath)) {
                if (!force) {
                    outputError("Failed to remove plist file: " + plistPath);
                    return EXIT_ERROR;
                }
            }

            getOut().println("Barn system daemon uninstalled successfully");
            getOut().println("Removed: " + plistPath);

            return EXIT_SUCCESS;

        } catch (LaunchdManager.LaunchdException e) {
            outputError("Failed to uninstall service: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private int uninstallLinux() {
        if (!SystemdManager.hasSystemd()) {
            outputError("Systemd is not available on this system");
            return EXIT_ERROR;
        }

        SystemdManager systemd = new SystemdManager();
        Path unitPath = systemd.getSystemServicePath();

        // Check if installed
        if (!Files.exists(unitPath)) {
            outputError("Barn service is not installed");
            return EXIT_ERROR;
        }

        // Check if we need sudo for system-wide uninstallation
        if (!canWriteToPath(unitPath)) {
            outputError("System-wide uninstallation requires root privileges. Run with sudo.");
            return EXIT_ERROR;
        }

        try {
            // Stop and disable the service first (system-wide)
            if (systemd.isActive(false)) {
                try {
                    systemd.stop(false);
                } catch (SystemdManager.SystemdException e) {
                    if (!force) {
                        outputError("Failed to stop service: " + e.getMessage()
                            + ". Use --force to uninstall anyway.");
                        return EXIT_ERROR;
                    }
                    LOG.warn("Force uninstalling despite error: {}", e.getMessage());
                }
            }

            if (systemd.isEnabled(false)) {
                try {
                    systemd.disable(false);
                } catch (SystemdManager.SystemdException e) {
                    LOG.warn("Failed to disable service: {}", e.getMessage());
                }
            }

            // Remove the unit file
            if (!systemd.deleteUnitFile(unitPath)) {
                if (!force) {
                    outputError("Failed to remove unit file: " + unitPath);
                    return EXIT_ERROR;
                }
            }

            // Reload systemd (system-wide)
            try {
                systemd.daemonReload(false);
            } catch (SystemdManager.SystemdException e) {
                LOG.warn("Failed to reload systemd: {}", e.getMessage());
            }

            getOut().println("Barn system service uninstalled successfully");
            getOut().println("Removed: " + unitPath);

            return EXIT_SUCCESS;

        } catch (SystemdManager.SystemdException e) {
            outputError("Failed to uninstall service: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private int uninstallWindows() {
        if (!WindowsServiceManager.hasWindowsServices()) {
            outputError("Windows services are not available on this system");
            return EXIT_ERROR;
        }

        WindowsServiceManager winService = new WindowsServiceManager();

        // Check if installed
        if (!winService.isInstalled()) {
            outputError("Barn service is not installed");
            return EXIT_ERROR;
        }

        // Check for admin privileges
        if (!WindowsServiceManager.isAdmin()) {
            outputError("Windows service uninstallation requires administrator privileges. "
                + "Please run Command Prompt as Administrator and try again.");
            return EXIT_ERROR;
        }

        try {
            // Stop the service first if it's running
            if (winService.isRunning()) {
                try {
                    winService.stop();
                } catch (WindowsServiceManager.WindowsServiceException e) {
                    if (!force) {
                        outputError("Failed to stop service: " + e.getMessage()
                            + ". Use --force to uninstall anyway.");
                        return EXIT_ERROR;
                    }
                    LOG.warn("Force uninstalling despite error: {}", e.getMessage());
                }
            }

            // Uninstall the service
            winService.uninstall();

            getOut().println("Barn Windows service uninstalled successfully");

            return EXIT_SUCCESS;

        } catch (WindowsServiceManager.WindowsServiceException e) {
            outputError("Failed to uninstall service: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private boolean canWriteToPath(Path path) {
        if (path == null) {
            return false;
        }
        return Files.isWritable(path);
    }
}
