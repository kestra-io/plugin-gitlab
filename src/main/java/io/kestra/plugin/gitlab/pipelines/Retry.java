package io.kestra.plugin.gitlab.pipelines;

import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;

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
    title = "Retry a GitLab CI/CD pipeline",
    description = "Retries the failed or canceled jobs of a pipeline and returns the pipeline as GitLab reports it after the request."
)
@Plugin(
    examples = {
        @Example(
            title = "Retry the failed jobs of a pipeline.",
            full = true,
            code = """
                id: gitlab_pipeline_retry
                namespace: company.team

                inputs:
                  - id: pipeline_id
                    type: INT

                tasks:
                  - id: retry_pipeline
                    type: io.kestra.plugin.gitlab.pipelines.Retry
                    token: "{{ secret('GITLAB_TOKEN') }}"
                    projectId: "12345"
                    pipelineId: "{{ inputs.pipeline_id }}"
                """
        )
    }
)
public class Retry extends AbstractPipelineTask implements RunnableTask<Pipeline> {
    @Schema(title = "Pipeline ID", description = "Global ID of the pipeline (the `id` output of `Create` or `List`, not the project-scoped `iid`).")
    @NotNull
    @PluginProperty(group = "main")
    private Property<Long> pipelineId;

    @Override
    public Pipeline run(RunContext runContext) throws Exception {
        Long rPipelineId = runContext.render(this.pipelineId).as(Long.class).orElseThrow();
        try (HttpClient client = httpClient(runContext)) {
            Pipeline pipeline = pipelineRequest(runContext, client, "POST", "pipelines/" + rPipelineId + "/retry", null);
            runContext.logger().info("Retried GitLab pipeline {} (status '{}')", pipeline.getId(), pipeline.getStatus());
            return pipeline;
        }
    }
}
