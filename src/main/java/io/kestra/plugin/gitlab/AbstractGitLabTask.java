package io.kestra.plugin.gitlab;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractGitLabTask extends Task {

    @Schema(
        title = "GitLab API base URL",
        description = "Base URL of the GitLab instance; defaults to `https://gitlab.com`. Override for self-hosted installations."
    )
    @Builder.Default
    private Property<String> url = Property.ofValue("https://gitlab.com");

    @Schema(
        title = "Access token used for API calls",
        description = "GitLab Personal/Project/Group Access Token sent as the PRIVATE-TOKEN header; requires scopes that cover the requested API operations. See the [GitLab Authentication docs](https://docs.gitlab.com/api/rest/authentication/)."
    )
    @NotNull
    private Property<String> token;

    @Schema(
        title = "Project ID or path",
        description = "Numeric project ID or URL-encoded project path rendered from the context to build API endpoints."
    )
    @NotNull
    private Property<String> projectId;

    @Schema(
        title = "Projects API path",
        description = "Projects API prefix appended before the project ID; defaults to `/api/v4/projects`. Override when fronting GitLab with a proxy or custom base path."
    )
    @Builder.Default
    private Property<String> apiPath = Property.ofValue("/api/v4/projects");

    protected HttpClient httpClient(RunContext runContext) throws IllegalVariableEvaluationException {

        HttpConfiguration config = null;
        return new HttpClient(runContext, config);
    }

    protected HttpRequest.HttpRequestBuilder authenticatedRequestBuilder(String endpoint, RunContext runContext) throws IllegalVariableEvaluationException {
        String baseUrl = runContext.render(this.url).as(String.class).orElse("https://gitlab.com");
        String renderedToken = runContext.render(this.token).as(String.class).orElseThrow();
        String fullUrl = baseUrl + endpoint;
        return HttpRequest.builder()
            .uri(URI.create(fullUrl))
            .addHeader("PRIVATE-TOKEN", renderedToken)
            .addHeader("Content-Type", "application/json");
    }

    protected String buildApiEndpoint(String resource, RunContext runContext) throws IllegalVariableEvaluationException {
        String renderedApiPath = runContext.render(this.apiPath).as(String.class).orElse("/api/v4/projects");
        String renderedProjectId = runContext.render(this.getProjectId()).as(String.class).orElseThrow();
        return renderedApiPath + "/" + renderedProjectId + "/" + resource;
    }

}
