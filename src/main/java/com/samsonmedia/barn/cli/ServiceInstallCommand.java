package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.config.OperatingSystem;
import com.samsonmedia.barn.logging.BarnLogger;
import com.samsonmedia.barn.platform.linux.SystemdManager;
import com.samsonmedia.barn.platform.macos.LaunchdManager;
import com.samsonmedia.barn.platform.windows.WindowsServiceManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to install Barn as a system service.
 *
 * <p>Supports platform-specific service installation:
 * <ul>
 *   <li>macOS: launchd (LaunchDaemons/LaunchAgents)</li>
 *   <li>Linux: systemd (planned)</li>
 *   <li>Windows: Windows Service (planned)</li>
 * </ul>
 */
@Command(
    name = "install",
    mixinStandardHelpOptions = true,
    description = "Install Barn as a system service"
)
public class ServiceInstallCommand extends BaseCommand {

    private static final BarnLogger LOG = BarnLogger.getLogger(ServiceInstallCommand.class);

    @Option(names = {"--user"}, description = "Install as user service instead of system-wide")
    private boolean userService;

    @Option(names = {"--barn-dir"}, description = "Barn data directory")
    private Path barnDir;

    @Option(names = {"--binary"}, description = "Path to barn binary (auto-detected if not specified)")
    private Path binaryPath;

    @Override
    public Integer call() {
        OperatingSystem os = OperatingSystem.current();

        return switch (os) {
            case MACOS -> installMacOs();
            case LINUX -> installLinux();
            case WINDOWS -> installWindows();
        };
    }

    private int installMacOs() {
        LaunchdManager launchd = new LaunchdManager();

        // Check if already installed
        if (launchd.isLoaded()) {
            outputError("Barn service is already installed. Use 'barn service uninstall' first.");
            return EXIT_ERROR;
        }

        try {
            Path effectiveBarnDir = getEffectiveBarnDir();
            Path effectiveBinaryPath = getEffectiveBinaryPath();

            // Ensure barn directory exists
            Files.createDirectories(effectiveBarnDir);
            Files.createDirectories(effectiveBarnDir.resolve("logs"));

            // Generate and write plist
            String plistContent = launchd.generatePlist(effectiveBinaryPath, effectiveBarnDir);
            Path plistPath = userService ? launchd.getUserAgentPath() : launchd.getSystemDaemonPath();

            // Check if we need sudo for system-wide installation
            if (!userService && !canWriteToPath(plistPath.getParent())) {
                outputError("System-wide installation requires root privileges. "
                    + "Run with sudo or use --user for user-level installation.");
                return EXIT_ERROR;
            }

            launchd.writePlist(plistPath, plistContent);

            // Load the service
            launchd.load(plistPath);

            String serviceType = userService ? "user agent" : "system daemon";
            getOut().println("Barn installed as macOS " + serviceType);
            getOut().println("Plist location: " + plistPath);
            getOut().println("Service will start automatically on login/boot.");
            getOut().println();
            getOut().println("To start now: barn service start");
            getOut().println("To uninstall: barn service uninstall");

            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to install service", e);
            return EXIT_ERROR;
        } catch (LaunchdManager.LaunchdException e) {
            outputError("Failed to load service: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private int installLinux() {
        if (!SystemdManager.hasSystemd()) {
            outputError("Systemd is not available on this system");
            return EXIT_ERROR;
        }

        SystemdManager systemd = new SystemdManager();

        // Check if already installed
        Path unitPath = userService ? systemd.getUserServicePath() : systemd.getSystemServicePath();
        if (Files.exists(unitPath)) {
            outputError("Barn service is already installed. Use 'barn service uninstall' first.");
            return EXIT_ERROR;
        }

        try {
            Path effectiveBarnDir = getEffectiveBarnDir();
            Path effectiveBinaryPath = getEffectiveBinaryPath();

            // Ensure barn directory exists
            Files.createDirectories(effectiveBarnDir);
            Files.createDirectories(effectiveBarnDir.resolve("logs"));

            // Generate and write unit file
            String unitContent = systemd.generateUnitFile(effectiveBinaryPath, effectiveBarnDir, userService);

            // Check if we need sudo for system-wide installation
            if (!userService && !canWriteToPath(unitPath.getParent())) {
                outputError("System-wide installation requires root privileges. "
                    + "Run with sudo or use --user for user-level installation.");
                return EXIT_ERROR;
            }

            systemd.writeUnitFile(unitPath, unitContent);

            // Reload systemd and enable the service
            systemd.daemonReload(userService);
            systemd.enable(userService);

            String serviceType = userService ? "user service" : "system service";
            getOut().println("Barn installed as Linux " + serviceType);
            getOut().println("Unit file: " + unitPath);
            getOut().println("Service will start automatically on boot.");
            getOut().println();
            getOut().println("To start now: barn service start");
            getOut().println("To uninstall: barn service uninstall");

            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to install service", e);
            return EXIT_ERROR;
        } catch (SystemdManager.SystemdException e) {
            outputError("Failed to configure systemd: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private int installWindows() {
        if (!WindowsServiceManager.hasWindowsServices()) {
            outputError("Windows services are not available on this system");
            return EXIT_ERROR;
        }

        WindowsServiceManager winService = new WindowsServiceManager();

        // Check if already installed
        if (winService.isInstalled()) {
            outputError("Barn service is already installed. Use 'barn service uninstall' first.");
            return EXIT_ERROR;
        }

        // Check for admin privileges
        if (!WindowsServiceManager.isAdmin()) {
            outputError("Windows service installation requires administrator privileges. "
                + "Please run Command Prompt as Administrator and try again.");
            return EXIT_ERROR;
        }

        try {
            Path effectiveBarnDir = getEffectiveBarnDir();
            Path effectiveBinaryPath = getEffectiveBinaryPath();

            // Ensure barn directory exists
            Files.createDirectories(effectiveBarnDir);
            Files.createDirectories(effectiveBarnDir.resolve("logs"));

            // Install the service
            winService.install(effectiveBinaryPath.toString(), effectiveBarnDir.toString());

            getOut().println("Barn installed as Windows service");
            getOut().println("Service name: " + winService.getServiceName());
            getOut().println("Service will start automatically on boot.");
            getOut().println();
            getOut().println("To start now: barn service start");
            getOut().println("To uninstall: barn service uninstall");

            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to install service", e);
            return EXIT_ERROR;
        } catch (WindowsServiceManager.WindowsServiceException e) {
            outputError("Failed to install Windows service: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private Path getEffectiveBarnDir() {
        if (barnDir != null) {
            return barnDir;
        }
        return ConfigDefaults.getDefaultBaseDir();
    }

    private Path getEffectiveBinaryPath() {
        if (binaryPath != null) {
            return binaryPath;
        }

        // Try to find barn binary in common locations
        Path[] candidates = {
            Path.of("/usr/local/bin/barn"),
            Path.of("/opt/homebrew/bin/barn"),
            Path.of(System.getProperty("user.home"), ".local", "bin", "barn"),
            Path.of(System.getProperty("user.home"), "bin", "barn")
        };

        for (Path candidate : candidates) {
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }

        // Fall back to assuming barn is in PATH
        return Path.of("barn");
    }

    private boolean canWriteToPath(Path path) {
        if (path == null) {
            return false;
        }
        if (Files.exists(path)) {
            return Files.isWritable(path);
        }
        // Check parent
        return canWriteToPath(path.getParent());
    }
}
