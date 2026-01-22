package io.kestra.plugin.gitlab.issues;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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
    title = "Create a GitLab issue.",
    description = "Create a new issue in a GitLab project. " +
        "You need to provide a valid GitLab project ID and an access token with the necessary permissions."
)
@Plugin(examples = {
    @Example(
        title = "Create an issue in a GitLab project using an access token.",
        full = true,
        code = """
            id: gitlab_create_issue
            namespace: company.team

            tasks:
              - id: create_issue
                type: io.kestra.plugin.gitlab.issues.Create
                token: "{{ secret('GITLAB_TOKEN') }}"
                projectId: "123"
                title: "Bug report"
                issueDescription: "Found a critical bug"
                labels:
                  - bug
                  - critical
            """
    ),
    @Example(
        title = "Create an issue with custom API path for self-hosted GitLab.",
        full = true,
        code = """
            id: gitlab_create_issue_self_hosted
            namespace: company.team

            tasks:
              - id: create_issue
                type: io.kestra.plugin.gitlab.issues.Create
                url: https://gitlab.example.com
                apiPath: /api/v4/projects
                token: "{{ secret('GITLAB_TOKEN') }}"
                projectId: "123"
                title: "Bug report"
                issueDescription: "Found a critical bug"
            """
    )
})
public class Create extends AbstractGitLabTask implements RunnableTask<Create.Output> {

    @Schema(title = "Issue title")
    @NotNull
    private Property<String> title;

    @Schema(title = "Issue description")
    private Property<String> issueDescription;

    @Schema(title = "Labels to assign to the issue")
    private Property<List<String>> labels;

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (HttpClient client = httpClient(runContext)) {

            Map<String, Object> body = new HashMap<>();
            body.put("title", runContext.render(this.title).as(String.class).orElseThrow());
            if (this.issueDescription != null) {
                body.put("description", runContext.render(this.issueDescription).as(String.class).orElseThrow());
            }
            if (this.labels != null) {
                List<String> renderedLabels = runContext.render(this.labels).asList(String.class);
                body.put("labels", renderedLabels);
            }
            ObjectMapper mapper = new ObjectMapper();
            String jsonBody = mapper.writeValueAsString(body);
            String endpoint = buildApiEndpoint("issues", runContext);

            HttpRequest request = authenticatedRequestBuilder(endpoint, runContext)
                .method("POST")
                .body(new HttpRequest.StringRequestBody("application/json",
                    StandardCharsets.UTF_8,
                    jsonBody))
                .build();

            HttpResponse<Map> response = client.request(request, Map.class);

            Map<String, Object> result = response.getBody();

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
        @Schema(title = "Issue ID")
        private String issueId;

        @Schema(title = "Issue URL")
        private String webUrl;

        @Schema(title = "HTTP status code")
        private Integer statusCode;
    }
}