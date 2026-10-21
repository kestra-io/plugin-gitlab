package io.kestra.plugin.gitlab.pipelines;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a GitLab CI/CD pipeline",
    description = """
        Creates a pipeline for a branch or tag with optional CI/CD variables. With `wait: true` the task polls the pipeline until it finishes, \
        fails when it ends `failed` or `canceled`, and cancels it when `maxWaitDuration` is reached or the execution is killed."""
)
@Plugin(
    examples = {
        @Example(
            title = "Run a pipeline on `main` with a variable and wait for the result.",
            full = true,
            code = """
                id: gitlab_pipeline_run
                namespace: company.team

                tasks:
                  - id: run_pipeline
                    type: io.kestra.plugin.gitlab.pipelines.Create
                    token: "{{ secret('GITLAB_TOKEN') }}"
                    projectId: "group%2Fmy-project"
                    ref: main
                    variables:
                      DEPLOY_ENV: staging
                    wait: true
                    maxWaitDuration: PT30M

                  - id: log_status
                    type: io.kestra.plugin.core.log.Log
                    message: "Pipeline {{ outputs.run_pipeline.id }} finished with {{ outputs.run_pipeline.status }}: {{ outputs.run_pipeline.webUrl }}"
                """
        ),
        @Example(
            title = "Start a pipeline on a self-hosted GitLab without waiting for it.",
            full = true,
            code = """
                id: gitlab_pipeline_start
                namespace: company.team

                tasks:
                  - id: start_pipeline
                    type: io.kestra.plugin.gitlab.pipelines.Create
                    url: https://gitlab.example.com
                    token: "{{ secret('GITLAB_TOKEN') }}"
                    projectId: "42"
                    ref: v1.4.0
                """
        )
    }
)
public class Create extends AbstractPipelineTask implements RunnableTask<Pipeline> {
    static final Duration MIN_POLL_INTERVAL = Duration.ofSeconds(1);

