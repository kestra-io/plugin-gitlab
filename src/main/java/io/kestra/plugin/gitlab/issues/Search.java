package io.kestra.plugin.gitlab.issues;

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
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search issues in a project",
    description = "Queries GitLab issues for the target project via the REST API. Requires `projectId` and `token`; `state` defaults to `opened`. Supports custom `url` and `apiPath` for self-hosted GitLab and renders templated values before the request."
)
@Plugin(examples = {
    @Example(
        title = "Search for issues in a GitLab project using an access token.",
        full = true,
        code = """
            id: gitlab_search_issues
            namespace: company.team

            tasks:
              - id: search_issues
                type: io.kestra.plugin.gitlab.issues.Search
                token: "{{ secret('GITLAB_TOKEN') }}"
                projectId: "123"
                search: "bug"
                state: "opened"
                labels:
                  - bug
                  - critical
            """
    ),
    @Example(
    title = "Search for issues in a GitLab project with custom API path for self-hosted GitLab.",
    full = true,
    code = """
        id: gitlab_search_issues
        namespace: company.team

        tasks:
            - id: search_issues
            type: io.kestra.plugin.gitlab.issues.Search
            url: https://gitlab.example.com
            apiPath: /api/v4/projects
            token: "{{ secret('GITLAB_TOKEN') }}"
            projectId: "123"
            search: "bug"
            state: "opened"
            labels:
                - bug
                - critical
        """
    )
})
public class Search extends AbstractGitLabTask implements RunnableTask<Search.Output> {

    @Schema(title = "Search query", description = "Free-text query matched against issue title and description.")
    private Property<String> search;

    @Schema(title = "Issue state", description = "Filter by state: opened, closed, or all; defaults to opened when not provided.")
    @Builder.Default
    private Property<String> state = Property.ofValue("opened");

    @Schema(title = "Labels to filter by", description = "Labels rendered from the context and comma-joined for the GitLab API.")
    private Property<List<String>> labels;

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (HttpClient client = httpClient(runContext)) {

            // Build the query params
            List<String> params = new ArrayList<>();
            if (this.search != null) {
                String rSearch = runContext.render(this.search).as(String.class).orElseThrow();
                params.add("search=" + URLEncoder.encode(rSearch, StandardCharsets.UTF_8));
            }
            String renderedState = runContext.render(this.state).as(String.class).orElse("opened");
            params.add("state=" + renderedState);
            if (this.labels != null) {
                List<String> renderedLabels = runContext.render(this.labels).asList(String.class);
                String labelStr = String.join(",", renderedLabels);
                params.add("labels=" + URLEncoder.encode(labelStr, StandardCharsets.UTF_8));
            }

            String queryStr = "?" + String.join("&", params);
            String endpoint = buildApiEndpoint("issues", runContext) + queryStr;

            // Create GET request
            HttpRequest request = authenticatedRequestBuilder(endpoint, runContext)
                .method("GET")
                .build();

            HttpResponse<List> response = client.request(request, List.class);
            List<Map<String, Object>> issues = response.getBody();

            return Output.builder()
                .issues(issues)
                .count(issues.size())
                .statusCode(response.getStatus().getCode())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Found issues")
        private List<Map<String, Object>> issues;

        @Schema(title = "Number of issues found", description = "Count of issues returned by the request.")
        private Integer count;

        @Schema(title = "HTTP status code", description = "HTTP response code from the GitLab API.")
        private Integer statusCode;
    }
}
