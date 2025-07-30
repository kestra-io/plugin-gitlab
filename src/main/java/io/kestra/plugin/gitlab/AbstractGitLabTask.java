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

    @Schema(title = "GitLab URL", description = "GitLab URL")
    @Builder.Default
    private Property<String> url = Property.ofValue("https://gitlab.com");

    @Schema(title = "Personal Access Token", description = "GitLab Personal Access Token")
    @NotNull
    private Property<String> token;

    @Schema(title = "Project ID", description = "GitLab project ID")
    @NotNull
    private Property<String> projectId;

    @Schema(title = "API Path", description = "Custom API path for GitLab API endpoints")
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
