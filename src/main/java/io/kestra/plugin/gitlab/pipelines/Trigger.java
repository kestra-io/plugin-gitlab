package io.kestra.plugin.gitlab.pipelines;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.*;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static io.kestra.core.models.triggers.StatefulTriggerService.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow when a GitLab pipeline finishes",
    description = """
        Polls the most recently updated pipelines of a project and starts one execution with every pipeline that reached one of the watched \
        statuses since the last poll. Pipelines already seen are kept in the trigger state, so each one fires once.

        The first poll, when the state is empty, only records the pipelines that already finished and starts no execution, \
        so the trigger does not fire on pipelines that ended before it was created.

        Every later poll asks GitLab only for the pipelines updated since the latest one recorded in the state (minus a one-minute overlap) \
        and pages through them up to `maxPipelines`; set `maxPipelines` above the number of pipelines updated during one `interval` \
        on busy projects so none is missed."""
)
@Plugin(
    examples = {
        @Example(
            title = "Log every pipeline of `main` that succeeded, failed or was canceled.",
            full = true,
            code = """
                id: gitlab_pipeline_finished
                namespace: company.team

                triggers:
                  - id: on_pipeline_finished
                    type: io.kestra.plugin.gitlab.pipelines.Trigger
                    token: "{{ secret('GITLAB_TOKEN') }}"
                    projectId: "12345"
                    ref: main
                    interval: PT1M

                tasks:
                  - id: handle_event
                    type: io.kestra.plugin.core.log.Log
                    message: "Pipeline {{ trigger.pipeline.id }} ended with {{ trigger.pipeline.status }}: {{ trigger.pipeline.webUrl }}"
                """
        ),
        @Example(
            title = "Alert on failed pipelines only, inspecting up to 50 updated pipelines per poll.",
            full = true,
            code = """
                id: gitlab_pipeline_failed
                namespace: company.team

                triggers:
                  - id: on_pipeline_failed
                    type: io.kestra.plugin.gitlab.pipelines.Trigger
                    token: "{{ secret('GITLAB_TOKEN') }}"
                    projectId: "12345"
                    statuses:
                      - FAILED
                    maxPipelines: 50
                    interval: PT2M

                tasks:
                  - id: log_failures
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ trigger.total }} pipeline(s) failed, latest: {{ trigger.pipeline.webUrl }}"
                """
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Trigger.Output>, StatefulTriggerInterface {
    static final int MAX_PIPELINES = 100;
    static final Duration UPDATED_AFTER_OVERLAP = Duration.ofMinutes(1);
    static final String SEEDED_KEY = "__seeded__";
    private static final int DEFAULT_MAX_PIPELINES = 20;

    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    @Schema(
        title = "GitLab API base URL",
        description = """
            Base URL of the GitLab instance; defaults to `https://gitlab.com`. Override for self-hosted installations."""
    )
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> url = Property.ofValue("https://gitlab.com");

    @Schema(
        title = "Access token used for API calls",
        description = """
            GitLab Personal/Project/Group Access Token with the `read_api` scope, sent as the PRIVATE-TOKEN header."""
    )
    @NotNull
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    private Property<String> token;

    @Schema(
        title = "Project ID or path",
        description = """
            Numeric project ID or URL-encoded project path."""
    )
    @NotNull
    @PluginProperty(group = "destination")
    private Property<String> projectId;

    @Schema(
        title = "Projects API path",
        description = """
            Projects API prefix appended before the project ID; defaults to `/api/v4/projects`."""
    )
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> apiPath = Property.ofValue("/api/v4/projects");

    @Schema(
        title = "Ref filter",
        description = """
            Only watch pipelines of this branch or tag."""
    )
    @PluginProperty(group = "main")
    private Property<String> ref;

    @Schema(
        title = "Watched statuses",
        description = """
            Pipeline statuses that start an execution. Defaults to `SUCCESS`, `FAILED` and `CANCELED`."""
    )
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<java.util.List<PipelineStatus>> statuses = Property.ofValue(
        java.util.List.of(PipelineStatus.SUCCESS, PipelineStatus.FAILED, PipelineStatus.CANCELED)
    );

    @Schema(
        title = "Maximum pipelines per poll",
        description = """
            Maximum number of updated pipelines inspected on each poll, most recently updated first. \
            Defaults to `20`; must be between `1` and `100`."""
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<@Min(1) @Max(MAX_PIPELINES) Integer> maxPipelines = Property.ofValue(DEFAULT_MAX_PIPELINES);

    @Schema(
        title = "State change mode",
        description = """
            `CREATE` fires once per pipeline, `UPDATE` also fires again when a watched pipeline changes status, for example after a retry."""
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<On> on = Property.ofValue(On.CREATE);

    @Schema(
        title = "State key",
        description = """
            Custom key used to store the pipelines already seen."""
    )
    @PluginProperty(group = "advanced")
    private Property<String> stateKey;

    @Schema(
        title = "State TTL",
        description = """
            How long the pipelines already seen are remembered. Defaults to `P7D` and must be at least twice the polling `interval`, \
            otherwise the state expires between two polls and new pipelines are never reported. The `updated_after` watermark \
            already keeps older pipelines from being listed again, so the state only needs to cover recent ones. \
            If the trigger is paused for longer than `stateTtl`, the state expires and the next poll starts over as a first poll: \
            it records the pipelines that finished meanwhile without starting an execution."""
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Duration> stateTtl = Property.ofValue(Duration.ofDays(7));

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var rStateKey = runContext.render(stateKey).as(String.class).orElse(StatefulTriggerService.defaultKey(context.getNamespace(), context.getFlowId(), id));
        var rTtl = runContext.render(stateTtl).as(Duration.class);
        if (rTtl.isPresent() && rTtl.get().compareTo(interval.multipliedBy(2)) < 0) {
            throw new IllegalArgumentException(
                "`stateTtl` must be at least twice the polling `interval` (%s), got %s.".formatted(interval.multipliedBy(2), rTtl.get())
            );
        }
        var rStatuses = runContext.render(statuses).asList(PipelineStatus.class).stream().map(PipelineStatus::apiValue).toList();
        var rOn = runContext.render(on).as(On.class).orElse(On.CREATE);
        var rMaxPipelines = AbstractPipelineTask.boundedInt(
            runContext.render(maxPipelines).as(Integer.class).orElse(DEFAULT_MAX_PIPELINES), "maxPipelines", 1, MAX_PIPELINES
        );

        var state = readState(runContext, rStateKey, rTtl);
        var seeding = !state.containsKey(SEEDED_KEY);
        var updatedAfter = seeding ? null
            : state.values().stream()
                .map(StatefulTriggerService.Entry::modifiedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .map(latest -> latest.minus(UPDATED_AFTER_OVERLAP))
                .orElse(null);

        var listed = List.builder()
            .id(this.id)
            .type(List.class.getName())
            .url(this.url)
            .token(this.token)
            .projectId(this.projectId)
            .apiPath(this.apiPath)
            .ref(this.ref)
            .updatedAfter(updatedAfter == null ? null : Property.ofValue(updatedAfter.toString()))
            .orderBy(Property.ofValue(PipelineOrderBy.UPDATED_AT))
            .limit(Property.ofValue(rMaxPipelines))
            .build()
            .fetch(runContext);

        var finished = new ArrayList<Pipeline>();
        for (var pipeline : listed) {
            if (pipeline.getStatus() == null || rStatuses.stream().noneMatch(pipeline.getStatus()::equalsIgnoreCase)) {
                continue;
            }

            var modifiedAt = Optional.ofNullable(pipeline.getUpdatedAt()).orElseGet(Instant::now);
            var candidate = StatefulTriggerService.Entry.candidate(String.valueOf(pipeline.getId()), pipeline.getStatus(), modifiedAt);
            if (computeAndUpdateState(state, candidate, rOn).fire()) {
                finished.add(pipeline);
            }
        }

        if (seeding) {
            // Pipelines updated after the newest one listed now are new to the trigger.
            var seededAt = listed.stream().map(Pipeline::getUpdatedAt).filter(Objects::nonNull).max(Instant::compareTo).orElseGet(Instant::now);
            state.put(SEEDED_KEY, new StatefulTriggerService.Entry(SEEDED_KEY, "1", seededAt, Instant.now()));
            writeState(runContext, rStateKey, state, rTtl);
            runContext.logger().info(
                "First poll: recorded {} finished GitLab pipeline(s) as already seen without starting an execution; later polls fire on newly finished pipelines.",
                finished.size()
            );
            return Optional.empty();
        }

        // Keep the marker alive and carry the newest update time, so the watermark survives entries expiring from the state.
        var latest = state.values().stream().map(StatefulTriggerService.Entry::modifiedAt).filter(Objects::nonNull).max(Instant::compareTo).orElseGet(Instant::now);
        state.put(SEEDED_KEY, new StatefulTriggerService.Entry(SEEDED_KEY, state.get(SEEDED_KEY).version(), latest, Instant.now()));
        writeState(runContext, rStateKey, state, rTtl);

        if (finished.isEmpty()) {
            return Optional.empty();
        }

        runContext.logger().info("{} GitLab pipeline(s) reached a watched status", finished.size());
        return Optional.of(
            TriggerService.generateExecution(
                this, conditionContext, context, Output.builder()
                    .pipeline(finished.getFirst())
                    .pipelines(finished)
                    .total(finished.size())
                    .build()
            )
        );
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "First pipeline",
            description = """
                The most recently updated pipeline that reached a watched status."""
        )
        private final Pipeline pipeline;

        @Schema(
            title = "Pipelines",
            description = """
                Every pipeline that reached a watched status since the last poll, most recently updated first."""
        )
        private final java.util.List<Pipeline> pipelines;

        @Schema(title = "Number of pipelines")
        private final Integer total;
    }
}
