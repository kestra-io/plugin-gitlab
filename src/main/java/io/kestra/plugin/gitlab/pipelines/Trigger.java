package io.kestra.plugin.gitlab.pipelines;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
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
    description = "Polls the most recently updated pipelines of a project and starts one execution with every pipeline that reached one of the watched statuses since the last poll. Pipelines already seen are kept in the trigger state, so each one fires once."
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
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Trigger.Output>, StatefulTriggerInterface {
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    @Schema(title = "GitLab API base URL", description = "Base URL of the GitLab instance; defaults to `https://gitlab.com`. Override for self-hosted installations.")
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> url = Property.ofValue("https://gitlab.com");

    @Schema(title = "Access token used for API calls", description = "GitLab Personal/Project/Group Access Token with the `read_api` scope, sent as the PRIVATE-TOKEN header.")
    @NotNull
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    private Property<String> token;

    @Schema(title = "Project ID or path", description = "Numeric project ID or URL-encoded project path.")
    @NotNull
    @PluginProperty(group = "destination")
    private Property<String> projectId;

    @Schema(title = "Projects API path", description = "Projects API prefix appended before the project ID; defaults to `/api/v4/projects`.")
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> apiPath = Property.ofValue("/api/v4/projects");

    @Schema(title = "Ref filter", description = "Only watch pipelines of this branch or tag.")
    @PluginProperty(group = "main")
    private Property<String> ref;

    @Schema(title = "Watched statuses", description = "Pipeline statuses that start an execution. Defaults to `success`, `failed` and `canceled`.")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<java.util.List<String>> statuses = Property.ofValue(java.util.List.of("success", "failed", "canceled"));

    @Schema(title = "Maximum pipelines per poll", description = "Number of most recently updated pipelines inspected on each poll.")
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<Integer> maxPipelines = Property.ofValue(20);

    @Schema(title = "State change mode", description = "`CREATE` fires once per pipeline, `UPDATE` also fires again when a watched pipeline changes status, for example after a retry.")
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<On> on = Property.ofValue(On.CREATE);

    @Schema(title = "State key", description = "Custom key used to store the pipelines already seen.")
    @PluginProperty(group = "advanced")
    private Property<String> stateKey;

    @Schema(title = "State TTL", description = "How long the pipelines already seen are remembered.")
    @PluginProperty(group = "advanced")
    private Property<Duration> stateTtl;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        String rStateKey = runContext.render(stateKey).as(String.class).orElse(StatefulTriggerService.defaultKey(context.getNamespace(), context.getFlowId(), id));
        Optional<Duration> rTtl = runContext.render(stateTtl).as(Duration.class);
        java.util.List<String> rStatuses = runContext.render(statuses).asList(String.class);
        On rOn = runContext.render(on).as(On.class).orElse(On.CREATE);

        List.Output listed = List.builder()
            .id(this.id)
            .type(List.class.getName())
            .url(this.url)
            .token(this.token)
            .projectId(this.projectId)
            .apiPath(this.apiPath)
            .ref(this.ref)
            .orderBy(Property.ofValue("updated_at"))
            .limit(this.maxPipelines)
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build()
            .run(runContext);

        var state = readState(runContext, rStateKey, rTtl);
        java.util.List<Pipeline> finished = new ArrayList<>();
        for (Pipeline pipeline : listed.getPipelines()) {
            if (pipeline.getStatus() == null || rStatuses.stream().noneMatch(pipeline.getStatus()::equalsIgnoreCase)) {
                continue;
            }

            Instant modifiedAt = Optional.ofNullable(pipeline.getUpdatedAt()).orElseGet(Instant::now);
            var candidate = StatefulTriggerService.Entry.candidate(String.valueOf(pipeline.getId()), pipeline.getStatus(), modifiedAt);
            if (computeAndUpdateState(state, candidate, rOn).fire()) {
                finished.add(pipeline);
            }
        }
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
        @Schema(title = "First pipeline", description = "The most recently updated pipeline that reached a watched status.")
        private final Pipeline pipeline;

        @Schema(title = "Pipelines", description = "Every pipeline that reached a watched status since the last poll, most recently updated first.")
        private final java.util.List<Pipeline> pipelines;

        @Schema(title = "Number of pipelines")
        private final Integer total;
    }
}
