package io.kestra.plugin.gitlab.pipelines;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Cancel a GitLab CI/CD pipeline",
    description = """
        Cancels the running jobs of a pipeline and returns the pipeline as GitLab reports it after the request."""
)
@Plugin(
    examples = {
        @Example(
            title = "Cancel a pipeline started earlier in the flow.",
            full = true,
            code = """
                id: gitlab_pipeline_cancel
                namespace: company.team

                inputs:
                  - id: pipeline_id
                    type: INT

                tasks:
                  - id: cancel_pipeline
                    type: io.kestra.plugin.gitlab.pipelines.Cancel
                    token: "{{ secret('GITLAB_TOKEN') }}"
                    projectId: "12345"
                    pipelineId: "{{ inputs.pipeline_id }}"
                """
        )
    }
)
public class Cancel extends AbstractPipelineActionTask implements RunnableTask<Pipeline> {
    @Override
    public Pipeline run(RunContext runContext) throws Exception {
        var pipeline = pipelineAction(runContext, "POST", "/cancel");
        runContext.logger().info("Canceled GitLab pipeline {} (status '{}')", pipeline.getId(), pipeline.getStatus());
        return pipeline;
    }
}
