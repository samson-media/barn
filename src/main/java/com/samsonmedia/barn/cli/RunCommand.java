package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.config.JobsConfig;
import com.samsonmedia.barn.ipc.IpcClient;
import com.samsonmedia.barn.ipc.IpcException;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.state.BarnDirectories;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command to start a new job.
 *
 * <p>Creates a job with the specified command and queues it for execution.
 * In offline mode, the job is executed directly without the service.
 */
@Command(
    name = "run",
    mixinStandardHelpOptions = true,
    description = "Start a new job"
)
public class RunCommand extends BaseCommand {

    private static final Logger LOG = LoggerFactory.getLogger(RunCommand.class);

    @Option(names = {"--tag", "-t"}, description = "Tag for filtering jobs")
    private String tag;

    @Option(names = {"--max-retries"}, description = "Override max retries")
    private Integer maxRetries;

    @Option(names = {"--retry-delay"}, description = "Override retry delay (seconds)")
    private Integer retryDelay;

    @Option(names = {"--timeout"}, description = "Job timeout (seconds)")
    private Integer timeout;

    @Parameters(description = "Command to run", arity = "1..*")
    private List<String> command;

    // For testing - allows overriding the barn directory
    private Path barnDir;

    /**
     * Sets the barn directory (for testing).
     *
     * @param barnDir the barn directory path
     */
    public void setBarnDir(Path barnDir) {
        this.barnDir = barnDir;
    }

    @Override
    public Integer call() {
        if (command == null || command.isEmpty()) {
            outputError("No command specified");
            return EXIT_USAGE;
        }

        if (isOffline()) {
            return runOffline();
        }

        return runWithService();
    }

    private int runOffline() {
        try {
            BarnDirectories dirs = getBarnDirectories();
            dirs.initialize();

            JobRepository repository = new JobRepository(dirs);
            JobsConfig jobsConfig = buildJobsConfig();

            // Create the job
            Job job = repository.create(command, tag, jobsConfig);

            LOG.info("Created job {} in offline mode", job.id());

            // Output job info in the appropriate format
            outputJobCreated(job);

            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to create job", e);
            return EXIT_ERROR;
        }
    }

    private int runWithService() {
        try (IpcClient client = new IpcClient(getBarnDirectories().getSocketPath())) {
            // Build request payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("command", command);
            if (tag != null) {
                payload.put("tag", tag);
            }
            if (maxRetries != null) {
                payload.put("maxRetries", maxRetries);
            }
            if (retryDelay != null) {
                payload.put("retryDelaySeconds", retryDelay);
            }

            // Send request and get response
            Object response = client.send("run_job", payload, Object.class);

            // Convert response to Job
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            Job job = mapper.convertValue(response, Job.class);

            LOG.info("Created job {} via service", job.id());

            // Output job info
            outputJobCreated(job);

            return EXIT_SUCCESS;

        } catch (IpcException e) {
            if ("SERVICE_NOT_RUNNING".equals(e.getCode())) {
                outputError("Service not running. Start with: barn service start\n"
                    + "Or use --offline to run without the service.");
            } else {
                outputError("IPC error: " + e.getMessage());
            }
            return EXIT_ERROR;
        }
    }

    private BarnDirectories getBarnDirectories() {
        if (barnDir != null) {
            return new BarnDirectories(barnDir);
        }
        // Use default barn directories in system temp
        Path defaultDir = ConfigDefaults.getDefaultBaseDir();
        return new BarnDirectories(defaultDir);
    }

    private JobsConfig buildJobsConfig() {
        JobsConfig defaults = JobsConfig.withDefaults();

        // Override with command-line options if provided
        int actualMaxRetries = maxRetries != null ? maxRetries : defaults.maxRetries();
        int actualRetryDelay = retryDelay != null ? retryDelay : defaults.retryDelaySeconds();
        int actualTimeout = timeout != null ? timeout : defaults.defaultTimeoutSeconds();

        return new JobsConfig(
            actualTimeout,
            actualMaxRetries,
            actualRetryDelay,
            defaults.retryBackoffMultiplier(),
            defaults.retryOnExitCodes()
        );
    }

    private void outputJobCreated(Job job) {
        OutputFormat format = globalOptions != null
            ? globalOptions.getOutputFormat()
            : OutputFormat.HUMAN;

        if (format == OutputFormat.HUMAN) {
            outputHumanFormat(job);
        } else {
            // JSON and XML use the standard formatters
            output(job);
        }
    }

    private void outputHumanFormat(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append("Job created: ").append(job.id()).append("\n");
        sb.append("  Command: ").append(String.join(" ", job.command())).append("\n");
        sb.append("  Tag: ").append(job.tag() != null ? job.tag() : "-").append("\n");
        sb.append("  State: ").append(job.state().toString().toLowerCase());

        getOut().println(sb.toString());
    }
}
