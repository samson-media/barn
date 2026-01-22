package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.samsonmedia.barn.config.ConfigDefaults;
import com.samsonmedia.barn.execution.ProcessUtils;
import com.samsonmedia.barn.ipc.IpcClient;
import com.samsonmedia.barn.ipc.IpcException;
import com.samsonmedia.barn.jobs.Job;
import com.samsonmedia.barn.jobs.JobRepository;
import com.samsonmedia.barn.logging.BarnLogger;
import com.samsonmedia.barn.state.BarnDirectories;
import com.samsonmedia.barn.state.JobState;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command to stop a running job.
 */
@Command(
    name = "kill",
    mixinStandardHelpOptions = true,
    description = "Stop a running job"
)
public class KillCommand extends BaseCommand {

    private static final BarnLogger LOG = BarnLogger.getLogger(KillCommand.class);

    @Parameters(index = "0", description = "Job ID")
    private String jobId;

    @Option(names = {"--force", "-f"}, description = "Force kill (SIGKILL)")
    private boolean force;

    @Option(names = {"--quiet", "-q"}, description = "No output on success")
    private boolean quiet;

    // For testing
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
        if (isOffline()) {
            return runOffline();
        }
        return runWithService();
    }

    private int runOffline() {
        try {
            BarnDirectories dirs = getBarnDirectories();
            JobRepository repository = new JobRepository(dirs);

            Optional<Job> jobOpt = repository.findById(jobId);
            if (jobOpt.isEmpty()) {
                outputError("Job not found: " + jobId);
                return EXIT_ERROR;
            }

            Job job = jobOpt.get();
            if (job.state() != JobState.RUNNING) {
                outputError("Job is not running (state: " + job.state().toLowercase() + ")");
                return EXIT_ERROR;
            }

            // Kill the process if it has a PID
            if (job.pid() != null) {
                boolean killed;
                if (force) {
                    killed = ProcessUtils.killTreeForcibly(job.pid());
                } else {
                    killed = ProcessUtils.killTree(job.pid());
                }
                LOG.debug("Kill result for PID {}: {}", job.pid(), killed);
            }

            // Mark job as canceled
            repository.markCanceled(jobId);

            // Output result
            if (!quiet) {
                outputKillResult(job);
            }

            return EXIT_SUCCESS;

        } catch (IOException e) {
            outputError("Failed to kill job", e);
            return EXIT_ERROR;
        }
    }

    private int runWithService() {
        try (IpcClient client = new IpcClient(getBarnDirectories().getSocketPath())) {
            // Build request payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("jobId", jobId);
            payload.put("force", force);

            // Send request and get response
            Object response = client.send("kill_job", payload, Object.class);

            // Convert response to Job
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            Job job = mapper.convertValue(response, Job.class);

            // Output result
            if (!quiet) {
                outputKillResult(job);
            }

            return EXIT_SUCCESS;

        } catch (IpcException e) {
            if ("SERVICE_NOT_RUNNING".equals(e.getCode())) {
                outputError("Service not running. Start with: barn service start\n"
                    + "Or use --offline to run without the service.");
            } else if ("NOT_FOUND".equals(e.getCode())) {
                outputError("Job not found: " + jobId);
            } else if ("INVALID_STATE".equals(e.getCode())) {
                outputError(e.getMessage());
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
        Path defaultDir = ConfigDefaults.getDefaultBaseDir();
        return new BarnDirectories(defaultDir);
    }

    private void outputKillResult(Job job) {
        OutputFormat format = globalOptions != null
            ? globalOptions.getOutputFormat()
            : OutputFormat.HUMAN;

        switch (format) {
            case HUMAN -> outputHumanFormat(job);
            case JSON -> outputJsonFormat(job);
            case XML -> outputXmlFormat(job);
            default -> outputHumanFormat(job);
        }
    }

    private void outputHumanFormat(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append("Job ").append(job.id()).append(" killed\n");
        sb.append("  Previous state: ").append(job.state().toLowercase()).append("\n");
        sb.append("  New state: canceled");
        getOut().println(sb.toString());
    }

    private void outputJsonFormat(Job job) {
        Map<String, Object> response = buildResponseMap(job);
        output(response);
    }

    private void outputXmlFormat(Job job) {
        Map<String, Object> response = buildResponseMap(job);
        output(response);
    }

    private Map<String, Object> buildResponseMap(Job job) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", job.id());
        response.put("previousState", job.state().toLowercase());
        response.put("newState", "canceled");
        response.put("message", "Job was killed");
        return response;
    }
}
