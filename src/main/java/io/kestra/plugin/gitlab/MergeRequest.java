package io.kestra.plugin.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Create a GitLab merge request",
    description = "Create a new merger request in a GitLab project"
)
@Plugin(examples = {
    @Example(
        title = "Create a merge request in a GitLab project using a [project access token](https://docs.gitlab.com/ee/user/project/settings/project_access_tokens.html).",
        full = true,
        code = """
            id: gitlab_merge_request
            namespace: company.team

            tasks:
              - id: create_merge_request
                type: io.kestra.plugin.gitlab.MergeRequest
                url: https://gitlab.com
                token: "{{ secret('GITLAB_TOKEN') }}"
                projectId: "123"
                title: "Feature: Add new functionality"
                description: "This merge request adds new functionality to the project"
                sourceBranch: "feat-testing"
                targetBranch: "main"
            """
    )
})
public class MergeRequest extends   AbstractGitLabTask implements RunnableTask<MergeRequest.Output> {

    @Schema(title = "Merge request title")
    @PluginProperty(dynamic = true)
    private String title;

    @Schema(title = "Source branch")
    @PluginProperty(dynamic = true)
    private String sourceBranch;

    @Schema(title = "Target branch")
    @PluginProperty(dynamic = true)
    private String targetBranch;

    @Schema(title = "Merge request description")
    @PluginProperty(dynamic = true)
    private String description;




    @Override
    public Output run(RunContext runContext) throws Exception {

        try (HttpClient client = httpClient(runContext)) {

            Map<String, Object> body = new HashMap<>();

            // Required fields for  merge request creation
            if(this.title != null) {
                body.put("title", runContext.render(this.title));
            }

            if(this.sourceBranch != null) {
                body.put("source_branch", runContext.render(this.sourceBranch));
            }

            if(this.targetBranch != null) {
                body.put("target_branch", runContext.render(this.targetBranch));
            }

            // Optional fields
            if (this.description != null) {
                body.put("description", runContext.render(this.description));
            }

            ObjectMapper mapper = new ObjectMapper();
            String jsonBody = mapper.writeValueAsString(body);


            String endpoint = "/api/v4/projects/" + runContext.render(this.getProjectId()) + "/merge_requests";


            HttpRequest request = authenticatedRequestBuilder(endpoint, runContext)
                .method("POST")
                .body(new HttpRequest.StringRequestBody("application/json",StandardCharsets.UTF_8,jsonBody))
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
