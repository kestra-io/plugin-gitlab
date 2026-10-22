package io.kestra.plugin.gitlab.pipelines;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
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
public abstract class AbstractPipelineActionTask extends AbstractPipelineTask {
    @Schema(
        title = "Pipeline ID",
        description = """
            Global ID of the pipeline (the `id` output of `Create` or `List`, not the project-scoped `iid`)."""
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<Long> pipelineId;

    protected Pipeline pipelineAction(RunContext runContext, String method, String suffix) throws Exception {
        var rPipelineId = runContext.render(this.pipelineId).as(Long.class)
            .orElseThrow(() -> new IllegalArgumentException("`pipelineId` is required: set the global ID of the pipeline."));
        try (var client = httpClient(runContext)) {
            return pipelineRequest(runContext, client, method, "pipelines/" + rPipelineId + suffix, null);
        }
    }
}