    @Schema(
        title = "Git ref",
        description = """
            Branch or tag to run the pipeline for."""
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> ref;

    @Schema(
        title = "CI/CD variables",
        description = """
            Variables passed to the pipeline as `env_var` variables, keyed by name."""
    )
    @PluginProperty(group = "main")
    private Property<Map<String, String>> variables;

    @Schema(
        title = "Wait for the pipeline to finish",
        description = """
            When true, the task polls the pipeline until it reaches `success`, `failed`, `canceled`, `skipped` or `manual`, \
            and fails on `failed` or `canceled`. Defaults to `false`."""
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<Boolean> wait = Property.ofValue(false);

    @Schema(
        title = "Poll interval",
        description = """
            Time between two status checks while waiting. Defaults to `PT10S`; the minimum is `PT1S`, \
            so the wait loop stays within the GitLab API rate limits."""
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<Duration> pollInterval = Property.ofValue(Duration.ofSeconds(10));

    @Schema(
        title = "Maximum wait duration",
        description = """
            How long to wait for the pipeline before the task fails, when `wait` is true. \
            Defaults to `PT1H`; must be a positive duration."""
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<Duration> maxWaitDuration = Property.ofValue(Duration.ofHours(1));

    @Schema(
        title = "Cancel the pipeline on timeout or kill",
        description = """
            When true (the default), the pipeline is canceled in GitLab when `maxWaitDuration` is reached or when the Kestra execution \
            is killed, including a kill that arrives while the pipeline is still being created."""
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<Boolean> cancelOnTimeout = Property.ofValue(true);

    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final transient AtomicBoolean killed = new AtomicBoolean(false);

    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final transient AtomicReference<Runnable> cancelOnKill = new AtomicReference<>();

    @Override
    public Pipeline run(RunContext runContext) throws Exception {
        var rRef = runContext.render(this.ref).as(String.class)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("`ref` is required: set a branch or tag name."));
        var rWait = runContext.render(this.wait).as(Boolean.class).orElse(false);
        var rCancel = runContext.render(this.cancelOnTimeout).as(Boolean.class).orElse(true);
        var rPollInterval = runContext.render(this.pollInterval).as(Duration.class).orElse(Duration.ofSeconds(10));
        var rMaxWait = runContext.render(this.maxWaitDuration).as(Duration.class).orElse(Duration.ofHours(1));
        if (rPollInterval.compareTo(MIN_POLL_INTERVAL) < 0) {
            throw new IllegalArgumentException("`pollInterval` must be at least %s, got %s.".formatted(MIN_POLL_INTERVAL, rPollInterval));
        }
        if (rMaxWait.isZero() || rMaxWait.isNegative()) {
            throw new IllegalArgumentException("`maxWaitDuration` must be a positive duration, got %s.".formatted(rMaxWait));
        }

        var body = new HashMap<String, Object>();
        body.put("ref", rRef);
        var rVariables = runContext.render(this.variables).asMap(String.class, String.class);
        if (!rVariables.isEmpty()) {
            body.put(
                "variables", rVariables.entrySet().stream()
                    .map(entry -> Map.of("key", entry.getKey(), "value", entry.getValue(), "variable_type", "env_var"))
                    .toList()
            );
        }

        try (var client = httpClient(runContext)) {
            var pipeline = pipelineRequest(runContext, client, "POST", "pipeline", JacksonMapper.ofJson().writeValueAsString(body));
            runContext.logger().info("Created GitLab pipeline {} on '{}' with status '{}': {}", pipeline.getId(), pipeline.getRef(), pipeline.getStatus(), pipeline.getWebUrl());

            var cancelResource = "pipelines/" + pipeline.getId() + "/cancel";
            if (rCancel) {
                cancelOnKill.set(() -> cancelQuietly(runContext, cancelResource));
            }

            try {
                if (killed.get()) {
                    // kill() arrived while the POST was in flight, before the cancel hook existed.
                    throw killedWhile(pipeline, "being created");
                }
                if (!rWait) {
                    return pipeline;
                }
                return waitForPipeline(runContext, client, pipeline, rPollInterval, rMaxWait);
            } finally {
                cancelOnKill.set(null);
            }
        }
    }

    private Pipeline waitForPipeline(RunContext runContext, HttpClient client, Pipeline pipeline, Duration rPollInterval, Duration rMaxWait) throws Exception {
        var deadline = Instant.now().plus(rMaxWait);
        var current = pipeline;
        while (!current.isTerminal()) {
            if (killed.get()) {
                throw killedWhile(pipeline, "running");
            }
            if (Instant.now().isAfter(deadline)) {
                // Take the hook so a concurrent kill() cannot cancel the pipeline a second time.
                var cancel = cancelOnKill.getAndSet(null);
                if (cancel != null) {
                    cancel.run();
                }
                throw new TimeoutException("GitLab pipeline %d did not finish within %s; last status was '%s'.".formatted(pipeline.getId(), rMaxWait, current.getStatus()));
            }

            Thread.sleep(rPollInterval.toMillis());
            current = pipelineRequest(runContext, client, "GET", "pipelines/" + pipeline.getId(), null);
            runContext.logger().debug("GitLab pipeline {} status: {}", current.getId(), current.getStatus());
        }

        if (current.isFailed()) {
            throw new IllegalStateException("GitLab pipeline %d finished with status '%s': %s".formatted(current.getId(), current.getStatus(), current.getWebUrl()));
        }

        runContext.logger().info("GitLab pipeline {} finished with status '{}'", current.getId(), current.getStatus());
        return current;
    }

    /**
     * Runs the cancel hook unless {@link #kill()} already took it, then builds the error thrown after a kill.
     */
    private IllegalStateException killedWhile(Pipeline pipeline, String phase) {
        var cancel = cancelOnKill.getAndSet(null);
        if (cancel != null) {
            cancel.run();
        }
        return new IllegalStateException("The execution was killed while GitLab pipeline %d was %s.".formatted(pipeline.getId(), phase));
    }

    private void cancelQuietly(RunContext runContext, String cancelResource) {
        try (var client = httpClient(runContext)) {
            var canceled = pipelineRequest(runContext, client, "POST", cancelResource, null);
            runContext.logger().warn("Canceled GitLab pipeline {} (status '{}')", canceled.getId(), canceled.getStatus());
        } catch (Exception e) {
            runContext.logger().error("Unable to cancel GitLab pipeline with '{}'.", cancelResource, e);
        }
    }

    @Override
    public void kill() {
        killed.set(true);
        var cancel = cancelOnKill.getAndSet(null);
        if (cancel != null) {
            cancel.run();
        }
    }
}
