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
    title = "Get a GitLab CI/CD pipeline",
    description = "Reads one pipeline of the project and returns its status, ref, commit, timing and URL."
)
@Plugin(
    examples = {
        @Example(
            title = "Read a pipeline and log its status.",
            full = true,
            code = """
                id: gitlab_pipeline_get
                namespace: company.team

                tasks:
                  - id: get_pipeline
                    type: io.kestra.plugin.gitlab.pipelines.Get
                    token: "{{ secret('GITLAB_TOKEN') }}"
                    projectId: "12345"
                    pipelineId: 987654

                  - id: log_status
                    type: io.kestra.plugin.core.log.Log
                    message: "Pipeline {{ outputs.get_pipeline.id }} on {{ outputs.get_pipeline.ref }} is {{ outputs.get_pipeline.status }}"
                """
        )
    }
)
public class Get extends AbstractPipelineTask implements RunnableTask<Pipeline> {
    @Schema(title = "Pipeline ID", description = "Global ID of the pipeline (the `id` output of `Create` or `List`, not the project-scoped `iid`).")
    @NotNull
    @PluginProperty(group = "main")
    private Property<Long> pipelineId;

    @Override
    public Pipeline run(RunContext runContext) throws Exception {
        Long rPipelineId = runContext.render(this.pipelineId).as(Long.class).orElseThrow();
        try (HttpClient client = httpClient(runContext)) {
            Pipeline pipeline = pipelineRequest(runContext, client, "GET", "pipelines/" + rPipelineId, null);
            runContext.logger().info("GitLab pipeline {} status: {}", pipeline.getId(), pipeline.getStatus());
            return pipeline;
        }
    }
}
