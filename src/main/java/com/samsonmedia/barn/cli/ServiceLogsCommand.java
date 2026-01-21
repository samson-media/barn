package com.samsonmedia.barn.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.samsonmedia.barn.config.ConfigDefaults;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to show Barn service logs.
 */
@Command(
    name = "logs",
    mixinStandardHelpOptions = true,
    description = "Show service logs"
)
public class ServiceLogsCommand extends BaseCommand {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceLogsCommand.class);
    private static final String LOG_FILE_NAME = "barn.log";

    @Option(names = {"--follow", "-f"}, description = "Follow log output")
    private boolean follow;

    @Option(names = {"--lines", "-n"}, description = "Number of lines to show", defaultValue = "100")
    private int lines;

    @Option(names = {"--barn-dir"}, description = "Barn data directory", hidden = true)
    private Path barnDir;

    @Override
    public Integer call() {
        try {
            Path effectiveBarnDir = getEffectiveBarnDir();
            Path logFile = effectiveBarnDir.resolve("logs").resolve(LOG_FILE_NAME);

            if (!Files.exists(logFile)) {
                getOut().println("No log file found at: " + logFile);
                return EXIT_SUCCESS;
            }

            if (follow) {
                return followLogs(logFile);
            } else {
                return tailLogs(logFile);
            }

        } catch (IOException e) {
            outputError("Failed to read logs", e);
            return EXIT_ERROR;
        }
    }

    private int tailLogs(Path logFile) throws IOException {
        List<String> lastLines = readLastLines(logFile, lines);

        for (String line : lastLines) {
            getOut().println(line);
        }

        return EXIT_SUCCESS;
    }

    private int followLogs(Path logFile) throws IOException {
        // First output existing lines
        tailLogs(logFile);

        // Then follow new content
        getOut().println("--- Following log output (Ctrl+C to stop) ---");

        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            raf.seek(raf.length());

            while (!Thread.currentThread().isInterrupted()) {
                String line = raf.readLine();
                if (line != null) {
                    getOut().println(line);
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return EXIT_SUCCESS;
    }

    /**
     * Reads the last N lines from a file efficiently.
     */
    private List<String> readLastLines(Path file, int numLines) throws IOException {
        List<String> allLines = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                allLines.add(line);
            }
        }

        if (allLines.size() <= numLines) {
            return allLines;
        }

        return allLines.subList(allLines.size() - numLines, allLines.size());
    }

    private Path getEffectiveBarnDir() {
        if (barnDir != null) {
            return barnDir;
        }
        return ConfigDefaults.getDefaultBaseDir();
    }
}
