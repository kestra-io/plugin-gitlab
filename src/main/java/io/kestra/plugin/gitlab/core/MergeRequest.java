package io.kestra.plugin.gitlab.core;

import io.kestra.core.models.annotations.Alias;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a GitLab merge request.",
    description = "Create a new merge request in a GitLab project. " +
        "You need to provide a valid GitLab project ID and a personal access token with the necessary permissions."
)
@Plugin(
    aliases = {
        @Alias(value = "MergeRequest", namespace = "io.kestra.plugin.gitlab")
    },
    
    examples = {
    @Example(
        title = "Create a merge request in a GitLab project using a project access token.",
        full = true,
        code = """
            id: gitlab_merge_request
            namespace: company.team

            tasks:
              - id: create_merge_request
                type: io.kestra.plugin.gitlab.core.MergeRequest
                url: https://gitlab.example.com
                token: "{{ secret('GITLAB_TOKEN') }}"
                projectId: "123"
                title: "Feature: Add new functionality"
                mergeRequestDescription: "This merge request adds new functionality to the project"
                sourceBranch: "feat-testing"
                targetBranch: "main"
            """
    )
})



public class MergeRequest extends AbstractGitLabTask implements RunnableTask<MergeRequest.Output> {

    @Schema(title = "Merge request title")
    @NotNull
    private Property<String> title;

    @Schema(title = "Source branch")
    @NotNull
    private Property<String> sourceBranch;

    @Schema(title = "Target branch")
    @NotNull
    private Property<String> targetBranch;

    @Schema(title = "Merge request description")
    private Property<String> mergeRequestDescription;

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (HttpClient client = httpClient(runContext)) {

            Map<String, Object> body = new HashMap<>();

            // Required fields for  merge request creation
            body.put("title", runContext.render(this.title).as(String.class).orElseThrow());

            body.put("source_branch", runContext.render(this.sourceBranch).as(String.class).orElseThrow());

            body.put("target_branch", runContext.render(this.targetBranch).as(String.class).orElseThrow());

            // Optional fields
            if (this.mergeRequestDescription != null) {
                body.put("description", runContext.render(this.mergeRequestDescription).as(String.class).orElseThrow());
            }

            ObjectMapper mapper = new ObjectMapper();
            String jsonBody = mapper.writeValueAsString(body);
            String endpoint = buildApiEndpoint("merge_requests", runContext);

            HttpRequest request = authenticatedRequestBuilder(endpoint, runContext)
                .method("POST")
                .body(new HttpRequest.StringRequestBody("application/json", StandardCharsets.UTF_8, jsonBody))
                .build();

            HttpResponse<Map> response = client.request(request, Map.class);
            Map<String, Object> result = response.getBody();

            return Output.builder()
                .mergeReqID(result.get("id").toString())
                .webUrl(result.get("web_url").toString())
                .statusCode(response.getStatus().getCode())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Created merge request ID")
        private String mergeReqID;

        @Schema(title = "web URL")
        private String webUrl;

        @Schema(title = "HTTP status code")
        private Integer statusCode;
    }

}
