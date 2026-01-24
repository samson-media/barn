package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.samsonmedia.barn.config.ConfigDefaults;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to install default configuration files.
 *
 * <p>Installs barn.conf and load level whitelist files (high.load, medium.load, low.load)
 * to the specified directory. By default, installs to the system config directory.
 */
@Command(
    name = "install",
    mixinStandardHelpOptions = true,
    description = "Install default configuration files"
)
public class ConfigInstallCommand extends BaseCommand {

    private static final String DEFAULTS_PATH = "/defaults/";
    private static final List<String> CONFIG_FILES = List.of(
        "barn.conf",
        "high.load",
        "medium.load",
        "low.load"
    );

    @Option(names = {"--directory", "-d"}, description = "Target directory for config files")
    private Path targetDirectory;

    @Option(names = {"--force", "-f"}, description = "Skip confirmation and overwrite existing files")
    private boolean force;

    @Option(names = {"--show"}, description = "Show what would be installed without writing")
    private boolean showOnly;

    @Override
    public Integer call() {
        Path targetDir = targetDirectory != null
            ? targetDirectory
            : ConfigDefaults.getSystemConfigDir();

        if (showOnly) {
            return showWhatWouldBeInstalled(targetDir);
        }

        if (!force) {
            getOut().println("The following files will be created/overwritten:");
            for (String file : CONFIG_FILES) {
                getOut().println("  " + targetDir.resolve(file));
            }
            getOut().println();
            getOut().println("WARNING: This will overwrite any existing configuration.");
            getOut().println("Use --force to proceed, or --show to preview without writing.");
            return EXIT_SUCCESS;
        }

        return installConfigFiles(targetDir);
    }

    private Integer showWhatWouldBeInstalled(Path targetDir) {
        getOut().println("Files that would be installed to: " + targetDir);
        getOut().println();

        for (String filename : CONFIG_FILES) {
            getOut().println("=== " + filename + " ===");
            try {
                String content = loadResourceFile(filename);
                getOut().println(content);
                getOut().println();
            } catch (IOException e) {
                outputError("Failed to read resource: " + filename + " - " + e.getMessage());
                return EXIT_ERROR;
            }
        }

        return EXIT_SUCCESS;
    }

    private Integer installConfigFiles(Path targetDir) {
        try {
            // Create target directory if needed
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            for (String filename : CONFIG_FILES) {
                Path targetPath = targetDir.resolve(filename);
                String content = loadResourceFile(filename);
                Files.writeString(targetPath, content);
                getOut().println("Installed: " + targetPath);
            }

            getOut().println();
            getOut().println("Configuration installed successfully.");
            getOut().println("Config directory: " + targetDir);

            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to install configuration: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private String loadResourceFile(String filename) throws IOException {
        String resourcePath = DEFAULTS_PATH + filename;
        try (InputStream is = ConfigInstallCommand.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
