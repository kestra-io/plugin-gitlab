package io.kestra.plugin.gitlab.issues;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.gitlab.AbstractGitLabTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a GitLab issue",
    description = "Create a new issue in a GitLab project"
)
@Plugin(examples = {
    @Example(
        title = "Create an issue in a GitLab project using a [project access token](https://docs.gitlab.com/ee/user/project/settings/project_access_tokens.html).",
        full = true,
        code = """
            id: gitlab_create_issue
            namespace: company.team

            tasks:
              - id: create_issue
                type: io.kestra.plugin.gitlab.issues.CreateIssue
                url: https://gitlab.com
                token: "{{ secret('GITLAB_TOKEN') }}"
                projectId: "123"
                title: "Bug report"
                description: "Found a critical bug"
                labels: ["bug", "critical"]
            """
    )
})
public class CreateIssue extends AbstractGitLabTask implements RunnableTask<CreateIssue.Output> {

    @Schema(title = "Issue title")
    @PluginProperty(dynamic = true)
    @NotNull
    private String title;

    @Schema(title = "Issue description")
    @PluginProperty(dynamic = true)
    private String description;

    @Schema(title = "Labels to assign to the issue")
    @PluginProperty(dynamic = true)
    private List<String> labels;

    @Override
    public Output run(RunContext runContext) throws Exception {

        try (HttpClient client = httpClient(runContext)) {

            Map<String, Object> body = new HashMap<>();
            body.put("title", runContext.render(this.title));
            if (this.description != null) {
                body.put("description", runContext.render(this.description));
            }
            if (this.labels != null) {
                body.put("labels", runContext.render(this.labels));
            }

            ObjectMapper mapper = new ObjectMapper();
            String jsonBody = mapper.writeValueAsString(body);


            String endpoint = "/api/v4/projects/" + runContext.render(this.getProjectId()) + "/issues";

            HttpRequest request = authenticatedRequestBuilder(endpoint,runContext)
                .method("POST")
                .body(new HttpRequest.StringRequestBody("application/json",
                    StandardCharsets.UTF_8,
                    jsonBody))
                .build();

            HttpResponse<Map> response = client.request(request, Map.class);

            Map<String, Object>  result = response.getBody();

            return Output.builder()
                .issueId(result.get("id").toString())
                .webUrl(result.get("web_url").toString())
                .statusCode(response.getStatus().getCode())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Created issue ID")
        private String issueId;

        @Schema(title = "Issue web URL")
        private String webUrl;

        @Schema(title = "HTTP status code")
        private Integer statusCode;
    }
}
